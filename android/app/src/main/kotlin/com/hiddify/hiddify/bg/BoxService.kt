package com.hiddify.hiddify.bg

import com.hiddify.hiddify.nativecore.NativeCoreControl

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.libbox.PlatformInterface
import com.hiddify.core.libbox.SystemProxyStatus
import com.hiddify.core.mobile.Mobile
import com.hiddify.core.mobile.SetupOptions
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.R
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.Action
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.Status
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BoxService(
    private val service: Service,
    private val platformInterface: PlatformInterface,
) {

    companion object {
        private const val TAG = "A/BoxService"

        private val lifecycleMutex = Mutex()
        @Volatile private var coreOwner: BoxService? = null
        private var initializeOnce = false
        private lateinit var workingDir: File

        private fun initialize() {
            if (initializeOnce) return

            val baseDir = Application.application.filesDir.apply { mkdirs() }
            workingDir = (Application.application.getExternalFilesDir(null) ?: File(baseDir, "working")).apply { mkdirs() }
            val tempDir = Application.application.cacheDir.apply { mkdirs() }

            // Tile/boot startup can happen before Flutter writes these paths. Never pass "./"
            // into gomobile in that case.
            if (Settings.baseDir.isBlank() || Settings.baseDir == "./") Settings.baseDir = baseDir.path
            if (Settings.workingDir.isBlank() || Settings.workingDir == "./") Settings.workingDir = workingDir.path
            if (Settings.tempDir.isBlank() || Settings.tempDir == "./") Settings.tempDir = tempDir.path

            Log.d(TAG, "base dir: ${Settings.baseDir}")
            Log.d(TAG, "working dir: ${Settings.workingDir}")
            Log.d(TAG, "temp dir: ${Settings.tempDir}")

            for (path in listOf(Settings.baseDir, Settings.workingDir, Settings.tempDir)) {
                val directory = File(path)
                check((directory.isDirectory || directory.mkdirs()) && directory.canWrite()) { "unwritable core directory: $path" }
            }
            Libbox.redirectStderr(File(Settings.workingDir, "stderr.log").path)
            initializeOnce = true
        }

        suspend fun <T> withNativeLifecycle(action: suspend () -> T): T = lifecycleMutex.withLock { action() }

        fun currentPlatformInterface(): PlatformInterface? = coreOwner?.platformInterface

        fun isRunning(): Boolean = coreOwner?.status?.value in listOf(Status.Started, Status.Starting)

        fun isStarted(): Boolean = coreOwner?.status?.value == Status.Started

        // Unlike Activity/AIDL state, ownership is set before Mobile.setup() and cleared only
        // after Mobile.close() finishes. Use it as the native lifecycle barrier.
        fun hasActiveCore(): Boolean = coreOwner != null

        fun vpnProtection(): Map<String, Boolean?> {
            val vpn = coreOwner?.service as? VPNService
            return if (vpn != null) {
                mapOf("alwaysOn" to vpn.isAlwaysOn, "lockdown" to vpn.isLockdownEnabled)
            } else mapOf("alwaysOn" to null, "lockdown" to null)
        }

        fun start() {
            val intent = Intent(Application.application, Settings.serviceClass()).putExtra("started_by_app", true)
            Settings.connectionDesired = true
            ContextCompat.startForegroundService(Application.application, intent)
        }

        fun stop(preserveIntent: Boolean = false) {
            // Publish intent before the broadcast. A pending start that is holding the native
            // lifecycle mutex must see cancellation even if the receiver runs later.
            if (!preserveIntent) Settings.connectionDesired = false
            Application.application.sendBroadcast(
                Intent(Action.SERVICE_CLOSE).setPackage(Application.application.packageName).putExtra("preserve_intent", preserveIntent),
            )
        }
    }

    var fileDescriptor: ParcelFileDescriptor? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var destroyed = false
    private val status = MutableLiveData(Status.Stopped)
    private val binder = ServiceBinder(status)
    private val notification = ServiceNotification(service)
    private var receiverRegistered = false
    private var packagesReceiverRegistered = false
    private val packagesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            if (Settings.privacyRoutingMode == "off" || Settings.privacyFullTunnel) return
            Settings.startCoreAfterStartingService = true
            serviceReload()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.SERVICE_CLOSE -> {
                    val preserveIntent = intent.getBooleanExtra("preserve_intent", false)
                    if (!preserveIntent) Settings.connectionDesired = false
                    stopService(preserveIntent)
                }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    serviceUpdateIdleMode()
                }
            }
        }
    }

    private var activeProfileName = ""
    @Volatile private var stopRequested = false
    @Volatile private var nativeStarting = false
    @Volatile private var startCancellation: kotlinx.coroutines.Job? = null
    @Volatile private var rootActive = false
    private var rootMonitor: kotlinx.coroutines.Job? = null

    private suspend fun finishCancelledStart(reason: String) {
        startCancellation?.join()
        if (!Settings.connectionDesired) Settings.startedByUser = false
        runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
        val closeError = runCatching { releaseNative(reason) }.exceptionOrNull()
        closeTun(reason)
        withContext(Dispatchers.Main) {
            if (destroyed) return@withContext
            unregisterReceiver()
            notification.close()
            if (closeError != null) {
                Log.e(TAG, "failed to close cancelled native start", closeError)
                binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
            }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    private suspend fun startService() {
        try {
            if (destroyed) return
            coreOwner?.takeIf { it !== this }?.releaseNative("replacement")
            if (!Settings.connectionDesired || stopRequested) {
                finishCancelledStart("cancelled before setup")
                return
            }
            initialize()
            status.postValue(Status.Starting)
            Log.d(TAG, "starting service")

            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_starting)
            }

            val selectedConfigPath = Settings.activeConfigPath
            if (selectedConfigPath.isBlank()) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            activeProfileName = Settings.activeProfileName

            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_starting)
                binder.broadcast { it.onServiceResetLogs(listOf()) }
            }

            if (!Settings.connectionDesired || stopRequested) {
                finishCancelledStart("cancelled before native setup")
                return
            }
            coreOwner = this
            DefaultNetworkMonitor.start()
            if (Settings.privacyUseRoot) {
                rootActive = true
                com.hiddify.hiddify.privacy.RootCore.start(service)
                if (!Settings.connectionDesired || stopRequested) {
                    finishCancelledStart("cancelled during root setup")
                    return
                }
                rootMonitor = serviceScope.launch {
                    while (com.hiddify.hiddify.privacy.RootCore.isAlive()) kotlinx.coroutines.delay(1000)
                    serviceScope.launch {
                        lifecycleMutex.withLock {
                            if (rootActive && !destroyed && coreOwner === this@BoxService) {
                                stopAndAlert(Alert.StartService, "Root core exited; connection stopped")
                            }
                        }
                    }
                }
            } else {
                Libbox.setMemoryLimit(!Settings.disableMemoryLimit)

                try {
                    Mobile.setup(
                        SetupOptions().also {
                            val memoryClass = (service.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
                            it.memoryLimit = memoryClass.coerceIn(128, 512).toLong() * 1024L * 1024L
                            it.basePath = Settings.baseDir
                            it.workingDir = Settings.workingDir
                            it.tempDir = Settings.tempDir
                            // Required by the Go runtime on every supported Android version.
                            it.fixAndroidStack = true
                            it.mode = 4L
                            it.listen = "127.0.0.1:${Settings.grpcServiceModePort}"
                            it.secret = Settings.grpcAuthToken
                            it.debug = Settings.debugMode
                        },
                        platformInterface,
                    )
                    if (destroyed) return
                } catch (e: Exception) {
                    // setup() can fail after allocating native state. Always make a best-effort close
                    // before tearing down Android network callbacks so a later setup starts cleanly.
                    stopAndAlert(Alert.CreateService, e.message)
                    return
                }

                if (!Settings.connectionDesired || stopRequested) {
                    finishCancelledStart("cancelled during native setup")
                    return
                }
                try {
                    NativeCoreControl.applyStoredSettings(service)
                } catch (error: Exception) {
                    stopAndAlert(Alert.CreateService, "Unable to apply core settings: ${error.message ?: error.javaClass.simpleName}")
                    return
                }
                val proxyPrivacy = com.hiddify.hiddify.privacy.NetworkPrivacySettings.loadProxyPrivacy(service)
                Mobile.applyDevicePrivacy(Settings.privacyFullTunnel,
                    Settings.privacyHideLocalProxy,
                    proxyPrivacy.hideClashApi, proxyPrivacy.disableSystemProxy, Settings.privacyEncryptedDns)
                val stored = org.json.JSONObject(Settings.configOptions.ifBlank { "{}" })
                val policy = org.json.JSONObject()
                com.hiddify.hiddify.privacy.RegionalRouting.policy(service, stored.optString("region", "other")).forEach { (key, value) ->
                    policy.put(key, if (value is List<*>) org.json.JSONArray(value) else value)
                }
                Mobile.applyRegionalPrivacy(policy.toString())
                if (Settings.startCoreAfterStartingService) {
                    // Stop() cancels the Go startup context before taking its lifecycle lock.
                    // It must be issued concurrently: waiting for our mutex first cannot abort Start().
                    nativeStarting = true
                    try {
                        if (!Settings.connectionDesired || stopRequested) {
                            nativeStarting = false
                            finishCancelledStart("cancelled before native start")
                            return
                        }
                        Mobile.start(selectedConfigPath, "")
                    } finally { nativeStarting = false }
                    if (!Settings.connectionDesired || stopRequested) {
                        finishCancelledStart("cancelled during native start")
                        return
                    }
                }

            }

            if (destroyed) return
            if (stopRequested || !Settings.connectionDesired) {
                finishCancelledStart("cancelled before publishing started")
                return
            }
            Settings.nativeAppliedQuickSettings = Settings.quickSettingsSignature(service)
            Settings.nativeReconnectRequired = false
            status.postValue(Status.Started)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_started)
            }
            notification.start()
        } catch (e: Exception) {
            if (stopRequested || !Settings.connectionDesired) finishCancelledStart("cancelled startup")
            else stopAndAlert(Alert.StartService, e.message)
        }
    }

    fun serviceReload() {
        serviceScope.launch {
            try { serviceReload0() } catch (e: Exception) {
                Log.e(TAG, "reload failed", e)
                lifecycleMutex.withLock { stopAndAlert(Alert.StartService, e.message) }
            }
        }
    }

    suspend fun serviceReload0() {
        lifecycleMutex.withLock {
            if (destroyed || coreOwner !== this@BoxService) return
            status.postValue(Status.Starting)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_starting)
            }

            DefaultNetworkMonitor.detachCoreListener()
            val closeError = runCatching { closeCore() }.exceptionOrNull()
            if (closeError != null) {
                Log.e(TAG, "failed to close mobile core for reload", closeError)
                status.postValue(Status.Started)
                withContext(Dispatchers.Main) {
                    if (destroyed) return@withContext
                    notification.show(activeProfileName, R.string.status_started)
                    binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
                }
                return
            }
            closeTun("reload")
            coreOwner = null
            runCatching { DefaultNetworkMonitor.stop() }
                .onFailure { Log.w(TAG, "failed to stop network monitor for reload", it) }
            startService()
        }
    }

    fun getSystemProxyStatus(): SystemProxyStatus {
        val proxyStatus = SystemProxyStatus()
        if (service is VPNService) {
            proxyStatus.available = service.systemProxyAvailable
            proxyStatus.enabled = service.systemProxyEnabled
        }
        return proxyStatus
    }

    fun setSystemProxyEnabled(isEnabled: Boolean) {
        Settings.systemProxyEnabled = isEnabled
        serviceReload()
    }

    private fun serviceUpdateIdleMode() {
        if (!rootActive && !Application.powerManager.isDeviceIdleMode) {
            runCatching { Mobile.wake() }
                .onFailure { Log.w(TAG, "failed to wake mobile core", it) }
        }
    }

    private fun cancelNativeStartup() {
        if (startCancellation?.isActive != true && nativeStarting && coreOwner === this && !rootActive) {
            startCancellation = serviceScope.launch {
                com.hiddify.hiddify.nativeconnection.NativeStartupCancellation.cancelWhileStarting(
                    isStarting = { nativeStarting && coreOwner === this@BoxService },
                    stopNative = {
                        runCatching { Mobile.stop() }
                            .onFailure { Log.w(TAG, "native startup cancellation failed", it) }
                    },
                )
            }
        }
    }

    private fun stopService(preserveIntent: Boolean = false) {
        if (destroyed || status.value == Status.Stopping) return
        if (status.value == Status.Stopped && coreOwner !== this) return

        stopRequested = true
        status.value = Status.Stopping
        cancelNativeStartup()
        serviceScope.launch {
            lifecycleMutex.withLock {
                if (destroyed) return@withLock
                startCancellation?.join()

                // Keep Android network discovery alive until gomobile has actually stopped using it.
                if (coreOwner !== this@BoxService) {
                    if (!preserveIntent) Settings.startedByUser = false
                    withContext(Dispatchers.Main) {
                        status.value = Status.Stopped
                        unregisterReceiver()
                        notification.close()
                        service.stopSelf()
                    }
                    return@withLock
                }
                if (!preserveIntent) runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
                    .onFailure { Log.w(TAG, "hotspot cleanup deferred", it) }
                DefaultNetworkMonitor.detachCoreListener()
                val closeError = runCatching { closeCore() }.exceptionOrNull()
                closeTun("stop")
                runCatching { DefaultNetworkMonitor.stop() }
                    .onFailure { Log.w(TAG, "failed to stop network monitor", it) }
                if (!preserveIntent) Settings.startedByUser = false

                if (closeError == null) {
                    coreOwner = null
                } else {
                    // Keep ownership until onDestroy retries the native close, but never keep a
                    // dead TUN/network monitor alive after the user requested a stop.
                    Log.e(TAG, "failed to close mobile core", closeError)
                }

                withContext(Dispatchers.Main) {
                    if (closeError != null) {
                        binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
                    }
                    unregisterReceiver()
                    notification.close()
                    status.value = Status.Stopped
                    service.stopSelf()
                }
            }
        }
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        if (destroyed) return
        if (stopRequested || !Settings.connectionDesired) {
            finishCancelledStart("cancelled before reporting error")
            return
        }
        startCancellation?.join()
        // Initial setup/configuration failures require user correction, not repeated retries.
        if (status.value != Status.Started) Settings.connectionDesired = false
        if (!Settings.connectionDesired) Settings.startedByUser = false
        runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
        val closeError = runCatching { releaseNative("service error") }.exceptionOrNull()
        closeTun("service error")

        withContext(Dispatchers.Main) {
            unregisterReceiver()
            notification.close()
            binder.broadcast { callback -> callback.onServiceAlert(type.ordinal, message ?: closeError?.message) }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    private suspend fun closeCore() {
        rootMonitor?.cancel(); rootMonitor = null
        if (rootActive) {
            com.hiddify.hiddify.privacy.RootCore.stop()
            rootActive = false
        } else {
            try { Mobile.close(4L) } catch (firstError: Exception) {
                // Go closes the control server even when Stop returns a teardown error. A second
                // idempotent close can release ownership instead of waiting for a bound service
                // to be destroyed (stopSelf alone does not destroy a service still bound by UI).
                Log.w(TAG, "retrying native close after teardown error", firstError)
                try { Mobile.close(4L) } catch (retryError: Exception) {
                    firstError.addSuppressed(retryError)
                    throw firstError
                }
            }
        }
    }

    private fun closeTun(reason: String) {
        runCatching { fileDescriptor?.close() }
            .onFailure { Log.w(TAG, "failed to close TUN during $reason", it) }
        fileDescriptor = null
    }

    @Suppress("SameReturnValue")
    internal fun onStartCommand(): Int {
        if (destroyed) return Service.START_NOT_STICKY
        if (status.value != Status.Stopped) return if (Settings.connectionDesired) Service.START_STICKY else Service.START_NOT_STICKY
        stopRequested = false
        startCancellation = null
        status.value = Status.Starting
        try {
            // Android's foreground deadline starts before IO/setup, not after it.
            notification.show(Settings.activeProfileName, R.string.status_starting)
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(
                    service, receiver,
                    IntentFilter().apply {
                        addAction(Action.SERVICE_CLOSE)
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                receiverRegistered = true
                ContextCompat.registerReceiver(service, packagesReceiver, IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
                packagesReceiverRegistered = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "foreground registration failed", e)
            Settings.connectionDesired = false
            Settings.startedByUser = false
            binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, e.message ?: e.javaClass.simpleName) }
            status.value = Status.Stopped
            unregisterReceiver()
            notification.close()
            service.stopSelf()
            return Service.START_NOT_STICKY
        }
        serviceScope.launch {
            lifecycleMutex.withLock {
                if (destroyed) return@withLock
                try {
                    if (!Settings.connectionDesired) {
                        finishCancelledStart("cancelled before lifecycle start")
                        return@withLock
                    }
                    Settings.startedByUser = true
                    startService()
                } catch (e: Exception) {
                    Log.e(TAG, "service initialization failed", e)
                    stopAndAlert(Alert.CreateService, e.message)
                }
            }
        }
        return if (Settings.connectionDesired) Service.START_STICKY else Service.START_NOT_STICKY
    }

    // All owners share the mutex. An old onDestroy must never close a new owner's core.
    private suspend fun releaseNative(reason: String) {
        if (coreOwner !== this@BoxService) return
        DefaultNetworkMonitor.detachCoreListener()
        var closeError: Exception? = null
        try {
            closeCore()
        } catch (e: Exception) {
            closeError = e
            Log.e(TAG, "failed to close native core during $reason", e)
        } finally {
            // Android resources must always be released even when gomobile close fails. Keeping
            // the owner on failure lets onDestroy/replacement retry the native close later.
            closeTun(reason)
            runCatching { DefaultNetworkMonitor.stop() }
                .onFailure { Log.w(TAG, "network cleanup failed", it) }
            if (closeError == null) coreOwner = null
        }
        closeError?.let { throw it }
    }

    fun onBind(intent: Intent): IBinder = binder

    fun onDestroy() {
        if (destroyed) return
        stopRequested = true
        cancelNativeStartup()
        destroyed = true
        unregisterReceiver()
        notification.destroy()
        binder.close()
        // Never block Android main, and do not cancel a blocking setup before its cleanup
        // has acquired the lifecycle lock and released native state.
        serviceScope.launch {
            try {
                lifecycleMutex.withLock {
                    startCancellation?.join()
                    if (coreOwner === this@BoxService && !Settings.connectionDesired) {
                        runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
                    }
                    releaseNative("destroy")
                    closeTun("destroy")
                }
            } catch (e: Exception) {
                Log.e(TAG, "destroy cleanup failed", e)
            } finally {
                serviceScope.cancel()
            }
        }
    }

    fun onRevoke() {
        Settings.connectionDesired = false
        stopService()
    }

    private fun unregisterReceiver() {
        if (packagesReceiverRegistered) {
            runCatching { service.unregisterReceiver(packagesReceiver) }
            packagesReceiverRegistered = false
        }
        if (!receiverRegistered) return
        runCatching { service.unregisterReceiver(receiver) }
            .onFailure { Log.w(TAG, "receiver was already unregistered", it) }
        receiverRegistered = false
    }

    fun writeDebugMessage(message: String?) {
        if (message == null) return
        Log.d(TAG, message)
        binder.broadcast { it.onServiceWriteLog(message) }
    }
}

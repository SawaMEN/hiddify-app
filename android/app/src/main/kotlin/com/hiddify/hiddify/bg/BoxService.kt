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
import com.hiddify.core.mobile.Mobile
import com.hiddify.core.mobile.SetupOptions
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.R
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.Action
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.Status
import java.io.File
import android.net.VpnService
import com.hiddify.hiddify.nativeconnection.NativeHealthProbePolicy
import com.hiddify.hiddify.nativeconnection.NativeHealthRepository
import com.hiddify.hiddify.nativeconnection.NativeInternetHealth
import com.hiddify.hiddify.nativeconnection.NativeStartupDeadline
import com.hiddify.hiddify.nativeconnection.NativeRecoveryPolicy
import com.hiddify.hiddify.nativeconnection.NativeServiceState
import com.hiddify.hiddify.nativecore.NativeStatsFeed
import com.hiddify.hiddify.privacy.NetworkPrivacySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

            com.hiddify.hiddify.nativecore.NativeCoreLibrary.ensureLoaded()

            val baseDir = Application.application.filesDir.apply { mkdirs() }
            workingDir = (Application.application.getExternalFilesDir(null) ?: File(baseDir, "working")).apply { mkdirs() }
            val tempDir = Application.application.cacheDir.apply { mkdirs() }

            // Tile/boot startup can precede the activity. Never pass "./" to gomobile.
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

        fun refreshConnectionPolicy() { coreOwner?.let { owner -> owner.serviceScope.launch { owner.startConnectionMonitor() } } }

        fun start() {
            val intent = Intent(Application.application, Settings.serviceClass()).putExtra("started_by_app", true)
            Settings.connectionDesired = true
            Settings.startCoreAfterStartingService = true
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

    @Volatile var fileDescriptor: ParcelFileDescriptor? = null

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
            val perApp = Settings.perAppProxyEnabled
            if (!perApp && (Settings.privacyRoutingMode == "off" || Settings.privacyFullTunnel)) return
            serviceScope.launch {
                try {
                    if (perApp) com.hiddify.hiddify.nativerouting.NativePerAppRepository(service).snapshot()
                    Settings.startCoreAfterStartingService = true
                    serviceReload()
                } catch (error: Exception) { Log.w(TAG, "Cannot update application routing after package change", error) }
            }
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

    private var monitorJob: Job? = null
    private var recoveryJob: Job? = null
    private val recoveryPolicy = NativeRecoveryPolicy()
    private var lastGoodProfile: String? = null
    private var lastGoodOptions: String? = null
    private var connectedAt = 0L
    private var activeProfileName = ""
    @Volatile private var stopRequested = false
    @Volatile private var nativeStarting = false
    @Volatile private var nativeStartupTimedOut = false
    @Volatile private var startCancellation: kotlinx.coroutines.Job? = null
    @Volatile private var rootActive = false
    private var rootMonitor: kotlinx.coroutines.Job? = null

    private suspend fun finishCancelledStart(reason: String) {
        startCancellation?.join()
        if (!Settings.connectionDesired) Settings.startedByUser = false
        NativeServiceState.reset()
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
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
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
        try {
            if (destroyed) return
            coreOwner?.takeIf { it !== this }?.let { previous ->
                previous.stopRequested = true
                previous.releaseNative("replacement")
                withContext(Dispatchers.Main) {
                    previous.unregisterReceiver()
                    previous.notification.close()
                    previous.status.value = Status.Stopped
                    previous.service.stopSelf()
                }
            }
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
            if (!File(selectedConfigPath).isFile) {
                stopAndAlert(Alert.EmptyConfiguration, "Selected profile file is missing: refresh or re-import the profile")
                return
            }

            activeProfileName = Settings.activeProfileName

            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_starting)
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
                    nativeStartupTimedOut = false
                    // Mobile.start() is a blocking JNI call. A separate IO coroutine can
                    // cancel Go's startup context even when Psiphon bootstrap stalls.
                    val startupWatchdog = serviceScope.launch {
                        delay(NativeStartupDeadline.TIMEOUT_MS)
                        if (NativeStartupDeadline.shouldAbort(
                                starting = nativeStarting,
                                stopRequested = stopRequested,
                                connectionDesired = Settings.connectionDesired,
                                ownsCore = coreOwner === this@BoxService,
                            )) {
                            nativeStartupTimedOut = true
                            Log.e(TAG, "Native VPN core startup exceeded ${NativeStartupDeadline.TIMEOUT_MS} ms; cancelling")
                            runCatching { Mobile.stop() }
                                .onFailure { Log.e(TAG, "Unable to cancel stalled core startup", it) }
                        }
                    }
                    try {
                        if (!Settings.connectionDesired || stopRequested) {
                            nativeStarting = false
                            finishCancelledStart("cancelled before native start")
                            return
                        }
                        Mobile.start(selectedConfigPath, "")
                        if (nativeStartupTimedOut) error("VPN core connection timed out during startup (90 seconds); check Psiphon bootstrap/network logs")
                        // Mobile.start builds a StartRequest with disable_memory_limit=false,
                        // overriding the flag applied before setup. Restore the requested policy.
                        Libbox.setMemoryLimit(!Settings.disableMemoryLimit)
                        // A successful core start is not enough for Android VPN mode:
                        // when enable-tun is missing the core can start only proxy listeners.
                        // Never display Connected unless Android actually established a TUN.
                        if (service is VPNService && fileDescriptor == null) {
                            error("Android VPN interface was not created by the core (missing TUN inbound)")
                        }
                    } finally {
                        nativeStarting = false
                        startupWatchdog.cancel()
                    }
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
            lastGoodProfile = Settings.activeConfigPath
            lastGoodOptions = Settings.configOptions
            connectedAt = android.os.SystemClock.elapsedRealtime()
            NativeServiceState.recovery(0)
            startConnectionMonitor()
        } catch (e: Exception) {
            if (stopRequested || !Settings.connectionDesired) finishCancelledStart("cancelled startup")
            else stopAndAlert(Alert.StartService, if (nativeStartupTimedOut) {
                "VPN core startup timed out after 90 seconds; inspect Psiphon and network logs"
            } else e.message)
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
            monitorJob?.cancel(); monitorJob = null
            NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
            status.postValue(Status.Starting)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_starting)
            }

            DefaultNetworkMonitor.detachCoreListener()
            val closeError = runCatching { closeCore() }.exceptionOrNull()
            if (closeError != null) {
                Log.e(TAG, "failed to close mobile core for reload", closeError)
                stopAndAlert(Alert.StartService, closeError.message)
                return
            }
            closeTun("reload")
            coreOwner = null
            runCatching { DefaultNetworkMonitor.stop() }
                .onFailure { Log.w(TAG, "failed to stop network monitor for reload", it) }
            startService()
        }
    }

    private fun serviceUpdateIdleMode() {
        serviceScope.launch {
            lifecycleMutex.withLock {
                if (!destroyed && coreOwner === this@BoxService && !rootActive &&
                    !Application.powerManager.isDeviceIdleMode) {
                    runCatching { Mobile.wake() }
                        .onFailure { Log.w(TAG, "failed to wake mobile core", it) }
                }
            }
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
        recoveryJob?.cancel(); recoveryJob = null
        monitorJob?.cancel(); monitorJob = null
        NativeServiceState.reset()
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
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

    private fun recoveryAllowed(type: Alert): Boolean {
        val options = NetworkPrivacySettings.loadConnection(service)
        val permission = service !is VPNService || Settings.privacyUseRoot || VpnService.prepare(service) == null
        // Never loop on a first-use error or on newly edited invalid configuration.
        return type == Alert.StartService && NativeRecoveryPolicy.canRecover(
            lastGoodProfile != null,
            lastGoodProfile == Settings.activeConfigPath && lastGoodOptions == Settings.configOptions,
            options.recoveryEnabled, Settings.connectionDesired, Settings.startedByUser, permission, stopRequested, destroyed)
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        if (destroyed) return
        if (stopRequested || !Settings.connectionDesired) {
            finishCancelledStart("cancelled before reporting error")
            return
        }
        monitorJob?.cancel(); monitorJob = null
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
        startCancellation?.join()
        Log.e(TAG, "VPN/core failed ($type): ${message ?: "unknown error"}")
        val recover = recoveryAllowed(type) && recoveryPolicy.nextDelaySeconds(NetworkPrivacySettings.loadConnection(service).adaptiveNetwork) != null
        if (!recover) { Settings.connectionDesired = false; Settings.startedByUser = false }
        val closeError = runCatching { releaseNative("service error") }.exceptionOrNull()
        closeTun("service error")
        if (recover && closeError == null && Settings.connectionDesired && !stopRequested) {
            withContext(Dispatchers.Main) {
                status.value = Status.Starting
                notification.show(activeProfileName, R.string.status_starting)
            }
            scheduleServiceRecovery()
            return
        }
        Settings.connectionDesired = false
        Settings.startedByUser = false
        NativeServiceState.reset()
        runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
        withContext(Dispatchers.Main) {
            unregisterReceiver()
            notification.close()
            binder.broadcast { callback -> callback.onServiceAlert(type.ordinal, message ?: closeError?.message) }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    private fun scheduleServiceRecovery() {
        if (recoveryJob?.isActive == true) return
        recoveryJob = serviceScope.launch {
            try {
                while (isActive && !destroyed && !stopRequested && Settings.connectionDesired) {
                    val options = NetworkPrivacySettings.loadConnection(service)
                    val wait = recoveryPolicy.nextDelaySeconds(options.adaptiveNetwork)
                    if (!recoveryAllowed(Alert.StartService) || wait == null) {
                        lifecycleMutex.withLock {
                            stopAndAlert(Alert.StartService, service.getString(R.string.native_recovery_exhausted))
                        }
                        break
                    }
                    NativeServiceState.recovery(recoveryPolicy.attempts + 1)
                    if (!NativeHealthRepository().underlyingNetworkAvailable()) { delay(15_000); continue }
                    delay(wait * 1000)
                    lifecycleMutex.withLock {
                        if (!recoveryAllowed(Alert.StartService) || coreOwner != null || !isActive) return@withLock
                        recoveryPolicy.recordAttempt()
                        withContext(Dispatchers.Main) { status.value = Status.Starting }
                        startService()
                    }
                    if (coreOwner === this@BoxService && status.value == Status.Started) break
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: Exception) {
                Log.e(TAG, "service recovery failed", error)
                lifecycleMutex.withLock {
                    stopAndAlert(Alert.CreateService, error.message)
                }
            } finally { NativeServiceState.recovery(0) }
        }
    }

    private fun startConnectionMonitor() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val repository = NativeHealthRepository()
            var coreFailures = 0
            var policy = NativeHealthProbePolicy(false)
            delay(3000)
            while (isActive && !destroyed && coreOwner === this@BoxService && status.value == Status.Started) {
                try {
                if (android.os.SystemClock.elapsedRealtime() - connectedAt >= 60_000) recoveryPolicy.reset()
                val options = NetworkPrivacySettings.loadConnection(service)
                if (policy.adaptive != options.adaptiveNetwork) policy = NativeHealthProbePolicy(options.adaptiveNetwork)
                if (!repository.underlyingNetworkAvailable()) {
                    NativeServiceState.health(NativeInternetHealth.UNAVAILABLE)
                    coreFailures = 0
                    delay(15_000)
                    continue
                }
                NativeServiceState.health(NativeInternetHealth.CHECKING)
                val url = runCatching { com.hiddify.hiddify.nativecore.NativeGeneralOptionsRepository(service).load().testUrl }
                    .getOrDefault("https://www.gstatic.com/generate_204")
                val started = android.os.SystemClock.elapsedRealtime()
                val healthy = repository.probe(url, policy.timeoutSeconds)
                policy.record(healthy, android.os.SystemClock.elapsedRealtime() - started)
                NativeServiceState.health(if (healthy) NativeInternetHealth.AVAILABLE else NativeInternetHealth.PROBE_FAILED)
                if (healthy) coreFailures = 0 else {
                    // Probe-site failure alone must never tear down a working VPN.
                    val responds = try { com.hiddify.hiddify.nativecore.NativeStatsRepository().load(); true }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { false }
                    coreFailures = if (responds) 0 else coreFailures + 1
                }
                if (options.recoveryEnabled && coreFailures >= 3 && recoveryAllowed(Alert.StartService)) {
                    serviceScope.launch {
                        try {
                            lifecycleMutex.withLock {
                                if (!destroyed && coreOwner === this@BoxService && !stopRequested && Settings.connectionDesired)
                                    stopAndAlert(Alert.StartService, "Core control channel is unavailable")
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                          catch (error: Exception) { Log.e(TAG, "Could not recover failed core control channel", error) }
                    }
                    break
                }
                delay(if (healthy) maxOf(60L, policy.intervalSeconds) * 1000 else policy.intervalSeconds * 1000)
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (error: Exception) {
                    // A transient Android connectivity/permission exception is not a
                    // fatal core error. Retry the health probe rather than crash the app.
                    Log.w(TAG, "Connection health monitor failed; retrying", error)
                    NativeServiceState.health(NativeInternetHealth.UNCHECKED)
                    coreFailures = 0
                    delay(15_000)
                }
            }
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
        recoveryJob?.cancel(); recoveryJob = null
        monitorJob?.cancel(); monitorJob = null
        if (coreOwner === this) { NativeServiceState.reset(); NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset() }
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

}

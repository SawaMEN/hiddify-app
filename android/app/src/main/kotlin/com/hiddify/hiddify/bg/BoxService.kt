package com.hiddify.hiddify.bg

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
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
        private var coreOwner: BoxService? = null
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

        fun start() {
            val intent = Intent(Application.application, Settings.serviceClass())
            ContextCompat.startForegroundService(Application.application, intent)
        }

        fun stop() {
            Application.application.sendBroadcast(
                Intent(Action.SERVICE_CLOSE).setPackage(Application.application.packageName),
            )
        }
    }

    var fileDescriptor: ParcelFileDescriptor? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var destroyed = false
    private val status = MutableLiveData(Status.Stopped)
    private val binder = ServiceBinder(status)
    private val notification = ServiceNotification(status, service)
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.SERVICE_CLOSE -> stopService()
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        serviceUpdateIdleMode()
                    }
                }
            }
        }
    }

    private var activeProfileName = ""

    private suspend fun startService() {
        try {
            if (destroyed) return
            coreOwner?.takeIf { it !== this }?.releaseNative("replacement")
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

            coreOwner = this
            DefaultNetworkMonitor.start()
            Libbox.setMemoryLimit(!Settings.disableMemoryLimit)

            try {
                Mobile.setup(
                    SetupOptions().also {
                        it.basePath = Settings.baseDir
                        it.workingDir = Settings.workingDir
                        it.tempDir = Settings.tempDir
                        it.fixAndroidStack = Bugs.fixAndroidStack
                        it.mode = 4L
                        it.listen = "127.0.0.1:${Settings.grpcServiceModePort}"
                        it.secret = ""
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

            if (Settings.startCoreAfterStartingService) {
                Mobile.start(selectedConfigPath, "")
            }

            if (destroyed) return
            status.postValue(Status.Started)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_started)
            }
            notification.start()
        } catch (e: Exception) {
            stopAndAlert(Alert.StartService, e.message)
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
            if (destroyed || coreOwner !== this) return
            status.postValue(Status.Starting)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_starting)
            }

            DefaultNetworkMonitor.detachCoreListener()
            val closeError = runCatching { Mobile.close(4L) }.exceptionOrNull()
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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun serviceUpdateIdleMode() {
        if (!Application.powerManager.isDeviceIdleMode) {
            runCatching { Mobile.wake() }
                .onFailure { Log.w(TAG, "failed to wake mobile core", it) }
        }
    }

    private fun stopService() {
        if (destroyed || status.value == Status.Stopped || status.value == Status.Stopping) return

        status.value = Status.Stopping
        serviceScope.launch {
            lifecycleMutex.withLock {
                if (destroyed) return@withLock

                // Keep Android network discovery alive until gomobile has actually stopped using it.
                if (coreOwner !== this) {
                    withContext(Dispatchers.Main) { status.value = Status.Stopped; service.stopSelf() }
                    return@withLock
                }
                DefaultNetworkMonitor.detachCoreListener()
                val closeError = runCatching { Mobile.close(4L) }.exceptionOrNull()
                if (closeError != null) {
                    Log.e(TAG, "failed to close mobile core", closeError)
                    withContext(Dispatchers.Main) {
                        // Allow a subsequent stop request to retry instead of reporting a false Stopped state.
                        status.value = Status.Started
                        if (destroyed) return@withContext
                notification.show(activeProfileName, R.string.status_started)
                        binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
                    }
                    return@withLock
                }

                closeTun("stop")
                coreOwner = null
                runCatching { DefaultNetworkMonitor.stop() }
                    .onFailure { Log.w(TAG, "failed to stop network monitor", it) }
                Settings.startedByUser = false

                withContext(Dispatchers.Main) {
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
        Settings.startedByUser = false
        releaseNative("service error")
        closeTun("service error")

        withContext(Dispatchers.Main) {
            unregisterReceiver()
            notification.close()
            binder.broadcast { callback -> callback.onServiceAlert(type.ordinal, message) }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    private fun closeTun(reason: String) {
        runCatching { fileDescriptor?.close() }
            .onFailure { Log.w(TAG, "failed to close TUN during $reason", it) }
        fileDescriptor = null
    }

    @Suppress("SameReturnValue")
    internal fun onStartCommand(): Int {
        if (destroyed || status.value != Status.Stopped) return Service.START_NOT_STICKY
        status.value = Status.Starting
        try {
            // Android's foreground deadline starts before IO/setup, not after it.
            notification.show(Settings.activeProfileName, R.string.status_starting)
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(
                    service, receiver,
                    IntentFilter().apply {
                        addAction(Action.SERVICE_CLOSE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                receiverRegistered = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "foreground registration failed", e)
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
                    Settings.startedByUser = true
                    startService()
                } catch (e: Exception) {
                    Log.e(TAG, "service initialization failed", e)
                    stopAndAlert(Alert.CreateService, e.message)
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    // All owners share the mutex. An old onDestroy must never close a new owner's core.
    private suspend fun releaseNative(reason: String) {
        if (coreOwner !== this) return
        DefaultNetworkMonitor.detachCoreListener()
        Mobile.close(4L) // retain ownership and abort replacement if native close throws
        closeTun(reason)
        runCatching { DefaultNetworkMonitor.stop() }.onFailure { Log.w(TAG, "network cleanup failed", it) }
        coreOwner = null
    }

    fun onBind(intent: Intent): IBinder = binder

    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        unregisterReceiver()
        notification.destroy()
        binder.close()
        // Never block Android main, and do not cancel a blocking setup before its cleanup
        // has acquired the lifecycle lock and released native state.
        serviceScope.launch {
            try {
                lifecycleMutex.withLock {
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
        stopService()
    }

    private fun unregisterReceiver() {
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

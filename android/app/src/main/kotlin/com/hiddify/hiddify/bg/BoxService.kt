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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BoxService(
    private val service: Service,
    private val platformInterface: PlatformInterface,
) {

    companion object {
        private const val TAG = "A/BoxService"

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

            Libbox.redirectStderr(File(Settings.workingDir, "stderr.log").path)
            initializeOnce = true
        }

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
    private val lifecycleMutex = Mutex()
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
        var coreSetupSucceeded = false
        try {
            status.postValue(Status.Starting)
            Log.d(TAG, "starting service")

            withContext(Dispatchers.Main) {
                notification.show(activeProfileName, R.string.status_starting)
            }

            val selectedConfigPath = Settings.activeConfigPath
            if (selectedConfigPath.isBlank()) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            activeProfileName = Settings.activeProfileName

            withContext(Dispatchers.Main) {
                notification.show(activeProfileName, R.string.status_starting)
                binder.broadcast { it.onServiceResetLogs(listOf()) }
            }

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
                coreSetupSucceeded = true
            } catch (e: Exception) {
                // setup() can fail after allocating native state. Always make a best-effort close
                // before tearing down Android network callbacks so a later setup starts cleanly.
                DefaultNetworkMonitor.detachCoreListener()
                runCatching { Mobile.close(4L) }
                    .onFailure { Log.e(TAG, "failed to close partially initialized core", it) }
                stopAndAlert(Alert.CreateService, e.message)
                return
            }

            if (Settings.startCoreAfterStartingService) {
                Mobile.start(selectedConfigPath, "")
            }

            status.postValue(Status.Started)
            withContext(Dispatchers.Main) {
                notification.show(activeProfileName, R.string.status_started)
            }
            notification.start()
        } catch (e: Exception) {
            if (coreSetupSucceeded) {
                DefaultNetworkMonitor.detachCoreListener()
                runCatching { Mobile.close(4L) }
                    .onFailure { Log.e(TAG, "failed to close core after start error", it) }
            }
            stopAndAlert(Alert.StartService, e.message)
        }
    }

    fun serviceReload() {
        runBlocking {
            serviceReload0()
        }
    }

    suspend fun serviceReload0() {
        lifecycleMutex.withLock {
            status.postValue(Status.Starting)
            withContext(Dispatchers.Main) {
                notification.show(activeProfileName, R.string.status_starting)
            }

            closeTun("reload")
            DefaultNetworkMonitor.detachCoreListener()
            val closeError = runCatching { Mobile.close(4L) }.exceptionOrNull()
            if (closeError != null) {
                Log.e(TAG, "failed to close mobile core for reload", closeError)
                status.postValue(Status.Started)
                withContext(Dispatchers.Main) {
                    notification.show(activeProfileName, R.string.status_started)
                    binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
                }
                return
            }
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
        if (status.value == Status.Stopped || status.value == Status.Stopping) return

        status.value = Status.Stopping
        serviceScope.launch {
            lifecycleMutex.withLock {
                closeTun("stop")

                // Keep Android network discovery alive until gomobile has actually stopped using it.
                DefaultNetworkMonitor.detachCoreListener()
                val closeError = runCatching { Mobile.close(4L) }.exceptionOrNull()
                if (closeError != null) {
                    Log.e(TAG, "failed to close mobile core", closeError)
                    withContext(Dispatchers.Main) {
                        // Allow a subsequent stop request to retry instead of reporting a false Stopped state.
                        status.value = Status.Started
                        notification.show(activeProfileName, R.string.status_started)
                        binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
                    }
                    return@withLock
                }

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
        Settings.startedByUser = false
        closeTun("service error")
        DefaultNetworkMonitor.detachCoreListener()
        runCatching { DefaultNetworkMonitor.stop() }
            .onFailure { Log.w(TAG, "failed to stop network monitor after service error", it) }

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
        if (status.value != Status.Stopped) return Service.START_NOT_STICKY
        status.value = Status.Starting

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                service,
                receiver,
                IntentFilter().apply {
                    addAction(Action.SERVICE_CLOSE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }

        serviceScope.launch {
            lifecycleMutex.withLock {
                Settings.startedByUser = true
                initialize()
                startService()
            }
        }
        return Service.START_NOT_STICKY
    }

    fun onBind(intent: Intent): IBinder = binder

    fun onDestroy() {
        unregisterReceiver()
        closeTun("destroy")
        DefaultNetworkMonitor.detachCoreListener()
        notification.destroy()
        binder.close()
        serviceScope.cancel()
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

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

            val baseDir = Application.application.filesDir
            baseDir.mkdirs()

            workingDir = Application.application.getExternalFilesDir(null) ?: return
            workingDir.mkdirs()

            val tempDir = Application.application.cacheDir
            tempDir.mkdirs()

            Log.d(TAG, "base dir: ${baseDir.path}")
            Log.d(TAG, "working dir: ${workingDir.path}")
            Log.d(TAG, "temp dir: ${tempDir.path}")

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
                binder.broadcast {
                    it.onServiceResetLogs(listOf())
                }
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
                stopAndAlert(Alert.CreateService, e.message)
                return
            }

            if (Settings.startCoreAfterStartingService) {
                Mobile.start("", "")
            }

            status.postValue(Status.Started)
            withContext(Dispatchers.Main) {
                notification.show(activeProfileName, R.string.status_started)
            }
            notification.start()
        } catch (e: Exception) {
            if (coreSetupSucceeded) {
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
        notification.close()
        status.postValue(Status.Starting)

        runCatching { fileDescriptor?.close() }
            .onFailure { Log.w(TAG, "failed to close TUN before reload", it) }
        fileDescriptor = null

        Mobile.stop()
        startService()
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
            Mobile.wake()
        }
    }

    private fun stopService() {
        if (status.value == Status.Stopped || status.value == Status.Stopping) return

        status.value = Status.Stopping
        unregisterReceiver()
        notification.close()

        serviceScope.launch {
            runCatching { fileDescriptor?.close() }
                .onFailure { Log.w(TAG, "failed to close TUN", it) }
            fileDescriptor = null

            runCatching { DefaultNetworkMonitor.stop() }
                .onFailure { Log.w(TAG, "failed to stop network monitor", it) }
            Settings.startedByUser = false

            runCatching { Mobile.close(4L) }
                .onFailure { Log.e(TAG, "failed to close mobile core", it) }

            withContext(Dispatchers.Main) {
                status.value = Status.Stopped
                service.stopSelf()
            }
        }
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        Settings.startedByUser = false

        runCatching { fileDescriptor?.close() }
            .onFailure { Log.w(TAG, "failed to close TUN after service error", it) }
        fileDescriptor = null
        runCatching { DefaultNetworkMonitor.stop() }
            .onFailure { Log.w(TAG, "failed to stop network monitor after service error", it) }

        withContext(Dispatchers.Main) {
            unregisterReceiver()
            notification.close()
            binder.broadcast { callback ->
                callback.onServiceAlert(type.ordinal, message)
            }
            status.value = Status.Stopped
            service.stopSelf()
        }
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
            Settings.startedByUser = true
            initialize()
            startService()
        }
        return Service.START_NOT_STICKY
    }

    fun onBind(intent: Intent): IBinder = binder

    fun onDestroy() {
        unregisterReceiver()
        runCatching { fileDescriptor?.close() }
            .onFailure { Log.w(TAG, "failed to close TUN on destroy", it) }
        fileDescriptor = null
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
        binder.broadcast {
            it.onServiceWriteLog(message)
        }
    }
}

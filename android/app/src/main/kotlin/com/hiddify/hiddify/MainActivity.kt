package com.hiddify.hiddify

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.hiddify.hiddify.bg.ServiceConnection
import com.hiddify.hiddify.bg.ServiceNotification
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.constant.Status
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import java.util.LinkedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FlutterFragmentActivity(), ServiceConnection.Callback {

    private val connection = ServiceConnection(this, this)

    val logList = LinkedList<String>()
    var logCallback: ((Boolean) -> Unit)? = null
    val serviceStatus = MutableLiveData(Status.Stopped)
    val serviceAlerts = MutableLiveData<ServiceEvent?>(null)

    private var startGeneration = 0L
    private var pendingStartGeneration = 0L
    private var pendingStart: CompletableDeferred<Boolean>? = null
    private var serviceStartIssued = false
    private var notificationRequestGeneration: Long? = null
    private var vpnRequestGeneration: Long? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        reconnect()
        flutterEngine.plugins.add(MethodHandler(lifecycleScope, this))
        flutterEngine.plugins.add(PlatformSettingsHandler())
        flutterEngine.plugins.add(EventHandler(this))
        flutterEngine.plugins.add(LogHandler(this))
    }

    fun reconnect() {
        connection.reconnect()
    }

    /**
     * Starts the selected service and completes only when the service reports Started or a
     * start-related alert is received. System permission dialogs are part of this request, so a
     * timed-out/cancelled request can be invalidated and cannot start the service later.
     */
    suspend fun startService(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (serviceStatus.value == Status.Started) return@withContext true

        cancelPendingStartInternal()
        val generation = ++startGeneration
        val deferred = CompletableDeferred<Boolean>()
        pendingStartGeneration = generation
        pendingStart = deferred
        serviceStartIssued = false

        try {
            beginStart(generation)
            deferred.await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notificationRequestGeneration = null
            failPendingStart(generation)
            onServiceAlert(Alert.StartService, e.message)
            false
        }
    }

    /**
     * Invalidates the current permission/start request. Returns true when startForegroundService
     * had already been issued, so the caller must also account for a service that is about to
     * transition from Stopped to Starting.
     */
    fun cancelPendingStart(message: String? = null): Boolean {
        val hadPending = pendingStart != null
        val wasIssued = cancelPendingStartInternal()
        if (hadPending && message != null) {
            onServiceAlert(Alert.StartService, message)
        }
        return wasIssued
    }

    private fun cancelPendingStartInternal(): Boolean {
        val wasIssued = serviceStartIssued
        startGeneration++
        pendingStartGeneration = startGeneration
        serviceStartIssued = false
        pendingStart?.complete(false)
        pendingStart = null
        return wasIssued
    }

    private fun isCurrentStart(generation: Long): Boolean =
        pendingStart != null && pendingStartGeneration == generation && startGeneration == generation

    private fun beginStart(generation: Long) {
        if (!isCurrentStart(generation)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ServiceNotification.checkPermission()) {
            if (notificationRequestGeneration != null) {
                failPendingStart(generation)
                onServiceAlert(Alert.RequestNotificationPermission, "previous permission request is still open")
                return
            }
            notificationRequestGeneration = generation
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        continueStart(generation)
    }

    private fun continueStart(generation: Long) {
        lifecycleScope.launch {
            try {
            if (!isCurrentStart(generation)) return@launch

            val serviceModeChanged = withContext(Dispatchers.IO) { Settings.rebuildServiceMode() }
            if (!isCurrentStart(generation)) return@launch
            if (serviceModeChanged) {
                withContext(Dispatchers.IO) { connection.reconnect() }
            }

            if (Settings.serviceMode == ServiceMode.VPN) {
                val permissionIntent = try {
                    VpnService.prepare(this@MainActivity)
                } catch (e: Exception) {
                    failPendingStart(generation)
                    onServiceAlert(Alert.RequestVPNPermission, e.message)
                    return@launch
                }
                if (permissionIntent != null) {
                    if (vpnRequestGeneration != null) {
                        failPendingStart(generation)
                        onServiceAlert(Alert.RequestVPNPermission, "previous permission request is still open")
                        return@launch
                    }
                    vpnRequestGeneration = generation
                    prepareLauncher.launch(permissionIntent)
                    return@launch
                }
            }

            issueServiceStart(generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainActivity", "permission/start request failed", e)
                if (vpnRequestGeneration == generation) vpnRequestGeneration = null
                failPendingStart(generation)
                onServiceAlert(Alert.StartService, e.message)
            }
        }
    }

    private fun issueServiceStart(generation: Long) {
        if (!isCurrentStart(generation)) return
        try {
            val intent = Intent(Application.application, Settings.serviceClass()).putExtra("started_by_app", true)
            serviceStartIssued = true
            ContextCompat.startForegroundService(this, intent)
            Settings.startedByUser = true
        } catch (e: Exception) {
            failPendingStart(generation)
            onServiceAlert(Alert.StartService, e.message)
        }
    }

    private fun failPendingStart(generation: Long) {
        if (!isCurrentStart(generation)) return
        pendingStart?.complete(false)
        pendingStart = null
        serviceStartIssued = false
    }

    private fun completePendingStart(generation: Long, success: Boolean) {
        if (!isCurrentStart(generation)) return
        pendingStart?.complete(success)
        pendingStart = null
        serviceStartIssued = false
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val generation = notificationRequestGeneration ?: return@registerForActivityResult
            notificationRequestGeneration = null
            if (!isCurrentStart(generation)) return@registerForActivityResult
            if (Settings.dynamicNotification && !isGranted) {
                failPendingStart(generation)
                onServiceAlert(Alert.RequestNotificationPermission, null)
            } else {
                continueStart(generation)
            }
        }

    private val prepareLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val generation = vpnRequestGeneration ?: return@registerForActivityResult
            vpnRequestGeneration = null
            if (!isCurrentStart(generation)) return@registerForActivityResult
            if (result.resultCode == RESULT_OK) {
                issueServiceStart(generation)
            } else {
                failPendingStart(generation)
                onServiceAlert(Alert.RequestVPNPermission, null)
            }
        }

    override fun onServiceStatusChanged(status: Status) {
        serviceStatus.postValue(status)
        val generation = pendingStartGeneration
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            if (!isCurrentStart(generation)) return@launch
            when (status) {
                Status.Started -> completePendingStart(generation, true)
                Status.Stopped -> if (serviceStartIssued) completePendingStart(generation, false)
                else -> Unit
            }
        }
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        serviceAlerts.postValue(ServiceEvent(Status.Stopped, type, message))
        val generation = pendingStartGeneration
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            failPendingStart(generation)
        }
    }

    override fun onDestroy() {
        cancelPendingStartInternal()
        logCallback = null
        connection.disconnect()
        super.onDestroy()
    }
}

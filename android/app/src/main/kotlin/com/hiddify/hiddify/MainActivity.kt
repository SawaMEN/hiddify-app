package com.hiddify.hiddify

import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.hiddify.hiddify.bg.ServiceNotification
import com.hiddify.hiddify.bg.ServiceConnection
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
    private val startTracker = ServiceStartTracker()
    var lastStartFailure: ServiceEvent? = null
        private set
    private var vpnRequestGeneration: Long? = null
    private var notificationRequestInFlight = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        val migrationError = runCatching { com.hiddify.hiddify.privacy.PackageIdentity.importMigration(this) }.exceptionOrNull()
        super.onCreate(savedInstanceState)
        if (migrationError != null) {
            android.app.AlertDialog.Builder(this).setMessage(migrationError.message)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }.show()
        }
    }

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
        lastStartFailure = null
        serviceAlerts.value = null
        val generation = ++startGeneration
        val deferred = CompletableDeferred<Boolean>()
        pendingStartGeneration = generation
        pendingStart = deferred
        startTracker.reset()

        try {
            beginStart(generation)
            deferred.await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            reportStartFailure(generation, Alert.StartService, e.message)
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
        val wasIssued = startTracker.issued
        startGeneration++
        pendingStartGeneration = startGeneration
        startTracker.reset()
        pendingStart?.complete(false)
        pendingStart = null
        return wasIssued
    }

    private fun isCurrentStart(generation: Long): Boolean =
        pendingStart != null && pendingStartGeneration == generation && startGeneration == generation

    private fun beginStart(generation: Long) {
        if (!isCurrentStart(generation)) return
        // Android 13+ notification permission controls whether the foreground-service
        // notification is visible in the notification drawer. It is not a prerequisite for
        // starting the foreground VPN service itself, so denying it must not block connection.
        continueStart(generation)
    }

    private fun continueStart(generation: Long) {
        lifecycleScope.launch {
            try {
                if (!isCurrentStart(generation)) return@launch

                val serviceModeChanged = withContext(Dispatchers.IO) { Settings.rebuildServiceMode() }
                if (!isCurrentStart(generation)) return@launch
                if (serviceModeChanged) {
                    connection.reconnect()
                }

                if (Settings.serviceMode == ServiceMode.VPN) {
                    val permissionIntent = try {
                        VpnService.prepare(this@MainActivity)
                    } catch (e: Exception) {
                        reportStartFailure(generation, Alert.RequestVPNPermission, e.message)
                        return@launch
                    }
                    if (permissionIntent != null) {
                        if (vpnRequestGeneration != null) {
                            reportStartFailure(generation, Alert.RequestVPNPermission, "previous permission request is still open")
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
                reportStartFailure(generation, Alert.StartService, e.message)
            }
        }
    }

    private fun issueServiceStart(generation: Long) {
        if (!isCurrentStart(generation)) return
        try {
            val intent = Intent(Application.application, Settings.serviceClass()).putExtra("started_by_app", true)
            startTracker.markIssued()
            ContextCompat.startForegroundService(this, intent)
            Settings.startedByUser = true
        } catch (e: Exception) {
            reportStartFailure(generation, Alert.StartService, e.message)
        }
    }

    private fun reportStartFailure(generation: Long, type: Alert, message: String?) {
        if (!isCurrentStart(generation)) return
        val event = ServiceEvent(Status.Stopped, type, message)
        lastStartFailure = event
        serviceAlerts.value = event
        failPendingStart(generation)
    }

    private fun failPendingStart(generation: Long) {
        if (!isCurrentStart(generation)) return
        pendingStart?.complete(false)
        pendingStart = null
        startTracker.reset()
    }

    private fun completePendingStart(generation: Long, success: Boolean) {
        if (!isCurrentStart(generation)) return
        pendingStart?.complete(success)
        pendingStart = null
        startTracker.reset()
    }

    // This prompt happens after startup completes. Denial never fails or stops the VPN.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            notificationRequestInFlight = false
            ServiceNotification.refreshActive()
        }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            serviceStatus.value != Status.Started || notificationRequestInFlight ||
            Settings.notificationPermissionAsked || ServiceNotification.hasRuntimePermission()) return
        try {
            notificationRequestInFlight = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            Settings.notificationPermissionAsked = true
        } catch (error: Exception) {
            notificationRequestInFlight = false
            Log.w("MainActivity", "unable to request optional notification permission", error)
        }
    }

    override fun onResume() {
        super.onResume()
        // On Android 29+, onResume can precede the lifecycle's RESUMED event.
        lifecycleScope.launch {
            lifecycle.withResumed {
                ServiceNotification.refreshActive()
                requestNotificationPermissionIfNeeded()
            }
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
                reportStartFailure(generation, Alert.RequestVPNPermission, null)
            }
        }

    override fun onServiceStatusChanged(status: Status) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            serviceStatus.value = status
            val generation = pendingStartGeneration
            if (isCurrentStart(generation)) {
                val result = startTracker.onStatus(status)
                if (result != null) {
                    if (!result && lastStartFailure == null) {
                        lastStartFailure = ServiceEvent(Status.Stopped, Alert.StartService, "Android service stopped during startup")
                    }
                    completePendingStart(generation, result)
                }
            }
            if (status == Status.Started) requestNotificationPermissionIfNeeded()
        }
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            val event = ServiceEvent(Status.Stopped, type, message)
            lastStartFailure = event
            serviceAlerts.value = event
            failPendingStart(pendingStartGeneration)
        }
    }

    override fun onDestroy() {
        cancelPendingStartInternal()
        logCallback = null
        connection.disconnect()
        super.onDestroy()
    }
}

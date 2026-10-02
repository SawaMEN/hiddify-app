package com.hiddify.hiddify

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList

class MainActivity : FlutterFragmentActivity(), ServiceConnection.Callback {

    private val connection = ServiceConnection(this, this)

    val logList = LinkedList<String>()
    var logCallback: ((Boolean) -> Unit)? = null
    val serviceStatus = MutableLiveData(Status.Stopped)
    val serviceAlerts = MutableLiveData<ServiceEvent?>(null)

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

    fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ServiceNotification.checkPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startService0()
    }

    private fun startService0() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (Settings.rebuildServiceMode()) {
                connection.reconnect()
            }
            if (Settings.serviceMode == ServiceMode.VPN && prepareVpn()) {
                return@launch
            }

            val intent = Intent(Application.application, Settings.serviceClass())
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
            Settings.startedByUser = true
        }
    }

    private suspend fun prepareVpn() = withContext(Dispatchers.Main) {
        try {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) {
                prepareLauncher.launch(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            onServiceAlert(Alert.RequestVPNPermission, e.message)
            true
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (Settings.dynamicNotification && !isGranted) {
                onServiceAlert(Alert.RequestNotificationPermission, null)
            } else {
                startService0()
            }
        }

    private val prepareLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startService0()
            } else {
                onServiceAlert(Alert.RequestVPNPermission, null)
            }
        }

    override fun onServiceStatusChanged(status: Status) {
        serviceStatus.postValue(status)
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        serviceAlerts.postValue(ServiceEvent(Status.Stopped, type, message))
    }

    override fun onDestroy() {
        logCallback = null
        connection.disconnect()
        super.onDestroy()
    }
}

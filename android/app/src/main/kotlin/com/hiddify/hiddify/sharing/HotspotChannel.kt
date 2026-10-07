package com.hiddify.hiddify.sharing

import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hiddify.hiddify.MainActivity
import com.hiddify.hiddify.constant.Status
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class HotspotChannel(private val activity: MainActivity) {
    private var permissionResult: CompletableDeferred<Boolean>? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionResult?.complete(it); permissionResult = null
    }
    private var methods: MethodChannel? = null
    private var events: EventChannel? = null

    fun attach(messenger: BinaryMessenger) {
        methods = MethodChannel(messenger, "vetroff/hotspot").also { channel ->
            channel.setMethodCallHandler { call, result ->
                activity.lifecycleScope.launch {
                    try {
                        when (call.method) {
                            "status" -> result.success(AutomaticHotspot.snapshot())
                            "start" -> {
                                val root = call.argument<Boolean>("root") == true
                                if (root) AutomaticHotspot.prepareRoot(activity)
                                val required = AutomaticHotspot.permission()
                                if (ContextCompat.checkSelfPermission(activity, required) != PackageManager.PERMISSION_GRANTED) {
                                    check(permissionResult == null) { "A Wi-Fi permission request is already pending" }
                                    val pending = CompletableDeferred<Boolean>().also { permissionResult = it }
                                    permission.launch(required)
                                    check(pending.await()) { "Wi-Fi hotspot permission was declined" }
                                }
                                result.success(AutomaticHotspot.start(activity, root))
                            }
                            "activate" -> {
                                withTimeout(30_000) {
                                    while (activity.serviceStatus.value != Status.Started) delay(100)
                                }
                                check(AutomaticHotspot.snapshot()["active"] == true) { "The hotspot stopped during VPN startup" }
                                result.success(AutomaticHotspot.snapshot())
                            }
                            "stop" -> { AutomaticHotspot.stop(); result.success(null) }
                            else -> result.notImplemented()
                        }
                    } catch (error: Exception) { result.error("hotspot_error", error.message, null) }
                }
            }
        }
        events = EventChannel(messenger, "vetroff/hotspot/events").also { channel ->
            channel.setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    AutomaticHotspot.listener = { sink.success(it) }
                    sink.success(AutomaticHotspot.snapshot())
                }
                override fun onCancel(arguments: Any?) { AutomaticHotspot.listener = null }
            })
        }
    }

    fun detach() {
        permissionResult?.cancel(); permissionResult = null
        methods?.setMethodCallHandler(null)
        events?.setStreamHandler(null)
        AutomaticHotspot.listener = null
    }
}

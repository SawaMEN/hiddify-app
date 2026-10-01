package com.hiddify.hiddify

import android.util.Log
import androidx.lifecycle.Observer
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.Status
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.JSONMethodCodec

class EventHandler(
    private val activity: MainActivity,
) : FlutterPlugin {

    companion object {
        const val TAG = "A/EventHandler"
        const val SERVICE_STATUS = "com.hiddify.app/service.status"
        const val SERVICE_ALERTS = "com.hiddify.app/service.alerts"
    }

    private var statusChannel: EventChannel? = null
    private var alertsChannel: EventChannel? = null
    private var statusObserver: Observer<Status>? = null
    private var alertsObserver: Observer<ServiceEvent?>? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        statusChannel = EventChannel(
            flutterPluginBinding.binaryMessenger,
            SERVICE_STATUS,
            JSONMethodCodec.INSTANCE,
        )
        alertsChannel = EventChannel(
            flutterPluginBinding.binaryMessenger,
            SERVICE_ALERTS,
            JSONMethodCodec.INSTANCE,
        )

        statusChannel?.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                removeStatusObserver()
                statusObserver = Observer { status ->
                    Log.d(TAG, "new status: $status")
                    events?.success(mapOf("status" to status.name))
                }.also { observer ->
                    activity.serviceStatus.observeForever(observer)
                }
            }

            override fun onCancel(arguments: Any?) {
                removeStatusObserver()
            }
        })

        alertsChannel?.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                removeAlertsObserver()
                alertsObserver = Observer { event ->
                    if (event == null) return@Observer
                    Log.d(TAG, "new alert: $event")
                    events?.success(
                        listOf(
                            "status" to event.status.name,
                            "alert" to event.alert?.name,
                            "message" to event.message,
                        ).mapNotNull { (key, value) -> value?.let { key to it } }.toMap(),
                    )
                }.also { observer ->
                    activity.serviceAlerts.observeForever(observer)
                }
            }

            override fun onCancel(arguments: Any?) {
                removeAlertsObserver()
            }
        })
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        removeStatusObserver()
        removeAlertsObserver()
        statusChannel?.setStreamHandler(null)
        alertsChannel?.setStreamHandler(null)
        statusChannel = null
        alertsChannel = null
    }

    private fun removeStatusObserver() {
        statusObserver?.let(activity.serviceStatus::removeObserver)
        statusObserver = null
    }

    private fun removeAlertsObserver() {
        alertsObserver?.let(activity.serviceAlerts::removeObserver)
        alertsObserver = null
    }
}

data class ServiceEvent(val status: Status, val alert: Alert? = null, val message: String? = null)

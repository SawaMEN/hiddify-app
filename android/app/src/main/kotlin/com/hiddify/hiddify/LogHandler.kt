package com.hiddify.hiddify

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel

class LogHandler(
    private val activity: MainActivity,
) : FlutterPlugin {

    companion object {
        const val SERVICE_LOGS = "com.hiddify.app/service.logs"
    }

    private var logsChannel: EventChannel? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        logsChannel = EventChannel(flutterPluginBinding.binaryMessenger, SERVICE_LOGS).also { channel ->
            channel.setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    events?.success(activity.logList)
                    activity.logCallback = {
                        events?.success(activity.logList)
                    }
                }

                override fun onCancel(arguments: Any?) {
                    activity.logCallback = null
                }
            })
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        activity.logCallback = null
        logsChannel?.setStreamHandler(null)
        logsChannel = null
    }
}

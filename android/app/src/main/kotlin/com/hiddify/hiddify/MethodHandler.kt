package com.hiddify.hiddify

import android.util.Log
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.mobile.Mobile
import com.hiddify.core.mobile.SetupOptions
import com.hiddify.hiddify.bg.BoxService
import com.hiddify.hiddify.bg.Bugs
import com.hiddify.hiddify.constant.Status
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MethodHandler(
    private val scope: CoroutineScope,
    private val mainActivity: MainActivity,
) : FlutterPlugin, MethodChannel.MethodCallHandler {

    private var channel: MethodChannel? = null

    companion object {
        const val TAG = "A/MethodHandler"
        const val channelName = "com.hiddify.app/method"
        private const val STOP_TIMEOUT_MS = 15_000L

        enum class Trigger(val method: String) {
            Setup("setup"),
            Start("start"),
            Stop("stop"),
            Restart("restart"),
            AddGrpcClientPublicKey("add_grpc_client_public_key"),
            GetGrpcServerPublicKey("get_grpc_server_public_key"),
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, channelName)
        channel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            Trigger.AddGrpcClientPublicKey.method -> launchResult(
                result = result,
                errorCode = "android_add_grpc_key_failed",
            ) {
                val args = call.arguments as Map<*, *>
                Settings.grpcFlutterPublicKey = args["clientPublicKey"] as ByteArray
                ""
            }

            Trigger.GetGrpcServerPublicKey.method -> launchResult(
                result = result,
                errorCode = "android_get_grpc_key_failed",
            ) {
                Mobile.getServerPublicKey()
            }

            Trigger.Setup.method -> launchResult(
                result = result,
                errorCode = "android_setup_failed",
            ) {
                val args = call.arguments as Map<*, *>
                Settings.baseDir = args["baseDir"] as String
                Settings.workingDir = args["workingDir"] as String
                Settings.tempDir = args["tempDir"] as String
                Settings.debugMode = args["debug"] as Boolean? ?: false
                val mode = args["mode"] as Int
                val grpcPort = args["grpcPort"] as Int

                Log.d(TAG, "debug mode: ${Settings.debugMode}")
                Mobile.setup(
                    SetupOptions().also {
                        it.basePath = Settings.baseDir
                        it.workingDir = Settings.workingDir
                        it.tempDir = Settings.tempDir
                        it.fixAndroidStack = Bugs.fixAndroidStack
                        it.mode = mode.toLong()
                        it.listen = "127.0.0.1:$grpcPort"
                        it.secret = ""
                        it.debug = Settings.debugMode
                    },
                    null,
                )
                Libbox.redirectStderr(File(Settings.workingDir, "stderr2.log").path)
                ""
            }

            Trigger.Start.method -> launchResult(
                result = result,
                dispatcher = Dispatchers.Main.immediate,
                errorCode = "android_start_failed",
            ) {
                val args = call.arguments as Map<*, *>
                Settings.activeConfigPath = args["path"] as String? ?: ""
                Settings.activeProfileName = args["name"] as String? ?: ""
                Settings.debugMode = args["debug"] as Boolean? ?: false
                Settings.grpcServiceModePort = args["grpcPort"] as Int
                Settings.startCoreAfterStartingService = false

                mainActivity.startService()
                true
            }

            Trigger.Stop.method -> launchResult(
                result = result,
                dispatcher = Dispatchers.Main.immediate,
                errorCode = "android_stop_failed",
            ) {
                val currentStatus = mainActivity.serviceStatus.value ?: Status.Stopped
                if (currentStatus == Status.Stopped) {
                    Log.d(TAG, "service is already stopped")
                    return@launchResult true
                }

                BoxService.stop()

                // BoxService.stop() only sends a broadcast. The actual Mobile.close(4L)
                // happens asynchronously in BoxService. Do not report success to Dart until
                // the native core has fully closed, otherwise the next connect can race
                // Mobile.setup() against the previous Mobile.close().
                val stopped = withTimeoutOrNull(STOP_TIMEOUT_MS) {
                    while (mainActivity.serviceStatus.value != Status.Stopped) {
                        delay(50L)
                    }
                    true
                } ?: false

                if (!stopped) {
                    throw IllegalStateException("timed out waiting for Android service to stop")
                }
                true
            }

            else -> result.notImplemented()
        }
    }

    private fun launchResult(
        result: MethodChannel.Result,
        errorCode: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> Any?,
    ) {
        scope.launch(dispatcher) {
            try {
                val value = block()
                withContext(Dispatchers.Main.immediate) {
                    result.success(value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, errorCode, t)
                withContext(Dispatchers.Main.immediate) {
                    result.error(
                        errorCode,
                        t.message ?: t.javaClass.simpleName,
                        Log.getStackTraceString(t),
                    )
                }
            }
        }
    }
}

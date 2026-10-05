package com.hiddify.hiddify

import android.util.Log
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.mobile.Mobile
import com.hiddify.core.mobile.SetupOptions
import com.hiddify.hiddify.bg.BoxService
import com.hiddify.hiddify.bg.Bugs
import com.hiddify.hiddify.constant.Alert
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
        private const val START_TIMEOUT_MS = 30_000L
        private const val START_ISSUE_SETTLE_MS = 5_000L
        private const val STOP_TIMEOUT_MS = 15_000L
        private const val STOP_RETRY_GRACE_MS = 5_000L

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
            "get_connection_intent" -> result.success(Settings.connectionDesired)
            "get_service_running" -> result.success(BoxService.isRunning())
            "get_network_status" -> launchResult(result, "android_network_state_failed") {
                com.hiddify.hiddify.Application.connectivity.allNetworks.any { network ->
                    val caps = com.hiddify.hiddify.Application.connectivity.getNetworkCapabilities(network)
                    caps != null && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }
            "get_vpn_protection" -> result.success(BoxService.vpnProtection())
            "open_vpn_settings" -> launchResult(result, "android_vpn_settings_failed", Dispatchers.Main.immediate) {
                mainActivity.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                null
            }
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
                BoxService.withNativeLifecycle {
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
                        BoxService.currentPlatformInterface(),
                    )
                }
                Libbox.redirectStderr(File(Settings.workingDir, "stderr2.log").path)
                ""
            }

            Trigger.Start.method -> launchResult(
                result = result,
                dispatcher = Dispatchers.Main.immediate,
                errorCode = "android_start_failed",
            ) {
                val args = call.arguments as Map<*, *>
                Settings.connectionDesired = true
                Settings.activeConfigPath = args["path"] as String? ?: ""
                Settings.activeProfileName = args["name"] as String? ?: ""
                Settings.debugMode = args["debug"] as Boolean? ?: false
                Settings.grpcServiceModePort = args["grpcPort"] as Int
                Settings.startCoreAfterStartingService = false

                val started = withTimeoutOrNull(START_TIMEOUT_MS) {
                    mainActivity.startService()
                }
                if (started == null) {
                    val issued = mainActivity.cancelPendingStart("timed out waiting for Android permission/service startup")
                    if (issued) {
                        withTimeoutOrNull(START_ISSUE_SETTLE_MS) {
                            while (mainActivity.serviceStatus.value == Status.Stopped) delay(25L)
                        }
                        BoxService.stop()
                    }
                }
                if (started != true) {
                    val failure = mainActivity.lastStartFailure
                    throw ServiceStartException(
                        failure?.alert ?: Alert.StartService,
                        failure?.message ?: "Android service startup was cancelled or failed",
                    )
                }
                true
            }

            Trigger.Stop.method -> launchResult(
                result = result,
                dispatcher = Dispatchers.Main.immediate,
                errorCode = "android_stop_failed",
            ) {
                // Invalidate an outstanding permission request before inspecting service state.
                // If startForegroundService was already issued, give Android time to deliver
                // onStartCommand so the close broadcast cannot be lost in the Stopped->Starting gap.
                Settings.connectionDesired = false
                val startAlreadyIssued = mainActivity.cancelPendingStart()
                if (startAlreadyIssued && mainActivity.serviceStatus.value == Status.Stopped) {
                    withTimeoutOrNull(START_ISSUE_SETTLE_MS) {
                        while (mainActivity.serviceStatus.value == Status.Stopped && !BoxService.hasActiveCore()) {
                            delay(25L)
                        }
                    }
                }

                val currentStatus = mainActivity.serviceStatus.value ?: Status.Stopped
                // MainActivity may still expose its initial Stopped snapshot while the service is
                // already alive but the AIDL binding has not completed. Never trust that snapshot
                // when the native core has an owner or a service start was already issued.
                if (currentStatus == Status.Stopped && !startAlreadyIssued && !BoxService.hasActiveCore()) {
                    Log.d(TAG, "service is already stopped")
                    return@launchResult true
                }

                BoxService.stop()

                // The Activity status can be stale during bind/unbind. coreOwner is cleared only
                // after Mobile.close(4L) and TUN cleanup finish, so it is the authoritative stop
                // barrier for avoiding setup/close overlap on the next connect.
                suspend fun waitForNativeStop(timeoutMs: Long): Boolean =
                    withTimeoutOrNull(timeoutMs) {
                        while (BoxService.hasActiveCore()) {
                            delay(50L)
                        }
                        true
                    } ?: false

                var stopped = waitForNativeStop(STOP_TIMEOUT_MS)
                if (!stopped) {
                    Log.w(TAG, "native core still active after stop timeout; retrying close broadcast")
                    BoxService.stop()
                    stopped = waitForNativeStop(STOP_RETRY_GRACE_MS)
                }
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
                        if (t is ServiceStartException) t.alert.name else errorCode,
                        t.message ?: t.javaClass.simpleName,
                        Log.getStackTraceString(t),
                    )
                }
            }
        }
    }
}

private class ServiceStartException(val alert: Alert, message: String) : Exception(message)

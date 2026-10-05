package com.hiddify.hiddify.bg

import android.net.ConnectivityManager
import android.net.Network
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hiddify.hiddify.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

object DefaultNetworkListener {
    private const val TAG = "DefaultNetworkListener"

    private sealed class NetworkMessage {
        class Start(val key: Any, val listener: (Network?) -> Unit) : NetworkMessage()
        class Get(val response: CompletableDeferred<Network>) : NetworkMessage()
        class CancelGet(val response: CompletableDeferred<Network>) : NetworkMessage()
        class Stop(val key: Any) : NetworkMessage()
        class Put(val network: Network) : NetworkMessage()
        class Update(val network: Network) : NetworkMessage()
        class Lost(val network: Network) : NetworkMessage()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messages = Channel<NetworkMessage>(Channel.UNLIMITED)

    init {
        scope.launch {
            val listeners = mutableMapOf<Any, (Network?) -> Unit>()
            var network: Network? = null
            val pendingRequests = arrayListOf<CompletableDeferred<Network>>()

            suspend fun notifyListeners(value: Network?) {
                listeners.values.toList().forEach { listener ->
                    runCatching { listener(value) }
                        .onFailure { Log.e(TAG, "network listener callback failed", it) }
                }
            }

            for (message in messages) {
                try {
                    when (message) {
                        is NetworkMessage.Start -> {
                            if (listeners.isEmpty()) {
                                register()
                                if (fallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    network = Application.connectivity.activeNetwork
                                }
                            }
                            listeners[message.key] = message.listener
                            network?.let { current ->
                                runCatching { message.listener(current) }
                                    .onFailure { Log.e(TAG, "initial network listener callback failed", it) }
                            }
                        }

                        is NetworkMessage.Get -> {
                            val currentNetwork = network
                            if (currentNetwork != null) {
                                message.response.complete(currentNetwork)
                            } else if (fallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val active = Application.connectivity.activeNetwork
                                if (active != null) {
                                    network = active
                                    message.response.complete(active)
                                } else {
                                    message.response.completeExceptionally(IllegalStateException("missing default network"))
                                }
                            } else if (listeners.isEmpty()) {
                                message.response.completeExceptionally(
                                    IllegalStateException("default network listener is not running"),
                                )
                            } else {
                                pendingRequests += message.response
                            }
                        }

                        is NetworkMessage.CancelGet -> pendingRequests.remove(message.response)
                        is NetworkMessage.Stop -> {
                            if (listeners.remove(message.key) != null && listeners.isEmpty()) {
                                network = null
                                unregister()
                                val error = CancellationException("default network listener stopped")
                                pendingRequests.forEach { request ->
                                    if (!request.isCompleted) request.completeExceptionally(error)
                                }
                                pendingRequests.clear()
                            }
                        }

                        is NetworkMessage.Put -> {
                            network = message.network
                            pendingRequests.forEach { request ->
                                if (!request.isCompleted) request.complete(message.network)
                            }
                            pendingRequests.clear()
                            notifyListeners(network)
                        }

                        is NetworkMessage.Update -> {
                            if (network == message.network) notifyListeners(network)
                        }

                        is NetworkMessage.Lost -> {
                            if (network == message.network) {
                                network = null
                                notifyListeners(null)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "failed to process network event ${message.javaClass.simpleName}", t)
                    if (message is NetworkMessage.Get && !message.response.isCompleted) {
                        message.response.completeExceptionally(t)
                    }
                }
            }
        }
    }

    suspend fun start(key: Any, listener: (Network?) -> Unit) {
        messages.send(NetworkMessage.Start(key, listener))
    }

    suspend fun get(): Network {
        if (fallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Application.connectivity.activeNetwork ?: error("missing default network")
        }
        val response = CompletableDeferred<Network>()
        messages.send(NetworkMessage.Get(response))
        return try { response.await() } finally {
            response.cancel()
            messages.trySend(NetworkMessage.CancelGet(response))
        }
    }

    suspend fun stop(key: Any) {
        messages.send(NetworkMessage.Stop(key))
    }

    private fun enqueue(message: NetworkMessage) {
        if (messages.trySend(message).isFailure) {
            Log.w(TAG, "dropping network callback because listener loop is unavailable")
        }
    }

    private object Callback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            enqueue(NetworkMessage.Put(network))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            enqueue(NetworkMessage.Update(network))
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            enqueue(NetworkMessage.Update(network))
        }

        override fun onLost(network: Network) {
            enqueue(NetworkMessage.Lost(network))
        }
    }

    @Volatile
    private var fallback = false

    private val request =
        NetworkRequest.Builder().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            if (Build.VERSION.SDK_INT == 23) {
                removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            }
        }.build()

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun register() {
        fallback = false
        try {
            when (Build.VERSION.SDK_INT) {
                in 31..Int.MAX_VALUE ->
                    Application.connectivity.registerBestMatchingNetworkCallback(request, Callback, mainHandler)

                in 28 until 31 ->
                    Application.connectivity.requestNetwork(request, Callback, mainHandler)

                in 26 until 28 ->
                    Application.connectivity.registerDefaultNetworkCallback(Callback, mainHandler)

                in 24 until 26 ->
                    Application.connectivity.registerDefaultNetworkCallback(Callback)

                else -> Application.connectivity.requestNetwork(request, Callback)
            }
        } catch (e: RuntimeException) {
            fallback = true
            Log.w(TAG, "network callback registration failed; using activeNetwork fallback", e)
        }
    }

    private fun unregister() {
        if (fallback) {
            fallback = false
            return
        }
        runCatching { Application.connectivity.unregisterNetworkCallback(Callback) }
            .onFailure { Log.w(TAG, "failed to unregister network callback", it) }
    }
}

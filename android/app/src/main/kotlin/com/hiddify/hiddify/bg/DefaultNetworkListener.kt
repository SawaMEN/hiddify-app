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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object DefaultNetworkListener {
    private const val TAG = "DefaultNetworkListener"

    private sealed class NetworkMessage {
        class Start(val key: Any, val listener: (Network?) -> Unit, val done: CompletableDeferred<Unit>) : NetworkMessage()
        class Get(val response: CompletableDeferred<Network>) : NetworkMessage()
        class CancelGet(val response: CompletableDeferred<Network>) : NetworkMessage()
        class Stop(val key: Any, val done: CompletableDeferred<Unit>) : NetworkMessage()
        class Put(val network: Network, val epoch: Int) : NetworkMessage()
        class Update(val network: Network, val epoch: Int) : NetworkMessage()
        class Lost(val network: Network, val epoch: Int) : NetworkMessage()
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
                                if (fallback) {
                                    network = runCatching { underlyingNetwork() }.getOrNull()
                                }
                            }
                            listeners[message.key] = message.listener
                            run {
                                runCatching { message.listener(network) }
                                    .onFailure { Log.e(TAG, "initial network listener callback failed", it) }
                            }
                            message.done.complete(Unit)
                        }

                        is NetworkMessage.Get -> {
                            val currentNetwork = network
                            if (currentNetwork != null) {
                                message.response.complete(currentNetwork)
                            } else if (fallback) {
                                val active = underlyingNetwork()
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
                            message.done.complete(Unit)
                        }

                        is NetworkMessage.Put -> {
                            if (listeners.isEmpty() || message.epoch != callbackEpoch) continue
                            network = message.network
                            pendingRequests.forEach { request ->
                                if (!request.isCompleted) request.complete(message.network)
                            }
                            pendingRequests.clear()
                            notifyListeners(network)
                        }

                        is NetworkMessage.Update -> {
                            if (listeners.isEmpty() || message.epoch != callbackEpoch) continue
                            if (network == message.network) notifyListeners(network)
                        }

                        is NetworkMessage.Lost -> {
                            if (listeners.isEmpty() || message.epoch != callbackEpoch) continue
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
                    if (message is NetworkMessage.Start) message.done.completeExceptionally(t)
                    if (message is NetworkMessage.Stop) message.done.completeExceptionally(t)
                }
            }
        }
    }

    suspend fun start(key: Any, listener: (Network?) -> Unit) {
        val done = CompletableDeferred<Unit>()
        messages.send(NetworkMessage.Start(key, listener, done))
        done.await()
    }

    suspend fun get(): Network {
        if (fallback) {
            return underlyingNetwork() ?: error("missing default network")
        }
        val response = CompletableDeferred<Network>()
        messages.send(NetworkMessage.Get(response))
        return try { response.await() } finally {
            response.cancel()
            messages.trySend(NetworkMessage.CancelGet(response))
        }
    }

    suspend fun stop(key: Any) {
        val done = CompletableDeferred<Unit>()
        messages.send(NetworkMessage.Stop(key, done))
        done.await()
    }

    /** activeNetwork may be the TUN itself during startup or a network transition. */
    fun underlyingNetwork(): Network? {
        fun usable(network: Network): Boolean {
            val caps = Application.connectivity.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        val connectivity = Application.connectivity
        connectivity.activeNetwork?.let { if (usable(it)) return it }
        return connectivity.allNetworks.firstOrNull { usable(it) }
    }

    private fun enqueue(message: NetworkMessage) {
        if (messages.trySend(message).isFailure) {
            Log.w(TAG, "dropping network callback because listener loop is unavailable")
        }
    }

    private class Callback(private val epoch: Int) : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            enqueue(NetworkMessage.Put(network, epoch))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            enqueue(NetworkMessage.Update(network, epoch))
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            enqueue(NetworkMessage.Update(network, epoch))
        }

        override fun onLost(network: Network) {
            enqueue(NetworkMessage.Lost(network, epoch))
        }
    }

    @Volatile
    private var fallback = false
    private var callbackEpoch = 0
    private var registeredCallback: Callback? = null
    private var fallbackJob: Job? = null

    private val request =
        NetworkRequest.Builder().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        }.build()

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun register() {
        fallback = false
        val epoch = ++callbackEpoch
        val callback = Callback(epoch)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Application.connectivity.registerBestMatchingNetworkCallback(request, callback, mainHandler)
            } else {
                Application.connectivity.requestNetwork(request, callback, mainHandler)
            }
            registeredCallback = callback
        } catch (e: RuntimeException) {
            fallback = true
            Log.w(TAG, "network callback registration failed; using activeNetwork fallback", e)
            fallbackJob = scope.launch {
                var previous: Network? = null
                while (isActive) {
                    val current = runCatching { underlyingNetwork() }.getOrNull()
                    if (current != null) enqueue(NetworkMessage.Put(current, epoch))
                    else previous?.let { enqueue(NetworkMessage.Lost(it, epoch)) }
                    previous = current
                    delay(1000)
                }
            }
        }
    }

    private fun unregister() {
        callbackEpoch++
        fallbackJob?.cancel()
        fallbackJob = null
        if (fallback) {
            fallback = false
            return
        }
        val callback = registeredCallback ?: return
        registeredCallback = null
        runCatching { Application.connectivity.unregisterNetworkCallback(callback) }
            .onFailure { Log.w(TAG, "failed to unregister network callback", it) }
    }
}

package com.hiddify.hiddify.bg

import android.net.Network
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.util.Log
import com.hiddify.core.libbox.InterfaceUpdateListener
import com.hiddify.hiddify.Application
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import com.hiddify.core.mobile.Mobile

object DefaultNetworkMonitor {
    private const val TAG = "DefaultNetworkMonitor"

    @Volatile
    var defaultNetwork: Network? = null
        private set

    private val listenerLock = Any()
    // Native callbacks must finish before Mobile.close(), but must never run while listenerLock
    // is held: gomobile can synchronously call back into platform code and invert that lock.
    private val nativeCallbackLock = Any()
    private var listener: InterfaceUpdateListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var updateJob: Job? = null
    private var generation = 0
    private var lastInterface: Pair<String, Int>? = null
    private var lastNetwork: Network? = null

    suspend fun start() {
        DefaultNetworkListener.start(this) {
            defaultNetwork = it
            checkDefaultInterfaceUpdate(it)
        }
        defaultNetwork = Application.connectivity.activeNetwork
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    /** Detach only the gomobile callback while keeping Android network discovery alive. */
    fun detachCoreListener() {
        synchronized(listenerLock) {
            listener = null
            generation++
            updateJob?.cancel()
            updateJob = null
            lastInterface = null
            lastNetwork = null
        }
        // Do not let Mobile.close() race an already-issued updateDefaultInterface/Mobile.wake.
        synchronized(nativeCallbackLock) { }
    }

    /** Fully release Android network discovery. Call this only after Mobile.close() returns. */
    suspend fun stop() {
        detachCoreListener()
        DefaultNetworkListener.stop(this)
        defaultNetwork = null
    }

    suspend fun require(): Network {
        defaultNetwork?.let { return it }
        Application.connectivity.activeNetwork?.let { return it }
        return DefaultNetworkListener.get()
    }

    fun setListener(newListener: InterfaceUpdateListener?) {
        synchronized(listenerLock) {
            listener = newListener
        }
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        synchronized(listenerLock) {
            generation++
            val expectedGeneration = generation
            updateJob?.cancel()
            if (listener == null) return
            updateJob = scope.launch {
                delay(150)
                if (newNetwork == null) {
                    notifyListener("", -1, newNetwork, expectedGeneration)
                    return@launch
                }
                for (wait in listOf(100L, 200L, 400L, 800L, 1000L, 1000L)) {
                    if (!isActive) return@launch
                    val name = runCatching { Application.connectivity.getLinkProperties(newNetwork)?.interfaceName }.getOrNull()
                    val index = if (name == null) -1 else runCatching { NetworkInterface.getByName(name)?.index ?: -1 }.getOrDefault(-1)
                    if (name != null && index >= 0) {
                        notifyListener(name, index, newNetwork, expectedGeneration)
                        return@launch
                    }
                    delay(wait)
                }
                notifyListener("", -1, newNetwork, expectedGeneration)
            }
        }
    }

    private fun notifyListener(interfaceName: String, interfaceIndex: Int, network: Network?, expectedGeneration: Int) {
        val snapshot = synchronized(listenerLock) {
            if (expectedGeneration != generation) return
            val currentListener = listener ?: return
            val next = Pair(interfaceName, interfaceIndex)
            if (next == lastInterface && network == lastNetwork) return
            Triple(currentListener, next, lastInterface != null && interfaceIndex >= 0)
        }
        val (currentListener, next, recovering) = snapshot

        synchronized(nativeCallbackLock) {
            val stillCurrent = synchronized(listenerLock) {
                expectedGeneration == generation && listener === currentListener
            }
            if (!stillCurrent) return

            runCatching {
                val capabilities = network?.let { Application.connectivity.getNetworkCapabilities(it) }
                val expensive = capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                val constrained = expensive &&
                    Application.connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                currentListener.updateDefaultInterface(interfaceName, interfaceIndex, expensive, constrained)
                if (recovering) Mobile.wake()
            }.onSuccess {
                synchronized(listenerLock) {
                    if (expectedGeneration == generation && listener === currentListener) {
                        lastInterface = next
                        lastNetwork = network
                    }
                }
            }.onFailure {
                Log.e(TAG, "failed to notify core about default interface", it)
            }
        }
    }
}

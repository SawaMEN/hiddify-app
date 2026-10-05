package com.hiddify.hiddify.bg

import android.net.Network
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.hiddify.core.libbox.InterfaceUpdateListener
import com.hiddify.hiddify.Application
import java.net.NetworkInterface

object DefaultNetworkMonitor {
    private const val TAG = "DefaultNetworkMonitor"

    @Volatile
    var defaultNetwork: Network? = null
        private set

    private val listenerLock = Any()
    private var listener: InterfaceUpdateListener? = null

    suspend fun start() {
        DefaultNetworkListener.start(this) {
            defaultNetwork = it
            checkDefaultInterfaceUpdate(it)
        }
        defaultNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Application.connectivity.activeNetwork
        } else {
            runCatching { DefaultNetworkListener.get() }.getOrNull()
        }
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    /** Detach only the gomobile callback while keeping Android network discovery alive. */
    fun detachCoreListener() {
        synchronized(listenerLock) {
            listener = null
        }
    }

    /** Fully release Android network discovery. Call this only after Mobile.close() returns. */
    suspend fun stop() {
        detachCoreListener()
        DefaultNetworkListener.stop(this)
        defaultNetwork = null
    }

    suspend fun require(): Network {
        defaultNetwork?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Application.connectivity.activeNetwork?.let { return it }
        }
        return DefaultNetworkListener.get()
    }

    fun setListener(newListener: InterfaceUpdateListener?) {
        synchronized(listenerLock) {
            listener = newListener
        }
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        if (newNetwork == null) {
            notifyListener("", -1)
            return
        }

        val interfaceName = runCatching {
            Application.connectivity.getLinkProperties(newNetwork)?.interfaceName
        }.getOrNull() ?: return

        repeat(10) {
            val interfaceIndex = try {
                NetworkInterface.getByName(interfaceName)?.index ?: -1
            } catch (_: Exception) {
                -1
            }

            if (interfaceIndex >= 0) {
                notifyListener(interfaceName, interfaceIndex)
                return
            }
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun notifyListener(interfaceName: String, interfaceIndex: Int) {
        synchronized(listenerLock) {
            val currentListener = listener ?: return
            runCatching {
                val network = defaultNetwork
                val capabilities = network?.let { Application.connectivity.getNetworkCapabilities(it) }
                val expensive = capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                val constrained = expensive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    Application.connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                currentListener.updateDefaultInterface(interfaceName, interfaceIndex, expensive, constrained)
            }.onFailure {
                // This callback crosses the gomobile boundary and must never unwind into Android.
                Log.e(TAG, "failed to notify core about default interface", it)
            }
        }
    }
}

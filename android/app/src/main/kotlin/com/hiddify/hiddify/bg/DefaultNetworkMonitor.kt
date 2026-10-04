package com.hiddify.hiddify.bg

import android.net.Network
import android.os.Build
import com.hiddify.core.libbox.InterfaceUpdateListener
import com.hiddify.hiddify.Application
import java.net.NetworkInterface

object DefaultNetworkMonitor {

    var defaultNetwork: Network? = null
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
            DefaultNetworkListener.get()
        }
    }

    suspend fun stop() {
        // A gomobile listener is valid only while its owning core is alive. Do not merely
        // clear the field: synchronize with an already-running callback so stop() cannot
        // return while that callback is still invoking the old Go reference.
        synchronized(listenerLock) {
            listener = null
        }
        DefaultNetworkListener.stop(this)
        defaultNetwork = null
    }

    suspend fun require(): Network {
        val network = defaultNetwork
        if (network != null) {
            return network
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

        val interfaceName =
            (Application.connectivity.getLinkProperties(newNetwork) ?: return).interfaceName ?: return

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
            Thread.sleep(100)
        }
    }

    private fun notifyListener(interfaceName: String, interfaceIndex: Int) {
        synchronized(listenerLock) {
            listener?.updateDefaultInterface(interfaceName, interfaceIndex, false, false)
        }
    }
}

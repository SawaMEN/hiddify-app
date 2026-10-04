package com.hiddify.hiddify.bg

import android.net.Network
import android.os.Build
import com.hiddify.core.libbox.InterfaceUpdateListener
import com.hiddify.hiddify.Application
import java.net.NetworkInterface

object DefaultNetworkMonitor {

    var defaultNetwork: Network? = null
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
        // The listener belongs to the current gomobile core instance. Clear it before
        // unregistering network callbacks so a reconnect can never call a stale Go ref.
        listener = null
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

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        val listener = listener ?: return
        if (newNetwork == null) {
            listener.updateDefaultInterface("", -1, false, false)
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
                listener.updateDefaultInterface(interfaceName, interfaceIndex, false, false)
                return
            }
            Thread.sleep(100)
        }
    }
}

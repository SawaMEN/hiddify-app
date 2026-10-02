package com.hiddify.hiddify.bg

import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.hiddify.core.libbox.Notification
import com.hiddify.core.libbox.TunOptions
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.PerAppProxyMode
import com.hiddify.hiddify.ktx.toIpPrefix

class VPNService : VpnService(), PlatformInterfaceWrapper {

    companion object {
        private const val TAG = "A/VPNService"
    }

    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        service.onStartCommand()

    override fun onBind(intent: Intent): IBinder {
        return super.onBind(intent) ?: service.onBind(intent)
    }

    override fun onDestroy() {
        service.onDestroy()
        super.onDestroy()
    }

    override fun onRevoke() {
        service.onRevoke()
        super.onRevoke()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    var systemProxyAvailable = false
    var systemProxyEnabled = false

    private fun addIncludePackage(builder: Builder, packageName: String) {
        if (packageName == this.packageName) {
            Log.d(TAG, "cannot include VPN app itself: $packageName")
            return
        }

        try {
            builder.addAllowedApplication(packageName)
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "cannot include missing package: $packageName", e)
        }
    }

    private fun addExcludePackage(builder: Builder, packageName: String) {
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "cannot exclude missing package: $packageName", e)
        }
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) {
            error("android: missing vpn permission")
        }

        val builder = Builder()
            .setSession("hiddify")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val address = inet4Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val address = inet6Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress.value)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4RouteAddress = options.inet4RouteAddress
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        builder.addRoute(inet4RouteAddress.next().toIpPrefix())
                    }
                } else {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6RouteAddress = options.inet6RouteAddress
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        builder.addRoute(inet6RouteAddress.next().toIpPrefix())
                    }
                } else {
                    builder.addRoute("::", 0)
                }

                val inet4RouteExcludeAddress = options.inet4RouteExcludeAddress
                while (inet4RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet4RouteExcludeAddress.next().toIpPrefix())
                }

                val inet6RouteExcludeAddress = options.inet6RouteExcludeAddress
                while (inet6RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet6RouteExcludeAddress.next().toIpPrefix())
                }
            } else {
                val inet4RouteAddress = options.inet4RouteRange
                while (inet4RouteAddress.hasNext()) {
                    val address = inet4RouteAddress.next()
                    builder.addRoute(address.address(), address.prefix())
                }

                val inet6RouteAddress = options.inet6RouteRange
                while (inet6RouteAddress.hasNext()) {
                    val address = inet6RouteAddress.next()
                    builder.addRoute(address.address(), address.prefix())
                }
            }

            if (Settings.perAppProxyEnabled) {
                val appList = Settings.perAppProxyList
                if (Settings.perAppProxyMode == PerAppProxyMode.INCLUDE) {
                    appList.forEach { addIncludePackage(builder, it) }
                } else {
                    appList.forEach { addExcludePackage(builder, it) }
                    addExcludePackage(builder, packageName)
                }
            } else {
                val includePackage = options.includePackage
                if (includePackage.hasNext()) {
                    while (includePackage.hasNext()) {
                        addIncludePackage(builder, includePackage.next())
                    }
                } else {
                    val excludePackage = options.excludePackage
                    while (excludePackage.hasNext()) {
                        addExcludePackage(builder, excludePackage.next())
                    }
                    addExcludePackage(builder, packageName)
                }
            }
        }

        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemProxyAvailable = true
            systemProxyEnabled = Settings.systemProxyEnabled
            if (systemProxyEnabled) {
                builder.setHttpProxy(
                    ProxyInfo.buildDirectProxy(
                        options.httpProxyServer,
                        options.httpProxyServerPort,
                    ),
                )
            }
        } else {
            systemProxyAvailable = false
            systemProxyEnabled = false
        }

        val pfd = builder.establish()
            ?: error("android: the application is not prepared or VPN permission was revoked")

        service.fileDescriptor?.let { oldPfd ->
            if (oldPfd !== pfd) {
                runCatching { oldPfd.close() }
            }
        }
        service.fileDescriptor = pfd
        return pfd.fd
    }

    override fun sendNotification(notification: Notification) = Unit
}

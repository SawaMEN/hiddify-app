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
import com.hiddify.hiddify.ktx.toIpPrefix

class VPNService : VpnService(), PlatformInterfaceWrapper {

    companion object {
        private const val TAG = "A/VPNService"
    }

    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A sticky restart must not undo an explicit disconnect.
        if (intent == null && !Settings.connectionDesired) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A system Always-on start can happen before the Flutter engine exists.
        if (intent?.getBooleanExtra("started_by_app", false) != true) {
            Settings.connectionDesired = true
            Settings.startCoreAfterStartingService = true
        }
        return service.onStartCommand()
    }

    override fun onBind(intent: Intent): IBinder = super.onBind(intent) ?: service.onBind(intent)

    override fun onDestroy() {
        service.onDestroy()
        super.onDestroy()
    }

    override fun onRevoke() {
        Settings.connectionDesired = false
        service.onRevoke()
        super.onRevoke()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        check(protect(fd)) { "Failed to protect socket $fd from VPN routing" }
    }

    var systemProxyAvailable = false
    var systemProxyEnabled = false

    private fun addIncludePackage(builder: Builder, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == this.packageName) {
            if (packageName == this.packageName) Log.d(TAG, "cannot include VPN app itself: $packageName")
            return false
        }
        try {
            builder.addAllowedApplication(packageName)
            return true
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "cannot include missing package: $packageName", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot include package: $packageName", e)
        }
        return false
    }

    private fun addExcludePackage(builder: Builder, packageName: String) {
        if (packageName.isBlank()) return
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "cannot exclude missing package: $packageName", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot exclude package: $packageName", e)
        }
    }

    override fun openTun(options: TunOptions): Int {
        return try {
            openTunInternal(options)
        } catch (e: Exception) {
            // openTun is invoked through gomobile. Do not allow Android/OEM exceptions to
            // unwind through the Go callback boundary and terminate the process.
            Log.e(TAG, "failed to establish VPN TUN", e)
            -1
        }
    }

    private fun openTunInternal(options: TunOptions): Int {
        if (prepare(this) != null) {
            Log.w(TAG, "VPN permission is missing or was revoked")
            return -1
        }

        val safeMtu = options.mtu.coerceIn(if (Settings.privacyDisableIpv6) 576 else 1280, 65_535)
        if (safeMtu != options.mtu) {
            Log.w(TAG, "clamping invalid MTU ${options.mtu} to $safeMtu")
        }

        val builder = Builder()
            .setSession(applicationInfo.loadLabel(packageManager).toString())
            .setMtu(safeMtu)

        builder.setMetered(false)

        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val address = inet4Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val address = inet6Address.next()
            if (!Settings.privacyDisableIpv6) builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute || Settings.privacyFullTunnel) {
            val dnsServerAddress = options.dnsServerAddress
            if (Settings.privacyPublicDns) {
                // The core hijacks DNS inside the TUN; the resolver is not contacted directly.
                builder.addDnsServer("1.1.1.1")
            } else {
                while (dnsServerAddress.hasNext()) {
                    val address = dnsServerAddress.next()
                    if (!Settings.privacyDisableIpv6 || !address.contains(':')) builder.addDnsServer(address)
                }
            }

            if (Settings.privacyFullTunnel) {
                builder.addRoute("0.0.0.0", 0)
                if (!Settings.privacyDisableIpv6) builder.addRoute("::", 0)
                addExcludePackage(builder, packageName)
            } else {
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
                            val route = inet6RouteAddress.next().toIpPrefix()
                            if (!Settings.privacyDisableIpv6) builder.addRoute(route)
                        }
                    } else {
                        if (!Settings.privacyDisableIpv6) builder.addRoute("::", 0)
                    }

                    val inet4RouteExcludeAddress = options.inet4RouteExcludeAddress
                    while (inet4RouteExcludeAddress.hasNext()) {
                        builder.excludeRoute(inet4RouteExcludeAddress.next().toIpPrefix())
                    }

                    val inet6RouteExcludeAddress = options.inet6RouteExcludeAddress
                    while (inet6RouteExcludeAddress.hasNext()) {
                        val route = inet6RouteExcludeAddress.next().toIpPrefix()
                        if (!Settings.privacyDisableIpv6) builder.excludeRoute(route)
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
                        if (!Settings.privacyDisableIpv6) builder.addRoute(address.address(), address.prefix())
                    }
                }

                val configured = org.json.JSONObject(Settings.configOptions.ifBlank { "{}" })
                val policy = com.hiddify.hiddify.privacy.RegionalRouting.policy(
                    this,
                    configured.optString("region", "other"),
                )
                if (policy["privacy-routing-mode"] != "off") {
                    @Suppress("UNCHECKED_CAST")
                    (policy["privacy-direct-packages"] as List<String>).forEach { addExcludePackage(builder, it) }
                    addExcludePackage(builder, packageName)
                } else {
                    val includePackage = options.includePackage
                    if (includePackage.hasNext()) {
                        var included = 0
                        while (includePackage.hasNext()) {
                            if (addIncludePackage(builder, includePackage.next())) included++
                        }
                        check(included > 0) { "no applications could be included" }
                    } else {
                        val excludePackage = options.excludePackage
                        while (excludePackage.hasNext()) {
                            addExcludePackage(builder, excludePackage.next())
                        }
                        addExcludePackage(builder, packageName)
                    }
                }
            }
        }

        if (!Settings.privacyDisableSystemProxy && options.isHTTPProxyEnabled) {
            systemProxyAvailable = true
            systemProxyEnabled = Settings.systemProxyEnabled
            if (systemProxyEnabled) {
                builder.setHttpProxy(
                    ProxyInfo.buildDirectProxy(options.httpProxyServer, options.httpProxyServerPort),
                )
            }
        } else {
            systemProxyAvailable = false
            systemProxyEnabled = false
        }

        val pfd = builder.establish()
        if (pfd == null) {
            Log.e(TAG, "Builder.establish() returned null; VPN permission may have been revoked")
            return -1
        }

        service.fileDescriptor?.let { oldPfd ->
            if (oldPfd !== pfd) {
                runCatching { oldPfd.close() }
                    .onFailure { Log.w(TAG, "failed to close previous TUN", it) }
            }
        }
        service.fileDescriptor = pfd
        return pfd.fd
    }

    override fun sendNotification(notification: Notification) = Unit
}

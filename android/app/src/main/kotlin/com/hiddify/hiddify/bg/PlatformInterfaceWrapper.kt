package com.hiddify.hiddify.bg

import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import com.hiddify.core.libbox.AutoRedirectHandler
import com.hiddify.core.libbox.AutoRedirectSession
import com.hiddify.core.libbox.BridgeOptions
import com.hiddify.core.libbox.BridgeSession
import com.hiddify.core.libbox.ConnectionOwner
import com.hiddify.core.libbox.InterfaceUpdateListener
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.libbox.LocalDNSTransport
import com.hiddify.core.libbox.NeighborSubscription
import com.hiddify.core.libbox.NeighborUpdateListener
import com.hiddify.core.libbox.NetworkInterfaceIterator
import com.hiddify.core.libbox.PlatformInterface
import com.hiddify.core.libbox.PlatformUser
import com.hiddify.core.libbox.ShellSession
import com.hiddify.core.libbox.StringBox
import com.hiddify.core.libbox.StringIterator
import com.hiddify.core.libbox.TunOptions
import com.hiddify.core.libbox.WIFIState
import com.hiddify.hiddify.Application
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.hiddify.core.libbox.NetworkInterface as LibboxNetworkInterface

private const val PLATFORM_TAG = "PlatformInterface"
private val neighborLock = Any()
private var neighborSubscription: NeighborSubscription? = null

interface PlatformInterfaceWrapper : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
    }

    override fun openTun(options: TunOptions): Int {
        Log.e(PLATFORM_TAG, "openTun called for a platform service that does not provide a TUN")
        return -1
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        val owner = ConnectionOwner()
        owner.userId = Process.INVALID_UID
        owner.userName = ""
        owner.setAndroidPackageNames(StringArray(emptyList<String>().iterator()))
        try {
            val uid = Application.connectivity.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort),
            )
            owner.userId = uid
            val packages = if (uid != Process.INVALID_UID) {
                Application.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
            } else {
                emptyList()
            }
            owner.userName = packages.firstOrNull() ?: ""
            owner.setAndroidPackageNames(StringArray(packages.iterator()))
        } catch (e: Exception) {
            // This method is called through gomobile and has no error return. Never unwind an
            // Android/OEM exception through the Go callback boundary.
            Log.w(PLATFORM_TAG, "getConnectionOwnerUid unavailable", e)
        }
        return owner
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        return try {
            val networks = Application.connectivity.allNetworks
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val interfaces = mutableListOf<LibboxNetworkInterface>()
            for (network in networks) {
                val boxInterface = LibboxNetworkInterface()
                val linkProperties = Application.connectivity.getLinkProperties(network) ?: continue
                val networkCapabilities = Application.connectivity.getNetworkCapabilities(network) ?: continue
                boxInterface.name = linkProperties.interfaceName
                val networkInterface = networkInterfaces.find { it.name == boxInterface.name } ?: continue
                boxInterface.dnsServer = StringArray(linkProperties.dnsServers.mapNotNull { it.hostAddress }.iterator())
                boxInterface.type = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                boxInterface.index = networkInterface.index
                boxInterface.mtu = runCatching { networkInterface.mtu }
                    .onFailure { Log.w(PLATFORM_TAG, "failed to get mtu for ${boxInterface.name}", it) }
                    .getOrDefault(1500)
                boxInterface.addresses = StringArray(
                    networkInterface.interfaceAddresses.mapTo(mutableListOf()) { it.toPrefix() }.iterator(),
                )

                var flags = 0
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                }
                if (runCatching { networkInterface.isLoopback }.getOrDefault(false)) {
                    flags = flags or OsConstants.IFF_LOOPBACK
                }
                if (runCatching { networkInterface.isPointToPoint }.getOrDefault(false)) {
                    flags = flags or OsConstants.IFF_POINTOPOINT
                }
                if (runCatching { networkInterface.supportsMulticast() }.getOrDefault(false)) {
                    flags = flags or OsConstants.IFF_MULTICAST
                }
                boxInterface.flags = flags
                boxInterface.metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                interfaces.add(boxInterface)
            }
            InterfaceArray(interfaces.iterator())
        } catch (e: Exception) {
            Log.w(PLATFORM_TAG, "failed to enumerate network interfaces", e)
            InterfaceArray(emptyList<LibboxNetworkInterface>().iterator())
        }
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {
    }

    override fun readWIFIState(): WIFIState? {
        return try {
            @Suppress("DEPRECATION")
            val wifiInfo = Application.wifiManager.connectionInfo ?: return null
            var ssid = wifiInfo.ssid ?: return null
            if (ssid == "<unknown ssid>") return WIFIState("", "")
            if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length >= 2) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            WIFIState(ssid, wifiInfo.bssid ?: "")
        } catch (e: Exception) {
            // ACCESS_WIFI_STATE/location behavior varies by Android/OEM. Treat it as optional.
            Log.w(PLATFORM_TAG, "Wi-Fi state unavailable", e)
            null
        }
    }

    override fun localDNSTransport(): LocalDNSTransport? = LocalResolver

    override fun cancelNotification(identifier: String, typeID: Int) = Unit

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {
        error("platform shell is not supported on Android")
    }

    override fun openShellSession(
        user: PlatformUser,
        command: String,
        environ: StringIterator,
        term: String,
        rows: Int,
        cols: Int,
    ): ShellSession = error("platform shell is not supported on Android")

    override fun lookupUser(username: String): PlatformUser =
        error("platform users are not supported on Android")

    override fun lookupSFTPServer(): StringBox =
        error("platform SFTP server is not supported on Android")

    override fun readSystemSSHHostKey(): StringBox =
        error("system SSH host key is not supported on Android")

    override fun tailscaleHostname(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions): BridgeSession =
        error("platform bridge is not supported on Android")

    override fun usePlatformAutoRedirect(): Boolean = false

    override fun createAutoRedirect(
        options: ByteArray,
        handler: AutoRedirectHandler,
    ): AutoRedirectSession = error("platform auto-redirect is not supported on Android")

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {
        synchronized(neighborLock) {
            runCatching { neighborSubscription?.close() }
                .onFailure { Log.w(PLATFORM_TAG, "failed to close previous neighbor monitor", it) }
            neighborSubscription = null
            if (listener == null) return
            try {
                neighborSubscription = Libbox.subscribeNeighborTable(listener)
            } catch (e: Exception) {
                Log.w(PLATFORM_TAG, "neighbor monitor is unavailable", e)
            }
        }
    }

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {
        synchronized(neighborLock) {
            runCatching { neighborSubscription?.close() }
                .onFailure { Log.w(PLATFORM_TAG, "failed to close neighbor monitor", it) }
            neighborSubscription = null
        }
    }

    override fun registerMyInterface(name: String?) {
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun systemCertificates(): StringIterator {
        val certificates = mutableListOf<String>()
        try {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null, null)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val cert = keyStore.getCertificate(aliases.nextElement()) ?: continue
                certificates.add(
                    "-----BEGIN CERTIFICATE-----\n" + Base64.encode(cert.encoded) + "\n-----END CERTIFICATE-----",
                )
            }
        } catch (e: Exception) {
            // Called by the Go core without an error return. An empty iterator is safer than
            // terminating the process due to a vendor KeyStore failure.
            Log.w(PLATFORM_TAG, "failed to load Android system certificates", e)
        }
        return StringArray(certificates.iterator())
    }

    // Neighbor monitor, platform shell, bridge and auto-redirect need root helpers
    // (RootClient in sing-box-for-android) which Hiddify does not ship; keep them disabled.
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {
    }

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {
    }

    override fun registerMyInterface(name: String?) {
    }

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {
        error("platform shell not supported")
    }

    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int,
    ): ShellSession {
        error("platform shell not supported")
    }

    override fun lookupUser(username: String?): PlatformUser {
        error("platform shell not supported")
    }

    override fun lookupSFTPServer(): StringBox {
        error("not supported")
    }

    override fun readSystemSSHHostKey(): StringBox {
        error("not supported")
    }

    override fun tailscaleHostname(): String = android.provider.Settings.Global.getString(
        Application.application.contentResolver,
        android.provider.Settings.Global.DEVICE_NAME,
    )?.takeIf { it.isNotBlank() }
        ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession {
        error("platform bridge not supported")
    }

    override fun usePlatformAutoRedirect(): Boolean = false

    override fun createAutoRedirect(options: ByteArray?, handler: AutoRedirectHandler?): AutoRedirectSession {
        error("platform auto redirect not supported")
    }

    private class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int = 0
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
    } else {
        "${address.hostAddress}/$networkPrefixLength"
    }
}

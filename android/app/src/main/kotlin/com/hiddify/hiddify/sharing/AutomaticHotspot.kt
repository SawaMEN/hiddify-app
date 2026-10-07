package com.hiddify.hiddify.sharing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The Android reservation owns DHCP, AP lifetime and system-generated credentials on older OSes. */
object AutomaticHotspot {
    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var generation = 0L
    private var context: Context? = null
    private var iface: String? = null
    private var address: String? = null
    private var root = false
    private var details: Map<String, Any?> = mapOf("active" to false)
    var listener: ((Map<String, Any?>) -> Unit)? = null

    fun snapshot(): Map<String, Any?> = details.toMap()
    private fun publish(value: Map<String, Any?>) {
        details = value
        main.post { listener?.invoke(snapshot()) }
    }

    fun permission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
        else Manifest.permission.ACCESS_FINE_LOCATION

    private fun addresses(): Map<String, Pair<String, String>> = NetworkInterface.getNetworkInterfaces()?.toList()
        .orEmpty().filter { it.isUp && !it.isLoopback && !it.name.startsWith("hc") && !it.name.startsWith("tun") }
        .flatMap { network -> network.inetAddresses.toList().filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }.map { ip -> "${network.name}/${ip.hostAddress}" to (network.name to ip.hostAddress!!) } }
        .toMap()

    suspend fun prepareRoot(context: Context) {
        // Existing root companion availability is checked before exposing an AP.
        check(com.hiddify.hiddify.privacy.RootCore.detect(context)["helper"] == true) { "This APK has no root companion" }
        HotspotRoot.prepare(context, permission())
    }

    private suspend fun cleanup(app: Context) {
        val endpoint = HotspotRoot.endpoint(app) ?: return
        withTimeout(10_000) {
            while (withContext(Dispatchers.IO) { addresses().values.any { it == endpoint } }) delay(200)
        }
        HotspotRoot.cleanup(app)
    }

    suspend fun start(appContext: Context, rootMode: Boolean): Map<String, Any?> = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            if (reservation != null) {
                check(root == rootMode) { "Stop sharing before changing its mode" }
                return@withContext snapshot()
            }
            val app = appContext.applicationContext
            check(ContextCompat.checkSelfPermission(app, permission()) == PackageManager.PERMISSION_GRANTED) {
                "Wi-Fi hotspot permission is required"
            }
            if (HotspotRoot.hasRecord(app)) {
                // A dead app reservation is released by Android; keep stale guards until its AP vanishes.
                cleanup(app)
            }
            context = app
            root = rootMode
            val before = withContext(Dispatchers.IO) { addresses() }
            val request = ++generation
            val ready = CompletableDeferred<WifiManager.LocalOnlyHotspotReservation>()
            val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    if (request != generation || !ready.complete(value)) value.close()
                }
                override fun onFailed(reason: Int) { ready.completeExceptionally(IllegalStateException("Android hotspot failed ($reason)")) }
                override fun onStopped() {
                    if (request == generation) {
                        ready.completeExceptionally(IllegalStateException("Android stopped the hotspot"))
                        reservation = null
                        publish(mapOf("active" to false))
                        // Root guards remain until stop() confirms that the AP interface is down.
                    }
                }
            }
            try {
                if (Build.VERSION.SDK_INT < 33) {
                    if (rootMode) HotspotRoot.enableLocation(app)
                    check(app.getSystemService(LocationManager::class.java).isLocationEnabled) {
                        "Android requires Location enabled to create a hotspot on this OS version"
                    }
                }
                val wifi = app.getSystemService(WifiManager::class.java)
                if (Build.VERSION.SDK_INT >= 36) {
                    val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
                    val password = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
                    val config = SoftApConfiguration.Builder().setSsid("VetrOFF-${password.takeLast(4)}")
                        .setPassphrase(password, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                        .setBand(SoftApConfiguration.BAND_2GHZ).build()
                    wifi.startLocalOnlyHotspotWithConfiguration(config, ContextCompat.getMainExecutor(app), callback)
                } else wifi.startLocalOnlyHotspot(callback, main)
                val value = withTimeout(30_000) { ready.await() }
                reservation = value
                val pair = withTimeout(10_000) {
                    var found: Pair<String, String>? = null
                    while (found == null) {
                        found = withContext(Dispatchers.IO) { addresses().entries.firstOrNull { it.key !in before }?.value }
                        if (found == null) delay(200)
                    }
                    found
                }
                iface = pair.first
                address = pair.second
                if (rootMode) HotspotRoot.install(app, pair.first, pair.second)
                @Suppress("DEPRECATION")
                val ssid = if (Build.VERSION.SDK_INT >= 30) value.softApConfiguration.ssid else value.wifiConfiguration?.SSID
                @Suppress("DEPRECATION")
                val password = if (Build.VERSION.SDK_INT >= 30) value.softApConfiguration.passphrase else value.wifiConfiguration?.preSharedKey
                check(!ssid.isNullOrBlank() && !password.isNullOrBlank()) { "Android did not return secured Wi-Fi credentials" }
                publish(mapOf("active" to true, "root" to rootMode, "ssid" to ssid, "password" to password,
                    "ip" to pair.second, "interface" to pair.first))
                snapshot()
            } catch (error: Exception) {
                ++generation
                ready.cancel()
                reservation?.close()
                reservation = null
                publish(mapOf("active" to false, "error" to (error.message ?: "Hotspot failed")))
                withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { cleanup(app); HotspotRoot.restoreLocation(app) }
                }
                throw error
            }
        }
    }

    suspend fun stop() = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            ++generation
            reservation?.close()
            reservation = null
            publish(mapOf("active" to false))
            val app = context ?: return@withContext
            if (root && HotspotRoot.hasRecord(app)) {
                // Do not remove leak guards while Android still exposes our old AP address.
                cleanup(app)
            }
            HotspotRoot.restoreLocation(app)
            iface = null; address = null; root = false
        }
    }
}

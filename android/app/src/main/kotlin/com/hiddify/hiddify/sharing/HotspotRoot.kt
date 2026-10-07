package com.hiddify.hiddify.sharing

import android.content.Context
import android.location.LocationManager
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.privacy.RootAccess
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object HotspotRoot {
    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    suspend fun run(script: String): String = withContext(Dispatchers.IO) {
        val su = RootAccess.findExecutable() ?: error("Root is unavailable")
        val child = ProcessBuilder(su, "-c", script).redirectErrorStream(true).start()
        val output = StringBuffer()
        val reader = Thread {
            child.inputStream.bufferedReader().use { input ->
                val buffer = CharArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    synchronized(output) { if (output.length < 8192) output.append(buffer, 0, count) }
                }
            }
        }.apply { isDaemon = true; start() }
        try {
            check(child.waitFor(30, TimeUnit.SECONDS)) { "Root command timed out" }
            reader.join(1000)
            check(child.exitValue() == 0) { "Root hotspot setup failed; check su permission and kernel netfilter support" }
            output.toString().trim()
        } finally { child.destroy() }
    }

    private fun record(context: Context) = File(context.filesDir, "hotspot-root-state.json")

    suspend fun prepare(context: Context, permission: String) {
        // Grant only the Wi-Fi permission required by this feature, to our own package.
        run("pm grant ${quote(context.packageName)} ${quote(permission)}")
    }

    suspend fun install(context: Context, iface: String, address: String) {
        val original = run("cat /proc/sys/net/ipv4/ip_forward")
        check(original in listOf("0", "1")) { "Cannot read forwarding state" }
        val chain = HotspotRootRules.chain(context.packageName.hashCode())
        val state = JSONObject().put("chain", chain).put("forward", original)
            .put("interface", iface).put("address", address)
        // Persist rollback before any privileged mutation, including partial failures.
        record(context).writeText(state.toString())
        run(HotspotRootRules.install(iface, Settings.rootRouteTable, chain))
    }

    suspend fun cleanup(context: Context) {
        val file = record(context)
        if (!file.exists()) return
        val state = JSONObject(file.readText())
        run(HotspotRootRules.remove(state.getString("chain"), state.getString("forward")))
        check(file.delete()) { "Cannot clear hotspot rollback state" }
    }

    fun hasRecord(context: Context) = record(context).isFile

    private fun locationRecord(context: Context) = File(context.filesDir, "hotspot-location-state")

    suspend fun enableLocation(context: Context) {
        if (context.getSystemService(LocationManager::class.java).isLocationEnabled) return
        val previous = run("settings get secure location_mode")
        check(previous.matches(Regex("[0-3]"))) { "Cannot read Location state" }
        locationRecord(context).writeText(previous)
        run("cmd location set-location-enabled true || settings put secure location_mode 3")
    }

    suspend fun restoreLocation(context: Context) {
        val file = locationRecord(context)
        if (!file.exists()) return
        val previous = file.readText()
        check(previous.matches(Regex("[0-3]")))
        run("settings put secure location_mode $previous")
        check(file.delete()) { "Cannot clear Location rollback state" }
    }

    fun endpoint(context: Context): Pair<String, String>? {
        if (!hasRecord(context)) return null
        val state = JSONObject(record(context).readText())
        return state.getString("interface") to state.getString("address")
    }
}

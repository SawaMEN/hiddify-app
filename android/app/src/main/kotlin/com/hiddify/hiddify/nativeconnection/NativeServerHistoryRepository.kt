package com.hiddify.hiddify.nativeconnection

import android.content.Context
import com.hiddify.hiddify.Settings
import org.json.JSONObject

/** Uses the Dart profile IDs, JSON samples and legacy shared_preferences list encoding. Call on IO. */
class NativeServerHistoryRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(profileId: String): Map<String, NativeServerSample> = synchronized(lock) {
        val raw = preferences.all["$prefix$profileId"] as? String ?: return@synchronized emptyMap()
        decode(raw)
    }

    fun save(profileId: String, samples: Map<String, NativeServerSample>) = synchronized(lock) {
        require(profileId.isNotBlank() && profileId.length <= 256)
        val json = JSONObject()
        val previous = decode(preferences.all["$prefix$profileId"] as? String ?: "")
        mergeNativeServerHistory(previous, samples).forEach { (tag, sample) ->
            json.put(tag, JSONObject().put("average", sample.average).put("successes", sample.successes)
                .put("failures", sample.failures).put("updated", sample.updated))
        }
        // Reconstruct the index from actual records, including histories left by Dart.
        // Reading serialized objects from preferences is unnecessary for this bounded cache.
        val existing = preferences.all.entries.filter { it.key.startsWith(prefix) && it.key != "flutter.server_health_profiles" && it.key != "$prefix$profileId" }
            .map { entry -> entry.key.removePrefix(prefix) to decode(entry.value as? String ?: "")
                .values.maxOfOrNull { it.updated }.let { it ?: 0L } }
            .sortedBy { it.second }.map { it.first }
        val retained = (existing + profileId).takeLast(16)
        val editor = preferences.edit().putString("$prefix$profileId", json.toString())
            .putString("flutter.server_health_profiles", Settings.encodeListString(retained))
        existing.filter { it !in retained }.forEach { editor.remove("$prefix$it") }
        check(editor.commit()) { "Could not save server health history" }
    }

    private fun decode(raw: String): Map<String, NativeServerSample> {
        if (raw.length > 262144) return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                json.keys().asSequence().take(128).forEach { tag ->
                    val sample = json.optJSONObject(tag) ?: return@forEach
                    try {
                        val value = NativeServerSample(sample.getDouble("average"), sample.getInt("successes"),
                            sample.getInt("failures"), sample.getLong("updated"))
                        if (tag.isNotBlank() && tag.length <= 1024 && value.average.isFinite() &&
                            value.average in 0.0..64999.0 && value.successes in 0..100 && value.failures in 0..20 && value.updated > 0) put(tag, value)
                    } catch (_: Exception) { /* Ignore only the malformed sample. */ }
                }
            }
        } catch (_: Exception) { emptyMap() }
    }

    private companion object {
        val lock = Any()
        const val prefix = "flutter.server_health_"
    }
}

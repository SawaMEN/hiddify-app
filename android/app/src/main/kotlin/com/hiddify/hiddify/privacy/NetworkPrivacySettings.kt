package com.hiddify.hiddify.privacy

import android.content.Context
import android.util.Log

/** Shared traffic policy for both Kotlin and the compatibility UI. */
object NetworkPrivacySettings {
    private const val TAG = "A/NetworkPrivacy"

    private fun boolean(context: Context, key: String): Boolean {
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val value = preferences.all["flutter.$key"] ?: return false
        if (value is Boolean) return value
        Log.w(TAG, "ignoring privacy preference with invalid type: $key (${value.javaClass.simpleName})")
        return false
    }

    fun loadFilters(context: Context): NativeTrafficFilters = NativeTrafficFilters.fromPreferences(
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all,
    )

    /** One disk transaction prevents a partially enabled group of filters. Call on IO. */
    fun saveFilters(context: Context, filters: NativeTrafficFilters) {
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
        filters.preferenceValues().forEach { (key, value) -> editor.putBoolean(key, value) }
        check(editor.commit()) { "Could not save traffic filters" }
    }

    fun policy(context: Context): Map<String, Boolean> = mapOf(
        "privacy-modern-allow-udp" to boolean(context, "privacy-modern-allow-udp"),
        "privacy-modern-protocols-only" to boolean(context, "privacy-modern-protocols-only"),
        "adaptive-network" to boolean(context, "adaptive_network"),
    ) + loadFilters(context).let { filters ->
        NativeTrafficFilter.entries.associate { it.key to (it in filters.enabled) }
    }
}

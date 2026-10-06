package com.hiddify.hiddify.privacy

import android.content.Context
import android.util.Log

/** Reads opt-in experimental traffic filters written by Flutter. */
object NetworkPrivacySettings {
    private const val TAG = "A/NetworkPrivacy"

    private fun boolean(context: Context, key: String): Boolean {
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val value = preferences.all["flutter.$key"] ?: return false
        if (value is Boolean) return value
        Log.w(TAG, "ignoring privacy preference with invalid type: $key (${value.javaClass.simpleName})")
        return false
    }

    fun policy(context: Context): Map<String, Boolean> = mapOf(
        "privacy-modern-allow-udp" to boolean(context, "privacy-modern-allow-udp"),
        "privacy-modern-protocols-only" to boolean(context, "privacy-modern-protocols-only"),
        "adaptive-network" to boolean(context, "adaptive_network"),
        "privacy-anonymization-block-quic" to boolean(context, "privacy-anonymization-block-quic"),
        "privacy-anonymization-block-stun" to boolean(context, "privacy-anonymization-block-stun"),
        "privacy-anonymization-block-plain-http" to boolean(context, "privacy-anonymization-block-plain-http"),
        "privacy-anonymization-isolate-lan" to boolean(context, "privacy-anonymization-isolate-lan"),
    )
}

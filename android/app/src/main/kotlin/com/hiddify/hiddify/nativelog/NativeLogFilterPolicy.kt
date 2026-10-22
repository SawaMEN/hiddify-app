package com.hiddify.hiddify.nativelog

/**
 * Persist only the log severity. Search strings may contain hostnames, tokens
 * or other private diagnostic fragments and must remain session-only.
 */
internal object NativeLogFilterPolicy {
    const val PREFERENCE_FILE = "native_log_view"
    const val LEVEL_KEY = "minimum_severity"
    fun restore(raw: String?): String? =
        raw?.uppercase(java.util.Locale.ROOT)?.takeIf { it in NativeLogPresentation.levels }
}

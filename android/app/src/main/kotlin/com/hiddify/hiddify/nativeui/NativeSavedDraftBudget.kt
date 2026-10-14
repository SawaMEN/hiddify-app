package com.hiddify.hiddify.nativeui

/** Saved-instance state shares Android's Binder budget with every other screen. */
internal object NativeSavedDraftBudget {
    private const val MAX_BYTES = 16 * 1024L

    fun fits(value: String): Boolean = 16L + value.length * 2L <= MAX_BYTES

    fun fits(values: List<String>): Boolean {
        var bytes = 16L
        for (value in values) {
            bytes += 16L + value.length * 2L
            if (bytes > MAX_BYTES) return false
        }
        return true
    }
}

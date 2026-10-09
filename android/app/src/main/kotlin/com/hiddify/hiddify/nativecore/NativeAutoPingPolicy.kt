package com.hiddify.hiddify.nativecore

/** Foreground ping schedule. Use monotonic time so wall-clock changes cannot stall it. */
internal class NativeAutoPingPolicy {
    private var target: String? = null
    private var lastStarted: Long? = null

    fun shouldTest(key: String, now: Long): Boolean {
        if (target != key) {
            target = key
            lastStarted = null
        }
        return lastStarted?.let { now - it >= 60_000 } ?: true
    }

    fun started(now: Long) { lastStarted = now }
    fun reset() { target = null; lastStarted = null }
}

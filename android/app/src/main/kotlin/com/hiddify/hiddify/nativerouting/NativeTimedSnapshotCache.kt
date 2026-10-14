package com.hiddify.hiddify.nativerouting

import java.util.concurrent.atomic.AtomicLong

/** One bounded snapshot, with single-flight loading and nonblocking invalidation. */
internal class NativeTimedSnapshotCache<K : Any, V : Any>(
    private val lifetimeMs: Long,
    private val clockMs: () -> Long,
) {
    private data class Entry<K, V>(val key: K, val value: V, val generation: Long, val loadedAt: Long)
    private val generation = AtomicLong()
    private val loadLock = Any()
    @Volatile private var entry: Entry<K, V>? = null

    init { require(lifetimeMs > 0) }

    fun get(key: K, loader: () -> V): V {
        fun cached(): V? {
            val current = entry ?: return null
            val age = clockMs() - current.loadedAt
            return current.value.takeIf {
                current.key == key && current.generation == generation.get() && age >= 0 && age < lifetimeMs
            }
        }
        cached()?.let { return it }
        return synchronized(loadLock) {
            cached() ?: run {
                val revision = generation.get()
                val value = loader()
                // Do not keep a result computed across a package-change notification.
                if (generation.get() == revision) entry = Entry(key, value, revision, clockMs())
                value
            }
        }
    }

    fun invalidate() {
        generation.incrementAndGet()
        entry = null
    }
}

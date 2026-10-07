package com.hiddify.hiddify.nativeconnection

/** Foreground fallback for raw configurations without the core's `lowest` balancer. */
class NativeServerRanker {
    private data class Sample(var average: Double = 0.0, var successes: Int = 0, var failures: Int = 0, var updated: Long = 0)
    private val samples = linkedMapOf<String, Sample>()
    private var candidate: String? = null
    private var wins = 0
    private var evaluated = 0L
    private var lastSwitch = 0L

    fun observe(tag: String, delay: Int, timestamp: Long, now: Long) {
        if (timestamp <= 0 || timestamp > now + 1000) return
        val sample = samples.getOrPut(tag) { Sample() }
        if (timestamp <= sample.updated) return
        sample.updated = timestamp
        if (delay in 1..64999) {
            sample.average = if (sample.successes == 0) delay.toDouble() else sample.average * .7 + delay * .3
            sample.successes = (sample.successes + 1).coerceAtMost(100)
            sample.failures = 0
        } else sample.failures = (sample.failures + 1).coerceAtMost(20)
    }

    fun recommend(current: String, eligible: Set<String>, now: Long): String? {
        samples.keys.retainAll(eligible)
        if (lastSwitch != 0L && now - lastSwitch < 120000) return null
        val best = samples.entries.filter { (_, sample) ->
            sample.successes >= 3 && sample.failures == 0 && now - sample.updated in 0..600000L
        }.minByOrNull { it.value.average }
        if (best == null || best.key == current) { candidate = null; wins = 0; return null }
        val selected = samples[current]
        if (selected == null || (selected.failures < 3 && (selected.successes == 0 ||
            selected.average - best.value.average < 50 || best.value.average > selected.average * .8))) {
            candidate = null; wins = 0; return null
        }
        val stamp = maxOf(best.value.updated, selected.updated)
        if (stamp <= evaluated) return null
        evaluated = stamp
        if (candidate == best.key) wins++ else { candidate = best.key; wins = 1 }
        return best.key.takeIf { wins >= 3 }
    }

    fun switched(now: Long) { lastSwitch = now; candidate = null; wins = 0 }
}

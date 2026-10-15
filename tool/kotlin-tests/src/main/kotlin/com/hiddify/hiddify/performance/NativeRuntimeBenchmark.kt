package com.hiddify.hiddify.performance

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.keepNativeOutboundOrder
import com.hiddify.hiddify.nativecore.presentNativeOutbounds
import com.hiddify.hiddify.nativepreferences.NativeOutboundSort
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import com.hiddify.hiddify.nativelog.NativeLogTailCache
import com.hiddify.hiddify.nativelog.NativeRecentLogLines
import java.lang.management.ManagementFactory
import java.math.BigInteger
import java.util.Locale

// Opt-in microbenchmark. Baselines preserve the previous implementation for comparison.
// Timing is informational; correctness tests have no timing thresholds.
object NativeRuntimeBenchmark {
    @Volatile private var sink: Any? = null

    @JvmStatic fun main(args: Array<String>) {
        val random = java.util.Random(23)
        val items = (0 until 5000).map {
            NativeOutbound("p$it", "p$it", "vless", false, true, it % 100, "example.com", 443,
                random.nextLong().ushr(1), random.nextLong().ushr(1), null)
        }.shuffled(kotlin.random.Random(23))
        val order = items.map { it.tag }.shuffled(kotlin.random.Random(24))
        val json = JsonArray().apply { repeat(50_000) { add("value $it") } }
        compare("scroll-order/5000", { oldOrder(items, order) }, { keepNativeOutboundOrder(items, order) })
        compare("traffic-sort/5000", { oldUsage(items) }, { presentNativeOutbounds(items, "", NativeOutboundSort.USAGE) })
        compare("json-hit/50000", { oldSearch(json, "value") }, { NativeJsonDocument.search(json, "value") })
        compare("json-miss/50000", { oldSearch(json, "absent") }, { NativeJsonDocument.search(json, "absent") })
        val directory = java.nio.file.Files.createTempDirectory("native-log-bench").toFile()
        try {
            val files = (0 until 9).map { index -> java.io.File(directory, "source$index.log").apply {
                writeText((0 until 5000).joinToString("\n") { "INFO $it: example diagnostic message" })
            } }
            val cache = NativeLogTailCache { 0 }
            compare("unchanged-logs/9x5000", {
                files.flatMap { file -> cache.read(file, 256 * 1024L).lineSequence()
                    .filter { it.isNotBlank() }.map { "[${file.name}] $it" }.toList() }.takeLast(500)
            }, {
                NativeRecentLogLines(500).apply {
                    files.forEach { appendPresented(cache.readLines(it, 256 * 1024L, 500)) }
                }.snapshot()
            })
        } finally { directory.deleteRecursively() }
    }

    private fun compare(name: String, before: () -> Any, after: () -> Any) {
        check(before() == after()) { "Different results: $name" }
        repeat(30) { sink = before(); sink = after() }
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        fun allocated(): Long = if (bean?.isThreadAllocatedMemoryEnabled == true) bean.getThreadAllocatedBytes(thread) else -1
        fun sample(action: () -> Any): Pair<Double, Long> {
            val bytes = allocated()
            val start = System.nanoTime()
            repeat(20) { sink = action() }
            val elapsed = (System.nanoTime() - start) / 20_000.0
            val endBytes = allocated()
            return elapsed to if (bytes >= 0 && endBytes >= 0) (endBytes - bytes) / 20 else -1
        }
        val old = ArrayList<Pair<Double, Long>>()
        val new = ArrayList<Pair<Double, Long>>()
        repeat(9) { index ->
            if (index % 2 == 0) { old += sample(before); new += sample(after) }
            else { new += sample(after); old += sample(before) }
        }
        val oldTime = old.map { it.first }.sorted()[4]
        val newTime = new.map { it.first }.sorted()[4]
        val oldBytes = old.map { it.second }.sorted()[4]
        val newBytes = new.map { it.second }.sorted()[4]
        println(String.format(Locale.ROOT, "%s: before %.1f us, %d bytes; after %.1f us, %d bytes; %.2fx", name, oldTime, oldBytes, newTime, newBytes, oldTime / newTime))
    }

    private fun oldOrder(items: List<NativeOutbound>, order: List<String>): List<NativeOutbound> {
        val positions = order.withIndex().associate { it.value to it.index }
        return items.sortedBy { positions[it.tag] ?: Int.MAX_VALUE }
    }

    private fun oldUsage(items: List<NativeOutbound>): List<NativeOutbound> {
        val filtered = items.filter { it.tag.contains("", true) || it.type.contains("", true) }
        val usage = filtered.associate { it.tag to BigInteger.valueOf(it.upload.coerceAtLeast(0)).add(BigInteger.valueOf(it.download.coerceAtLeast(0))) }
        return filtered.sortedWith(Comparator { a, b ->
            if (a.isGroup != b.isGroup) return@Comparator if (a.isGroup) -1 else 1
            usage.getValue(b.tag).compareTo(usage.getValue(a.tag))
        })
    }

    private fun oldSearch(root: JsonElement, query: String): List<String> {
        val result = mutableListOf<String>()
        fun visit(value: JsonElement, path: String) {
            if (result.size >= 1000) return
            val children = when {
                value.isJsonObject -> value.asJsonObject.entrySet().map { it.key to it.value }
                value.isJsonArray -> value.asJsonArray.mapIndexed { index, child -> index.toString() to child }
                else -> emptyList()
            }
            for ((key, child) in children) {
                if (result.size >= 1000) return
                val next = NativeJsonDocument.child(path, key)
                val text = if (child.isJsonPrimitive) child.asString else if (child.isJsonNull) "null" else ""
                if (key.contains(query, true) || text.contains(query, true)) result += next
                visit(child, next)
            }
        }
        visit(root, "")
        return result
    }
}

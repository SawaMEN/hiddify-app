package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativelog.NativeLogTailCache
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class NativeLogTailCacheTest {
    @Test fun appendAndTruncateInvalidateCachedTail() {
        val file = File.createTempFile("tail-test", ".log")
        try {
            val cache = NativeLogTailCache()
            file.writeText("one\n")
            assertEquals("one\n", cache.read(file, 100))
            file.appendText("two\n")
            assertEquals("one\ntwo\n", cache.read(file, 100))
            file.writeText("")
            assertEquals("", cache.read(file, 100))
        } finally { file.delete() }
    }
    @Test fun equalSizeAndTimestampRotationEventuallyRefreshes() {
        val file = File.createTempFile("tail-test", ".log")
        try {
            var now = 100L
            val cache = NativeLogTailCache { now }
            file.writeText("old\n")
            val modified = file.lastModified()
            assertEquals("old\n", cache.read(file, 100))
            file.writeText("new\n"); check(file.setLastModified(modified))
            assertEquals("old\n", cache.read(file, 100))
            now += 10_000
            assertEquals("new\n", cache.read(file, 100))
            file.writeText("raw\n"); check(file.setLastModified(modified))
            cache.clear()
            assertEquals("raw\n", cache.read(file, 100))
        } finally { file.delete() }
    }

    @Test fun allNineSourcesRemainCachedAcrossPassesAndCacheStaysBounded() {
        val files = (0 until 17).map { File.createTempFile("tail-test", ".log").apply { writeText("line\n") } }
        try {
            val cache = NativeLogTailCache { 100 }
            val first = files.take(9).map { cache.read(it, 100) }
            repeat(3) { files.take(9).forEachIndexed { index, file -> assertSame(first[index], cache.read(file, 100)) } }
            files.drop(9).forEach { cache.read(it, 100) }
            assertNotSame(first[0], cache.read(files[0], 100))
        } finally { files.forEach { it.delete() } }
    }

    @Test fun displayLinesReuseUnchangedTailsAndInvalidateOnLimitsWritesExpiryAndClear() {
        val file = File.createTempFile("tail-test", ".log")
        try {
            var now = 100L
            val cache = NativeLogTailCache { now }
            file.writeText("one\n \ntwo\nthree\n")
            val first = cache.readLines(file, 100, 2)
            assertEquals(listOf("[${file.name}] two", "[${file.name}] three"), first)
            assertSame(first, cache.readLines(file, 100, 2))
            assertEquals(listOf("[${file.name}] three"), cache.readLines(file, 100, 1))
            file.appendText("four\n")
            val appended = cache.readLines(file, 100, 2)
            assertEquals(listOf("[${file.name}] three", "[${file.name}] four"), appended)
            assertEquals(listOf("[${file.name}] two", "[${file.name}] three"), first)
            now += 10_000
            assertNotSame(appended, cache.readLines(file, 100, 2))
            val expired = cache.readLines(file, 100, 2)
            cache.clear()
            assertNotSame(expired, cache.readLines(file, 100, 2))
            file.writeText(" \n\t")
            assertTrue(cache.readLines(file, 100, 2).isEmpty())
        } finally { file.delete() }
    }
}

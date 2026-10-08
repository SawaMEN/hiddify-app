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
}

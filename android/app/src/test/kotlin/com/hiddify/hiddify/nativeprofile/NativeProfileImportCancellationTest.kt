package com.hiddify.hiddify.nativeprofile

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class NativeProfileImportCancellationTest {
    @Test fun failingNativeCallbackStillMarksTheImportCancelled() {
        val token = NativeProfileImportCancellation()
        val error = IllegalStateException("native cancellation failed")
        token.attachRequest { throw error }
        assertSame(error, assertThrows(IllegalStateException::class.java) { token.cancel() })
        assertThrows(CancellationException::class.java) { token.ensureActive() }
        assertThrows(CancellationException::class.java) { token.beginCommit() }
        assertFalse(token.cancel())
    }

    @Test fun cancellationBeforeRequestPreventsExecutionAndCommit() {
        val token = NativeProfileImportCancellation()
        assertTrue(token.cancel())
        var closed = false
        assertThrows(CancellationException::class.java) { token.attachRequest { closed = true } }
        assertTrue(closed)
        assertThrows(CancellationException::class.java) { token.beginCommit() }
        assertFalse(token.cancel())
    }

    @Test fun cancellationInterruptsOnlyTheCurrentlyAttachedRequest() {
        val token = NativeProfileImportCancellation()
        var first = 0
        var nested = 0
        token.attachRequest { first++ }
        token.detachRequest()
        token.attachRequest { nested++ }
        assertTrue(token.cancel())
        assertFalse(token.cancel())
        assertEquals(0, first)
        assertEquals(1, nested)
        token.detachRequest()
        assertThrows(CancellationException::class.java) { token.ensureActive() }
    }

    @Test fun completedDownloadsCanCommitAndLateCancelCannotInterruptWrites() {
        val token = NativeProfileImportCancellation()
        token.attachRequest { fail("Completed request must not be cancelled") }
        token.detachRequest()
        token.beginCommit()
        assertFalse(token.cancel())
        token.ensureActive()
        assertThrows(IllegalStateException::class.java) { token.attachRequest {} }
    }

    @Test fun retryHasIndependentCancellationAndRequests() {
        val cancelledImport = NativeProfileImportCancellation()
        val retry = NativeProfileImportCancellation()
        retry.attachRequest { fail("Other import must not be cancelled") }
        cancelledImport.cancel()
        retry.ensureActive()
        retry.detachRequest()
        retry.beginCommit()
    }

    @Test fun cancelAndCommitHaveExactlyOneWinner() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(250) {
                val token = NativeProfileImportCancellation()
                val ready = CountDownLatch(1)
                val winners = AtomicInteger()
                val cancel = workers.submit {
                    ready.await()
                    if (token.cancel()) winners.incrementAndGet()
                }
                val commit = workers.submit {
                    ready.await()
                    try { token.beginCommit(); winners.incrementAndGet() }
                    catch (_: CancellationException) { }
                }
                ready.countDown()
                cancel.get()
                commit.get()
                assertEquals(1, winners.get())
            }
        } finally { workers.shutdownNow() }
    }
    @Test fun archiveCanConfirmAndCommitMultipleEntries() {
        val token = NativeProfileImportCancellation()
        repeat(3) {
            token.attachRequest { fail("No cancellation requested") }
            token.detachRequest()
            token.beginCommit()
            token.finishArchiveEntry()
            token.ensureActive()
        }
    }

    @Test fun cancellationDuringArchiveWriteIsDeferredUntilNextEntry() {
        val token = NativeProfileImportCancellation()
        token.beginCommit()
        assertFalse(token.cancel())
        // The in-flight transaction completes without aborting the atomic file/DB write.
        token.ensureActive()
        token.finishArchiveEntry()
        assertThrows(CancellationException::class.java) { token.ensureActive() }
        assertThrows(CancellationException::class.java) { token.beginCommit() }
        assertFalse(token.cancel())
    }

    @Test fun cancellationBetweenArchiveEntriesPreventsNextCommit() {
        val token = NativeProfileImportCancellation()
        token.beginCommit()
        token.finishArchiveEntry()
        assertTrue(token.cancel())
        assertThrows(CancellationException::class.java) { token.beginCommit() }
    }

}

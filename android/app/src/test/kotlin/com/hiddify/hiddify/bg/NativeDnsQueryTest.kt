package com.hiddify.hiddify.bg

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeDnsQueryTest {
    private fun assertOriginalFailure(expected: Throwable, actual: Throwable) {
        // Coroutine debug/stack recovery can copy the exception and chain the original.
        assertEquals(expected.javaClass, actual.javaClass)
        assertEquals(expected.message, actual.message)
        assertTrue(generateSequence(actual) { it.cause }.take(16).any { it === expected })
    }

    @Test fun onlyTheFirstAndroidCallbackCompletesTheQuery() = runBlocking {
        var cancelled = 0
        val result = NativeDnsQuery.await<Pair<String, Int>>({ cancelled++ }, { fail("Unexpected cleanup error") }) { success, failure ->
            success("answer" to 0)
            failure(IOException("late error"))
            success("duplicate" to 3)
        }
        assertEquals("answer" to 0, result)
        assertEquals(0, cancelled)
    }

    @Test fun cancellationCleanupFailureDoesNotEscapeIntoTheUncaughtHandler() = runBlocking {
        val unhandled = mutableListOf<Throwable>()
        val reported = mutableListOf<Exception>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined +
            CoroutineExceptionHandler { _, error -> unhandled += error })
        val cleanupError = IllegalStateException("Android DNS signal failed")
        var cancelCalls = 0
        lateinit var answer: (String) -> Unit
        lateinit var failQuery: (Exception) -> Unit
        try {
            val query = scope.async {
                NativeDnsQuery.await<String>({ cancelCalls++; throw cleanupError }, { reported += it }) { success, failure ->
                    answer = success; failQuery = failure
                }
            }
            query.cancel()
            query.join()
            answer("late answer")
            failQuery(IOException("late error"))
            assertTrue(query.isCancelled)
            assertEquals(1, cancelCalls)
            assertEquals(listOf(cleanupError), reported)
            assertTrue(unhandled.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun startFailureIsPreservedWhenSignalCleanupAlsoFails() = runBlocking {
        val original = IOException("query registration failed")
        val cleanup = IllegalArgumentException("signal failed")
        val reported = mutableListOf<Exception>()
        try {
            NativeDnsQuery.await<String>({ throw cleanup }, { reported += it }) { _, _ -> throw original }
            fail("Expected the original start failure")
        } catch (error: IOException) { assertOriginalFailure(original, error) }
        assertEquals(listOf(cleanup), reported)
    }

    @Test fun startFailureIsPreservedWhenCleanupInvokesAnotherErrorCallback() = runBlocking {
        val original = IOException("query registration failed")
        lateinit var lateFailure: (Exception) -> Unit
        try {
            NativeDnsQuery.await<String>({ lateFailure(IllegalStateException("signal error")) }, {}) { _, failure ->
                lateFailure = failure
                throw original
            }
            fail("Expected original registration failure")
        } catch (error: IOException) { assertOriginalFailure(original, error) }
    }

    @Test fun cancellationCanSynchronouslyInvokeALateCallback() = runBlocking {
        var cancelCalls = 0
        lateinit var answer: (String) -> Unit
        val query = async(start = CoroutineStart.UNDISPATCHED) {
            NativeDnsQuery.await<String>({ cancelCalls++; answer("late answer") }, { fail("Unexpected cleanup error") }) { success, _ ->
                answer = success
            }
        }
        query.cancelAndJoin()
        assertTrue(query.isCancelled)
        assertEquals(1, cancelCalls)
    }

    @Test fun failedErrorReportingDoesNotInterruptQueryCancellation() = runBlocking {
        val query = async(start = CoroutineStart.UNDISPATCHED) {
            NativeDnsQuery.await<String>(
                { throw IllegalStateException("signal failed") },
                { throw IOException("diagnostic sink failed") },
            ) { _, _ -> }
        }
        query.cancelAndJoin()
        assertTrue(query.isCancelled)
    }

    @Test fun completedErrorCannotBeOverwrittenByAnAnswer() = runBlocking {
        val original = IOException("DNS query failed")
        try {
            NativeDnsQuery.await<String>({}, {}) { success, failure ->
                failure(original)
                success("late answer")
            }
            fail("Expected DNS query failure")
        } catch (error: IOException) { assertOriginalFailure(original, error) }
    }
}

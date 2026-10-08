package com.hiddify.hiddify.nativeprofile

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class NativeProfileValidationTest {
    private class Backend : NativeProfileValidationBackend {
        val calls = mutableListOf<String>()
        var onBegin: () -> Unit = {}
        var onValidate: () -> Unit = {}
        override fun begin(id: String) { calls += "begin"; onBegin() }
        override fun validate(id: String, content: String, settings: String) { calls += "validate"; onValidate() }
        override fun cancel(id: String) { calls += "cancel" }
        override fun finish(id: String) { calls += "finish" }
    }

    @Test fun successReleasesNativeSessionAndAllowsCommit() {
        val backend = Backend()
        val token = NativeProfileImportCancellation()
        NativeProfileValidation.validate("source", "{}", token, backend)
        assertEquals(listOf("begin", "validate", "finish"), backend.calls)
        token.beginCommit()
        assertFalse(token.cancel())
    }

    @Test fun cancelledBeforeRegistrationNeverEntersNativeCode() {
        val backend = Backend()
        val token = NativeProfileImportCancellation().apply { cancel() }
        assertThrows(CancellationException::class.java) { NativeProfileValidation.validate("source", "{}", token, backend) }
        assertTrue(backend.calls.isEmpty())
    }

    @Test fun cancellationBetweenRegistrationAndParserEntryClosesSession() {
        val token = NativeProfileImportCancellation()
        val backend = Backend().apply { onBegin = { token.cancel() } }
        assertThrows(CancellationException::class.java) { NativeProfileValidation.validate("source", "{}", token, backend) }
        assertEquals(listOf("begin", "cancel", "finish"), backend.calls)
        assertThrows(CancellationException::class.java) { token.beginCommit() }
    }

    @Test fun cancellationDuringValidationCannotCommitEvenIfParserReturns() {
        val token = NativeProfileImportCancellation()
        val backend = Backend().apply { onValidate = { token.cancel() } }
        assertThrows(CancellationException::class.java) { NativeProfileValidation.validate("source", "{}", token, backend) }
        assertEquals(listOf("begin", "validate", "cancel", "finish"), backend.calls)
        assertThrows(CancellationException::class.java) { token.beginCommit() }
    }

    @Test fun parserFailureIsRetainedAndDoesNotLeakTheSession() {
        val error = IllegalArgumentException("invalid configuration")
        val backend = Backend().apply { onValidate = { throw error } }
        val token = NativeProfileImportCancellation()
        assertSame(error, runCatching { NativeProfileValidation.validate("source", "{}", token, backend) }.exceptionOrNull())
        assertEquals(listOf("begin", "validate", "finish"), backend.calls)
        assertTrue(token.cancel())
        assertEquals(listOf("begin", "validate", "finish"), backend.calls)
    }
}

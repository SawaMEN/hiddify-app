package com.hiddify.hiddify.nativeprofile

import java.util.UUID

interface NativeProfileValidationBackend {
    fun begin(id: String)
    fun validate(id: String, content: String, settings: String)
    fun cancel(id: String)
    fun finish(id: String)
}

/** Register native cancellation first, including cancellation just before JNI parser entry. */
object NativeProfileValidation {
    fun validate(
        content: String,
        settings: String,
        cancellation: NativeProfileImportCancellation,
        backend: NativeProfileValidationBackend,
    ) {
        cancellation.ensureActive()
        val id = UUID.randomUUID().toString()
        backend.begin(id)
        try {
            cancellation.attachRequest { backend.cancel(id) }
            cancellation.ensureActive()
            backend.validate(id, content, settings)
            cancellation.ensureActive()
        } catch (error: Exception) {
            cancellation.ensureActive()
            throw error
        } finally {
            cancellation.detachRequest()
            backend.finish(id)
        }
    }
}

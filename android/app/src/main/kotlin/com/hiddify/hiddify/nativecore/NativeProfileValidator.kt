package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativeprofile.NativeProfileImportCancellation
import com.hiddify.hiddify.nativeprofile.NativeProfileValidation
import com.hiddify.hiddify.nativeprofile.NativeProfileValidationBackend
import com.hiddify.core.mobile.Mobile

/** Native validation without Mobile.setup/start or changing the active core's options. */
internal object NativeProfileValidator : NativeProfileValidationBackend {
    override fun begin(id: String) {
        NativeCoreLibrary.ensureLoaded()
        Mobile.beginProfileValidation(id)
    }
    override fun validate(id: String, content: String, settings: String) = Mobile.validateProfile(id, content, settings)
    override fun cancel(id: String) = Mobile.cancelProfileValidation(id)
    override fun finish(id: String) = Mobile.finishProfileValidation(id)

    fun validate(content: String, settings: String, cancellation: NativeProfileImportCancellation?) {
        NativeProfileValidation.validate(content, settings, cancellation ?: NativeProfileImportCancellation(), this)
    }
}

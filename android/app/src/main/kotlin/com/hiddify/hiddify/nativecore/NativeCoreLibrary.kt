package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.Application
import go.Seq

internal object NativeCoreLibrary {
    private val initialization = NativeRuntimeInitialization {
        Seq.setContext(Application.application)
        // Initialize both generated bindings here, inside the linkage error boundary.
        Class.forName("com.hiddify.core.libbox.Libbox")
        Class.forName("com.hiddify.core.mobile.Mobile")
    }

    fun ensureLoaded() = initialization.ensureLoaded()
}

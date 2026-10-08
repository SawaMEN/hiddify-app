package com.hiddify.hiddify.nativecore

/** Load on first core operation. JNI linkage failures must reach the normal error UI. */
internal class NativeRuntimeInitialization(private val load: () -> Unit) {
    private var loaded = false
    private var failure: LinkageError? = null

    @Synchronized
    fun ensureLoaded() {
        failure?.let { throw IllegalStateException("Unable to load VPN core. Reinstall a compatible ARM64 build.", it) }
        if (loaded) return
        try {
            load()
            loaded = true
        } catch (error: LinkageError) {
            failure = error
            throw IllegalStateException("Unable to load VPN core. Reinstall a compatible ARM64 build.", error)
        }
    }
}

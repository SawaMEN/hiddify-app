package com.hiddify.hiddify.nativeupdate

enum class NativeUpdatePhase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, VERIFYING, READY, FAILED }
data class NativeAppUpdateState(
    val channel: NativeUpdateChannel = NativeUpdateChannel.STABLE,
    val phase: NativeUpdatePhase = NativeUpdatePhase.IDLE,
    val release: NativeAppRelease? = null,
    val skipped: Boolean = false,
    val autoDownload: Boolean = true,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val waiting: Boolean = false,
    val error: String? = null,
) {
    val downloading get() = phase == NativeUpdatePhase.DOWNLOADING || phase == NativeUpdatePhase.VERIFYING
    val busy get() = downloading || phase == NativeUpdatePhase.CHECKING
    val showBanner get() = release != null && !skipped
    internal fun checkCancelled(): NativeAppUpdateState = if (phase == NativeUpdatePhase.CHECKING)
        copy(phase = if (release == null) NativeUpdatePhase.IDLE else NativeUpdatePhase.AVAILABLE) else this
}


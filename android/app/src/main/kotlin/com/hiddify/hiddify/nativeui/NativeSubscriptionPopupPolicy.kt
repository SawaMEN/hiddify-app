package com.hiddify.hiddify.nativeui

/** Only a visual, in-app message. Never posted to NotificationManager. */
data class NativeSubscriptionBanner(
    val revision: Long,
    val message: String,
    val kind: Kind,
) {
    enum class Kind { SUCCESS, INFO, ERROR }
}

/** Pure state rules allow timeout/race conditions to be tested without Compose. */
internal object NativeSubscriptionPopupPolicy {
    const val NORMAL_DURATION_MS = 3_400L
    const val ERROR_DURATION_MS = 5_200L

    fun durationMillis(kind: NativeSubscriptionBanner.Kind): Long =
        if (kind == NativeSubscriptionBanner.Kind.ERROR) ERROR_DURATION_MS else NORMAL_DURATION_MS

    /** A stale coroutine must never dismiss a newer popup. */
    fun dismiss(current: NativeSubscriptionBanner?, revision: Long): NativeSubscriptionBanner? =
        current?.takeUnless { it.revision == revision }
}

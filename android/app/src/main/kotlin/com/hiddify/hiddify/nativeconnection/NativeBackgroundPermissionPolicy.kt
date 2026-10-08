package com.hiddify.hiddify.nativeconnection

/** One automatic reminder per app version; manual entry in Settings always remains available. */
internal object NativeBackgroundPermissionPolicy {
    fun shouldPrompt(
        vpnStarted: Boolean,
        appResumed: Boolean,
        notificationPermissionPending: Boolean,
        batteryExempt: Boolean,
        alreadyShown: Boolean,
        dialogVisible: Boolean,
    ): Boolean =
        vpnStarted && appResumed && !notificationPermissionPending &&
            !batteryExempt && !alreadyShown && !dialogVisible
}

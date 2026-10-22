package com.hiddify.hiddify.nativeconnection

import com.hiddify.hiddify.constant.Status

/**
 * Event-level motion rules, intentionally independent of Compose and VPN lifecycle.
 * Animation must never infer a successful connection from stale UI snapshots.
 */
internal object NativeConnectionMotionPolicy {
    fun shouldCelebrate(
        previous: Status?,
        current: Status,
        motionEnabled: Boolean,
        recovering: Boolean,
        reconnecting: Boolean,
        failed: Boolean,
        requiresReconnect: Boolean,
    ): Boolean =
        motionEnabled && previous == Status.Starting && current == Status.Started &&
            !recovering && !reconnecting && !failed && !requiresReconnect

    fun showProgress(status: Status, recovering: Boolean, reconnecting: Boolean): Boolean =
        status == Status.Starting || status == Status.Stopping || recovering || reconnecting
}

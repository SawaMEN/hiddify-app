package com.hiddify.hiddify.nativeprofile

import java.time.Duration
import java.time.LocalDateTime

/** Subscription headers are untrusted; invalid metadata must not break profile updates. */
object NativeSubscriptionMetadata {
    const val INFINITE_TRAFFIC = 1_099_511_627_776_001L
    const val INFINITE_EXPIRE_SECONDS = 92_233_720_368L

    data class Usage(val upload: Long, val download: Long, val total: Long, val expireSeconds: Long)

    fun intervalSeconds(hours: Long?): Long? = hours
        ?.takeIf { it > 0 && it <= Long.MAX_VALUE / 3600L }
        ?.times(3600L)

    fun isUpdateDue(lastUpdate: String, intervalSeconds: Long?, now: LocalDateTime): Boolean {
        val interval = intervalSeconds?.takeIf { it > 0 } ?: return false
        val last = runCatching { LocalDateTime.parse(lastUpdate) }.getOrNull() ?: return true
        // Adding an arbitrary server-supplied interval to a date can overflow LocalDateTime.
        return Duration.between(last, now).seconds >= interval
    }

    fun usage(value: String?): Usage? {
        val fields = value?.split(';')?.mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) null else pieces[0].trim().lowercase() to pieces[1].trim().toLongOrNull()
        }?.toMap() ?: return null
        val upload = fields["upload"]?.takeIf { it >= 0 } ?: return null
        val download = fields["download"]?.takeIf { it >= 0 } ?: return null
        val total = fields["total"]?.takeIf { it > 0 } ?: INFINITE_TRAFFIC
        val expire = fields["expire"]?.takeIf { it in 1..8_640_000_000_000L } ?: INFINITE_EXPIRE_SECONDS
        return Usage(upload, download, total, expire)
    }
}

package com.hiddify.hiddify.nativecore

/**
 * Short-lived transport failures after a valid snapshot are recoverable by reopening
 * the stream. Show a warning after three consecutive failures; do not hide a real
 * first-load failure or a non-transport error.
 */
internal fun shouldReportOutboundsStreamError(
    hasSnapshot: Boolean,
    consecutiveFailures: Int,
    message: String,
): Boolean {
    if (!hasSnapshot || consecutiveFailures >= 3) return true
    val normalized = message.lowercase(java.util.Locale.ROOT)
    val interruptedGrpc = normalized.contains("grpc transport failure") &&
        normalized.contains("grpc-status=null")
    val normalStreamEnd = normalized.contains("proxy updates stream closed")
    return !interruptedGrpc && !normalStreamEnd
}

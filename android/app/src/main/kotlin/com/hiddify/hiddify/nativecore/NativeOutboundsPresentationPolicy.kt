package com.hiddify.hiddify.nativecore

/**
 * Keep the last successful server list through an interrupted gRPC stream and its
 * resubscription. Only a real server-list snapshot may replace/clear an old list.
 *
 * This pure reducer is shared by Android Compose and JVM regression tests.
 */
internal data class NativeOutboundsPresentationState(
    val group: NativeOutboundGroup? = null,
    val error: String? = null,
    val loading: Boolean = true,
)

internal object NativeOutboundsPresentationPolicy {
    fun initial(connected: Boolean) = NativeOutboundsPresentationState(loading = connected)

    /** A foreground transition or user retry does not mean the VPN disconnected. */
    fun resubscribing(previous: NativeOutboundsPresentationState) =
        previous.copy(error = null, loading = previous.group == null)

    fun received(previous: NativeOutboundsPresentationState, update: NativeOutboundUpdate): NativeOutboundsPresentationState =
        when {
            update.snapshot -> NativeOutboundsPresentationState(group = update.group, loading = false)
            update.error != null -> previous.copy(error = update.error, loading = false)
            else -> resubscribing(previous)
        }
}

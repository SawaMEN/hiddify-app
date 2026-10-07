package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.hiddify.utils.GrpcClientProvider

data class NativeSystemStats(
    val memoryBytes: Long = 0,
    val goroutines: Int = 0,
    val connectionsIn: Int = 0,
    val connectionsOut: Int = 0,
    val trafficAvailable: Boolean = false,
    val uplink: Long = 0,
    val downlink: Long = 0,
    val uplinkTotal: Long = 0,
    val downlinkTotal: Long = 0,
    val currentOutbound: String = "",
    val currentProfile: String = "",
)

class NativeStatsRepository {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    fun load(): NativeSystemStats {
        val response = client().GetSystemInfo().executeBlocking(Empty())
        return NativeSystemStats(
            memoryBytes = response.memory,
            goroutines = response.goroutines,
            connectionsIn = response.connections_in,
            connectionsOut = response.connections_out,
            trafficAvailable = response.traffic_available,
            uplink = response.uplink,
            downlink = response.downlink,
            uplinkTotal = response.uplink_total,
            downlinkTotal = response.downlink_total,
            currentOutbound = response.current_outbound,
            currentProfile = response.current_profile,
        )
    }
}

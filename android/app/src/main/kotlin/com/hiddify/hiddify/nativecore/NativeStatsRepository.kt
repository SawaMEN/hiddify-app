package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.hiddify.utils.GrpcClientProvider

class NativeStatsRepository {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    fun load(): NativeSystemStats {
        val call = client().GetSystemInfo()
        call.timeout.timeout(2, java.util.concurrent.TimeUnit.SECONDS)
        val response = try { call.executeBlocking(Empty()) } finally { call.cancel() }
        return NativeSystemStats(
            memoryBytes = response.memory,
            goroutines = response.goroutines,
            connectionsIn = response.connections_in,
            connectionsOut = response.connections_out,
            trafficAvailable = response.traffic_available,
            uplinkTotal = response.uplink_total,
            downlinkTotal = response.downlink_total,
            currentOutbound = response.current_outbound,
            currentProfile = response.current_profile,
        )
    }
}

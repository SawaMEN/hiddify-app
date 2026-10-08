package com.hiddify.hiddify.nativeconnection

import android.net.NetworkCapabilities
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.NetworkProbeRequest
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.bg.BoxService
import com.hiddify.hiddify.nativediagnostics.NativeProbePolicy
import com.hiddify.hiddify.utils.GrpcClientProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

class NativeHealthRepository {
    fun underlyingNetworkAvailable(): Boolean = try {
        Application.connectivity.allNetworks.any { network ->
            Application.connectivity.getNetworkCapabilities(network)?.let { caps ->
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }
    } catch (_: Exception) {
        false
    }

    /** All attempts use the selected outbound; one blocked check host cannot decide the result. */
    suspend fun probe(url: String, timeoutSeconds: Long): Boolean {
        if (!BoxService.isStarted()) return false
        val targets = NativeHealthEndpoints.urls(url)
        val budget = timeoutSeconds.coerceIn(12, 30)
        return withTimeoutOrNull(budget * 1000) {
            for (target in targets) {
                if (probeOne(target, (budget / targets.size).coerceAtLeast(4))) return@withTimeoutOrNull true
            }
            false
        } ?: false
    }

    private suspend fun probeOne(url: String, timeoutSeconds: Long): Boolean = try {
        val call = GrpcClientProvider.diagnosticsGrpcClient.create(CoreClient::class).ProbeConnection()
        call.timeout.timeout(timeoutSeconds, TimeUnit.SECONDS)
        val response = try { call.execute(NetworkProbeRequest(url = url)) } finally { call.cancel() }
        NativeProbePolicy.healthy(url, response.status_code)
    } catch (error: CancellationException) { throw error }
      catch (_: Exception) { false }
}

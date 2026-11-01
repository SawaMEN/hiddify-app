package com.hiddify.hiddify.nativediagnostics

import android.net.NetworkCapabilities
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.NetworkProbeRequest
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativeconnection.NativeHealthEndpoints
import com.hiddify.hiddify.bg.BoxService
import com.hiddify.hiddify.utils.GrpcClientProvider
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.runInterruptible
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NativeDiagnosticsRepository {
    companion object {
        // Platform DNS may ignore interruption. Keep its workers and caller wait bounded.
        private val dnsWorkers = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(8), { task ->
                Thread(task, "native-diagnostic-dns").apply { isDaemon = true }
            }, ThreadPoolExecutor.AbortPolicy())
    }
    fun protection(): NativeVpnProtection {
        val values = BoxService.vpnProtection()
        return NativeVpnProtection(values["alwaysOn"], values["lockdown"])
    }

    suspend fun run(raw: String?, headers: Set<String>, activeTag: String?,
        activeOutbound: NativeOutbound? = null, availableOutbounds: List<NativeOutbound> = emptyList(),
        onProgress: (NativeDiagnosticSnapshot) -> Unit): NativeDiagnosticSnapshot {
        val startedAt = Instant.now()
        val running = BoxService.isStarted()
        val testUrl = withContext(Dispatchers.IO) {
            com.hiddify.hiddify.nativecore.NativeGeneralOptionsRepository(Application.application).load().testUrl
        }
        val checks = mutableListOf<NativeDiagnosticCheck>()
        suspend fun add(check: NativeDiagnosticCheck) {
            currentCoroutineContext().ensureActive()
            checks += check
            onProgress(NativeDiagnosticSnapshot(startedAt, running, checks.toList()))
        }
        val networkAvailable = try { withContext(Dispatchers.IO) {
            Application.connectivity.allNetworks.any { network ->
                Application.connectivity.getNetworkCapabilities(network)?.let { caps ->
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }
        } } catch (error: CancellationException) { throw error } catch (_: Exception) { false }
        add(NativeDiagnosticCheck(
            NativeDiagnosticStage.NETWORK,
            if (networkAvailable) NativeDiagnosticOutcome.PASSED else NativeDiagnosticOutcome.FAILED,
            if (networkAvailable) NativeDiagnosticDetail.NETWORK_AVAILABLE else NativeDiagnosticDetail.NETWORK_UNAVAILABLE,
        ))
        val dnsAvailable = try {
            val host = URI(testUrl).host?.takeIf { it.isNotBlank() } ?: error("Invalid probe host")
            val network = com.hiddify.hiddify.bg.DefaultNetworkListener.underlyingNetwork()
                ?: error("No underlying network")
            val future = dnsWorkers.submit<Boolean> { network.getAllByName(host).isNotEmpty() }
            try { runInterruptible(Dispatchers.IO) { future.get(5, TimeUnit.SECONDS) } }
            finally { future.cancel(true); dnsWorkers.purge() }
        } catch (error: CancellationException) { throw error } catch (_: Exception) { false }
        add(NativeDiagnosticCheck(NativeDiagnosticStage.DNS,
            if (dnsAvailable) NativeDiagnosticOutcome.PASSED else NativeDiagnosticOutcome.FAILED,
            if (dnsAvailable) NativeDiagnosticDetail.DNS_AVAILABLE else NativeDiagnosticDetail.DNS_UNAVAILABLE))
        val server = try {
            // The running core resolves selector groups and subscription format details.
            // A stale or merely decorative outbound must never become a TCP target.
            val endpoint = NativeDiagnosticEndpoints.resolve(
                raw?.let(NativeDiagnosticEndpoints::parse).orEmpty(), activeTag,
                activeOutbound.takeIf { running },
                if (running) availableOutbounds else emptyList(),
            )
            when {
                endpoint?.udp == true || NativeDiagnosticEndpoints.chained(raw.orEmpty(), headers) ->
                    NativeDiagnosticCheck(NativeDiagnosticStage.SERVER, NativeDiagnosticOutcome.SKIPPED, NativeDiagnosticDetail.TRANSPORT_SKIPPED)
                endpoint == null || endpoint.host.isBlank() || endpoint.port !in 1..65535 ->
                    NativeDiagnosticCheck(NativeDiagnosticStage.SERVER, NativeDiagnosticOutcome.SKIPPED, NativeDiagnosticDetail.NO_ENDPOINT)
                else -> {
                    val socket = Socket()
                    try {
                        val network = com.hiddify.hiddify.bg.DefaultNetworkListener.underlyingNetwork()
                            ?: error("No underlying network")
                        network.bindSocket(socket)
                        val future = dnsWorkers.submit<Boolean> {
                            val addresses = network.getAllByName(endpoint.host)
                            check(addresses.isNotEmpty()) { "Server DNS returned no addresses" }
                            socket.connect(InetSocketAddress(addresses.first(), endpoint.port), 5_000)
                            true
                        }
                        try { runInterruptible(Dispatchers.IO) { future.get(5, TimeUnit.SECONDS) } }
                        finally { future.cancel(true); dnsWorkers.purge() }
                    } finally { socket.close() }
                    NativeDiagnosticCheck(NativeDiagnosticStage.SERVER, NativeDiagnosticOutcome.PASSED, NativeDiagnosticDetail.SERVER_AVAILABLE)
                }
            }
        } catch (error: CancellationException) { throw error } catch (_: Exception) {
            NativeDiagnosticCheck(NativeDiagnosticStage.SERVER, NativeDiagnosticOutcome.FAILED, NativeDiagnosticDetail.SERVER_UNAVAILABLE)
        }
        add(server)
        val tunnel = when {
            !running || !BoxService.isStarted() -> NativeDiagnosticCheck(NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.SKIPPED, NativeDiagnosticDetail.DISCONNECTED)
            !NativeProbePolicy.validUrl(testUrl) -> NativeDiagnosticCheck(NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.FAILED, NativeDiagnosticDetail.INVALID_PROBE_URL)
            else -> probe(testUrl)
        }
        add(tunnel)
        return NativeDiagnosticSnapshot(startedAt, running, checks.toList())
    }

    private fun client(): CoreClient =
        GrpcClientProvider.diagnosticsGrpcClient.create(CoreClient::class)

    /**
     * Use the same independent HTTPS test targets as the service-owned monitor.
     * Legacy/default HTTP test URLs can be useful for latency, but may be intercepted
     * or redirected by captive portals; they must not be the only VPN health check.
     * Total diagnostic budget is bounded even when several hosts are unreachable.
     */
    private suspend fun probe(url: String): NativeDiagnosticCheck {
        val targets = NativeHealthEndpoints.urls(url)
        val budget = 24L
        var lastStatus: Int? = null
        val succeeded = withTimeoutOrNull(budget * 1000L) {
            for (target in targets) {
                currentCoroutineContext().ensureActive()
                try {
                    val call = client().ProbeConnection()
                    call.timeout.timeout((budget / targets.size).coerceAtLeast(6L), TimeUnit.SECONDS)
                    val response = try {
                        call.execute(NetworkProbeRequest(url = target))
                    } finally {
                        call.cancel()
                    }
                    lastStatus = response.status_code
                    if (NativeProbePolicy.healthy(target, response.status_code)) return@withTimeoutOrNull true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Try the next independent host; no single endpoint proves
                    // that the selected VPN route has no Internet access.
                }
            }
            false
        } == true
        return NativeDiagnosticCheck(
            NativeDiagnosticStage.TUNNEL,
            if (succeeded) NativeDiagnosticOutcome.PASSED else NativeDiagnosticOutcome.FAILED,
            if (succeeded) NativeDiagnosticDetail.PROBE_OK else NativeDiagnosticDetail.PROBE_FAILED,
            lastStatus,
        )
    }
}

package com.hiddify.hiddify.nativediagnostics

import android.net.NetworkCapabilities
import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.NetworkProbeRequest
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.bg.BoxService
import com.hiddify.hiddify.utils.GrpcClientProvider
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class NativeDiagnosticsRepository {
    fun protection(): NativeVpnProtection {
        val values = BoxService.vpnProtection()
        return NativeVpnProtection(values["alwaysOn"], values["lockdown"])
    }

    suspend fun run(onProgress: (NativeDiagnosticSnapshot) -> Unit): NativeDiagnosticSnapshot {
        val startedAt = Instant.now()
        val running = BoxService.isStarted()
        val profilePath = Settings.activeConfigPath
        val testUrl = withContext(Dispatchers.IO) {
            com.hiddify.hiddify.nativecore.NativeGeneralOptionsRepository(Application.application).load().testUrl
        }
        val checks = mutableListOf<NativeDiagnosticCheck>()
        suspend fun add(check: NativeDiagnosticCheck) {
            currentCoroutineContext().ensureActive()
            checks += check
            onProgress(NativeDiagnosticSnapshot(startedAt, running, checks.toList()))
        }
        val networkAvailable = withContext(Dispatchers.IO) {
            Application.connectivity.allNetworks.any { network ->
                Application.connectivity.getNetworkCapabilities(network)?.let { caps ->
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }
        }
        add(NativeDiagnosticCheck(
            NativeDiagnosticStage.NETWORK,
            if (networkAvailable) NativeDiagnosticOutcome.PASSED else NativeDiagnosticOutcome.FAILED,
            if (networkAvailable) NativeDiagnosticDetail.NETWORK_AVAILABLE else NativeDiagnosticDetail.NETWORK_UNAVAILABLE,
        ))
        val profileAvailable = withContext(Dispatchers.IO) {
            profilePath.isNotBlank() && File(profilePath).let { it.isFile && it.length() > 0L }
        }
        add(NativeDiagnosticCheck(
            NativeDiagnosticStage.PROFILE,
            if (profileAvailable) NativeDiagnosticOutcome.PASSED else NativeDiagnosticOutcome.FAILED,
            if (profileAvailable) NativeDiagnosticDetail.PROFILE_AVAILABLE else NativeDiagnosticDetail.PROFILE_MISSING,
        ))
        if (!running || !BoxService.isStarted()) {
            add(NativeDiagnosticCheck(NativeDiagnosticStage.CORE, NativeDiagnosticOutcome.SKIPPED, NativeDiagnosticDetail.DISCONNECTED))
            add(NativeDiagnosticCheck(NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.SKIPPED, NativeDiagnosticDetail.DISCONNECTED))
        } else {
            val coreAvailable = try {
                val call = client().GetSystemInfo()
                call.timeout.timeout(5, TimeUnit.SECONDS)
                try { call.execute(Empty()); true } finally { call.cancel() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            add(NativeDiagnosticCheck(
                NativeDiagnosticStage.CORE,
                if (coreAvailable) NativeDiagnosticOutcome.PASSED else NativeDiagnosticOutcome.FAILED,
                if (coreAvailable) NativeDiagnosticDetail.CORE_AVAILABLE else NativeDiagnosticDetail.CORE_UNAVAILABLE,
            ))
            val tunnel = when {
                !BoxService.isStarted() -> NativeDiagnosticCheck(NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.SKIPPED, NativeDiagnosticDetail.DISCONNECTED)
                !coreAvailable -> NativeDiagnosticCheck(NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.SKIPPED, NativeDiagnosticDetail.CORE_UNAVAILABLE)
                !NativeProbePolicy.validUrl(testUrl) -> NativeDiagnosticCheck(NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.FAILED, NativeDiagnosticDetail.INVALID_PROBE_URL)
                else -> probe(testUrl)
            }
            add(tunnel)
        }
        return NativeDiagnosticSnapshot(startedAt, running, checks.toList())
    }

    private fun client(): CoreClient =
        GrpcClientProvider.diagnosticsGrpcClient.create(CoreClient::class)

    private suspend fun probe(url: String): NativeDiagnosticCheck = try {
        val call = client().ProbeConnection()
        call.timeout.timeout(32, TimeUnit.SECONDS)
        val response = try { call.execute(NetworkProbeRequest(url = url)) } finally { call.cancel() }
        val healthy = NativeProbePolicy.healthy(url, response.status_code)
        NativeDiagnosticCheck(
            NativeDiagnosticStage.TUNNEL,
            if (healthy) NativeDiagnosticOutcome.PASSED else NativeDiagnosticOutcome.FAILED,
            if (healthy) NativeDiagnosticDetail.PROBE_OK else NativeDiagnosticDetail.PROBE_FAILED,
            response.status_code,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        NativeDiagnosticCheck(NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.FAILED, NativeDiagnosticDetail.PROBE_FAILED)
    }
}

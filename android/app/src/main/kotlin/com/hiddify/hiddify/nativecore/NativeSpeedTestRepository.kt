package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.NetworkProbeRequest
import com.hiddify.hiddify.bg.BoxService
import com.hiddify.hiddify.utils.GrpcClientProvider
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object NativeSpeedTestRepository {
    private val active = AtomicBoolean(false)
    val running: Boolean get() = active.get()
    suspend fun download(): NativeSpeedSample {
        check(BoxService.isStarted()) { "VPN is stopped" }
        check(active.compareAndSet(false, true)) { "Speed test already running" }
        try {
            val call = GrpcClientProvider.diagnosticsGrpcClient.create(CoreClient::class).ProbeConnection()
            call.timeout.timeout(30, TimeUnit.SECONDS)
            val response = try {
                call.execute(NetworkProbeRequest(
                    url = "https://speed.cloudflare.com/__down?bytes=${NativeSpeedSample.MAX_DOWNLOAD}",
                    download_bytes = NativeSpeedSample.MAX_DOWNLOAD))
            } finally { call.cancel() }
            check(response.status_code == 200) { "Download test failed" }
            return NativeSpeedSample(response.download_bytes, response.transfer_nanos).validated()
        } finally { active.set(false) }
    }
}

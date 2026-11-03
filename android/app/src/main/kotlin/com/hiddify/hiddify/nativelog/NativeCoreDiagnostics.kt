package com.hiddify.hiddify.nativelog

import android.content.Context
import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.ExportDiagnosticsRequest
import com.hiddify.hiddify.utils.GrpcClientProvider
import java.io.File
import java.util.concurrent.TimeUnit
import okio.ByteString.Companion.toByteString

/** Use the core's own scrubber and current crash inventory, including native panics/OOM. */
internal object NativeCoreDiagnostics {
    suspend fun newestCrash(): String? {
        val call = GrpcClientProvider.grpcClient.create(CoreClient::class).ListCrashReports()
        call.timeout.timeout(3, TimeUnit.SECONDS)
        return try {
            call.execute(Empty()).reports.lastOrNull { it.is_crash }?.let { "${it.source}/${it.name}/${it.crashed_at}" }
        } finally { call.cancel() }
    }

    suspend fun export(context: Context): File {
        val appLogs = NativeLogRepository(context).readRecent().lines.joinToString("\n")
            .takeLast(256 * 1024).toByteArray()
        val extra = if (appLogs.isNotEmpty()) mapOf("app-logs.txt" to appLogs.toByteString()) else emptyMap()
        val call = GrpcClientProvider.grpcClient.create(CoreClient::class).ExportDiagnostics()
        call.timeout.timeout(20, TimeUnit.SECONDS)
        val response = try { call.execute(ExportDiagnosticsRequest(extra_files = extra, include_content = true)) }
            finally { call.cancel() }
        require(response.content.size in 4..8 * 1024 * 1024) { "Invalid diagnostic archive size" }
        val bytes = response.content.toByteArray()
        require(bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) { "Invalid diagnostic archive" }
        val directory = File(context.cacheDir, "logs").apply { check(isDirectory || mkdirs()) }
        val file = File.createTempFile("diagnostics-", ".zip", directory)
        try { file.writeBytes(bytes) } catch (error: Exception) { file.delete(); throw error }
        return file
    }
}

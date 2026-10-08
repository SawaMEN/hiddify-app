package com.hiddify.hiddify.nativediagnostics

import java.net.URI
import java.time.Instant

enum class NativeDiagnosticStage { NETWORK, DNS, SERVER, PROFILE, CORE, TUNNEL }
enum class NativeDiagnosticOutcome { PASSED, FAILED, SKIPPED }
enum class NativeDiagnosticDetail {
    NETWORK_AVAILABLE, NETWORK_UNAVAILABLE, PROFILE_AVAILABLE, PROFILE_MISSING,
    DNS_AVAILABLE, DNS_UNAVAILABLE, SERVER_AVAILABLE, SERVER_UNAVAILABLE, NO_ENDPOINT, TRANSPORT_SKIPPED,
    CORE_AVAILABLE, CORE_UNAVAILABLE, DISCONNECTED, PROBE_OK, PROBE_FAILED, INVALID_PROBE_URL,
}

data class NativeDiagnosticCheck(
    val stage: NativeDiagnosticStage,
    val outcome: NativeDiagnosticOutcome,
    val detail: NativeDiagnosticDetail,
    val httpStatus: Int? = null,
)

data class NativeVpnProtection(val alwaysOn: Boolean? = null, val lockdown: Boolean? = null)

data class NativeDiagnosticSnapshot(
    val startedAt: Instant = Instant.now(),
    val vpnStarted: Boolean = false,
    val checks: List<NativeDiagnosticCheck> = emptyList(),
)

object NativeProbePolicy {
    fun validUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null &&
            (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)

    fun healthy(url: String, status: Int): Boolean {
        if (!validUrl(url)) return false
        val path = URI(url).path.orEmpty()
        val expectsNoContent = path.contains("generate_204") || path.contains("generate204")
        return status == 204 || (status == 200 && !expectsNoContent)
    }
}

/** A report contains only fixed codes and numeric/boolean fields, never configuration or logs. */
object NativeDiagnosticReport {
    fun create(version: String, snapshot: NativeDiagnosticSnapshot, protection: NativeVpnProtection): String {
        val safeVersion = version.takeIf { it.matches(Regex("[0-9A-Za-z.+_-]{1,64}")) } ?: "unknown"
        val checks = snapshot.checks.joinToString(",\n") { check ->
            "    {\"stage\":\"${check.stage.name.lowercase()}\",\"result\":\"${check.outcome.name.lowercase()}\"," +
                "\"detail\":\"${check.detail.name.lowercase()}\",\"httpStatus\":${check.httpStatus?.takeIf { it in 100..599 }}}"
        }
        return """
            {
              "version": "$safeVersion",
              "platform": "android",
              "time": "${snapshot.startedAt}",
              "vpnStarted": ${snapshot.vpnStarted},
              "alwaysOn": ${protection.alwaysOn},
              "lockdown": ${protection.lockdown},
              "checks": [
            $checks
              ]
            }
        """.trimIndent()
    }
}

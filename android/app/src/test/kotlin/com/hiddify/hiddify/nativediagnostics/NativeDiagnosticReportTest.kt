package com.hiddify.hiddify.nativediagnostics

import com.google.gson.JsonParser
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NativeDiagnosticReportTest {
    @Test fun exportsValidJsonWithOnlyAllowedFields() {
        val snapshot = NativeDiagnosticSnapshot(
            startedAt = Instant.parse("2026-10-07T09:00:00Z"),
            vpnStarted = true,
            checks = listOf(NativeDiagnosticCheck(
                NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.PASSED,
                NativeDiagnosticDetail.PROBE_OK, 204,
            )),
        )
        val report = JsonParser.parseString(NativeDiagnosticReport.create("4.0.0", snapshot, NativeVpnProtection(true, false))).asJsonObject
        assertEquals(setOf("version", "platform", "time", "vpnStarted", "alwaysOn", "lockdown", "checks"), report.keySet())
        assertEquals("4.0.0", report["version"].asString)
        assertTrue(report["vpnStarted"].asBoolean)
        assertFalse(report["lockdown"].asBoolean)
        val check = report["checks"].asJsonArray[0].asJsonObject
        assertEquals(setOf("stage", "result", "detail", "httpStatus"), check.keySet())
        assertEquals(204, check["httpStatus"].asInt)
    }

    @Test fun keepsUnknownProtectionDistinctFromDisabled() {
        val report = JsonParser.parseString(NativeDiagnosticReport.create("1.0.0", NativeDiagnosticSnapshot(), NativeVpnProtection())).asJsonObject
        assertTrue(report["alwaysOn"].isJsonNull)
        assertTrue(report["lockdown"].isJsonNull)
        assertEquals(0, report["checks"].asJsonArray.size())
    }

    @Test fun refusesToIncludeArbitraryVersionText() {
        val report = NativeDiagnosticReport.create("https://private.example/sub?token=secret", NativeDiagnosticSnapshot(), NativeVpnProtection())
        assertFalse(report.contains("private.example"))
        assertFalse(report.contains("secret"))
        assertEquals("unknown", JsonParser.parseString(report).asJsonObject["version"].asString)
    }

    @Test fun omitsOutOfRangeHttpCodes() {
        val snapshot = NativeDiagnosticSnapshot(checks = listOf(NativeDiagnosticCheck(
            NativeDiagnosticStage.TUNNEL, NativeDiagnosticOutcome.FAILED,
            NativeDiagnosticDetail.PROBE_FAILED, -1,
        )))
        val report = JsonParser.parseString(NativeDiagnosticReport.create("1.0.0", snapshot, NativeVpnProtection())).asJsonObject
        assertTrue(report["checks"].asJsonArray[0].asJsonObject["httpStatus"].isJsonNull)
    }
}

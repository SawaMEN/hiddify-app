package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfileStartFormatTest {
    @Test fun rawShareLinksAndBase64SubscriptionsNeedCoreNormalization() {
        for (content in listOf(
            "naive://user:password@server.example:443",
            "trojan://password@server.example:443",
            "vless://id@server.example:443",
            "dmxlc3M6Ly9leGFtcGxl", // base64-encoded share-link subscription
            "# profile-title: subscription\nnaive://user:secret@server.example:443",
            "proxies:\n  - name: Proxy\n    type: trojan",
        )) assertTrue(content, NativeProfileStartFormat.needsNormalization(content))
    }

    @Test fun NativeJsonConfigsKeepTheirOriginalContent() {
        for (content in listOf(
            """{"outbounds":[{"type":"direct","tag":"direct"}]}""",
            """[{"type":"direct","tag":"direct"}]""",
            "  \n\t{ \"outbounds\": [] } ",
            "\uFEFF { \"outbounds\": [] }",
        )) assertFalse(content, NativeProfileStartFormat.needsNormalization(content))
    }

    @Test fun malformedOrEmptyTextNeverMasqueradesAsValidJson() {
        assertTrue(NativeProfileStartFormat.needsNormalization(""))
        assertTrue(NativeProfileStartFormat.needsNormalization("nan"))
        assertTrue(NativeProfileStartFormat.needsNormalization("invalid"))
    }
}

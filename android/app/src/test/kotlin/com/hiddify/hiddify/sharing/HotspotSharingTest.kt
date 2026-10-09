package com.hiddify.hiddify.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HotspotSharingTest {
    @Test fun selectsWirelessAddressDuringCellularHandover() {
        val addresses = linkedMapOf(
            "rmnet_data0/10.1.2.3" to ("rmnet_data0" to "10.1.2.3"),
            "swlan0/192.168.43.1" to ("swlan0" to "192.168.43.1"),
        )
        assertEquals("swlan0" to "192.168.43.1", HotspotInterfaceSelection.find(emptySet(), addresses))
        assertNull(HotspotInterfaceSelection.find(addresses.keys, addresses))
        assertNull(HotspotInterfaceSelection.find(emptySet(), addresses +
            ("ap0/192.168.42.1" to ("ap0" to "192.168.42.1"))))
    }

    @Test fun wifiQrEscapesCredentialDelimiters() {
        assertEquals("WIFI:T:WPA;S:Test;P:password;;", HotspotWifiQr.encode("Test", "password"))
        assertEquals("WIFI:T:WPA;S:A\\;B;P:a\\:b\\,c;;", HotspotWifiQr.encode("A;B", "a:b,c"))
    }

    @Test fun shellRulesRollbackPartialFailureAndRestoreForwarding() {
        assumeTrue(File("/bin/sh").canExecute())
        val chain = HotspotRootRules.chain(-1)
        val script = HotspotRootRules.install("ap0", 2022, chain)
        for (value in listOf("ap0;id", "ap 0", "$(id)", "", "a".repeat(16))) {
            check(runCatching { HotspotRootRules.install(value, 2022, chain) }.isFailure)
        }
        check(runCatching { HotspotRootRules.install("ap0", 0, chain) }.isFailure)
        check(runCatching { HotspotRootRules.remove("FORWARD", "0") }.isFailure)
        check(runCatching { HotspotRootRules.remove(chain, "0;id") }.isFailure)
        check("-i ap0 -o hc2022 -j ACCEPT" in script && "-i hc2022 -o ap0 -j ACCEPT" in script)
        check("-i ap0 -j REJECT" in script && "-o ap0 -j REJECT" in script)
        check(script.indexOf("-I FORWARD") < script.indexOf("echo 1"))

        val dir = Files.createTempDirectory("hotspot-rules").toFile()
        try {
            val state = File(dir, "forwarding").apply { writeText("0") }
            val state6 = File(dir, "forwarding6").apply { writeText("0") }
            val log = File(dir, "log")
            for (tool in listOf("iptables", "ip6tables")) {
                File(dir, tool).apply {
                    writeText("""#!/bin/sh
printf '%s\n' "${'$'}*" >> "${'$'}HOTSPOT_LOG"
case " ${'$'}* " in *" -C "*|*" -L "*) exit 1 ;; esac
if [ "${'$'}FAIL_TOOL" = "$tool" ]; then exit 1; fi
exit 0
""")
                    setExecutable(true)
                }
            }
            fun run(script: String, fail: String = ""): Int {
                val process = ProcessBuilder("/bin/sh", "-c", script.replace("/proc/sys/net/ipv4/ip_forward", state.path)
                    .replace("/proc/sys/net/ipv6/conf/all/forwarding", state6.path))
                process.environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
                process.environment()["HOTSPOT_LOG"] = log.path
                process.environment()["FAIL_TOOL"] = fail
                return process.start().waitFor()
            }
            check(run(script, "ip6tables") != 0)
            check(state.readText().trim() == "0") { "Partial firewall failure must not enable forwarding" }
            check(state6.readText().trim() == "0")
            check(run(script) == 0)
            check(state.readText().trim() == "1")
            check(state6.readText().trim() == "1")
            check(run(HotspotRootRules.remove(chain, "0")) == 0)
            check(state.readText().trim() == "0") { "Restore the original forwarding state" }
            check(state6.readText().trim() == "0")
            check(run(HotspotRootRules.remove(chain, "0")) == 0) { "Cleanup is repeatable" }
            check(log.readLines().none { it.matches(Regex(".*-F(?! VTR_AP_).*")) }) { "Never flush system chains" }
        } finally { dir.deleteRecursively() }
    }
}

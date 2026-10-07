package com.hiddify.hiddify.sharing

import java.io.File
import java.nio.file.Files

// kotlinc android/app/src/main/kotlin/com/hiddify/hiddify/sharing/HotspotRootRules.kt \
//   android/app/src/main/kotlin/com/hiddify/hiddify/sharing/HotspotInterfaceSelection.kt \
//   tool/check_hotspot_rules.kt -include-runtime -d /tmp/hotspot-tests.jar
// java -jar /tmp/hotspot-tests.jar
fun main() {
    check(HotspotWifiQr.encode("Test", "password") == "WIFI:T:WPA;S:Test;P:password;;")
    check(HotspotWifiQr.encode("A;B", "a:b,c") == "WIFI:T:WPA;S:A\\;B;P:a\\:b\\,c;;")
    val existing = setOf("wlan0/192.168.1.100")
    val appeared = linkedMapOf(
        "rmnet_data0/10.10.0.2" to ("rmnet_data0" to "10.10.0.2"),
        "wlan0/192.168.1.100" to ("wlan0" to "192.168.1.100"),
        "swlan0/192.168.43.1" to ("swlan0" to "192.168.43.1"),
    )
    check(HotspotInterfaceSelection.find(existing, appeared) == ("swlan0" to "192.168.43.1"))
    check(HotspotInterfaceSelection.find(existing, appeared.filterKeys { !it.startsWith("swlan") }) == null)
    check(HotspotInterfaceSelection.find(emptySet(), appeared) == null) { "Ambiguous Wi-Fi addresses must not be guessed" }
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
    println("Hotspot rule validation, scoped forwarding, partial failure and rollback checks passed.")
}

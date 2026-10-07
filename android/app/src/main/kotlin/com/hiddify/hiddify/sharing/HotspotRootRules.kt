package com.hiddify.hiddify.sharing

/** Rules are restricted to this app's AP interface and its root TUN. Never flush a system table. */
object HotspotRootRules {
    fun validateInterface(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_.-]{1,15}"))) { "Invalid hotspot interface" }
        return value
    }

    fun chain(packageHash: Int) = "VTR_AP_${packageHash.toUInt().toString(16)}"

    fun install(ap: String, table: Int, chain: String): String {
        validateInterface(ap)
        require(table in 1..65535)
        require(chain.matches(Regex("VTR_AP_[a-f0-9]{1,8}")))
        val tun = "hc$table"
        // Kernel-forwarded traffic is allowed only between the AP and our TUN.
        // REDIRECT/TPROXY flows delivered to core sockets use INPUT instead.
        return """
            set -e
            for tool in iptables ip6tables; do
              ${'$'}tool -w 5 -N $chain
              ${'$'}tool -w 5 -A $chain -i $ap -o $tun -j ACCEPT
              ${'$'}tool -w 5 -A $chain -i $tun -o $ap -j ACCEPT
              ${'$'}tool -w 5 -A $chain -i $ap -j REJECT
              ${'$'}tool -w 5 -A $chain -o $ap -j REJECT
              ${'$'}tool -w 5 -I FORWARD 1 -j $chain
            done
            echo 1 > /proc/sys/net/ipv4/ip_forward
        """.trimIndent()
    }

    fun remove(chain: String, previousForwarding: String): String {
        require(chain.matches(Regex("VTR_AP_[a-f0-9]{1,8}")))
        require(previousForwarding in listOf("0", "1"))
        return """
            for tool in iptables ip6tables; do
              while ${'$'}tool -w 5 -C FORWARD -j $chain 2>/dev/null; do
                ${'$'}tool -w 5 -D FORWARD -j $chain || exit 1
              done
              if ${'$'}tool -w 5 -L $chain >/dev/null 2>&1; then
                ${'$'}tool -w 5 -F $chain || exit 1
                ${'$'}tool -w 5 -X $chain || exit 1
              fi
            done
            if [ "${'$'}(cat /proc/sys/net/ipv4/ip_forward)" = 1 ]; then
              echo $previousForwarding > /proc/sys/net/ipv4/ip_forward
            fi
        """.trimIndent()
    }
}

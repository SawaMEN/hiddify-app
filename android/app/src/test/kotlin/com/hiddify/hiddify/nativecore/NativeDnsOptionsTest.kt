package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeDnsOptionsTest {
    @Test fun scopedEditPreservesLegacyAndUnrelatedResolverValues() {
        val imported = NativeDnsOptions("  quic://resolver.example:853  ", "prefer_ipv6", true, "", "ipv6_only")
        assertEquals(imported.copy(remoteStrategy = "ipv4_only"),
            NativeDnsOptionField.REMOTE_STRATEGY.applyTo(imported, "ipv4_only"))
        assertEquals(imported.copy(fakeDns = false), NativeDnsOptionField.FAKE_DNS.applyTo(imported, "false"))
        assertEquals(imported.copy(directAddress = "local"), NativeDnsOptionField.DIRECT_ADDRESS.applyTo(imported, "local"))
    }

    @Test fun resolverSchemesAndBareAddressesRemainSupported() {
        val defaults = NativeDnsOptions()
        listOf("local", "8.8.8.8", "2001:4860:4860::8888", "tls://dns.example:853", "quic://dns.example",
            "h3://dns.example/dns-query", "custom+resolver://dns.example/query").forEach { resolver ->
            assertEquals(resolver, NativeDnsOptionField.REMOTE_ADDRESS.applyTo(defaults, "  $resolver  ").remoteAddress)
            assertEquals(resolver, NativeDnsOptionField.DIRECT_ADDRESS.applyTo(defaults, resolver).directAddress)
        }
    }

    @Test fun invalidFieldDoesNotProduceAnUpdate() {
        val defaults = NativeDnsOptions()
        listOf("", "  ", "udp://1.1.\n1.1", "a".repeat(2049)).forEach { address ->
            assertThrows(IllegalArgumentException::class.java) { NativeDnsOptionField.REMOTE_ADDRESS.applyTo(defaults, address) }
            assertThrows(IllegalArgumentException::class.java) { NativeDnsOptionField.DIRECT_ADDRESS.applyTo(defaults, address) }
        }
        assertThrows(IllegalArgumentException::class.java) { NativeDnsOptionField.FAKE_DNS.applyTo(defaults, "yes") }
        assertThrows(IllegalArgumentException::class.java) { NativeDnsOptionField.REMOTE_STRATEGY.applyTo(defaults, "ipv7_only") }
        assertThrows(IllegalArgumentException::class.java) { NativeDnsOptionField.DIRECT_STRATEGY.applyTo(defaults, "auto") }
    }

    @Test fun resetRestoresOnlyItsFieldIncludingEmptyAutoStrategy() {
        val custom = NativeDnsOptions("local", "ipv4_only", true, "tls://dns.example", "prefer_ipv6")
        val defaults = NativeDnsOptions()
        NativeDnsOptionField.entries.forEach { field ->
            val reset = field.applyTo(custom, field.value(defaults).toString())
            assertEquals(field.value(defaults), field.value(reset))
            NativeDnsOptionField.entries.filter { it != field }.forEach { untouched ->
                assertEquals(untouched.value(custom), untouched.value(reset))
            }
        }
    }
}

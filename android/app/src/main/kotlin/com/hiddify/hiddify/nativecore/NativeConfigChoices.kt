package com.hiddify.hiddify.nativecore

/** Wire values shared by scoped Kotlin editors and the Dart configuration format. */
object NativeConfigChoices {
    val balancerChoices = listOf("round-robin", "consistent-hashing", "sticky-sessions")
    val domainStrategyChoices = listOf("", "prefer_ipv4", "prefer_ipv6", "ipv4_only", "ipv6_only")
    val tunChoices = listOf("mixed", "system", "gvisor")
}

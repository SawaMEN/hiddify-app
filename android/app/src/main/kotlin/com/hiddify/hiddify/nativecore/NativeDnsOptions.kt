package com.hiddify.hiddify.nativecore

data class NativeDnsOptions(
    val remoteAddress: String = "tcp://8.8.8.8",
    val remoteStrategy: String = "",
    val fakeDns: Boolean = false,
    val directAddress: String = "udp://1.1.1.1",
    val directStrategy: String = "",
) {
    fun validated(): NativeDnsOptions {
        fun address(value: String): String = value.trim().also {
            require(it.isNotEmpty() && it.length <= 2048 && it.none(Char::isISOControl)) { "Invalid DNS address" }
        }
        require(remoteStrategy in NativeConfigChoices.domainStrategyChoices &&
            directStrategy in NativeConfigChoices.domainStrategyChoices) { "Invalid DNS strategy" }
        // Match Dart's nonempty-address validator; do not restrict core-supported resolver schemes.
        return copy(remoteAddress = address(remoteAddress), directAddress = address(directAddress))
    }
}


enum class NativeDnsOptionField(val storageKey: String) {
    REMOTE_ADDRESS("remote-dns-address"), REMOTE_STRATEGY("remote-dns-domain-strategy"),
    FAKE_DNS("enable-fake-dns"), DIRECT_ADDRESS("direct-dns-address"),
    DIRECT_STRATEGY("direct-dns-domain-strategy");

    fun value(options: NativeDnsOptions): Any = when (this) {
        REMOTE_ADDRESS -> options.remoteAddress
        REMOTE_STRATEGY -> options.remoteStrategy
        FAKE_DNS -> options.fakeDns
        DIRECT_ADDRESS -> options.directAddress
        DIRECT_STRATEGY -> options.directStrategy
    }

    fun applyTo(options: NativeDnsOptions, input: String): NativeDnsOptions {
        fun address() = input.trim().also {
            require(it.isNotEmpty() && it.length <= 2048 && it.none(Char::isISOControl)) { "Invalid DNS address" }
        }
        fun strategy() = input.also {
            require(it in NativeConfigChoices.domainStrategyChoices) { "Invalid DNS strategy" }
        }
        // Validate the edited field alone; preserve other imported resolver values verbatim.
        return when (this) {
            REMOTE_ADDRESS -> options.copy(remoteAddress = address())
            REMOTE_STRATEGY -> options.copy(remoteStrategy = strategy())
            FAKE_DNS -> options.copy(fakeDns = input.toBooleanStrict())
            DIRECT_ADDRESS -> options.copy(directAddress = address())
            DIRECT_STRATEGY -> options.copy(directStrategy = strategy())
        }
    }
}

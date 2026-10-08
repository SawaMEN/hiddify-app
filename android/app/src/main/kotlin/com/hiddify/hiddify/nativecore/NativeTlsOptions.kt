package com.hiddify.hiddify.nativecore

data class NativeTlsOptions(
    val fragment: Boolean = false,
    val fragmentSize: String = "10-30",
    val fragmentSleep: String = "2-8",
    val mixedSniCase: Boolean = false,
    val padding: Boolean = false,
    val paddingSize: String = "1-1500",
) {
    fun validated(): NativeTlsOptions = copy(
        fragmentSize = normalizeRange(fragmentSize, allowEmpty = true),
        fragmentSleep = normalizeRange(fragmentSleep, allowEmpty = true),
        paddingSize = normalizeRange(paddingSize, allowEmpty = true),
    )

    companion object {
        /** Dart OptionalRange: a nonnegative integer or an ordered pair; stored empty ranges are valid. */
        fun normalizeRange(input: String, allowEmpty: Boolean = false): String {
            val text = input.trim()
            if (allowEmpty && text.isEmpty()) return ""
            require(text.length <= 21 && Regex("[0-9]+(?:-[0-9]+)?").matches(text)) { "Invalid TLS range" }
            val parts = text.split('-').map { requireNotNull(it.toIntOrNull()) { "TLS range is too large" } }
            require(parts.size == 1 || parts[0] <= parts[1]) { "Reversed TLS range" }
            return parts.joinToString("-")
        }
    }
}

enum class NativeTlsOptionField(val coreKey: String, val legacyKey: String) {
    FRAGMENT("enable-fragment", "enable-tls-fragment"),
    FRAGMENT_SIZE("fragment-size", "tls-fragment-size"),
    FRAGMENT_SLEEP("fragment-sleep", "tls-fragment-sleep"),
    MIXED_SNI_CASE("mixed-sni-case", "enable-tls-mixed-sni-case"),
    PADDING("enable-padding", "enable-tls-padding"),
    PADDING_SIZE("padding-size", "tls-padding-size");

    fun value(options: NativeTlsOptions): Any = when (this) {
        FRAGMENT -> options.fragment
        FRAGMENT_SIZE -> options.fragmentSize
        FRAGMENT_SLEEP -> options.fragmentSleep
        MIXED_SNI_CASE -> options.mixedSniCase
        PADDING -> options.padding
        PADDING_SIZE -> options.paddingSize
    }

    fun applyTo(options: NativeTlsOptions, input: String): NativeTlsOptions = when (this) {
        FRAGMENT -> options.copy(fragment = input.toBooleanStrict())
        FRAGMENT_SIZE -> options.copy(fragmentSize = NativeTlsOptions.normalizeRange(input))
        FRAGMENT_SLEEP -> options.copy(fragmentSleep = NativeTlsOptions.normalizeRange(input))
        MIXED_SNI_CASE -> options.copy(mixedSniCase = input.toBooleanStrict())
        PADDING -> options.copy(padding = input.toBooleanStrict())
        PADDING_SIZE -> options.copy(paddingSize = NativeTlsOptions.normalizeRange(input))
    }
}

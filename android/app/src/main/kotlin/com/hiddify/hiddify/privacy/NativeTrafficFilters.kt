package com.hiddify.hiddify.privacy

/** Shared preference keys are also consumed by the core and the compatibility UI. */
enum class NativeTrafficFilter(val key: String) {
    QUIC("privacy-anonymization-block-quic"),
    STUN("privacy-anonymization-block-stun"),
    PLAIN_HTTP("privacy-anonymization-block-plain-http"),
    LAN("privacy-anonymization-isolate-lan"),
}

data class NativeTrafficFilters(val enabled: Set<NativeTrafficFilter> = emptySet()) {
    fun withFilter(filter: NativeTrafficFilter, value: Boolean): NativeTrafficFilters =
        copy(enabled = if (value) enabled + filter else enabled - filter)

    fun preferenceValues(): Map<String, Boolean> =
        NativeTrafficFilter.entries.associate { "flutter.${it.key}" to (it in enabled) }

    companion object {
        fun fromPreferences(values: Map<String, *>): NativeTrafficFilters =
            NativeTrafficFilters(NativeTrafficFilter.entries.filter {
                values["flutter.${it.key}"] == true
            }.toSet())

        fun all(value: Boolean): NativeTrafficFilters =
            NativeTrafficFilters(if (value) NativeTrafficFilter.entries.toSet() else emptySet())
    }
}

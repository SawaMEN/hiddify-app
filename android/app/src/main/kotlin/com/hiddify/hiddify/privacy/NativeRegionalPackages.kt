package com.hiddify.hiddify.privacy

enum class NativeRegionalAppKind { DIRECT, PROXY }

data class NativePackageSelection(val manual: Boolean, val values: List<String>)
data class NativeRegionalPackageValues(val direct: String, val proxy: String)

object NativeRegionalPackages {
    const val MANUAL_PREFIX = "manual:"

    fun selection(stored: String, automatic: List<String>): NativePackageSelection =
        if (stored.startsWith(MANUAL_PREFIX)) {
            NativePackageSelection(true, NativeRoutingTokens.parse(stored.removePrefix(MANUAL_PREFIX), false))
        } else {
            NativePackageSelection(false, NativeRoutingTokens.merge(automatic, stored, false))
        }

    fun encodeManual(packages: Set<String>): String {
        val values = NativeRoutingTokens.parse(packages.sorted().joinToString(","), false)
        require(values.toSet() == packages) { "Invalid application packages" }
        return MANUAL_PREFIX + values.sorted().joinToString(",")
    }

    /** Remove overlaps only from the opposite manual list; retain automatic suggestions. */
    fun replace(current: NativeRegionalPackageValues, kind: NativeRegionalAppKind,
        selected: Set<String>): NativeRegionalPackageValues {
        val encoded = encodeManual(selected)
        fun cleaned(other: String): String =
            if (other.startsWith(MANUAL_PREFIX)) encodeManual(selection(other, emptyList()).values.toSet() - selected)
            else other
        return when (kind) {
            NativeRegionalAppKind.DIRECT -> NativeRegionalPackageValues(encoded, cleaned(current.proxy))
            NativeRegionalAppKind.PROXY -> NativeRegionalPackageValues(cleaned(current.direct), encoded)
        }
    }

    fun reset(current: NativeRegionalPackageValues, kind: NativeRegionalAppKind): NativeRegionalPackageValues =
        when (kind) {
            NativeRegionalAppKind.DIRECT -> current.copy(direct = "")
            NativeRegionalAppKind.PROXY -> current.copy(proxy = "")
        }
}

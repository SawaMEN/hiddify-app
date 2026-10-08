package com.hiddify.hiddify.nativecore

/** Bounds must be checked before narrowing legacy Longs and JSON numbers to Int. */
internal object NativeStoredNumbers {
    fun int(value: Any?, range: IntRange): Int? {
        if (value !is Number && value !is String) return null
        val number = runCatching { java.math.BigDecimal(value.toString()).intValueExact() }.getOrNull() ?: return null
        return number.takeIf { it in range }
    }
}

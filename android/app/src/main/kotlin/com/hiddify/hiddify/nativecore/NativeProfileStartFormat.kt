package com.hiddify.hiddify.nativecore

/**
 * The released Android core still reads a profile as JSON before attempting
 * its general subscription parser. Detect formats which must first be converted
 * using the core's own Parse RPC. Never reinterpret or rewrite valid JSON files.
 */
internal object NativeProfileStartFormat {
    fun needsNormalization(content: String): Boolean {
        val first = content.firstOrNull { !it.isWhitespace() && it != '\uFEFF' }
        return first != '{' && first != '['
    }
}

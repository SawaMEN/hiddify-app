package com.hiddify.hiddify.nativecore

/** A running core may still be waiting for its first ping or have no reachable proxies. */
internal object NativeHotReloadPolicy {
    fun applied(messageEmpty: Boolean, coreState: Int): Boolean =
        messageEmpty && coreState in setOf(2, 5, 6) // STARTED, WAITING, NO_AVAILABLE_PROXIES
}

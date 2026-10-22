package com.hiddify.hiddify.nativecore

/**
 * A successful HotReload response must confirm that the service is still started.
 * When an incompatible TUN or other option change is rejected, the Go core leaves
 * the old configuration running; the UI can then use its existing full-reconnect path.
 */
internal object NativeHotReloadPolicy {
    fun applied(messageEmpty: Boolean, coreStarted: Boolean): Boolean = messageEmpty && coreStarted
}

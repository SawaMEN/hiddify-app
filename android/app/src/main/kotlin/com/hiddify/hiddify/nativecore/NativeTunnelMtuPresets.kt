package com.hiddify.hiddify.nativecore

/** Manual comparison choices; never silently replace an imported profile's MTU. */
internal object NativeTunnelMtuPresets {
    val values = listOf(1280, 1380, 1400, 1500, 9000)
}

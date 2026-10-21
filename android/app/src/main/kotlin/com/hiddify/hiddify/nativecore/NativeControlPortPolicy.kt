package com.hiddify.hiddify.nativecore

/** The legacy 17079 control port is shared with other Hiddify installations. */
internal object NativeControlPortPolicy {
    private const val LEGACY_PORT = 17079

    fun select(
        current: Int,
        frontPort: Int,
        isAvailable: (Int) -> Boolean,
        allocateEphemeral: () -> Int,
    ): Int {
        if (current in 1024..65535 && current != LEGACY_PORT &&
            current != frontPort && isAvailable(current)) return current

        repeat(16) {
            val next = allocateEphemeral()
            if (next in 1024..65535 && next != LEGACY_PORT &&
                next != frontPort && isAvailable(next)) return next
        }
        error("Unable to allocate a free local gRPC control port")
    }
}

package com.hiddify.hiddify.nativecore

/** The legacy 17079 control port is shared with other Hiddify installations. */
internal object NativeControlPortPolicy {
    private const val LEGACY_PORT = 17079

    fun select(
        current: Int,
        frontPort: Int,
        isAvailable: (Int) -> Boolean,
        allocateEphemeral: () -> Int,
        excludedPorts: Set<Int> = emptySet(),
    ): Int {
        fun acceptable(port: Int): Boolean =
            port in 1024..65535 && port != LEGACY_PORT &&
                port != frontPort && port !in excludedPorts

        if (acceptable(current) && isAvailable(current)) return current

        repeat(16) {
            val next = allocateEphemeral()
            if (acceptable(next) && isAvailable(next)) return next
        }
        error("Unable to allocate a free local gRPC control port")
    }

    /** Retry only the specific EADDRINUSE bind race, never authentication or DB failures. */
    fun isBindConflict(error: Throwable): Boolean {
        var cause: Throwable? = error
        val seen = HashSet<Throwable>()
        while (cause != null && seen.add(cause)) {
            val message = cause.message.orEmpty().lowercase(java.util.Locale.ROOT)
            if (message.contains("address already in use") ||
                message.contains("eaddrinuse")) return true
            cause = cause.cause
        }
        return false
    }
}

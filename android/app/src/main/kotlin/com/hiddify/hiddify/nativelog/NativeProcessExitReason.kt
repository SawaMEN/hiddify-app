package com.hiddify.hiddify.nativelog

/** Stable ApplicationExitInfo reason values, available for JVM diagnostics tests too. */
internal object NativeProcessExitReason {
    fun name(reason: Int): String = when (reason) {
        0 -> "unknown"
        1 -> "exit_self"
        2 -> "signal"
        3 -> "low_memory"
        4 -> "jvm_crash"
        5 -> "native_crash"
        6 -> "anr"
        7 -> "initialization_failure"
        8 -> "permission_change"
        9 -> "excessive_resource_usage"
        10 -> "user_requested"
        11 -> "user_stopped"
        12 -> "dependency_died"
        13 -> "other"
        14 -> "freezer"
        15 -> "package_state_change"
        16 -> "package_updated"
        17 -> "memory_limiter"
        18 -> "resource_anomaly"
        else -> "unknown_$reason"
    }
}

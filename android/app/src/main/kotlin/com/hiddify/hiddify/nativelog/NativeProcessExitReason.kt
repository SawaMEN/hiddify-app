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

    fun level(reason: Int): String = when (reason) {
        1, 8, 10, 11, 15, 16 -> "INFO"
        0, 13 -> "WARN"
        else -> "ERROR"
    }

    fun signal(status: Int): String = when (status) {
        0 -> "unreported"
        6 -> "SIGABRT"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        15 -> "SIGTERM"
        else -> "signal_$status"
    }

    fun format(timestamp: Long, reason: Int, status: Int, pid: Int, process: String,
        importance: Int, vpnPhase: String?, description: String?): String = buildString {
        append(java.time.Instant.ofEpochMilli(timestamp)).append(' ').append(level(reason))
        append(" Previous process exit: reason=").append(name(reason))
        append(" status=").append(status)
        if (reason == 2) append(" signal=").append(signal(status))
        append(" pid=").append(pid).append(" process=").append(singleLine(process))
        append(" importance=").append(importance).append(" vpnPhase=").append(vpnPhase ?: "unknown")
        if (!description.isNullOrBlank()) append(" description=").append(singleLine(description))
    }

    private fun singleLine(value: String): String = value.take(1024)
        .map { if (it.isISOControl()) ' ' else it }.joinToString("")
}

package com.hiddify.hiddify.nativeprofile

/** Visual feedback from a completed, real profile operation — never from a tap alone. */
enum class NativeProfileRefreshPhase { UPDATING, SUCCESS, FAILURE }

/** The special update-all request is tracked separately from individual profile IDs. */
data class NativeProfileRefreshFeedback(val id: String, val phase: NativeProfileRefreshPhase)

object NativeProfileRefreshMotionPolicy {
    const val UPDATE_ALL_ID = "update-all"

    fun phaseFor(id: String, feedback: NativeProfileRefreshFeedback?): NativeProfileRefreshPhase? =
        feedback?.takeIf { it.id == id }?.phase
}

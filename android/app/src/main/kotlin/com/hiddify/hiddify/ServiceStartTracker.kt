package com.hiddify.hiddify

import com.hiddify.hiddify.constant.Status

/** Ignores the initial Stopped snapshot delivered when Android binds the service. */
internal class ServiceStartTracker {
    var issued = false
        private set
    private var observedStarting = false

    fun markIssued() {
        issued = true
        observedStarting = false
    }

    fun reset() {
        issued = false
        observedStarting = false
    }

    fun onStatus(status: Status): Boolean? {
        if (!issued) return null
        return when (status) {
            Status.Starting -> {
                observedStarting = true
                null
            }
            Status.Started -> true
            Status.Stopped -> if (observedStarting) false else null
            Status.Stopping -> null
        }
    }
}

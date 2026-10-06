package com.hiddify.hiddify.constant

import com.hiddify.hiddify.Application

object Action {
    // Resolve at runtime so privacy-repacked installations stay isolated too.
    val SERVICE get() = "${Application.application.packageName}.SERVICE"
    val SERVICE_CLOSE get() = "${Application.application.packageName}.SERVICE_CLOSE"
}

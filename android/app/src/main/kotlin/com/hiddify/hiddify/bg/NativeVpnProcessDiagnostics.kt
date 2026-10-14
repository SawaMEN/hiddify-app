package com.hiddify.hiddify.bg

import android.app.ActivityManager
import android.os.Build
import android.util.Log
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.nativelog.NativeVpnProcessPhase

/** Android preserves this summary even for SIGKILL/native crashes without JVM exceptions. */
internal object NativeVpnProcessDiagnostics {
    fun phase(value: NativeVpnProcessPhase) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            Application.application.getSystemService(ActivityManager::class.java)
                ?.setProcessStateSummary(value.summary())
        }.onFailure { Log.w("A/Diagnostics", "Unable to record VPN process phase", it) }
    }
}

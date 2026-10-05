package com.hiddify.hiddify.privacy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.hiddify.hiddify.bg.VPNService

/**
 * Best-effort reduction of the package-manager signal used by apps that enumerate
 * services advertising android.net.VpnService.
 *
 * The component is hidden only while root mode is selected. Normal Android VPN mode
 * always restores the manifest default before attempting to start the service.
 */
object VpnServiceVisibility {
    private const val TAG = "A/VpnServiceVisibility"

    fun sync(context: Context, hideForRootMode: Boolean) {
        val component = ComponentName(context, VPNService::class.java)
        val manager = context.packageManager
        val desired = if (hideForRootMode) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        if (manager.getComponentEnabledSetting(component) == desired) return

        runCatching {
            manager.setComponentEnabledSetting(
                component,
                desired,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { error ->
            Log.w(TAG, "failed to update VPN service component visibility", error)
        }
    }
}

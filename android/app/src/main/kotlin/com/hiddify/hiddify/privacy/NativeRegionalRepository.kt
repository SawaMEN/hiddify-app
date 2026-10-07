package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings

class NativeRegionalRepository(private val context: Context) {
    fun load(): NativeRegionalOptions = NativeRegionalOptions(
        mode = NativeRegionalMode.fromValue(Settings.privacyRoutingMode),
        russianNetworkBypass = Settings.privacyRussianNetworkBypass,
        russianAppsBypass = Settings.privacyRussianAppsBypass,
        restrictedServicesProxy = Settings.privacyRestrictedServicesProxy,
        directDomains = Settings.privacyDirectDomains,
        proxyDomains = Settings.privacyProxyDomains,
    )

    /** Validate the merged catalogue before committing any preference. Call on IO. */
    fun save(value: NativeRegionalOptions): NativeRegionalOptions {
        val valid = value.validated()
        RegionalRouting.policy(context, options = valid)
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.privacy-routing-mode", valid.mode.value)
            .putBoolean("flutter.privacy-russian-network-bypass", valid.russianNetworkBypass)
            .putBoolean("flutter.privacy-russian-apps-bypass", valid.russianAppsBypass)
            .putBoolean("flutter.privacy-restricted-services-proxy", valid.restrictedServicesProxy)
            .putString("flutter.privacy-direct-domains", valid.directDomains)
            .putString("flutter.privacy-proxy-domains", valid.proxyDomains)
        check(editor.commit()) { "Could not save regional routing" }
        return load()
    }
}

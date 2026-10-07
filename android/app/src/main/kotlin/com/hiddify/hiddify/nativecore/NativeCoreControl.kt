package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcore.ChangeHiddifySettingsRequest
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.utils.GrpcClientProvider

/**
 * Small native control bridge for the background hiddify-core gRPC server.
 *
 * Mobile.setup() starts the authenticated localhost server. Applying the persisted options through
 * the same RPC used by Flutter keeps Kotlin-only launches consistent with the previous client and
 * stores the snapshot in the core database for Always-on/restart paths.
 */
object NativeCoreControl {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    fun effectiveOptions(context: android.content.Context): String {
        val profile = com.hiddify.hiddify.nativeprofile.NativeProfileRepository(context).activeProfile()
        return com.hiddify.hiddify.nativeprofile.NativeProfileOverrides.apply(Settings.configOptions,
            profile?.populatedHeaders, profile?.userOverride)
    }

    fun applyStoredSettings(context: android.content.Context) {
        val json = effectiveOptions(context)
        client()
            .ChangeHiddifySettings()
            .executeBlocking(ChangeHiddifySettingsRequest(hiddify_settings_json = json))
    }
}

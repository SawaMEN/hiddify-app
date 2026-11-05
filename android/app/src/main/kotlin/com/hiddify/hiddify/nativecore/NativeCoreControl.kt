package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcore.ChangeHiddifySettingsRequest
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.MessageType
import com.hiddify.core.api.v2.hcore.ParseRequest
import com.hiddify.core.api.v2.hcore.StartRequest
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.ServiceMode
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
        val repository = com.hiddify.hiddify.nativeprofile.NativeProfileRepository(context)
        val profile = repository.activeProfile()
        // A VPN service must always ask sing-box to create its Android TUN inbound.
        // The core's default enable-tun=false otherwise lets startup report Started
        // while there is no Android VPN interface and no traffic can pass through it.
        val overrides = com.hiddify.hiddify.nativeprofile.NativeProfileOverrides.apply(
            NativeSettingsTransferRepository(context).exportJson(includePrivate = true), profile?.populatedHeaders, profile?.userOverride,
        )
        return NativeServiceModeOptions.apply(
            NativeCoreOptionsProjection.apply(overrides, profile?.id, repository::chainProfileContent),
            vpnMode = Settings.serviceMode == ServiceMode.VPN,
            ipv4Only = Settings.privacyDisableIpv6,
        )
    }

    /**
     * Released hiddify-core SDK builds have a JSON-only shortcut in buildRunningConfig()
     * even though imports already accept URI subscriptions and Clash YAML. Convert only
     * non-JSON source with the core's own parser before Start/HotReload; the source file,
     * subscription updates, headers and credentials on disk remain completely unchanged.
     *
     * Parse with no destination path: do not overwrite the user's profile or leave a
     * second credential-bearing file behind. The caller supplies the result as content
     * to the native start request, which can persist it for an ordinary core restart.
     */
    fun normalizedProfileContent(path: String): String? {
        val source = java.io.File(path)
        check(source.isFile) { "Selected profile configuration file is missing" }
        val content = source.inputStream().use(
            com.hiddify.hiddify.nativeprofile.NativeProfileTransfer::readText,
        )
        if (!NativeProfileStartFormat.needsNormalization(content)) return null
        val call = client().Parse()
        call.timeout.timeout(45, java.util.concurrent.TimeUnit.SECONDS)
        val response = try {
            call.executeBlocking(ParseRequest(content = content))
        } finally {
            call.cancel()
        }
        check(response.content.isNotBlank()) { "Core returned an empty normalized profile" }
        check(response.content.toByteArray(Charsets.UTF_8).size <=
            com.hiddify.hiddify.nativeprofile.NativeProfileTransfer.MAX_CONFIG_BYTES) {
            "Normalized profile exceeds 8 MiB"
        }
        return response.content
    }

    /**
     * Apply a changed active subscription without recreating Android's VPN interface.
     * Call only while BoxService's native lifecycle lock is held and the core is STARTED.
     *
     * A rejected or unavailable RPC is handled by the caller as a full reconnect.
     * The Go core promises to keep its previous running configuration on reload failure.
     */
    fun hotReloadActiveProfile(): Boolean {
        val path = Settings.activeConfigPath
        check(path.isNotBlank() && java.io.File(path).isFile) {
            "No active configuration available for hot reload"
        }
        val normalized = normalizedProfileContent(path)
        val call = client().HotReload()
        call.timeout.timeout(20, java.util.concurrent.TimeUnit.SECONDS)
        val response = try {
            call.executeBlocking(StartRequest(
                config_path = if (normalized == null) path else "",
                config_content = normalized ?: "",
                config_name = Settings.activeProfileName,
                disable_memory_limit = Settings.disableMemoryLimit,
            ))
        } finally {
            call.cancel()
        }
        return NativeHotReloadPolicy.applied(
            messageEmpty = response.message_type == MessageType.EMPTY,
            coreState = response.core_state.value,
        )
    }

    fun applyStoredSettings(context: android.content.Context) {
        val json = effectiveOptions(context)
        val call = client().ChangeHiddifySettings()
        call.timeout.timeout(10, java.util.concurrent.TimeUnit.SECONDS)
        val response = try { call.executeBlocking(ChangeHiddifySettingsRequest(hiddify_settings_json = json)) }
            finally { call.cancel() }
        check(response.message_type == MessageType.EMPTY) { response.message.ifBlank { "Core rejected connection settings" } }
    }
}

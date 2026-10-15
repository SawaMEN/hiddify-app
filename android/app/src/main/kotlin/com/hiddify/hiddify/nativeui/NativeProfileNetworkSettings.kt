package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.*

@Composable
internal fun NativeProfileNetworkSettings(
    options: NativeProfileNetworkOptions, remote: Boolean, json: Boolean, enabled: Boolean,
    showTls: Boolean = true, content: String = "",
    onChange: (NativeProfileNetworkOptions) -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    val targets = remember(content) { NativeProfileTlsTargets.inspect(content) }
    val tls = options.tls
    val matching = targets.filter { tls.targetTag.isEmpty() || it.tag == tls.targetTag }
    val tlsEnabled = enabled && targets.isNotEmpty()
    val fingerprints = tlsEnabled && matching.any { it.fingerprint }
    val fragment = tlsEnabled && matching.any { it.fragment }
    val tcpOnly = tlsEnabled && matching.isNotEmpty() && matching.all { it.fragment }
    val automatic = stringResource(R.string.native_tls_auto_profile)
    val saved = stringResource(R.string.native_network_saved)
    fun retained(choices: List<Pair<String, String>>, current: String) =
        if (choices.any { it.first == current }) choices else choices + (current to saved)
    fun updateTls(value: NativeProfileTlsOptions) = onChange(options.copy(tls = value))

    if (remote) {
        NetworkHeading(R.string.native_subscription_request, R.string.native_request_auto_help)
        val agents = NativeProfileNetworkPresets.clients.map { it.agent to if (it.agent.isEmpty()) automatic else it.title }
        NetworkChoice(R.string.native_request_client, options.request.userAgent, retained(agents, options.request.userAgent), enabled) {
            onChange(options.copy(request = options.request.copy(userAgent = it)))
        }
    }
    if (showTls) {
        NetworkHeading(R.string.native_profile_tls, if (targets.isEmpty()) R.string.native_tls_no_servers else R.string.native_tls_auto_help)
        val tags = listOf("" to stringResource(R.string.native_tls_all_servers)) + targets.map { it.tag }.filter { it.isNotEmpty() }.distinct().map { it to it }
        NetworkChoice(R.string.native_profile_tls_tag, tls.targetTag, retained(tags, tls.targetTag), tlsEnabled) {
            updateTls(NativeProfileNetworkPresets.withTarget(tls, it, targets))
        }
        val browserChoices = listOf("" to automatic) + NativeProfileNetworkPresets.browserNames.map { it.key to it.value } +
            listOf("random" to stringResource(R.string.native_tls_random_browser), "randomized" to stringResource(R.string.native_tls_random_hello)) +
            if (json && tcpOnly) listOf("off" to stringResource(R.string.native_tls_native)) else emptyList()
        NetworkChoice(R.string.native_profile_tls_fingerprint, tls.fingerprint, retained(browserChoices, tls.fingerprint), fingerprints) {
            updateTls(tls.copy(fingerprint = it))
        }
        val modes = listOf("" to automatic, "off" to stringResource(R.string.native_profile_tls_fragment_off)) +
            if (fragment) listOf("tcp" to stringResource(R.string.native_profile_tls_fragment_tcp)) +
                if (json) listOf("record" to stringResource(R.string.native_profile_tls_fragment_record), "both" to stringResource(R.string.native_profile_tls_fragment_both)) else emptyList()
            else emptyList()
        NetworkChoice(R.string.native_profile_tls_fragment, tls.fragmentMode, retained(modes, tls.fragmentMode), fingerprints) {
            updateTls(tls.copy(fragmentMode = it, fallbackDelayMs = if (it in listOf("tcp", "record", "both")) tls.fallbackDelayMs else ""))
        }
    }
    NativeTextButton(enabled = enabled, onClick = { advanced = !advanced }, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(if (advanced) R.string.native_network_hide else R.string.native_network_advanced))
    }
    if (advanced) {
        if (remote) {
            val headers = remember(options.request) { options.request.parsedHeaders() }
            val accepts = listOf("" to automatic, "application/json" to "JSON", "text/plain" to stringResource(R.string.native_network_plain))
                .filter { runCatching { NativeProfileNetworkPresets.withAccept(options.request, it.first) }.isSuccess }
            val metadata = runCatching { NativeProfileNetworkPresets.withMetadata(options.request,
                headers["x-device-os"] != "Android", com.hiddify.hiddify.BuildConfig.VERSION_NAME) }.getOrNull()
            NetworkChoice(R.string.native_network_format, headers["accept"].orEmpty(), retained(accepts, headers["accept"].orEmpty()), enabled) {
                onChange(options.copy(request = NativeProfileNetworkPresets.withAccept(options.request, it)))
            }
            NativePreferenceTile(R.string.native_network_metadata, R.drawable.native_tls_text_fields,
                stringResource(R.string.native_network_metadata_help), enabled && metadata != null, headers["x-device-os"] == "Android") {
                metadata?.let { onChange(options.copy(request = it)) }
            }
            val preserved = headers.keys - setOf("accept", "x-device-os", "x-app-version")
            if (preserved.isNotEmpty()) Text(stringResource(R.string.native_network_headers_kept, preserved.size),
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (showTls) {
            val names = matching.map { it.serverName }.filter { it.isNotEmpty() }.distinct()
            val sni = listOf("" to automatic) + if (tcpOnly && names.size == 1) names.map { it to it } else emptyList()
            NetworkChoice(R.string.native_profile_tls_sni, tls.serverName, retained(sni, tls.serverName), tlsEnabled) { updateTls(tls.copy(serverName = it)) }
            Text(if (names.isEmpty()) stringResource(R.string.native_network_sni_auto) else stringResource(R.string.native_tls_detected_sni, names.take(3).joinToString(", ")),
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            if (json) NetworkChoice(R.string.native_network_sni_mode, tls.sniMode,
                listOf("" to automatic, "on" to stringResource(R.string.native_network_sni_on), "off" to stringResource(R.string.native_network_sni_off)), tcpOnly) { updateTls(tls.copy(sniMode = it)) }
            val alpn = listOf("" to automatic, "h2,http/1.1" to stringResource(R.string.native_network_h2_prefer), "http/1.1" to "HTTP/1.1", "h2" to "HTTP/2")
            NetworkChoice(R.string.native_profile_tls_alpn, tls.alpn, retained(alpn, tls.alpn), tcpOnly) { updateTls(tls.copy(alpn = it)) }
            if (json) {
                val versions = listOf("" to automatic, "modern" to "TLS 1.2–1.3", "1.3" to "TLS 1.3", "1.2" to "TLS 1.2")
                val version = when {
                    tls.minVersion.isEmpty() && tls.maxVersion.isEmpty() -> ""
                    tls.minVersion == "1.2" && tls.maxVersion.isEmpty() -> "modern"
                    tls.minVersion == tls.maxVersion -> tls.minVersion
                    else -> "saved"
                }
                NetworkChoice(R.string.native_network_tls_version, version, retained(versions, version), tcpOnly) {
                    if (it != "saved") updateTls(tls.copy(minVersion = if (it == "modern") "1.2" else it, maxVersion = if (it == "modern") "" else it))
                }
                val delays = NativeProfileNetworkPresets.delays.map { it to if (it.isEmpty()) automatic else stringResource(R.string.native_network_ms, it) }
                NetworkChoice(R.string.native_profile_tls_delay, tls.fallbackDelayMs, retained(delays, tls.fallbackDelayMs), fragment && tls.fragmentMode in listOf("tcp", "record", "both")) { updateTls(tls.copy(fallbackDelayMs = it)) }
            }
            Text(stringResource(R.string.native_network_compatibility), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    NativeTextButton(enabled = enabled, onClick = { onChange(NativeProfileNetworkPresets.automatic(options)) }, modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.native_network_reset))
    }
}

@Composable
private fun NetworkHeading(title: Int, help: Int) {
    Text(stringResource(title), Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(help), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NetworkChoice(label: Int, selected: String, choices: List<Pair<String, String>>, enabled: Boolean, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    NativePreferenceTile(label, R.drawable.native_tls_text_fields, choices.firstOrNull { it.first == selected }?.second.orEmpty(), enabled) { open = true }
    if (open) NativeSettingPickerDialog(stringResource(label), selected, choices, enabled,
        onSelect = { if (it != selected) onChange(it); open = false }, onReset = { onChange(""); open = false }, onDismiss = { open = false })
}

package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfileNetworkOptions
import com.hiddify.hiddify.nativeprofile.NativeProfileTlsOptions

@Composable
internal fun NativeProfileNetworkSettings(
    options: NativeProfileNetworkOptions, remote: Boolean, json: Boolean, enabled: Boolean,
    showTls: Boolean = true, content: String = "",
    onChange: (NativeProfileNetworkOptions) -> Unit,
) {
    var requestExpanded by rememberSaveable { mutableStateOf(false) }
    var tlsExpanded by rememberSaveable { mutableStateOf(false) }
    var customAgent by rememberSaveable { mutableStateOf(false) }
    val targets = remember(content) { com.hiddify.hiddify.nativeprofile.NativeProfileTlsTargets.inspect(content) }
    val matchingTargets = targets.filter { options.tls.targetTag.isEmpty() || it.tag == options.tls.targetTag }
    val tlsEnabled = enabled && targets.isNotEmpty()
    val fingerprintEnabled = tlsEnabled && matchingTargets.any { it.fingerprint }
    val fragmentEnabled = tlsEnabled && matchingTargets.any { it.fragment }
    val tls = options.tls
    if (remote) {
        Text(stringResource(R.string.native_subscription_request), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.native_request_auto_help), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        val agents = listOf("" to stringResource(R.string.native_tls_auto_profile),
            "VetrOFF-Android" to "VetrOFF", "Hiddify" to "Hiddify", "Mozilla/5.0" to stringResource(R.string.native_request_browser),
            "custom" to stringResource(R.string.native_request_custom))
        val agentChoice = if (customAgent || agents.none { it.first == options.request.userAgent }) "custom" else options.request.userAgent
        ProfileTlsChoice(R.string.native_request_client, agentChoice, agents, enabled) { choice ->
            customAgent = choice == "custom"
            if (customAgent) requestExpanded = true
            else onChange(options.copy(request = options.request.copy(userAgent = choice)))
        }
        NativeTextButton(enabled = enabled, onClick = { requestExpanded = !requestExpanded }) {
            Text(stringResource(R.string.native_network_advanced))
        }
        AnimatedVisibility(requestExpanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.native_subscription_request_help), style = MaterialTheme.typography.bodySmall)
                if (customAgent) OutlinedTextField(options.request.userAgent, { if (it.length <= 1024) onChange(options.copy(request = options.request.copy(userAgent = it))) },
                    Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
                    label = { Text("User-Agent") }, placeholder = { Text("VetrOFF-Android") })
                OutlinedTextField(options.request.headers, { if (it.length <= 16384) onChange(options.copy(request = options.request.copy(headers = it))) },
                    Modifier.fillMaxWidth(), enabled = enabled, minLines = 3, maxLines = 8,
                    label = { Text(stringResource(R.string.native_subscription_headers)) },
                    placeholder = { Text("x-device-os: Android\nx-app-version: 4.0\nAuthorization: Bearer …") })
                if (runCatching { options.request.validated() }.isFailure) Text(stringResource(R.string.native_subscription_headers_invalid),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                NativeTextButton(enabled = enabled, onClick = { onChange(options.copy(request = com.hiddify.hiddify.nativeprofile.NativeSubscriptionRequestOptions())) }) {
                    Text(stringResource(R.string.native_quick_reset))
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
    if (!showTls) return
    Text(stringResource(R.string.native_profile_tls), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(if (targets.isEmpty()) R.string.native_tls_no_servers else R.string.native_tls_auto_help),
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
    val presets = listOf("auto" to stringResource(R.string.native_tls_auto_profile)) +
        (if (fingerprintEnabled) listOf("browser" to stringResource(R.string.native_tls_preset_browser)) else emptyList()) +
        (if (fragmentEnabled) listOf("fragment" to stringResource(R.string.native_tls_preset_fragment)) else emptyList()) +
        listOf("custom" to stringResource(R.string.native_request_custom))
    val preset = when (tls) {
        NativeProfileTlsOptions() -> "auto"
        NativeProfileTlsOptions(fingerprint = "chrome") -> "browser"
        NativeProfileTlsOptions(fragmentMode = "tcp") -> "fragment"
        else -> "custom"
    }
    if (preset == "custom") Text(stringResource(R.string.native_profile_tls_custom), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
    ProfileTlsChoice(R.string.native_tls_preset, preset, presets, tlsEnabled) {
        if (it == "custom") tlsExpanded = true
        else onChange(options.copy(tls = when (it) {
            "browser" -> NativeProfileTlsOptions(fingerprint = "chrome")
            "fragment" -> NativeProfileTlsOptions(fragmentMode = "tcp")
            else -> NativeProfileTlsOptions()
        }))
    }
    NativeTextButton(enabled = enabled, onClick = { tlsExpanded = !tlsExpanded }) {
        Text(stringResource(R.string.native_network_advanced))
    }
    AnimatedVisibility(tlsExpanded) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.native_profile_tls_help), style = MaterialTheme.typography.bodySmall)
            val tags = listOf("" to stringResource(R.string.native_tls_all_servers)) + targets.filter { it.tag.isNotEmpty() }.map { it.tag to it.tag }
            ProfileTlsChoice(R.string.native_profile_tls_tag, tls.targetTag, tags, tlsEnabled) { onChange(options.copy(tls = tls.copy(targetTag = it))) }
            val names = matchingTargets.map { it.serverName }.filter { it.isNotEmpty() }.distinct()
            if (names.isNotEmpty()) Text(stringResource(R.string.native_tls_detected_sni, names.take(3).joinToString(", ")), style = MaterialTheme.typography.bodySmall)
            TlsTextField(R.string.native_profile_tls_sni, tls.serverName, tlsEnabled) { onChange(options.copy(tls = tls.copy(serverName = it))) }
            ProfileTlsChoice(R.string.native_profile_tls_fingerprint, tls.fingerprint,
                NativeProfileTlsOptions.fingerprints.map { it to if (it.isEmpty()) stringResource(R.string.native_profile_tls_inherit) else it }, fingerprintEnabled) {
                onChange(options.copy(tls = tls.copy(fingerprint = it)))
            }
            TlsTextField(R.string.native_profile_tls_alpn, tls.alpn, tlsEnabled) { onChange(options.copy(tls = tls.copy(alpn = it))) }
            val modes = listOf("" to R.string.native_profile_tls_inherit, "off" to R.string.native_profile_tls_fragment_off,
                "tcp" to R.string.native_profile_tls_fragment_tcp, "record" to R.string.native_profile_tls_fragment_record, "both" to R.string.native_profile_tls_fragment_both)
                .filter { json || it.first !in listOf("record", "both") }
            ProfileTlsChoice(R.string.native_profile_tls_fragment, tls.fragmentMode,
                modes.map { (value, label) -> value to stringResource(label) }, fragmentEnabled) { onChange(options.copy(tls = tls.copy(fragmentMode = it))) }
            if (json) {
                TlsTextField(R.string.native_profile_tls_delay, tls.fallbackDelayMs, fragmentEnabled && tls.fragmentMode in listOf("tcp", "record", "both")) { onChange(options.copy(tls = tls.copy(fallbackDelayMs = it))) }
                val versions = NativeProfileTlsOptions.versions.map { it to if (it.isEmpty()) stringResource(R.string.native_profile_tls_inherit) else "TLS $it" }
                ProfileTlsChoice(R.string.native_profile_tls_min_version, tls.minVersion, versions, tlsEnabled) { onChange(options.copy(tls = tls.copy(minVersion = it))) }
                ProfileTlsChoice(R.string.native_profile_tls_max_version, tls.maxVersion, versions, tlsEnabled) { onChange(options.copy(tls = tls.copy(maxVersion = it))) }
            } else Text(stringResource(R.string.native_profile_tls_json_help), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.native_profile_tls_spoof_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (runCatching { tls.validated() }.isFailure) Text(stringResource(R.string.native_profile_tls_invalid), color = MaterialTheme.colorScheme.error)
            NativeTextButton(enabled = enabled, onClick = { onChange(options.copy(tls = NativeProfileTlsOptions())) }) {
                Text(stringResource(R.string.native_quick_reset))
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun TlsTextField(label: Int, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 1024) onChange(it) }, Modifier.fillMaxWidth(), enabled = enabled,
        singleLine = true, label = { Text(stringResource(label)) })
}

@Composable
private fun ProfileTlsChoice(label: Int, selected: String, choices: List<Pair<String, String>>, enabled: Boolean, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    NativeTextButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label), Modifier.weight(1f))
        Text(choices.firstOrNull { it.first == selected }?.second ?: selected)
    }
    if (open) NativeSettingPickerDialog(stringResource(label), selected, choices, enabled,
        onSelect = { onChange(it); open = false }, onReset = { onChange(""); open = false }, onDismiss = { open = false })
}

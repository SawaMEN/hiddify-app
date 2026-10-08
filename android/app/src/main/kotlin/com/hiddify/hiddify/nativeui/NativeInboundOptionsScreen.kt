package com.hiddify.hiddify.nativeui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.nativecore.NativeInboundOptions
import com.hiddify.hiddify.nativecore.NativeLanSharingLink
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** InboundOptionsPage's immediate preference editors, including independent port switches. */
@Composable
internal fun NativeInboundOptionsScreen(
    options: NativeInboundOptions, busy: Boolean, canSave: Boolean, onBack: () -> Unit,
    onSave: (NativeInboundOptions) -> Unit, serviceMode: String, wifiSharing: Boolean,
    onServiceMode: (Boolean) -> Unit, onResolveLanSharing: suspend () -> NativeWifiSharingDetails,
) {
    var editor by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    val enabled = !busy && canSave
    fun edit(key: String, value: String = "") { input = value; editor = key }
    fun save(value: NativeInboundOptions) { if (enabled) { onSave(value); editor = "" } }
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_inbound_title), onBack)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            NativePreferenceTile(R.string.native_inbound_service_mode, R.drawable.native_inbound_tune,
                stringResource(if (serviceMode == ServiceMode.VPN) R.string.native_quick_vpn else R.string.native_inbound_proxy_service), enabled) { edit("mode") }
            NativePreferenceTile(R.string.native_core_strict_route, R.drawable.native_inbound_merge,
                null, enabled, options.strictRoute) { save(options.copy(strictRoute = !options.strictRoute)) }
            NativePreferenceTile(R.string.native_core_tun_stack, R.drawable.native_inbound_tun,
                options.tunImplementation, enabled) { edit("tun") }
            NativePreferenceTile(R.string.native_core_mixed_port, R.drawable.native_inbound_port,
                options.mixedPort.toString(), enabled, options.mixedEnabled,
                onToggle = { save(options.copy(mixedEnabled = it)) }) { edit("mixed", options.mixedPort.toString()) }
            NativePreferenceTile(R.string.native_core_direct_port, R.drawable.native_inbound_port,
                options.directPort.toString(), enabled, options.directEnabled,
                onToggle = { save(options.copy(directEnabled = it)) }) { edit("direct", options.directPort.toString()) }
            NativeLanSharingPreference(options, wifiSharing, enabled,
                onPassword = { edit("password", options.lanPassword) },
                onToggle = { save(options.copy(allowLan = it)) }, onResolve = onResolveLanSharing)
        }
    }
    when (editor) {
        "mode" -> NativeSettingPickerDialog(stringResource(R.string.native_inbound_service_mode), serviceMode,
            listOf(ServiceMode.NORMAL to stringResource(R.string.native_inbound_proxy_service), ServiceMode.VPN to stringResource(R.string.native_quick_vpn)),
            enabled, onSelect = { if (enabled) { onServiceMode(it != ServiceMode.VPN); editor = "" } },
            onReset = { if (enabled) { onServiceMode(false); editor = "" } }, onDismiss = { editor = "" })
        "tun" -> NativeSettingPickerDialog(stringResource(R.string.native_core_tun_stack), options.tunImplementation,
            listOf("mixed", "system", "gvisor").map { it to it }, enabled,
            onSelect = { save(options.copy(tunImplementation = it)) }, onReset = { save(options.copy(tunImplementation = NativeInboundOptions().tunImplementation)) },
            onDismiss = { editor = "" })
        "mixed", "direct", "password" -> {
            val key = editor
            val password = key == "password"
            fun updated(text: String) = when (key) {
                "mixed" -> options.copy(mixedPort = text.toIntOrNull() ?: 0)
                "direct" -> options.copy(directPort = text.toIntOrNull() ?: 0)
                else -> options.copy(lanPassword = text)
            }
            val valid = runCatching { updated(input).validated() }.isSuccess
            NativeSettingInputDialog(stringResource(when (key) {
                "mixed" -> R.string.native_core_mixed_port
                "direct" -> R.string.native_core_direct_port
                else -> R.string.native_inbound_sharing_password
            }), input, onValueChange = { text ->
                if (password) { if (text.length <= 128) input = text }
                else if (text.length <= 5 && text.all { it in '0'..'9' }) input = text
            }, valid = valid, enabled = enabled, invalidMessage = stringResource(R.string.native_inbound_invalid),
                onDismiss = { editor = "" }, onConfirm = { save(updated(input)) },
                onReset = { save(updated(when (key) { "mixed" -> "12334"; "direct" -> "12337"; else -> "" })) },
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Text else KeyboardType.Number, imeAction = ImeAction.Done))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeLanSharingPreference(options: NativeInboundOptions, wifiSharing: Boolean, enabled: Boolean,
    onPassword: () -> Unit, onToggle: (Boolean) -> Unit, onResolve: suspend () -> NativeWifiSharingDetails) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolve by rememberUpdatedState(onResolve)
    var linkBusy by remember { mutableStateOf(false) }
    var qrLink by remember { mutableStateOf<String?>(null) }
    fun share(qr: Boolean) {
        if (linkBusy) return
        linkBusy = true
        scope.launch {
            try {
                val current = resolve()
                val link = NativeLanSharingLink.create(current.host, current.port, current.password)
                if (qr) qrLink = link else {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("LAN", link))
                    Toast.makeText(context, R.string.native_profile_copied, Toast.LENGTH_SHORT).show()
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(context, R.string.native_quick_lan_error, Toast.LENGTH_SHORT).show() }
            finally { linkBusy = false }
        }
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onPassword)
        .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(R.drawable.native_inbound_share), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.native_inbound_lan), style = MaterialTheme.typography.bodyLarge)
            Text(options.lanPassword.ifEmpty { stringResource(R.string.native_inbound_password_not_set) },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (wifiSharing || (options.allowLan && options.mixedEnabled)) {
                Spacer(Modifier.height(12.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NativeElevatedButton(onClick = { share(false) }, enabled = enabled && !linkBusy) {
                        Icon(painterResource(R.drawable.native_quick_link), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.native_inbound_copy_link))
                    }
                    NativeElevatedButton(onClick = { share(true) }, enabled = enabled && !linkBusy) {
                        Icon(painterResource(R.drawable.native_quick_qr_code), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.native_inbound_qr))
                    }
                }
            }
        }
        Switch(wifiSharing || options.allowLan, onCheckedChange = onToggle, enabled = enabled && !wifiSharing)
    }
    qrLink?.let { LanQrDialog(it) { qrLink = null } }
}

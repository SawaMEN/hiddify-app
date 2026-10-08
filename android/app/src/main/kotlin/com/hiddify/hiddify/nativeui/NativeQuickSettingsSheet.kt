package com.hiddify.hiddify.nativeui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeElevatedButton as ElevatedButton
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.nativecore.NativeChainOptions
import com.hiddify.hiddify.nativecore.NativeLanSharingLink
import com.hiddify.hiddify.nativecore.NativeInboundOptions
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails
import com.hiddify.hiddify.nativeprofile.NativeQrImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NativeQuickSettingsSheet(
    serviceMode: String,
    wifiSharing: Boolean,
    inbound: NativeInboundOptions,
    chain: NativeChainOptions,
    activeProfileName: String,
    busy: Boolean,
    detailsBusy: Boolean,
    onServiceMode: (Boolean) -> Unit,
    onLanSharing: (Boolean, String) -> Unit,
    onChain: (Boolean, String?) -> Unit,
    onResolveLanSharing: suspend () -> NativeWifiSharingDetails,
    onOpenChain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var passwordOpen by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable(inbound.lanPassword) { mutableStateOf(inbound.lanPassword) }
    var qrLink by rememberSaveable { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resolveLan by rememberUpdatedState(onResolveLanSharing)
    fun sharingAction(qr: Boolean) {
        if (linkBusy || detailsBusy || busy) return
        linkBusy = true
        scope.launch {
            try {
                val current = resolveLan()
                val link = NativeLanSharingLink.create(current.host, current.port, current.password)
                if (qr) qrLink = link
                else {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("LAN", link))
                    Toast.makeText(context, R.string.native_profile_copied, Toast.LENGTH_SHORT).show()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, R.string.native_quick_lan_error, Toast.LENGTH_SHORT).show() }
            finally { linkBusy = false }
        }
    }
    ModalBottomSheet(modifier = Modifier.nativeGlassDecoration(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp, onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                listOf(ServiceMode.NORMAL, ServiceMode.VPN).forEachIndexed { index, mode ->
                    SegmentedButton(selected = serviceMode == mode,
                        onClick = { if (serviceMode != mode) onServiceMode(mode == ServiceMode.NORMAL) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2), enabled = !busy,
                        icon = {}, label = { Text(stringResource(if (mode == ServiceMode.NORMAL)
                            R.string.native_quick_proxy else R.string.native_quick_vpn), Modifier.padding(vertical = 8.dp)) })
                }
            }
            HorizontalDivider(thickness = 2.dp)
            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { password = inbound.lanPassword; passwordOpen = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.native_quick_share), null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.native_quick_lan), style = MaterialTheme.typography.bodyLarge)
                    Text(inbound.lanPassword.ifBlank { stringResource(R.string.native_quick_password_empty) },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (wifiSharing || (inbound.allowLan && inbound.mixedEnabled)) {
                        FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ElevatedButton(enabled = !detailsBusy && !linkBusy && !busy, onClick = { sharingAction(false) }) {
                                Icon(painterResource(R.drawable.native_quick_link), null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.native_quick_copy_link), color = MaterialTheme.colorScheme.primary)
                            }
                            ElevatedButton(enabled = !detailsBusy && !linkBusy && !busy, onClick = { sharingAction(true) }) {
                                Icon(painterResource(R.drawable.native_quick_qr_code), null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.native_quick_qr), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Switch(checked = wifiSharing || inbound.allowLan, enabled = !wifiSharing && !busy,
                    onCheckedChange = { onLanSharing(it, inbound.lanPassword) })
            }
            HorizontalDivider(thickness = 2.dp)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ChainStage(Modifier.weight(1f), true, chain, busy, onChain, onOpenChain)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        Row(Modifier.clickable(enabled = !busy, role = Role.Button, onClick = onOpenChain)
                            .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.native_chain_webhook), null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.native_quick_chain), Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.native_chain_arrow_forward), null, Modifier.padding(horizontal = 4.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.native_quick_main_profile), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(activeProfileName.ifBlank { stringResource(R.string.native_quick_not_set) },
                                Modifier.height(32.dp).padding(horizontal = 4.dp).basicMarquee(),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            ChainFinalIp(chain.status != "extra_security")
                        }
                        Icon(painterResource(R.drawable.native_chain_arrow_forward), null, Modifier.padding(horizontal = 4.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                ChainStage(Modifier.weight(1f), false, chain, busy, onChain, onOpenChain)
            }
        }
    }
    if (passwordOpen) LanPasswordDialog(password, { password = it }, busy,
        onSave = { onLanSharing(inbound.allowLan, password); passwordOpen = false },
        onReset = { onLanSharing(inbound.allowLan, ""); passwordOpen = false; password = "" },
        onDismiss = { passwordOpen = false; password = inbound.lanPassword })
    qrLink?.let { link -> LanQrDialog(link) { qrLink = null } }
}

@Composable
private fun ChainFinalIp(visible: Boolean) {
    val opacity by animateFloatAsState(if (visible) 1f else 0f,
        tween(if (LocalNativeMotionEnabled.current) 500 else 0), label = "Chain final IP")
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    Text(stringResource(R.string.native_quick_final_ip), Modifier.padding(horizontal = 4.dp).alpha(opacity),
        style = MaterialTheme.typography.labelSmall, color = if (dark) Color(0xFF99AD7A) else Color(0xFF57880D))
}

@Composable
private fun ChainModeIcon(mode: String) {
    val (drawable, color) = when (mode) {
        "psiphon" -> R.drawable.native_chain_local_parking to Color(0xFFD52027)
        "warp" -> R.drawable.native_provider_cloud to Color(0xFFF6821F)
        else -> R.drawable.native_chain_link to Color(0xFF3282B8)
    }
    Icon(painterResource(drawable), null, Modifier.size(16.dp), tint = color)
}

@Composable
private fun ChainStage(modifier: Modifier, extra: Boolean, chain: NativeChainOptions, busy: Boolean,
    onChange: (Boolean, String?) -> Unit, onConfigure: () -> Unit) {
    val stage = if (extra) "extra_security" else "unblocker"
    val enabled = chain.status == stage
    val mode = if (extra) chain.extraMode else chain.unblockerMode
    val scheme = MaterialTheme.colorScheme
    val foreground = if (enabled) scheme.onPrimaryContainer else scheme.onSecondaryContainer
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(painterResource(if (extra) R.drawable.native_chain_phone_android else R.drawable.native_chain_wifi),
                null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(if (extra) R.string.native_quick_app else R.string.native_quick_filtering),
                Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Icon(painterResource(R.drawable.native_chain_arrow_upward), null, Modifier.size(20.dp)
            .rotate(if (extra) 180f else 0f), tint = scheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(if (extra) R.string.native_quick_extra else R.string.native_quick_unblocker),
            Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(vertical = 2.dp)) {
            Surface(shape = RoundedCornerShape(100.dp),
                color = if (enabled) scheme.primaryContainer else scheme.secondaryContainer) {
                Row(Modifier.height(32.dp).clickable(enabled = !busy, role = Role.Button) { menuOpen = true }
                    .padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (enabled) { ChainModeIcon(mode); Spacer(Modifier.width(4.dp)) }
                    Text(if (enabled) modeTitle(mode) else stringResource(R.string.native_quick_disable),
                        Modifier.weight(1f, fill = false).basicMarquee(), style = MaterialTheme.typography.labelMedium,
                        color = foreground, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(painterResource(R.drawable.native_chain_arrow_drop_down), null, Modifier.size(16.dp), tint = foreground)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.native_quick_disable)) },
                    leadingIcon = { Icon(painterResource(R.drawable.native_chain_block), null, Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onChange(extra, null) }, enabled = !busy)
                listOf("psiphon", "warp", "profile").forEach { choice ->
                    DropdownMenuItem(text = { Text(modeTitle(choice)) }, leadingIcon = { ChainModeIcon(choice) },
                        onClick = { menuOpen = false; onChange(extra, choice) }, enabled = !busy)
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.native_quick_configuration)) },
                    trailingIcon = { Icon(painterResource(R.drawable.native_chevron), null, Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onConfigure() }, enabled = !busy)
            }
        }
        ChainFinalIp(extra && enabled)
    }
}

@Composable
private fun LanPasswordDialog(password: String, onPassword: (String) -> Unit, busy: Boolean,
    onSave: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var invalid by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val valid = password.length <= 128 && password.none(Char::isISOControl)
    fun submit() { if (!busy) { if (valid) onSave() else invalid = true } }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.native_quick_password)) }, text = {
            NativeTextField(password, { onPassword(it); invalid = false }, Modifier.focusRequester(focus),
                enabled = !busy, singleLine = true, isError = invalid,
                supportingText = { if (invalid) Text(stringResource(R.string.native_quick_password_invalid)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
        }, confirmButton = { NativeTextButton(enabled = !busy, onClick = { submit() }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = {
            Row {
                NativeTextButton(enabled = !busy, onClick = onReset) { Text(stringResource(R.string.native_quick_reset)) }
                NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        })
}

@Composable
private fun modeTitle(mode: String): String = when (mode) {
    "warp" -> "WARP"
    "psiphon" -> "Psiphon"
    else -> stringResource(R.string.native_chain_profile_mode)
}

@Composable
internal fun LanQrDialog(link: String, onDismiss: () -> Unit) {
    var bitmap by remember(link) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(link) { mutableStateOf(false) }
    LaunchedEffect(link) {
        bitmap = withContext(Dispatchers.Default) { runCatching { NativeQrImages.create("#profile-title: LAN only\n$link#LAN only") }.getOrNull() }
        failed = bitmap == null
    }
    DisposableEffect(bitmap) { val owned = bitmap; onDispose { owned?.recycle() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.native_quick_qr)) }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.native_quick_qr), Modifier.size(192.dp)) }
                ?: if (failed) Text(stringResource(R.string.native_profile_share_failed)) else CircularProgressIndicator()
            Text(link, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } })
}

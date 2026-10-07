package com.hiddify.hiddify.nativeui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.nativecore.NativeChainOptions
import com.hiddify.hiddify.nativecore.NativeInboundOptions
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails
import com.hiddify.hiddify.nativeprofile.NativeQrImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeQuickSettingsSheet(
    serviceMode: String,
    wifiSharing: Boolean,
    inbound: NativeInboundOptions,
    chain: NativeChainOptions,
    activeProfileName: String,
    busy: Boolean,
    details: NativeWifiSharingDetails,
    detailsBusy: Boolean,
    onServiceMode: (Boolean) -> Unit,
    onLanSharing: (Boolean, String) -> Unit,
    onChain: (NativeChainOptions) -> Unit,
    onOpenChain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var passwordOpen by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable(inbound.lanPassword) { mutableStateOf(inbound.lanPassword) }
    var qrLink by remember { mutableStateOf<String?>(null) }
    fun sharingLink(): String? {
        val host = details.host.trim().removeSurrounding("[", "]")
        if (host.isBlank() || host.any { it.isWhitespace() || it in "@/?#" }) {
            Toast.makeText(context, R.string.native_quick_lan_error, Toast.LENGTH_SHORT).show()
            return null
        }
        val address = if (':' in host) "[$host]" else host
        val credentials = if (inbound.lanPassword.isBlank()) "" else "hiddify:${Uri.encode(inbound.lanPassword)}@"
        return Uri.Builder().scheme("socks").encodedAuthority("$credentials$address:${inbound.mixedPort}").build().toString()
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
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
            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { passwordOpen = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.native_share), null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.native_quick_lan), style = MaterialTheme.typography.bodyLarge)
                    Text(inbound.lanPassword.ifBlank { stringResource(R.string.native_quick_password_empty) },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (wifiSharing || (inbound.allowLan && inbound.mixedEnabled)) {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                            ElevatedButton(enabled = !detailsBusy, onClick = {
                                sharingLink()?.let { link ->
                                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("LAN", link))
                                    Toast.makeText(context, R.string.native_profile_copied, Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(stringResource(R.string.native_quick_copy_link)) }
                            Spacer(Modifier.width(10.dp))
                            ElevatedButton(enabled = !detailsBusy, onClick = { qrLink = sharingLink() }) {
                                Text(stringResource(R.string.native_quick_qr))
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
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !busy, onClick = onOpenChain) { Text(stringResource(R.string.native_quick_chain)) }
                    Text(stringResource(R.string.native_quick_main_profile), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(activeProfileName.ifBlank { stringResource(R.string.native_quick_not_set) },
                        Modifier.height(32.dp).basicMarquee(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(stringResource(R.string.native_quick_final_ip), style = MaterialTheme.typography.labelSmall,
                        color = if (chain.status == "extra_security") androidx.compose.ui.graphics.Color.Transparent
                            else MaterialTheme.colorScheme.primary)
                }
                ChainStage(Modifier.weight(1f), false, chain, busy, onChain, onOpenChain)
            }
        }
    }
    if (passwordOpen) AlertDialog(onDismissRequest = { passwordOpen = false },
        title = { Text(stringResource(R.string.native_quick_password)) }, text = {
            NativeTextField(password, { if (it.length <= 128 && it.none(Char::isISOControl)) password = it }, singleLine = true)
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            onLanSharing(inbound.allowLan, password); passwordOpen = false
        }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = {
            Row {
                TextButton(onClick = { password = "" }) { Text(stringResource(R.string.native_quick_reset)) }
                TextButton(onClick = { passwordOpen = false; password = inbound.lanPassword }) { Text(stringResource(android.R.string.cancel)) }
            }
        })
    qrLink?.let { link -> LanQrDialog(link) { qrLink = null } }
}

@Composable
private fun ChainStage(modifier: Modifier, extra: Boolean, chain: NativeChainOptions, busy: Boolean,
    onChange: (NativeChainOptions) -> Unit, onConfigure: () -> Unit) {
    val stage = if (extra) "extra_security" else "unblocker"
    val enabled = chain.status == stage
    val mode = if (extra) chain.extraMode else chain.unblockerMode
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(if (extra) R.string.native_quick_app else R.string.native_quick_filtering),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(painterResource(R.drawable.native_arrow_up), null, Modifier.size(20.dp)
            .rotate(if (extra) 180f else 0f), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (extra) R.string.native_quick_extra else R.string.native_quick_unblocker),
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(vertical = 2.dp)) {
            Surface(shape = RoundedCornerShape(100.dp),
                color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.height(32.dp).clickable(enabled = !busy) { menuOpen = true }.padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enabled) modeTitle(mode) else stringResource(R.string.native_quick_disable),
                        Modifier.weight(1f, fill = false).basicMarquee(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text("▾", Modifier.padding(start = 4.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.native_quick_disable)) }, onClick = {
                    menuOpen = false; if (enabled) onChange(chain.copy(status = "off"))
                }, enabled = !busy)
                listOf("warp", "psiphon", "profile").forEach { choice ->
                    DropdownMenuItem(text = { Text(modeTitle(choice)) }, onClick = {
                        menuOpen = false
                        onChange(if (extra) chain.copy(status = stage, extraMode = choice)
                            else chain.copy(status = stage, unblockerMode = choice))
                    }, enabled = !busy)
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.native_quick_configuration)) },
                    onClick = { menuOpen = false; onConfigure() }, enabled = !busy)
            }
        }
        Text(stringResource(R.string.native_quick_final_ip), style = MaterialTheme.typography.labelSmall,
            color = if (extra && enabled) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
    }
}

@Composable
private fun modeTitle(mode: String): String = when (mode) {
    "warp" -> "WARP"
    "psiphon" -> "Psiphon"
    else -> stringResource(R.string.native_chain_profile_mode)
}

@Composable
private fun LanQrDialog(link: String, onDismiss: () -> Unit) {
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
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } })
}

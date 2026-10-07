package com.hiddify.hiddify.nativeui

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfileTransfer
import com.hiddify.hiddify.nativeprofile.NativeQrImages
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Options and manual pages from Dart AddProfileModal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeAddProfileSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    onImport: (String, String?, Int?, Boolean) -> Unit,
    onImportFree: (com.hiddify.hiddify.nativeprofile.NativeFreeProfile, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var freeEnabled by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    // File contents can exceed the saved-instance Binder budget. Keep only small drafts there.
    var raw by rememberSaveable(stateSaver = Saver<String, String>(
        save = { it.takeIf { text -> text.toByteArray(Charsets.UTF_8).size <= 64 * 1024 } ?: "" },
        restore = { it },
    )) { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var interval by rememberSaveable { mutableFloatStateOf(0f) }
    var disableAutoUpdate by rememberSaveable { mutableStateOf(false) }
    var fileBusy by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var validate by rememberSaveable { mutableStateOf(false) }
    var qrOptions by rememberSaveable { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val enabled = !busy && !fileBusy

    fun importSource(text: String) {
        raw = text
        if (text.isBlank()) importError = context.getString(R.string.native_profile_file_empty)
        else {
            importError = null
            onImport(text, null, null, false)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileBusy = true
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use(NativeProfileTransfer::readText)
                            ?: throw java.io.FileNotFoundException()
                    }
                    importSource(text)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    importError = context.getString(R.string.native_profile_file_read_failed,
                        error.message ?: error.javaClass.simpleName)
                } finally { fileBusy = false }
            }
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(::importSource)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileBusy = true
            scope.launch {
                try {
                    importSource(withContext(Dispatchers.IO) { NativeQrImages.read(context.contentResolver, uri) })
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    importError = context.getString(R.string.native_profile_qr_not_found)
                } finally { fileBusy = false }
            }
        }
    }
    fun scan() {
        qrOptions = false
        runCatching {
            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(context.getString(R.string.native_profile_qr_prompt))
                .setBeepEnabled(false).setOrientationLocked(false))
        }.onFailure { importError = context.getString(R.string.native_profile_qr_camera_failed) }
    }
    fun paste() {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
        if (text.isNullOrBlank()) importError = context.getString(R.string.native_profile_clipboard_empty)
        else importSource(text.trim())
    }
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull()
    val validUrl = uri != null && (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
        !uri.host.isNullOrBlank() && (uri.port == -1 || uri.port in 1..65535) && raw.none(Char::isWhitespace)

    ModalBottomSheet(
        onDismissRequest = { if (enabled) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 456.dp,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())) {
            if (busy || fileBusy) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.native_profile_adding), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), trackColor = androidx.compose.ui.graphics.Color.Transparent)
                }
            } else if (!manual) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                    val tileSize = ((maxWidth - 48.dp) / 4).coerceAtLeast(64.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AddSourceTile(R.string.native_add_clipboard, R.drawable.native_clipboard, tileSize,
                            Modifier.weight(1f), ::paste)
                        AddSourceTile(R.string.native_add_file, R.drawable.native_file, tileSize,
                            Modifier.weight(1f), { filePicker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) })
                        AddSourceTile(R.string.native_add_scan, R.drawable.native_qr, tileSize,
                            Modifier.weight(1f), { qrOptions = true })
                        AddSourceTile(R.string.native_add_manual, R.drawable.native_add, tileSize,
                            Modifier.weight(1f), { manual = true; importError = null })
                    }
                }
                importError?.let {
                    Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                }
                if (raw.isNotBlank()) TextButton(onClick = { importSource(raw) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.native_profiles_retry))
                }
                if (freeEnabled) NativeFreeProfilesPane(onImportFree)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.native_free_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Switch(freeEnabled, { freeEnabled = it })
                    Spacer(Modifier.weight(1f))
                    AssistChip(onClick = { helpOpen = true }, label = { Text(stringResource(R.string.native_add_help)) },
                        leadingIcon = { Icon(painterResource(R.drawable.native_info), null, Modifier.size(18.dp)) })
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.native_add_manual), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = { manual = false; validate = false }) {
                        Icon(painterResource(R.drawable.native_close), stringResource(R.string.native_back))
                    }
                }
                NativeTextField(name, { name = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text(stringResource(R.string.native_profile_name)) }, singleLine = true,
                    isError = validate && name.isBlank(), supportingText = if (validate && name.isBlank()) {
                        { Text(stringResource(R.string.native_profile_name_required)) }
                    } else null)
                Spacer(Modifier.height(16.dp))
                NativeTextField(raw, { raw = it; importError = null }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text(stringResource(R.string.native_profile_url)) }, singleLine = true,
                    isError = validate && !validUrl, supportingText = if (validate && !validUrl) {
                        { Text(stringResource(R.string.native_profile_invalid_url)) }
                    } else null)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.native_profile_disable_update), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(disableAutoUpdate, { disableAutoUpdate = it })
                }
                AnimatedVisibility(!disableAutoUpdate) {
                    Column {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.native_profile_update_interval), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(profileIntervalLabel(interval.toInt()), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Slider(interval, { interval = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            valueRange = 0f..96f, steps = 95)
                    }
                }
                importError?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    validate = true
                    if (name.isNotBlank() && validUrl) onImport(raw.trim(), name.trim(), interval.toInt(), disableAutoUpdate)
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.native_profile_add))
                }
            }
        }
    }
    if (qrOptions) AlertDialog(onDismissRequest = { qrOptions = false },
        title = { Text(stringResource(R.string.native_profile_scan_qr)) },
        text = { Text(stringResource(R.string.native_profile_qr_prompt)) },
        confirmButton = { TextButton(onClick = ::scan) { Text(stringResource(R.string.native_add_scan)) } },
        dismissButton = { TextButton(onClick = { qrOptions = false; imagePicker.launch(arrayOf("image/*")) }) {
            Text(stringResource(R.string.native_profile_qr_image))
        } })
    if (helpOpen) AlertDialog(onDismissRequest = { helpOpen = false },
        title = { Text(stringResource(R.string.native_profile_help_title)) },
        text = { Text(stringResource(R.string.native_profile_help_message)) },
        confirmButton = { TextButton(onClick = { helpOpen = false }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://hiddify.com/manager/"))) }
                .onFailure { importError = context.getString(R.string.native_profile_share_failed) }
        }) { Text(stringResource(R.string.native_profile_help_link)) } })
}

@Composable
private fun AddSourceTile(label: Int, icon: Int, height: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = height), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(label), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun profileIntervalLabel(hours: Int): String = when {
    hours == 0 -> stringResource(R.string.native_profile_interval_auto)
    hours < 24 -> stringResource(R.string.native_profile_interval_hours, hours)
    else -> stringResource(R.string.native_profile_interval_days_hours, hours / 24, hours % 24)
}

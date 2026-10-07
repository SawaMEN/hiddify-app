package com.hiddify.hiddify.nativeui

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import com.hiddify.hiddify.nativeui.NativeButton as Button
import com.hiddify.hiddify.nativeui.NativeCard as Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
import com.hiddify.hiddify.nativeui.NativeTextField as OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfileTransfer
import com.hiddify.hiddify.nativeprofile.NativeQrImages
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.hiddify.hiddify.nativeprofile.NativeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun NativeProfilesScreen(
    profiles: List<NativeProfile>,
    busyProfileId: String?,
    onBack: () -> Unit,
    onSelect: (NativeProfile) -> Unit,
    onDelete: (NativeProfile) -> Unit,
    onRefresh: (NativeProfile) -> Unit,
    onEdit: (NativeProfile) -> Unit,
    onImport: (raw: String, name: String?, intervalHours: Int?, disableAutoUpdate: Boolean) -> Unit,
    onCopyConfig: (NativeProfile) -> Unit,
    onExportConfig: (NativeProfile) -> Unit,
) {
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var shareCandidate by remember { mutableStateOf<NativeProfile?>(null) }
    var deleteCandidate by remember { mutableStateOf<NativeProfile?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.native_profiles),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { addOpen = true }, enabled = busyProfileId == null) {
                    Text(stringResource(R.string.native_profile_add))
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.native_back))
                }
            }
        }

        if (profiles.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.native_profiles_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.native_profiles_empty_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { addOpen = true }, enabled = busyProfileId == null) {
                        Text(stringResource(R.string.native_profile_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        busy = busyProfileId != null,
                        onShare = { shareCandidate = profile },
                        onSelect = { onSelect(profile) },
                        onDelete = { deleteCandidate = profile },
                        onRefresh = { onRefresh(profile) },
                        onEdit = { onEdit(profile) },
                    )
                }
            }
        }
    }

    if (addOpen) {
        AddProfileDialog(
            onDismiss = { addOpen = false },
            onImport = { raw, name, interval, disabled ->
                onImport(raw, name, interval, disabled)
                addOpen = false
            },
        )
    }

    shareCandidate?.let { profile ->
        NativeProfileShareDialog(
            profile = profile,
            onDismiss = { shareCandidate = null },
            onCopyConfig = {
                onCopyConfig(profile)
                shareCandidate = null
            },
            onExportConfig = {
                onExportConfig(profile)
                shareCandidate = null
            },
        )
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.native_profile_delete_title)) },
            text = { Text(stringResource(R.string.native_profile_delete_message, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(profile)
                        deleteCandidate = null
                    },
                ) {
                    Text(stringResource(R.string.native_profile_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: NativeProfile,
    busy: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (profile.active) {
                                stringResource(R.string.native_profile_active)
                            } else if (profile.isRemote) {
                                stringResource(R.string.native_profile_remote)
                            } else {
                                stringResource(R.string.native_profile_local)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (profile.active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                if (!profile.active) {
                    OutlinedButton(onClick = onSelect, enabled = !busy) {
                        Text(stringResource(R.string.native_profile_use))
                    }
                }
            }

            if (profile.isRemote && !profile.url.isNullOrBlank()) {
                Text(
                    text = profile.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val total = profile.total
            val consumed = profile.consumed
            if (total != null && consumed != null) {
                val remaining = (total - consumed).coerceAtLeast(0)
                Text(
                    text =
                        stringResource(
                            R.string.native_profile_traffic,
                            formatBytes(consumed),
                            formatBytes(total),
                            formatBytes(remaining),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onEdit, enabled = !busy) {
                    Text(stringResource(R.string.native_profile_edit))
                }
                if (profile.isRemote) {
                    TextButton(onClick = onRefresh, enabled = !busy) {
                        Text(
                            if (busy) {
                                stringResource(R.string.native_profile_updating)
                            } else {
                                stringResource(R.string.native_profile_update)
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onShare, enabled = !busy) {
                    Text(stringResource(R.string.native_profile_share))
                }
                TextButton(onClick = onDelete, enabled = !busy) {
                    Text(stringResource(R.string.native_profile_delete))
                }
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onImport: (String, String?, Int?, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // QR/link drafts survive the scanner Activity. Large imported files must not go into the
    // saved-instance Bundle, whose Binder limit is smaller than our profile size limit.
    var raw by rememberSaveable(
        stateSaver = Saver<String, String>(
            save = { it.takeIf { value -> value.toByteArray(Charsets.UTF_8).size <= 64 * 1024 } ?: "" },
            restore = { it },
        ),
    ) { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var interval by rememberSaveable { mutableStateOf("") }
    var disableAutoUpdate by rememberSaveable { mutableStateOf(false) }
    var fileBusy by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            fileBusy = true
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)
                                ?.use(NativeProfileTransfer::readText)
                                ?: throw java.io.FileNotFoundException(uri.toString())
                        }
                    }
                fileBusy = false
                result.fold(
                    onSuccess = { text ->
                        if (text.isBlank()) {
                            importError = context.getString(R.string.native_profile_file_empty)
                        } else {
                            raw = text
                            importError = null
                        }
                    },
                    onFailure = { error ->
                        importError =
                            context.getString(
                                R.string.native_profile_file_read_failed,
                                error.message ?: error.javaClass.simpleName,
                            )
                    },
                )
            }
        }

    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            raw = result.contents
            importError = null
        }
    }
    val qrImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileBusy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { NativeQrImages.read(context.contentResolver, uri) }
                }
                fileBusy = false
                result.fold(
                    onSuccess = {
                        raw = it
                        importError = null
                    },
                    onFailure = { importError = context.getString(R.string.native_profile_qr_not_found) },
                )
            }
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val text =
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context)?.toString()
            } else {
                null
            }

        if (text.isNullOrBlank()) {
            importError = context.getString(R.string.native_profile_clipboard_empty)
        } else {
            raw = text.trim()
            importError = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.native_profile_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = ::pasteFromClipboard,
                        enabled = !fileBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.native_profile_paste_clipboard))
                    }
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !fileBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.native_profile_choose_file))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !fileBusy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runCatching {
                                qrScanner.launch(
                                    ScanOptions()
                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt(context.getString(R.string.native_profile_qr_prompt))
                                        .setBeepEnabled(false)
                                        .setOrientationLocked(false),
                                )
                            }.onFailure {
                                importError = context.getString(R.string.native_profile_qr_camera_failed)
                            }
                        },
                    ) { Text(stringResource(R.string.native_profile_scan_qr)) }
                    OutlinedButton(
                        enabled = !fileBusy,
                        modifier = Modifier.weight(1f),
                        onClick = { qrImagePicker.launch(arrayOf("image/*")) },
                    ) { Text(stringResource(R.string.native_profile_qr_image)) }
                }

                OutlinedTextField(
                    value = raw,
                    onValueChange = {
                        raw = it
                        importError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.native_profile_url_or_content)) },
                    supportingText = { Text(stringResource(R.string.native_profile_url_or_content_hint)) },
                    minLines = 3,
                    maxLines = 8,
                )

                importError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.native_profile_name_optional)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.native_profile_disable_update))
                        Text(
                            text = stringResource(R.string.native_profile_disable_update_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = disableAutoUpdate,
                        onCheckedChange = { disableAutoUpdate = it },
                    )
                }
                if (!disableAutoUpdate) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit) && value.length <= 4) interval = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.native_profile_update_interval)) },
                        supportingText = { Text(stringResource(R.string.native_profile_update_interval_hint)) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = raw.isNotBlank() && !fileBusy,
                onClick = {
                    onImport(
                        raw,
                        name.trim().takeIf { it.isNotEmpty() },
                        interval.toIntOrNull()?.takeIf { it > 0 },
                        disableAutoUpdate,
                    )
                },
            ) {
                Text(stringResource(R.string.native_profile_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun formatBytes(value: Long): String {
    if (value <= 0L) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    val group = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val scaled = value / 1024.0.pow(group.toDouble())
    return if (scaled >= 100 || group == 0) {
        "%.0f %s".format(scaled, units[group])
    } else {
        "%.1f %s".format(scaled, units[group])
    }
}

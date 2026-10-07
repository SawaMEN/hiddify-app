package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfileEditor
import com.hiddify.hiddify.nativeprofile.NativeProfileEditorSession
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun NativeProfileDetailsScreen(
    editor: NativeProfileEditor?,
    session: NativeProfileEditorSession,
    busy: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSave: (name: String, disableAutoUpdate: Boolean, updateIntervalHours: Int?, content: String) -> Unit,
) {
    LaunchedEffect(editor) { editor?.let(session::load) }
    var jsonValid by remember(editor?.profile?.id, editor?.profile?.lastUpdate) { mutableStateOf(editor?.isJson != true) }
    var validateName by remember { mutableStateOf(false) }
    val ready = editor != null && session.identity == "${editor.profile.id}:${editor.profile.lastUpdate}"
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(painterResource(R.drawable.native_back_arrow), stringResource(R.string.native_back))
            }
            Text(stringResource(R.string.native_profile_details), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = ready && !busy && session.changed && jsonValid && session.content.isNotBlank(), onClick = {
                validateName = true
                if (session.name.isNotBlank()) onSave(session.name.trim(), session.disableAutoUpdate,
                    session.intervalHours.takeIf { it > 0 }, session.content)
            }) {
                Icon(painterResource(R.drawable.native_check), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.native_profile_save))
            }
            Spacer(Modifier.width(8.dp))
        }
        if (!ready) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (loadFailed) {
                    Text(stringResource(R.string.native_editor_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.native_profiles_retry)) }
                } else CircularProgressIndicator()
            }
        } else if (editor != null) {
            val profile = editor.profile
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()) {
                NativeTextField(session.name, { session.name = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    enabled = !busy, singleLine = true, label = { Text(stringResource(R.string.native_profile_name)) },
                    isError = validateName && session.name.isBlank(), supportingText = if (validateName && session.name.isBlank()) {
                        { Text(stringResource(R.string.native_profile_name_required)) }
                    } else null)
                if (profile.isRemote) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.native_profile_url), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer { Text(profile.url.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                if (profile.isRemote) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.native_profile_disable_update), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Switch(session.disableAutoUpdate, { session.disableAutoUpdate = it }, enabled = !busy)
                    }
                    AnimatedVisibility(!session.disableAutoUpdate) {
                        Column {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.native_profile_update_interval), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text(profileIntervalLabel(session.intervalHours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Existing subscription intervals above 96 hours must remain intact when opening the form.
                            val maximum = maxOf(96, editor.updateIntervalHours ?: 0).toFloat()
                            Slider(session.intervalHours.toFloat().coerceIn(0f, maximum),
                                { session.intervalHours = it.toInt() },
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp), enabled = !busy,
                                valueRange = 0f..maximum, steps = if (maximum <= 1000) maximum.toInt() - 1 else 0)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                ListItem(headlineContent = { Text(stringResource(R.string.native_editor_last_update)) },
                    supportingContent = { Text(editorDate(profile.lastUpdate)) },
                    leadingContent = { Icon(painterResource(R.drawable.native_history), null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                if (profile.isRemote && profile.upload != null && profile.download != null && profile.total != null) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.native_editor_traffic, editorBytes(profile.upload), editorBytes(profile.download), editorBytes(profile.total)), style = MaterialTheme.typography.bodySmall)
                        profile.expire?.let { Text(stringResource(R.string.native_editor_expiry, editorDate(it)), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                HorizontalDivider()
                val editorHeight = LocalConfiguration.current.screenHeightDp.dp * .7f
                if (editor.isJson) NativeJsonEditor(session.content, !busy, { session.content = it }, { jsonValid = it }, Modifier.fillMaxWidth().height(editorHeight))
                else BasicTextField(session.content, { session.content = it }, Modifier.fillMaxWidth().height(editorHeight).padding(start = 5.dp, top = 8.dp, bottom = 8.dp),
                    enabled = !busy, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp))
            }
        }
    }
}

private fun editorDate(value: String): String {
    val instant = runCatching { Instant.parse(value) }.recoverCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
    return if (instant == null) value else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(instant))
}

private fun editorBytes(bytes: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var value = bytes.toDouble(); var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return String.format(Locale.getDefault(), if (unit == 0) "%.0f %s" else "%.2f %s", value, units[unit])
}

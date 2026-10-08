package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativelog.NativeLogPresentation
import com.hiddify.hiddify.nativelog.NativeLogSnapshot

@Composable
fun NativeLogsScreen(
    snapshot: NativeLogSnapshot,
    busy: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    sharingEnabled: Boolean,
    onShare: (Boolean) -> Unit,
    onPollingChanged: (Boolean) -> Unit = {},
) {
    var paused by rememberSaveable { mutableStateOf(false) }
    val pollingChanged by rememberUpdatedState(onPollingChanged)
    DisposableEffect(paused) {
        pollingChanged(!paused)
        onDispose { pollingChanged(false) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var sharingOpen by remember { mutableStateOf(false) }
    var levelsOpen by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf(snapshot) }
    LaunchedEffect(snapshot, paused) { if (!paused) frozen = snapshot }
    val displayed = if (paused) frozen else snapshot
    val lines = remember(displayed.lines, query, level) { NativeLogPresentation.visible(displayed.lines, query, level) }
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_logs_title), onBack) {
            IconButton(onClick = { frozen = snapshot; paused = !paused }) {
                Icon(painterResource(if (paused) R.drawable.native_log_play else R.drawable.native_log_pause),
                    stringResource(if (paused) R.string.native_logs_resume else R.string.native_logs_pause),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { frozen = NativeLogSnapshot(emptyList(), emptyList()); onClear() }, enabled = !busy) {
                Icon(painterResource(R.drawable.native_log_clear), stringResource(R.string.native_logs_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sharingEnabled) Box {
                IconButton(onClick = { sharingOpen = true }, enabled = !busy) {
                    Icon(painterResource(R.drawable.native_more), stringResource(R.string.native_logs_share), Modifier.size(24.dp))
                }
                DropdownMenu(sharingOpen, { sharingOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.native_logs_share_core)) },
                        onClick = { sharingOpen = false; onShare(false) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.native_logs_share_app)) },
                        onClick = { sharingOpen = false; onShare(true) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NativeTextField(query, { query = it.take(256) }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text(stringResource(R.string.native_logs_filter)) },
                leadingIcon = { Icon(painterResource(R.drawable.native_search), null) })
            Box {
                NativeTextButton(onClick = { levelsOpen = true }) {
                    Text(level ?: stringResource(R.string.native_logs_all))
                    Icon(painterResource(R.drawable.native_drop_down), null, Modifier.size(20.dp))
                }
                DropdownMenu(levelsOpen, { levelsOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.native_logs_all)) },
                        onClick = { level = null; levelsOpen = false })
                    NativeLogPresentation.levels.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice) }, onClick = { level = choice; levelsOpen = false })
                    }
                }
            }
        }
        if (lines.isEmpty()) {
            Text(stringResource(if (displayed.lines.isEmpty()) R.string.native_logs_empty else R.string.native_logs_no_matches),
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), reverseLayout = true) {
                itemsIndexed(lines.asReversed(), key = { index, line -> "$index:${line.hashCode()}" }) { _, line ->
                    val entry = remember(line) { NativeLogPresentation.entry(line) }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (entry.level != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.level, style = MaterialTheme.typography.labelMedium, color = logLevelColor(entry.level))
                            entry.time?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        }
                        Text(entry.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (line != lines.firstOrNull()) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun logLevelColor(level: String): androidx.compose.ui.graphics.Color {
    val scheme = MaterialTheme.colorScheme
    return when (level) {
        "TRACE" -> scheme.primary
        "DEBUG" -> scheme.onSurfaceVariant
        "INFO" -> scheme.tertiary
        "WARN" -> if (scheme.onSurface.red > .7f) androidx.compose.ui.graphics.Color(0xFFFFC857)
            else androidx.compose.ui.graphics.Color(0xFF805500)
        else -> scheme.error
    }
}

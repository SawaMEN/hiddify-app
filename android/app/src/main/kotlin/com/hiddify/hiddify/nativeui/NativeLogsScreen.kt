package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
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
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    var paused by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var levelsOpen by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf(snapshot) }
    LaunchedEffect(snapshot, paused) { if (!paused) frozen = snapshot }
    val displayed = if (paused) frozen else snapshot
    val lines = remember(displayed.lines, query, level) { NativeLogPresentation.visible(displayed.lines, query, level) }
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_logs_title), onBack) {
            IconButton(onClick = { frozen = snapshot; paused = !paused }) {
                Icon(painterResource(if (paused) R.drawable.native_play else R.drawable.native_pause),
                    stringResource(if (paused) R.string.native_logs_resume else R.string.native_logs_pause),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(painterResource(R.drawable.native_refresh), stringResource(R.string.native_logs_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { frozen = NativeLogSnapshot(emptyList(), emptyList()); onClear() }, enabled = !busy) {
                Icon(painterResource(R.drawable.native_delete), stringResource(R.string.native_logs_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NativeTextField(query, { query = it.take(256) }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text(stringResource(R.string.native_logs_filter)) },
                leadingIcon = { Icon(painterResource(R.drawable.native_search), null) })
            Box {
                TextButton(onClick = { levelsOpen = true }) {
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
                    Text(line, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                        color = if (NativeLogPresentation.level(line) in setOf("ERROR", "FATAL", "PANIC"))
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.PerAppProxyMode
import com.hiddify.hiddify.nativerouting.NativeInstalledApp
import com.hiddify.hiddify.nativerouting.NativePerAppBackup
import com.hiddify.hiddify.nativerouting.NativePerAppFlags
import com.hiddify.hiddify.nativerouting.NativePerAppSnapshot
import kotlinx.coroutines.launch

@Composable
fun NativePerAppScreen(snapshot: NativePerAppSnapshot, canChange: Boolean, busy: Boolean,
    onBack: () -> Unit, onModeChanged: (String) -> Unit, onTogglePackage: (String) -> Unit,
    onClear: () -> Unit, onRetry: () -> Unit, pendingImport: NativePerAppBackup?,
    onImportClipboard: () -> Unit, onImportFile: () -> Unit, onExportClipboard: () -> Unit,
    onExportFile: () -> Unit, onConfirmImport: () -> Unit, onDismissImport: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var hideSystem by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var modeMenu by remember { mutableStateOf(false) }
    var closeWhenOff by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val filtered = remember(snapshot.apps, snapshot.flags, search, hideSystem) {
        val visible = snapshot.apps.filter { !hideSystem || !it.system }
        if (search.isBlank()) visible.sortedBy { NativePerAppFlags.priority(snapshot.flags[it.packageName]) }
        else visible.filter { it.label.contains(search, ignoreCase = true) }
    }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    LaunchedEffect(snapshot.mode, busy) {
        if (closeWhenOff && snapshot.mode == PerAppProxyMode.OFF && !busy) { closeWhenOff = false; onBack() }
    }
    val enabled = canChange && !busy
    Column(Modifier.fillMaxSize()) {
        if (searching) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { search = ""; searching = false }) {
                Icon(painterResource(R.drawable.native_close), stringResource(android.R.string.cancel))
            }
            Box(Modifier.weight(1f).padding(end = 16.dp)) {
                if (search.isEmpty()) Text(stringResource(R.string.native_per_app_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(search, { search = it.take(256) }, Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface))
            }
        } else {
            NativePageHeader(stringResource(R.string.native_per_app_title), onBack) {
                IconButton(onClick = { searching = true }) {
                    Icon(painterResource(R.drawable.native_search), stringResource(R.string.native_per_app_search), Modifier.size(24.dp))
                }
                Box {
                    if (busy) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
                    else IconButton(onClick = { menu = "main" }) {
                        Icon(painterResource(R.drawable.native_per_app_more), stringResource(R.string.native_per_app_actions), Modifier.size(24.dp))
                    }
                    DropdownMenu(menu != null, { menu = null }) {
                        when (menu) {
                            "import" -> {
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_per_app_import_clipboard)) }, enabled = enabled,
                                    onClick = { menu = null; onImportClipboard() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_per_app_import_file)) }, enabled = enabled,
                                    onClick = { menu = null; onImportFile() })
                            }
                            "export" -> {
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_per_app_export_clipboard)) }, onClick = { menu = null; onExportClipboard() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_per_app_export_file)) }, onClick = { menu = null; onExportFile() })
                            }
                            else -> {
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_import_confirm)) }, onClick = { menu = "import" })
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_per_app_export)) }, onClick = { menu = "export" })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_per_app_clear_all)) }, enabled = enabled && snapshot.mode != PerAppProxyMode.OFF,
                                    onClick = { menu = null; onClear() })
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.clickable(enabled = enabled, onClick = { modeMenu = true }).padding(start = 16.dp, end = 8.dp).heightIn(min = 32.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(modeTitle(snapshot.mode)))
                            Spacer(Modifier.width(4.dp))
                            Icon(painterResource(R.drawable.native_drop_down), null, Modifier.size(24.dp))
                        }
                    }
                    DropdownMenu(modeMenu, { modeMenu = false }) {
                        listOf(PerAppProxyMode.OFF, PerAppProxyMode.INCLUDE, PerAppProxyMode.EXCLUDE).forEach { mode ->
                            DropdownMenuItem(text = { Text(stringResource(modeTitle(mode))) }, enabled = enabled,
                                onClick = { modeMenu = false; if (mode != snapshot.mode) { closeWhenOff = mode == PerAppProxyMode.OFF; onModeChanged(mode) } })
                        }
                    }
                }
                FilterChip(hideSystem, { hideSystem = !hideSystem }, label = { Text(stringResource(R.string.native_per_app_hide_system)) }, shape = RoundedCornerShape(8.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !snapshot.loaded && busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !snapshot.loaded -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.native_settings_load_failed), color = MaterialTheme.colorScheme.error)
                    NativeTextButton(onRetry) { Text(stringResource(R.string.native_profiles_retry)) }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        PerAppRow(app, snapshot.flags[app.packageName], enabled && snapshot.mode != PerAppProxyMode.OFF) { onTogglePackage(app.packageName) }
                    }
                }
            }
            if (list.firstVisibleItemIndex > 4 || list.firstVisibleItemScrollOffset > 300) FloatingActionButton(
                onClick = { scope.launch { list.animateScrollToItem(0) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                shape = RoundedCornerShape(24.dp), elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) {
                Icon(painterResource(R.drawable.native_arrow_up), stringResource(R.string.native_per_app_scroll_top), Modifier.size(24.dp))
            }
        }
    }
    NativePerAppImportDialog(pendingImport, busy, canChange, onConfirmImport, onDismissImport)
}

private fun modeTitle(mode: String) = when (mode) {
    PerAppProxyMode.INCLUDE -> R.string.native_per_app_include
    PerAppProxyMode.EXCLUDE -> R.string.native_per_app_exclude
    else -> R.string.native_per_app_all
}

@Composable
private fun PerAppRow(app: NativeInstalledApp, flags: Int?, enabled: Boolean, onToggle: () -> Unit) {
    val state = when (flags?.let(NativePerAppFlags::checkboxValue) ?: if (flags == null) false else null) {
        true -> ToggleableState.On
        false -> ToggleableState.Off
        null -> ToggleableState.Indeterminate
    }
    Row(Modifier.fillMaxWidth().triStateToggleable(state, enabled = enabled, role = Role.Checkbox, onClick = onToggle)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NativeInstalledAppIcon(app.packageName)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (flags != null && flags and NativePerAppFlags.FORCE_DESELECTION != 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                }
            }
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TriStateCheckbox(state, onClick = null, enabled = enabled)
    }
}

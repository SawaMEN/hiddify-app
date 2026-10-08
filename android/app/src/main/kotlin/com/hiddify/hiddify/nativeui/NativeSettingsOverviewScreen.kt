package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

/** SettingsPage's mobile card list and MenuAnchor actions, before Flutter removal. */
@Composable
internal fun NativeSettingsOverviewScreen(
    hasProfiles: Boolean,
    busy: Boolean,
    onOpenProfiles: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenChain: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenInbound: () -> Unit,
    onOpenTls: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportFile: () -> Unit,
    onExportClipboard: (Boolean) -> Unit,
    onExportFile: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var submenu by rememberSaveable { mutableStateOf("") }
    var pendingImport by rememberSaveable { mutableStateOf("") }
    fun dismissMenu() { menuOpen = false; submenu = "" }
    fun action(block: () -> Unit) { dismissMenu(); if (!busy) block() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.native_settings), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Box {
                IconButton(onClick = { if (menuOpen) dismissMenu() else menuOpen = true }) {
                    Icon(painterResource(R.drawable.settings_more), stringResource(R.string.native_settings_page_menu),
                        Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = ::dismissMenu) {
                    SettingsSubmenu(R.string.native_settings_page_import, submenu == "import", !busy,
                        onOpen = { submenu = if (submenu == "import") "" else "import" }, onDismiss = { submenu = "" }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_settings_page_import_clipboard)) },
                            enabled = !busy, onClick = { action { pendingImport = "clipboard" } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_settings_page_import_file)) },
                            enabled = !busy, onClick = { action { pendingImport = "file" } })
                    }
                    SettingsSubmenu(R.string.native_settings_page_export, submenu == "export", !busy,
                        onOpen = { submenu = if (submenu == "export") "" else "export" }, onDismiss = { submenu = "" }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_settings_page_export_anonymous_clipboard)) },
                            enabled = !busy, onClick = { action { onExportClipboard(false) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_settings_page_export_anonymous_file)) },
                            enabled = !busy, onClick = { action { onExportFile(false) } })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_settings_page_export_all_clipboard)) },
                            enabled = !busy, onClick = { action { onExportClipboard(true) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_settings_page_export_all_file)) },
                            enabled = !busy, onClick = { action { onExportFile(true) } })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.native_settings_page_reset)) }, enabled = !busy,
                        onClick = { action(onReset) })
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp)) {
            SettingsSection(R.string.native_settings_page_profiles, R.drawable.settings_view_list, onOpenProfiles)
            SettingsSection(R.string.native_settings_page_general, R.drawable.settings_layers, onOpenGeneral)
            if (hasProfiles) SettingsSection(R.string.native_settings_page_chain, R.drawable.settings_webhook, onOpenChain,
                R.string.native_settings_page_chain_summary)
            SettingsSection(R.string.native_settings_page_dns, R.drawable.settings_dns, onOpenDns)
            SettingsSection(R.string.native_settings_page_inbound, R.drawable.settings_input, onOpenInbound)
            SettingsSection(R.string.native_settings_page_tls, R.drawable.settings_cut, onOpenTls)
            SettingsSection(R.string.native_settings_page_logs, R.drawable.settings_description, onOpenLogs)
            SettingsSection(R.string.native_settings_page_about, R.drawable.settings_info, onOpenAbout)
        }
    }
    if (pendingImport.isNotEmpty()) AlertDialog(
        onDismissRequest = { pendingImport = "" },
        title = { Text(stringResource(R.string.native_settings_page_confirm_import)) },
        text = { Text(stringResource(R.string.native_settings_page_confirm_import_message)) },
        confirmButton = { NativeTextButton(enabled = !busy, onClick = {
            val fromFile = pendingImport == "file"
            pendingImport = ""
            if (fromFile) onImportFile() else onImportClipboard()
        }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { NativeTextButton(onClick = { pendingImport = "" }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun SettingsSection(title: Int, icon: Int, onClick: () -> Unit, summary: Int? = null) {
    NativeSurface(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = if (summary == null) 72.dp else 88.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                if (summary != null) Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.settings_chevron), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSubmenu(title: Int, expanded: Boolean, enabled: Boolean, onOpen: () -> Unit,
    onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var width by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { width = it.width; height = it.height }) {
        DropdownMenuItem(text = { Text(stringResource(title)) }, enabled = enabled, onClick = onOpen,
            trailingIcon = { Icon(painterResource(R.drawable.settings_chevron), null, Modifier.size(24.dp)) })
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
            offset = with(density) { DpOffset(width.toDp(), -height.toDp()) }, content = content)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Settings · light", widthDp = 390, heightDp = 844)
@androidx.compose.ui.tooling.preview.Preview(name = "Settings · dark", widthDp = 390, heightDp = 844,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsOverviewPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.SYSTEM) {
        NativeAtmosphere {
            NativeSettingsOverviewScreen(
                hasProfiles = true, busy = false, onOpenProfiles = {}, onOpenGeneral = {},
                onOpenChain = {}, onOpenDns = {}, onOpenInbound = {}, onOpenTls = {},
                onOpenLogs = {}, onOpenAbout = {}, onImportClipboard = {}, onImportFile = {},
                onExportClipboard = {}, onExportFile = {}, onReset = {},
            )
        }
    }
}

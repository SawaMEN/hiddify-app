package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

@Composable
fun NativeAboutScreen(
    versionName: String,
    versionCode: Int,
    updateChecking: Boolean,
    updateMessage: String?,
    updateUrl: String?,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenFork: () -> Unit,
    onOpenUpstream: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onCopyAppInfo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var dismissedUpdate by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_about_title), onBack) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.native_more), stringResource(R.string.native_about_actions), Modifier.size(24.dp))
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.native_about_copy_info)) },
                        onClick = { menuOpen = false; onCopyAppInfo() })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.vetroff_app_logo), null, Modifier.size(64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.native_about_version, versionName, versionCode),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.native_about_fork_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.native_about_fork_description), style = MaterialTheme.typography.bodyMedium)
                }
            }
            AboutLink(stringResource(R.string.native_about_check_update), onClick = {
                dismissedUpdate = null
                onCheckUpdate()
            }, enabled = !updateChecking, icon = R.drawable.native_about_sync, busy = updateChecking)
            if (updateUrl == null) updateMessage?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            AboutLink(stringResource(R.string.native_about_source), "SawaMEN/hiddify-app", onOpenFork)
            AboutLink(stringResource(R.string.native_about_upstream), "hiddify/hiddify-app", onOpenUpstream)
            AboutLink(stringResource(R.string.native_about_terms), onClick = onOpenTerms)
            AboutLink(stringResource(R.string.native_about_privacy), onClick = onOpenPrivacy)
        }
    }
    if (updateUrl != null && dismissedUpdate != updateUrl) {
        AlertDialog(onDismissRequest = { dismissedUpdate = updateUrl },
            title = { Text(stringResource(R.string.native_about_updates)) },
            text = { Text(updateMessage ?: stringResource(R.string.native_about_open_release)) },
            confirmButton = { NativeTextButton(onClick = { dismissedUpdate = updateUrl; onOpenUpdate() }) {
                Text(stringResource(R.string.native_about_open_release))
            } },
            dismissButton = { NativeTextButton(onClick = { dismissedUpdate = updateUrl }) {
                Text(stringResource(android.R.string.cancel))
            } })
    }
}

@Composable
private fun AboutLink(
    text: String,
    subtext: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: Int = R.drawable.native_about_open,
    busy: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = if (subtext != null) { { Text(subtext) } } else null,
        trailingContent = {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(painterResource(icon), null, Modifier.size(24.dp))
        },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    updateState: com.hiddify.hiddify.nativeupdate.NativeAppUpdateState,
    onUpdateChannel: (com.hiddify.hiddify.nativeupdate.NativeUpdateChannel) -> Unit,
    onAutoDownload: (Boolean) -> Unit,
    onCancelUpdate: () -> Unit,
    onSkipUpdate: () -> Unit,
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
            NativeAppUpdateCard(updateState, onCheckUpdate, onOpenUpdate, onCancelUpdate, onSkipUpdate,
                onUpdateChannel, onAutoDownload)
            HorizontalDivider()
            AboutLink(stringResource(R.string.native_about_source), "SawaMEN/hiddify-app", onOpenFork)
            AboutLink(stringResource(R.string.native_about_upstream), "hiddify/hiddify-app", onOpenUpstream)
            AboutLink(stringResource(R.string.native_about_terms), onClick = onOpenTerms)
            AboutLink(stringResource(R.string.native_about_privacy), onClick = onOpenPrivacy)
        }
    }
}

@Composable
private fun AboutLink(
    text: String,
    subtext: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = if (subtext != null) { { Text(subtext) } } else null,
        trailingContent = {
            Icon(painterResource(R.drawable.native_about_open), null, Modifier.size(24.dp))
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

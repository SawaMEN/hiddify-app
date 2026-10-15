package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeupdate.*

@Composable
internal fun NativeAppUpdateCard(
    state: NativeAppUpdateState,
    onCheck: () -> Unit,
    onAction: () -> Unit,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
    onChannel: (NativeUpdateChannel) -> Unit,
    onAutoDownload: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.native_about_updates), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.native_update_channel))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeUpdateChannel.entries.forEach { channel ->
                    FilterChip(selected = state.channel == channel, onClick = { onChannel(channel) }, enabled = !state.busy,
                        label = { Text(stringResource(if (channel == NativeUpdateChannel.STABLE)
                            R.string.native_update_stable else R.string.native_update_dev)) })
                }
            }
            Text(stringResource(R.string.native_update_channel_hint), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.native_update_auto_download), Modifier.weight(1f))
                Switch(checked = state.autoDownload, onCheckedChange = onAutoDownload)
            }
            val release = state.release
            if (release != null) {
                Text(stringResource(R.string.native_about_update_available, release.version))
                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(release.publishedAt)), style = MaterialTheme.typography.bodySmall)
            }
            if (state.skipped) Text(stringResource(R.string.native_update_skipped), style = MaterialTheme.typography.bodySmall)
            when (state.phase) {
                NativeUpdatePhase.CHECKING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.native_update_checking))
                }
                NativeUpdatePhase.DOWNLOADING -> {
                    val progress = (state.downloadedBytes.toFloat() / state.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.native_update_progress, (progress * 100).toInt(),
                        state.downloadedBytes / (1024 * 1024), state.totalBytes / (1024 * 1024)))
                    Text(stringResource(if (state.waiting) R.string.native_update_waiting else R.string.native_update_background),
                        style = MaterialTheme.typography.bodySmall)
                }
                NativeUpdatePhase.VERIFYING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.native_update_verifying))
                }
                NativeUpdatePhase.READY -> Text(stringResource(R.string.native_update_ready, release?.version.orEmpty()))
                NativeUpdatePhase.IDLE -> Text(stringResource(R.string.native_about_update_current))
                NativeUpdatePhase.FAILED -> Text(stringResource(R.string.native_about_update_error, state.error.orEmpty()),
                    color = MaterialTheme.colorScheme.error)
                NativeUpdatePhase.AVAILABLE -> Unit
            }
            if (release != null && !state.busy) NativeButton(onAction, Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.phase == NativeUpdatePhase.READY) R.string.native_update_install else R.string.native_update_download))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.downloading) NativeTextButton(onClick = onCancel) { Text(stringResource(R.string.native_update_cancel_download)) }
                else if (state.phase != NativeUpdatePhase.READY) NativeTextButton(onClick = onCheck, enabled = !state.busy) {
                    Icon(painterResource(R.drawable.native_about_sync), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.native_about_check_update))
                }
                if (release != null && !state.skipped) NativeTextButton(onClick = onSkip, enabled = state.phase != NativeUpdatePhase.VERIFYING) {
                    Text(stringResource(R.string.native_update_skip))
                }
            }
            Text(stringResource(R.string.native_update_install_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.privacy.NativeTrafficFilter
import com.hiddify.hiddify.privacy.NativeTrafficFilters

@Composable
fun NativeTrafficFiltersScreen(
    filters: NativeTrafficFilters,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (NativeTrafficFilters) -> Unit,
) {
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.native_filters_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text(stringResource(R.string.native_back)) }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.native_filters_warning), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.native_filters_warning_summary))
                Text(stringResource(R.string.native_settings_reconnect_note))
            }
        }
        Text(stringResource(R.string.native_filters_count, filters.enabled.size, NativeTrafficFilter.entries.size))
        OutlinedButton(onClick = { confirmAll = true }, enabled = !busy && filters.enabled.size < NativeTrafficFilter.entries.size,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_filters_enable_all)) }
        TextButton(onClick = { onSave(NativeTrafficFilters.all(false)) }, enabled = !busy && filters.enabled.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_filters_disable_all)) }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        NativeTrafficFilter.entries.forEach { filter ->
            val (title, summary) = when (filter) {
                NativeTrafficFilter.QUIC -> R.string.native_filters_quic to R.string.native_filters_quic_summary
                NativeTrafficFilter.STUN -> R.string.native_filters_stun to R.string.native_filters_stun_summary
                NativeTrafficFilter.PLAIN_HTTP -> R.string.native_filters_http to R.string.native_filters_http_summary
                NativeTrafficFilter.LAN -> R.string.native_filters_lan to R.string.native_filters_lan_summary
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(summary), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = filter in filters.enabled, enabled = !busy,
                    onCheckedChange = { onSave(filters.withFilter(filter, it)) })
            }
        }
        Text(stringResource(R.string.native_filters_limits), style = MaterialTheme.typography.bodySmall)
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.native_filters_enable_all)) },
            text = { Text(stringResource(R.string.native_filters_confirm)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmAll = false
                    onSave(NativeTrafficFilters.all(true))
                }) { Text(stringResource(R.string.native_filters_enable)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

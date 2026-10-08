package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.privacy.NativeTrafficFilter
import com.hiddify.hiddify.privacy.NativeTrafficFilters

@Composable
fun NativeTrafficFiltersScreen(filters: NativeTrafficFilters, busy: Boolean, onBack: () -> Unit,
    onSave: (NativeTrafficFilters) -> Unit) {
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_filters_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp)) {
            NativeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(painterResource(R.drawable.native_warning), null, Modifier.padding(top = 2.dp, end = 12.dp).size(24.dp))
                        Text(stringResource(R.string.native_filters_warning), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.native_filters_warning_summary))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.native_filters_reconnect))
                }
            }
            Spacer(Modifier.height(12.dp))
            NativeButton(onClick = { confirmAll = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.native_privacy_tip), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.native_filters_enable_all))
            }
            NativeTextButton(onClick = { onSave(NativeTrafficFilters.all(false)) }, enabled = !busy && filters.enabled.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.native_restart_alt), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.native_filters_disable_all))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            NativeTrafficFilter.entries.forEach { filter ->
                val (title, summary) = when (filter) {
                    NativeTrafficFilter.QUIC -> R.string.native_filters_quic to R.string.native_filters_quic_summary
                    NativeTrafficFilter.STUN -> R.string.native_filters_stun to R.string.native_filters_stun_summary
                    NativeTrafficFilter.PLAIN_HTTP -> R.string.native_filters_http to R.string.native_filters_http_summary
                    NativeTrafficFilter.LAN -> R.string.native_filters_lan to R.string.native_filters_lan_summary
                }
                ListItem(headlineContent = { Text(stringResource(title)) },
                    supportingContent = { Text(stringResource(summary)) },
                    trailingContent = { Switch(filter in filters.enabled, onCheckedChange = null, enabled = !busy) },
                    modifier = Modifier.toggleable(filter in filters.enabled, enabled = !busy, role = Role.Switch,
                        onValueChange = { onSave(filters.withFilter(filter, it)) }),
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            ListItem(leadingContent = { Icon(painterResource(R.drawable.native_fingerprint), null) },
                headlineContent = { Text(stringResource(R.string.native_filters_limits_title)) },
                supportingContent = { Text(stringResource(R.string.native_filters_limits)) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
        }
    }
    if (confirmAll) AlertDialog(onDismissRequest = { confirmAll = false },
        title = { Text(stringResource(R.string.native_filters_confirm_title)) },
        text = { Text(stringResource(R.string.native_filters_confirm)) },
        confirmButton = { NativeButton(enabled = !busy, onClick = { confirmAll = false; onSave(NativeTrafficFilters.all(true)) }) {
            Text(stringResource(R.string.native_filters_enable))
        } },
        dismissButton = { NativeTextButton(onClick = { confirmAll = false }) { Text(stringResource(android.R.string.cancel)) } })
}

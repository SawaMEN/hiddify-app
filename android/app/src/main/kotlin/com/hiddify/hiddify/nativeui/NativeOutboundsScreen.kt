package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.NativeOutboundGroup

@Composable
fun NativeOutboundsScreen(
    groups: List<NativeOutboundGroup>,
    busyTag: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String, String) -> Unit,
    onTest: (String) -> Unit,
    onTestActive: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.native_outbounds_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row {
                TextButton(onClick = onRefresh, enabled = busyTag == null) {
                    Text(stringResource(R.string.native_logs_refresh))
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.native_back))
                }
            }
        }

        if (groups.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.native_outbounds_empty),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.native_outbounds_empty_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRefresh, enabled = busyTag == null) {
                        Text(stringResource(R.string.native_logs_refresh))
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = onTestActive,
                enabled = busyTag == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_outbounds_test_active))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.tag }) { group ->
                    GroupCard(
                        group = group,
                        busyTag = busyTag,
                        onSelect = onSelect,
                        onTest = onTest,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: NativeOutboundGroup,
    busyTag: String?,
    onSelect: (String, String) -> Unit,
    onTest: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = group.tag,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    stringResource(
                        R.string.native_outbounds_group_summary,
                        group.type,
                        group.selectedTag.ifBlank { "—" },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            group.items.forEachIndexed { index, outbound ->
                if (index > 0) HorizontalDivider()
                OutboundRow(
                    group = group,
                    outbound = outbound,
                    busy = busyTag == outbound.tag,
                    operationsEnabled = busyTag == null,
                    onSelect = onSelect,
                    onTest = onTest,
                )
            }
        }
    }
}

@Composable
private fun OutboundRow(
    group: NativeOutboundGroup,
    outbound: NativeOutbound,
    busy: Boolean,
    operationsEnabled: Boolean,
    onSelect: (String, String) -> Unit,
    onTest: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = outbound.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (outbound.selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = outboundMeta(outbound),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (outbound.selected) {
                Text(
                    text = stringResource(R.string.native_outbounds_selected),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (group.selectable && !outbound.selected) {
                TextButton(
                    onClick = { onSelect(group.tag, outbound.tag) },
                    enabled = operationsEnabled,
                ) {
                    Text(stringResource(R.string.native_outbounds_select))
                }
            }
            TextButton(
                onClick = { onTest(outbound.tag) },
                enabled = operationsEnabled,
            ) {
                Text(
                    if (busy) {
                        stringResource(R.string.native_outbounds_testing)
                    } else {
                        stringResource(R.string.native_outbounds_test)
                    },
                )
            }
        }
    }
}

@Composable
private fun outboundMeta(outbound: NativeOutbound): String {
    val parts = mutableListOf<String>()
    if (outbound.type.isNotBlank()) parts += outbound.type
    if (outbound.delayMs > 0) parts += stringResource(R.string.native_outbounds_delay, outbound.delayMs)
    if (outbound.host.isNotBlank()) {
        parts += if (outbound.port > 0) "${outbound.host}:${outbound.port}" else outbound.host
    }
    outbound.selectedChild?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}

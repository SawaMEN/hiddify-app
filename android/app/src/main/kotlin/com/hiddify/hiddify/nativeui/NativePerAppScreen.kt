package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.PerAppProxyMode
import com.hiddify.hiddify.nativerouting.NativeInstalledApp
import com.hiddify.hiddify.nativerouting.NativePerAppSnapshot

@Composable
fun NativePerAppScreen(
    snapshot: NativePerAppSnapshot,
    canChange: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onModeChanged: (String) -> Unit,
    onTogglePackage: (String) -> Unit,
    onClear: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered =
        remember(snapshot.apps, search) {
            val query = search.trim()
            if (query.isEmpty()) {
                snapshot.apps
            } else {
                snapshot.apps.filter {
                    it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
            }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.native_per_app_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.native_back))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ModeRow(
                    title = stringResource(R.string.native_per_app_all),
                    summary = stringResource(R.string.native_per_app_all_summary),
                    selected = snapshot.mode == PerAppProxyMode.OFF,
                    enabled = canChange && !busy,
                    onClick = { onModeChanged(PerAppProxyMode.OFF) },
                )
                ModeRow(
                    title = stringResource(R.string.native_per_app_include),
                    summary = stringResource(R.string.native_per_app_include_summary),
                    selected = snapshot.mode == PerAppProxyMode.INCLUDE,
                    enabled = canChange && !busy,
                    onClick = { onModeChanged(PerAppProxyMode.INCLUDE) },
                )
                ModeRow(
                    title = stringResource(R.string.native_per_app_exclude),
                    summary = stringResource(R.string.native_per_app_exclude_summary),
                    selected = snapshot.mode == PerAppProxyMode.EXCLUDE,
                    enabled = canChange && !busy,
                    onClick = { onModeChanged(PerAppProxyMode.EXCLUDE) },
                )
            }
        }

        if (!canChange) {
            Text(
                text = stringResource(R.string.native_per_app_disconnect),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (snapshot.mode != PerAppProxyMode.OFF) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.native_per_app_search)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.native_per_app_selected_count,
                            snapshot.selectedPackages.size,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    enabled = canChange && !busy && snapshot.selectedPackages.isNotEmpty(),
                    onClick = onClear,
                ) {
                    Text(stringResource(R.string.native_per_app_clear))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        selected = app.packageName in snapshot.selectedPackages,
                        enabled = canChange && !busy,
                        onToggle = { onTogglePackage(app.packageName) },
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.native_per_app_all_active),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeRow(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: NativeInstalledApp,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.system) {
                    Text(
                        text = stringResource(R.string.native_per_app_system),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

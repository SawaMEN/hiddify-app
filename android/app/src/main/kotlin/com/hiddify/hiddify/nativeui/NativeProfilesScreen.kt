package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.hiddify.hiddify.nativeprofile.NativeProfile
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun NativeProfilesScreen(
    profiles: List<NativeProfile>,
    busyProfileId: String?,
    onBack: () -> Unit,
    onSelect: (NativeProfile) -> Unit,
    onDelete: (NativeProfile) -> Unit,
    onRefresh: (NativeProfile) -> Unit,
    onImport: (raw: String, name: String?, intervalHours: Int?, disableAutoUpdate: Boolean) -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<NativeProfile?>(null) }

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
                text = stringResource(R.string.native_profiles),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { addOpen = true }) {
                    Text(stringResource(R.string.native_profile_add))
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.native_back))
                }
            }
        }

        if (profiles.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.native_profiles_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.native_profiles_empty_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { addOpen = true }) {
                        Text(stringResource(R.string.native_profile_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        busy = busyProfileId == profile.id,
                        onSelect = { onSelect(profile) },
                        onDelete = { deleteCandidate = profile },
                        onRefresh = { onRefresh(profile) },
                    )
                }
            }
        }
    }

    if (addOpen) {
        AddProfileDialog(
            onDismiss = { addOpen = false },
            onImport = { raw, name, interval, disabled ->
                onImport(raw, name, interval, disabled)
                addOpen = false
            },
        )
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.native_profile_delete_title)) },
            text = { Text(stringResource(R.string.native_profile_delete_message, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(profile)
                        deleteCandidate = null
                    },
                ) {
                    Text(stringResource(R.string.native_profile_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: NativeProfile,
    busy: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (profile.active) {
                                stringResource(R.string.native_profile_active)
                            } else if (profile.isRemote) {
                                stringResource(R.string.native_profile_remote)
                            } else {
                                stringResource(R.string.native_profile_local)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (profile.active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                if (!profile.active) {
                    OutlinedButton(onClick = onSelect, enabled = !busy) {
                        Text(stringResource(R.string.native_profile_use))
                    }
                }
            }

            if (profile.isRemote && !profile.url.isNullOrBlank()) {
                Text(
                    text = profile.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val total = profile.total
            val consumed = profile.consumed
            if (total != null && consumed != null) {
                val remaining = (total - consumed).coerceAtLeast(0)
                Text(
                    text =
                        stringResource(
                            R.string.native_profile_traffic,
                            formatBytes(consumed),
                            formatBytes(total),
                            formatBytes(remaining),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (profile.isRemote) {
                    TextButton(onClick = onRefresh, enabled = !busy) {
                        Text(
                            if (busy) {
                                stringResource(R.string.native_profile_updating)
                            } else {
                                stringResource(R.string.native_profile_update)
                            },
                        )
                    }
                }
                TextButton(onClick = onDelete, enabled = !busy) {
                    Text(stringResource(R.string.native_profile_delete))
                }
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onImport: (String, String?, Int?, Boolean) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var disableAutoUpdate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.native_profile_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.native_profile_url_or_content)) },
                    supportingText = { Text(stringResource(R.string.native_profile_url_or_content_hint)) },
                    minLines = 3,
                    maxLines = 8,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.native_profile_name_optional)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.native_profile_disable_update))
                        Text(
                            text = stringResource(R.string.native_profile_disable_update_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = disableAutoUpdate,
                        onCheckedChange = { disableAutoUpdate = it },
                    )
                }
                if (!disableAutoUpdate) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit) && value.length <= 4) interval = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.native_profile_update_interval)) },
                        supportingText = { Text(stringResource(R.string.native_profile_update_interval_hint)) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = raw.isNotBlank(),
                onClick = {
                    onImport(
                        raw,
                        name.trim().takeIf { it.isNotEmpty() },
                        interval.toIntOrNull()?.takeIf { it > 0 },
                        disableAutoUpdate,
                    )
                },
            ) {
                Text(stringResource(R.string.native_profile_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun formatBytes(value: Long): String {
    if (value <= 0L) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    val group = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val scaled = value / 1024.0.pow(group.toDouble())
    return if (scaled >= 100 || group == 0) {
        "%.0f %s".format(scaled, units[group])
    } else {
        "%.1f %s".format(scaled, units[group])
    }
}

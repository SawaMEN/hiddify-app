package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.hiddify.hiddify.nativeprofile.NativeProfileEditor

@Composable
fun NativeProfileDetailsScreen(
    editor: NativeProfileEditor?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (
        name: String,
        disableAutoUpdate: Boolean,
        updateIntervalHours: Int?,
        content: String,
    ) -> Unit,
) {
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
                text = stringResource(R.string.native_profile_details),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onBack, enabled = !busy) {
                Text(stringResource(R.string.native_back))
            }
        }

        if (editor == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        var name by remember(editor.profile.id, editor.profile.lastUpdate) {
            mutableStateOf(editor.name)
        }
        var disableAutoUpdate by remember(editor.profile.id, editor.profile.lastUpdate) {
            mutableStateOf(editor.disableAutoUpdate)
        }
        var interval by remember(editor.profile.id, editor.profile.lastUpdate) {
            mutableStateOf(editor.updateIntervalHours?.toString().orEmpty())
        }
        var config by remember(editor.profile.id, editor.profile.lastUpdate) {
            mutableStateOf(editor.content)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        singleLine = true,
                        label = { Text(stringResource(R.string.native_profile_name)) },
                    )

                    if (editor.profile.isRemote && !editor.profile.url.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.native_profile_subscription_url),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = editor.profile.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
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
                                enabled = !busy,
                            )
                        }

                        if (!disableAutoUpdate) {
                            OutlinedTextField(
                                value = interval,
                                onValueChange = { value ->
                                    if (value.all(Char::isDigit) && value.length <= 4) interval = value
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy,
                                singleLine = true,
                                label = { Text(stringResource(R.string.native_profile_update_interval)) },
                                supportingText = {
                                    Text(stringResource(R.string.native_profile_update_interval_hint))
                                },
                            )
                        }
                    }

                    Text(
                        text =
                            stringResource(
                                R.string.native_profile_last_update,
                                editor.profile.lastUpdate,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.native_profile_raw_config),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.native_profile_raw_config_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = config,
                onValueChange = { config = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                minLines = 16,
                label = { Text(stringResource(R.string.native_profile_configuration)) },
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && name.isNotBlank() && config.isNotBlank(),
                onClick = {
                    onSave(
                        name.trim(),
                        disableAutoUpdate,
                        interval.toIntOrNull()?.takeIf { it > 0 },
                        config,
                    )
                },
            ) {
                Text(
                    if (busy) {
                        stringResource(R.string.native_profile_saving)
                    } else {
                        stringResource(R.string.native_profile_save)
                    },
                )
            }
        }
    }
}

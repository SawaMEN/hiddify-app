package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.hiddify.hiddify.nativeui.NativeButton as Button
import com.hiddify.hiddify.nativeui.NativeCard as Card
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeCoreOptions
import com.hiddify.hiddify.nativecore.NativeCoreOptionsRepository

@Composable
fun NativeCoreOptionsScreen(
    options: NativeCoreOptions,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (NativeCoreOptions) -> Unit,
    onOpenDns: () -> Unit,
    onOpenTls: () -> Unit,
    onOpenGeneralOptions: () -> Unit,
    onOpenTunnel: () -> Unit,
) {
    var value by remember(options) { mutableStateOf(options) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.native_core_options_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onBack, enabled = !busy) {
                Text(stringResource(R.string.native_back))
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
                NativeSettingsLink(R.string.native_general_options_title, R.drawable.native_route, onOpenGeneralOptions, enabled = !busy)
            }

            NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
                NativeSettingsLink(R.string.native_core_dns, R.drawable.native_dns, onOpenDns, enabled = !busy)
            }

            SectionCard(stringResource(R.string.native_core_inbound)) {
                SwitchSetting(
                    title = stringResource(R.string.native_core_strict_route),
                    checked = value.strictRoute,
                    enabled = !busy,
                ) { value = value.copy(strictRoute = it) }

                ChoiceSetting(
                    title = stringResource(R.string.native_core_tun_stack),
                    selected = value.tunImplementation,
                    choices = NativeCoreOptionsRepository.tunChoices,
                    label = { it },
                    enabled = !busy,
                ) { value = value.copy(tunImplementation = it) }

                SwitchSetting(
                    title = stringResource(R.string.native_core_mixed_port_enabled),
                    checked = value.mixedPort > 0,
                    enabled = !busy,
                ) { enabled ->
                    value = value.copy(mixedPort = if (enabled) 12334 else 0)
                }
                if (value.mixedPort > 0) {
                    IntSetting(
                        title = stringResource(R.string.native_core_mixed_port),
                        value = value.mixedPort,
                        enabled = !busy,
                        range = 1..65535,
                    ) { value = value.copy(mixedPort = it) }
                }

                SwitchSetting(
                    title = stringResource(R.string.native_core_direct_port_enabled),
                    checked = value.directPort > 0,
                    enabled = !busy,
                ) { enabled ->
                    value = value.copy(directPort = if (enabled) 12337 else 0)
                }
                if (value.directPort > 0) {
                    IntSetting(
                        title = stringResource(R.string.native_core_direct_port),
                        value = value.directPort,
                        enabled = !busy,
                        range = 1..65535,
                    ) { value = value.copy(directPort = it) }
                }

            }

            NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
                NativeSettingsLink(R.string.native_tunnel_title, R.drawable.native_route, onOpenTunnel, enabled = !busy)
            }

            NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
                NativeSettingsLink(R.string.native_core_tls, R.drawable.native_shield, onOpenTls, enabled = !busy)
            }

            Text(
                text = stringResource(R.string.native_core_reconnect_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { onSave(value) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged, enabled = enabled)
    }
}

@Composable
private fun TextSetting(
    title: String,
    value: String,
    enabled: Boolean,
    onChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        enabled = enabled,
        singleLine = true,
    )
}

@Composable
private fun IntSetting(
    title: String,
    value: Int,
    enabled: Boolean,
    range: IntRange,
    onChanged: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            if (next.all(Char::isDigit) && next.length <= 6) {
                text = next
                next.toIntOrNull()?.takeIf { it in range }?.let(onChanged)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        enabled = enabled,
        singleLine = true,
    )
}

@Composable
private fun ChoiceSetting(
    title: String,
    selected: String,
    choices: List<String>,
    label: @Composable (String) -> String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(label(selected), style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    choices.forEach { choice ->
                        TextButton(
                            onClick = {
                                onSelected(choice)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text =
                                    if (choice == selected) {
                                        "✓ ${label(choice)}"
                                    } else {
                                        label(choice)
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

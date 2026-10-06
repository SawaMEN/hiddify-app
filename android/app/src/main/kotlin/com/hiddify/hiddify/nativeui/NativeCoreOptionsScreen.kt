package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
            SectionCard(stringResource(R.string.native_core_general)) {
                ChoiceSetting(
                    title = stringResource(R.string.native_core_balancer),
                    selected = value.balancerStrategy,
                    choices = NativeCoreOptionsRepository.balancerChoices,
                    label = ::balancerLabel,
                    enabled = !busy,
                ) { value = value.copy(balancerStrategy = it) }

                SwitchSetting(
                    title = stringResource(R.string.native_core_resolve_destination),
                    checked = value.resolveDestination,
                    enabled = !busy,
                ) { value = value.copy(resolveDestination = it) }

                ChoiceSetting(
                    title = stringResource(R.string.native_core_log_level),
                    selected = value.logLevel,
                    choices = NativeCoreOptionsRepository.logLevelChoices,
                    label = { it.uppercase() },
                    enabled = !busy,
                ) { value = value.copy(logLevel = it) }

                TextSetting(
                    title = stringResource(R.string.native_core_test_url),
                    value = value.connectionTestUrl,
                    enabled = !busy,
                ) { value = value.copy(connectionTestUrl = it) }

                IntSetting(
                    title = stringResource(R.string.native_core_test_interval_minutes),
                    value = (value.urlTestIntervalSeconds / 60).coerceAtLeast(1),
                    enabled = !busy,
                    range = 1..1440,
                ) { value = value.copy(urlTestIntervalSeconds = it * 60) }

                IntSetting(
                    title = stringResource(R.string.native_core_clash_api_port),
                    value = value.clashApiPort,
                    enabled = !busy,
                    range = 1..65535,
                ) { value = value.copy(clashApiPort = it) }

                SwitchSetting(
                    title = stringResource(R.string.native_core_use_xray),
                    checked = value.useXrayCoreWhenPossible,
                    enabled = !busy,
                ) { value = value.copy(useXrayCoreWhenPossible = it) }
            }

            SectionCard(stringResource(R.string.native_core_dns)) {
                TextSetting(
                    title = stringResource(R.string.native_core_remote_dns),
                    value = value.remoteDnsAddress,
                    enabled = !busy,
                ) { value = value.copy(remoteDnsAddress = it) }

                ChoiceSetting(
                    title = stringResource(R.string.native_core_remote_dns_strategy),
                    selected = value.remoteDnsStrategy,
                    choices = NativeCoreOptionsRepository.domainStrategyChoices,
                    label = ::domainStrategyLabel,
                    enabled = !busy,
                ) { value = value.copy(remoteDnsStrategy = it) }

                SwitchSetting(
                    title = stringResource(R.string.native_core_fake_dns),
                    checked = value.fakeDns,
                    enabled = !busy,
                ) { value = value.copy(fakeDns = it) }

                TextSetting(
                    title = stringResource(R.string.native_core_direct_dns),
                    value = value.directDnsAddress,
                    enabled = !busy,
                ) { value = value.copy(directDnsAddress = it) }

                ChoiceSetting(
                    title = stringResource(R.string.native_core_direct_dns_strategy),
                    selected = value.directDnsStrategy,
                    choices = NativeCoreOptionsRepository.domainStrategyChoices,
                    label = ::domainStrategyLabel,
                    enabled = !busy,
                ) { value = value.copy(directDnsStrategy = it) }
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

                IntSetting(
                    title = stringResource(R.string.native_core_mtu),
                    value = value.mtu,
                    enabled = !busy,
                    range = 1280..65535,
                ) { value = value.copy(mtu = it) }
            }

            SectionCard(stringResource(R.string.native_core_tls)) {
                SwitchSetting(
                    title = stringResource(R.string.native_core_tls_fragment),
                    checked = value.tlsFragment,
                    enabled = !busy,
                ) { value = value.copy(tlsFragment = it) }

                if (value.tlsFragment) {
                    TextSetting(
                        title = stringResource(R.string.native_core_tls_fragment_size),
                        value = value.tlsFragmentSize,
                        enabled = !busy,
                    ) { value = value.copy(tlsFragmentSize = it) }

                    TextSetting(
                        title = stringResource(R.string.native_core_tls_fragment_sleep),
                        value = value.tlsFragmentSleep,
                        enabled = !busy,
                    ) { value = value.copy(tlsFragmentSleep = it) }

                    SwitchSetting(
                        title = stringResource(R.string.native_core_tls_mixed_sni),
                        checked = value.tlsMixedSniCase,
                        enabled = !busy,
                    ) { value = value.copy(tlsMixedSniCase = it) }

                    SwitchSetting(
                        title = stringResource(R.string.native_core_tls_padding),
                        checked = value.tlsPadding,
                        enabled = !busy,
                    ) { value = value.copy(tlsPadding = it) }

                    if (value.tlsPadding) {
                        TextSetting(
                            title = stringResource(R.string.native_core_tls_padding_size),
                            value = value.tlsPaddingSize,
                            enabled = !busy,
                        ) { value = value.copy(tlsPaddingSize = it) }
                    }
                }
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

@Composable
private fun balancerLabel(value: String): String =
    when (value) {
        "consistent-hashing" -> stringResource(R.string.native_core_balancer_consistent)
        "sticky-sessions" -> stringResource(R.string.native_core_balancer_sticky)
        else -> stringResource(R.string.native_core_balancer_round_robin)
    }

@Composable
private fun domainStrategyLabel(value: String): String =
    when (value) {
        "prefer_ipv4" -> stringResource(R.string.native_core_strategy_prefer_ipv4)
        "prefer_ipv6" -> stringResource(R.string.native_core_strategy_prefer_ipv6)
        "ipv4_only" -> stringResource(R.string.native_core_strategy_ipv4_only)
        "ipv6_only" -> stringResource(R.string.native_core_strategy_ipv6_only)
        else -> stringResource(R.string.native_core_strategy_auto)
    }

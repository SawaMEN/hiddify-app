package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.hiddify.hiddify.nativeui.NativeButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeChainOptions
import com.hiddify.hiddify.nativecore.NativeChainRepository
import com.hiddify.hiddify.nativeprofile.NativeProfile

@Composable
fun NativeChainScreen(
    options: NativeChainOptions,
    profiles: List<NativeProfile>,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (NativeChainOptions) -> Unit,
) {
    var value by remember(options) { mutableStateOf(options) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NativePageHeader(stringResource(R.string.native_chain_title), onBack)

        Text(
            text = stringResource(R.string.native_chain_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ChainSection(stringResource(R.string.native_chain_active_stage)) {
            ChoiceButton(
                title = stringResource(R.string.native_chain_status),
                selected = value.status,
                choices = NativeChainRepository.statusChoices,
                enabled = !busy,
                label = { chainStatusLabel(it) },
            ) { value = value.copy(status = it) }
        }

        ChainSection(stringResource(R.string.native_chain_extra_security)) {
            ChoiceButton(
                title = stringResource(R.string.native_chain_mode),
                selected = value.extraMode,
                choices = NativeChainRepository.modeChoices,
                enabled = !busy,
                label = { chainModeLabel(it) },
            ) { value = value.copy(extraMode = it) }

            when (value.extraMode) {
                "warp" ->
                    OutlinedTextField(
                        value = value.extraWarpLicense,
                        onValueChange = { value = value.copy(extraWarpLicense = it) },
                        label = { Text(stringResource(R.string.native_chain_warp_license)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                "psiphon" -> {
                    ChoiceButton(
                        title = stringResource(R.string.native_chain_psiphon_region),
                        selected = value.extraPsiphonRegion,
                        choices = NativeChainRepository.psiphonRegions,
                        enabled = !busy,
                        label = { it },
                    ) { value = value.copy(extraPsiphonRegion = it) }
                    OutlinedTextField(
                        value = value.extraPsiphonConduit,
                        onValueChange = { value = value.copy(extraPsiphonConduit = it) },
                        label = { Text(stringResource(R.string.native_chain_conduit)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                "profile" ->
                    ProfileChoiceButton(
                        selectedId = value.extraProfileId,
                        profiles = profiles,
                        enabled = !busy,
                    ) { value = value.copy(extraProfileId = it) }
            }
        }

        ChainSection(stringResource(R.string.native_chain_main_profile)) {
            Text(
                text =
                    profiles.firstOrNull { it.active }?.name
                        ?: stringResource(R.string.native_no_profile_selected),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        ChainSection(stringResource(R.string.native_chain_unblocker)) {
            ChoiceButton(
                title = stringResource(R.string.native_chain_mode),
                selected = value.unblockerMode,
                choices = NativeChainRepository.modeChoices,
                enabled = !busy,
                label = { chainModeLabel(it) },
            ) { value = value.copy(unblockerMode = it) }

            when (value.unblockerMode) {
                "warp" -> {
                    OutlinedTextField(
                        value = value.unblockerWarpLicense,
                        onValueChange = { value = value.copy(unblockerWarpLicense = it) },
                        label = { Text(stringResource(R.string.native_chain_warp_license)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = value.unblockerWarpCleanIp,
                        onValueChange = { value = value.copy(unblockerWarpCleanIp = it) },
                        label = { Text(stringResource(R.string.native_chain_clean_ip)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    IntChainField(
                        title = stringResource(R.string.native_chain_clean_port),
                        value = value.unblockerWarpPort,
                        enabled = !busy,
                    ) { value = value.copy(unblockerWarpPort = it) }
                    OutlinedTextField(
                        value = value.unblockerWarpNoise,
                        onValueChange = { value = value.copy(unblockerWarpNoise = it) },
                        label = { Text(stringResource(R.string.native_chain_noise_count)) },
                        supportingText = { Text(stringResource(R.string.native_chain_range_hint)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = value.unblockerWarpNoiseMode,
                        onValueChange = { value = value.copy(unblockerWarpNoiseMode = it) },
                        label = { Text(stringResource(R.string.native_chain_noise_mode)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = value.unblockerWarpNoiseSize,
                        onValueChange = { value = value.copy(unblockerWarpNoiseSize = it) },
                        label = { Text(stringResource(R.string.native_chain_noise_size)) },
                        supportingText = { Text(stringResource(R.string.native_chain_range_hint)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = value.unblockerWarpNoiseDelay,
                        onValueChange = { value = value.copy(unblockerWarpNoiseDelay = it) },
                        label = { Text(stringResource(R.string.native_chain_noise_delay)) },
                        supportingText = { Text(stringResource(R.string.native_chain_range_hint)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                "psiphon" -> {
                    ChoiceButton(
                        title = stringResource(R.string.native_chain_psiphon_region),
                        selected = value.unblockerPsiphonRegion,
                        choices = NativeChainRepository.psiphonRegions,
                        enabled = !busy,
                        label = { it },
                    ) { value = value.copy(unblockerPsiphonRegion = it) }
                    OutlinedTextField(
                        value = value.unblockerPsiphonConduit,
                        onValueChange = { value = value.copy(unblockerPsiphonConduit = it) },
                        label = { Text(stringResource(R.string.native_chain_conduit)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                "profile" ->
                    ProfileChoiceButton(
                        selectedId = value.unblockerProfileId,
                        profiles = profiles,
                        enabled = !busy,
                    ) { value = value.copy(unblockerProfileId = it) }
            }
        }

        Text(
            text = stringResource(R.string.native_chain_reconnect_note),
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

@Composable
private fun ChainSection(
    title: String,
    content: @Composable () -> Unit,
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
private fun ChoiceButton(
    title: String,
    selected: String,
    choices: List<String>,
    enabled: Boolean,
    label: @Composable (String) -> String,
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    choices.forEach { choice ->
                        NativeTextButton(
                            onClick = {
                                onSelected(choice)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (choice == selected) "✓ " + label(choice) else label(choice),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                NativeTextButton(onClick = { open = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileChoiceButton(
    selectedId: String?,
    profiles: List<NativeProfile>,
    enabled: Boolean,
    onSelected: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedId }
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled && profiles.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.native_chain_select_profile),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                selected?.name ?: stringResource(R.string.native_no_profile_selected),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.native_chain_select_profile)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    profiles.filterNot { it.active }.forEach { profile ->
                        NativeTextButton(
                            onClick = {
                                onSelected(profile.id)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (profile.id == selectedId) "✓ " + profile.name else profile.name,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                NativeTextButton(onClick = { open = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun IntChainField(
    title: String,
    value: Int,
    enabled: Boolean,
    onChanged: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            if (next.all(Char::isDigit) && next.length <= 5) {
                text = next
                next.toIntOrNull()?.takeIf { it in 0..65535 }?.let(onChanged)
            }
        },
        label = { Text(title) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
    )
}

@Composable
private fun chainStatusLabel(value: String): String =
    when (value) {
        "extra_security" -> stringResource(R.string.native_chain_extra_security)
        "unblocker" -> stringResource(R.string.native_chain_unblocker)
        else -> stringResource(R.string.native_chain_disabled)
    }

@Composable
private fun chainModeLabel(value: String): String =
    when (value) {
        "warp" -> "WARP"
        "psiphon" -> "Psiphon"
        "profile" -> stringResource(R.string.native_chain_profile_mode)
        else -> value
    }

package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.Status

@Composable
fun NativeApp(
    status: Status,
    activeProfileName: String,
    hasActiveProfile: Boolean,
    rootMode: Boolean,
    wifiSharing: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onToggleConnection: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                ConnectionCard(
                    status = status,
                    activeProfileName = activeProfileName,
                    hasActiveProfile = hasActiveProfile,
                    onToggleConnection = onToggleConnection,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.native_current_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text =
                                if (rootMode) {
                                    stringResource(R.string.native_mode_root)
                                } else {
                                    stringResource(R.string.native_mode_android_vpn)
                                },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                if (wifiSharing) {
                                    stringResource(R.string.native_wifi_sharing_on)
                                } else {
                                    stringResource(R.string.native_wifi_sharing_off)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenProfiles,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.native_profiles))
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.native_settings))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.native_migration_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = onDismissError,
                confirmButton = {
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.native_error_title)) },
                text = { Text(errorMessage) },
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    status: Status,
    activeProfileName: String,
    hasActiveProfile: Boolean,
    onToggleConnection: () -> Unit,
) {
    val statusText =
        when (status) {
            Status.Stopped -> stringResource(R.string.native_status_stopped)
            Status.Starting -> stringResource(R.string.native_status_starting)
            Status.Started -> stringResource(R.string.native_status_started)
            Status.Stopping -> stringResource(R.string.native_status_stopping)
        }

    val buttonText =
        when (status) {
            Status.Started -> stringResource(R.string.native_disconnect)
            Status.Stopped -> stringResource(R.string.native_connect)
            Status.Starting -> stringResource(R.string.native_connecting)
            Status.Stopping -> stringResource(R.string.native_disconnecting)
        }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text =
                    if (hasActiveProfile) {
                        activeProfileName.ifBlank {
                            stringResource(R.string.native_active_profile_unnamed)
                        }
                    } else {
                        stringResource(R.string.native_no_profile_selected)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onToggleConnection,
                enabled = status == Status.Stopped || status == Status.Started,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonText)
            }
        }
    }
}

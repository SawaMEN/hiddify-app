package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hiddify.hiddify.nativeui.NativeCard as Card
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails

private enum class ReceiverPlatform {
    ANDROID,
    WINDOWS,
    LINUX,
    MACOS,
    IOS,
}

@Composable
fun NativeWifiSharingGuideScreen(
    rootMode: Boolean,
    details: NativeWifiSharingDetails,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val platform = ReceiverPlatform.entries[selectedIndex]
    val host =
        details.host.ifBlank {
            stringResource(R.string.native_wifi_guide_gateway_fallback)
        }
    val password =
        details.password.ifBlank {
            stringResource(R.string.native_wifi_guide_password_missing)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NativePageHeader(stringResource(R.string.native_wifi_guide_title), onBack)

        GuideCard(title = stringResource(R.string.native_wifi_guide_receiver_title)) {
            Text(stringResource(R.string.native_wifi_guide_receiver_text))
            if (rootMode) {
                Text(
                    text = stringResource(R.string.native_wifi_guide_root_note),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        NativeWifiCredentialsCard(details)

        if (!rootMode) {
            GuideCard(title = stringResource(R.string.native_wifi_guide_details)) {
                DetailRow(stringResource(R.string.native_wifi_guide_host), host)
                DetailRow(stringResource(R.string.native_wifi_guide_port), details.port.toString())
                DetailRow(stringResource(R.string.native_wifi_guide_username), details.username)
                DetailRow(stringResource(R.string.native_wifi_guide_password), password)
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (busy) {
                            stringResource(R.string.native_profile_updating)
                        } else {
                            stringResource(R.string.native_wifi_guide_refresh_ip)
                        },
                    )
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReceiverPlatform.entries.forEachIndexed { index, item ->
                OutlinedButton(
                    onClick = { selectedIndex = index },
                    enabled = index != selectedIndex,
                ) {
                    Text(platformName(item))
                }
            }
        }

        NumberedGuideCard(
            number = 1,
            title = stringResource(R.string.native_wifi_guide_step_connect),
        ) {
            Text(stringResource(R.string.native_wifi_guide_connect_text))
        }

        NumberedGuideCard(
            number = 2,
            title =
                if (rootMode) {
                    stringResource(R.string.native_wifi_guide_step_no_proxy)
                } else {
                    stringResource(R.string.native_wifi_guide_step_proxy)
                },
        ) {
            if (rootMode) {
                Text(stringResource(R.string.native_wifi_guide_root_client_text))
                SettingsIllustration(
                    title = platformPath(platform),
                    rows =
                        listOf(
                            stringResource(R.string.native_wifi_guide_proxy_field) to
                                stringResource(R.string.native_wifi_guide_proxy_off),
                        ),
                )
            } else {
                Text(stringResource(R.string.native_wifi_guide_proxy_text))
                SettingsIllustration(
                    title = platformPath(platform),
                    rows =
                        listOf(
                            stringResource(R.string.native_wifi_guide_proxy_field) to
                                stringResource(R.string.native_wifi_guide_proxy_manual),
                            stringResource(R.string.native_wifi_guide_server_field) to host,
                            stringResource(R.string.native_wifi_guide_port) to details.port.toString(),
                            stringResource(R.string.native_wifi_guide_auth_field) to
                                platformAuthHint(platform),
                        ),
                )
                Text(
                    text = platformAuthNote(platform),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        NumberedGuideCard(
            number = 3,
            title = stringResource(R.string.native_wifi_guide_step_verify),
        ) {
            Text(stringResource(R.string.native_wifi_guide_verify_text))
        }

        NumberedGuideCard(
            number = 4,
            title = stringResource(R.string.native_wifi_guide_step_troubleshoot),
        ) {
            Text(
                if (rootMode) {
                    stringResource(R.string.native_wifi_guide_troubleshoot_root)
                } else {
                    stringResource(R.string.native_wifi_guide_troubleshoot_proxy)
                },
            )
        }

        NumberedGuideCard(
            number = 5,
            title = stringResource(R.string.native_wifi_guide_step_finish),
        ) {
            Text(
                if (rootMode) {
                    stringResource(R.string.native_wifi_guide_finish_root)
                } else {
                    stringResource(R.string.native_wifi_guide_finish_proxy)
                },
            )
        }
    }
}

@Composable
private fun GuideCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
private fun NumberedGuideCard(
    number: Int,
    title: String,
    content: @Composable () -> Unit,
) {
    GuideCard(title = "$number. $title", content = content)
}

@Composable
private fun SettingsIllustration(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            rows.forEach { (label, value) ->
                DetailRow(label, value)
            }
            Text(
                text = stringResource(R.string.native_wifi_guide_illustration_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun platformName(platform: ReceiverPlatform): String =
    when (platform) {
        ReceiverPlatform.ANDROID -> stringResource(R.string.native_wifi_platform_android)
        ReceiverPlatform.WINDOWS -> stringResource(R.string.native_wifi_platform_windows)
        ReceiverPlatform.LINUX -> stringResource(R.string.native_wifi_platform_linux)
        ReceiverPlatform.MACOS -> stringResource(R.string.native_wifi_platform_macos)
        ReceiverPlatform.IOS -> stringResource(R.string.native_wifi_platform_ios)
    }

@Composable
private fun platformPath(platform: ReceiverPlatform): String =
    when (platform) {
        ReceiverPlatform.ANDROID -> stringResource(R.string.native_wifi_path_android)
        ReceiverPlatform.WINDOWS -> stringResource(R.string.native_wifi_path_windows)
        ReceiverPlatform.LINUX -> stringResource(R.string.native_wifi_path_linux)
        ReceiverPlatform.MACOS -> stringResource(R.string.native_wifi_path_macos)
        ReceiverPlatform.IOS -> stringResource(R.string.native_wifi_path_ios)
    }

@Composable
private fun platformAuthHint(platform: ReceiverPlatform): String =
    when (platform) {
        ReceiverPlatform.ANDROID ->
            stringResource(R.string.native_wifi_auth_browser)
        ReceiverPlatform.WINDOWS,
        ReceiverPlatform.LINUX ->
            stringResource(R.string.native_wifi_auth_app)
        ReceiverPlatform.MACOS,
        ReceiverPlatform.IOS ->
            stringResource(R.string.native_wifi_auth_on)
    }

@Composable
private fun platformAuthNote(platform: ReceiverPlatform): String =
    when (platform) {
        ReceiverPlatform.ANDROID ->
            stringResource(R.string.native_wifi_auth_note_android)
        ReceiverPlatform.WINDOWS ->
            stringResource(R.string.native_wifi_auth_note_windows)
        ReceiverPlatform.LINUX ->
            stringResource(R.string.native_wifi_auth_note_linux)
        ReceiverPlatform.MACOS ->
            stringResource(R.string.native_wifi_auth_note_macos)
        ReceiverPlatform.IOS ->
            stringResource(R.string.native_wifi_auth_note_ios)
    }

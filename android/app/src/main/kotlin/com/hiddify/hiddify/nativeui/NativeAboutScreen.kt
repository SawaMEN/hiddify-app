package com.hiddify.hiddify.nativeui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

@Composable
fun NativeAboutScreen(
    versionName: String,
    versionCode: Int,
    updateChecking: Boolean,
    updateMessage: String?,
    updateUrl: String?,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenFork: () -> Unit,
    onOpenUpstream: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NativePageHeader(stringResource(R.string.native_about_title), onBack)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.native_about_version, versionName, versionCode),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.native_about_fork_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.native_about_updates),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !updateChecking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (updateChecking) {
                            stringResource(R.string.native_about_checking_update)
                        } else {
                            stringResource(R.string.native_about_check_update)
                        },
                    )
                }
                updateMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (updateUrl != null) {
                    OutlinedButton(
                        onClick = onOpenUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.native_about_open_release))
                    }
                }
            }
        }

        LinkButton(
            text = stringResource(R.string.native_about_source),
            subtext = "SawaMEN/hiddify-app",
            onClick = onOpenFork,
        )
        LinkButton(
            text = stringResource(R.string.native_about_upstream),
            subtext = "hiddify/hiddify-app",
            onClick = onOpenUpstream,
        )
        LinkButton(
            text = stringResource(R.string.native_about_terms),
            onClick = onOpenTerms,
        )
        LinkButton(
            text = stringResource(R.string.native_about_privacy),
            onClick = onOpenPrivacy,
        )
    }
}

@Composable
private fun LinkButton(
    text: String,
    subtext: String? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text, fontWeight = FontWeight.Medium)
            if (subtext != null) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeOperaProfiles

/** Shared by catalog import and saved-profile selection: the same four readable points. */
@Composable
internal fun NativeOperaNoticeContent(russian: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(NativeOperaProfiles.introduction(russian), style = MaterialTheme.typography.bodyMedium)
        NativeOperaProfiles.noticePoints(russian).forEach { (heading, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(heading, style = MaterialTheme.typography.titleSmall)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun NativeOperaNoticeDialog(onDismiss: () -> Unit, onContinue: () -> Unit, enabled: Boolean) {
    val russian = LocalContext.current.resources.configuration.locales[0].language == "ru"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Opera Proxy") }, text = {
        Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            NativeOperaNoticeContent(russian)
        }
    }, confirmButton = {
        NativeTextButton(onClick = onContinue, enabled = enabled) { Text(stringResource(R.string.native_free_continue)) }
    }, dismissButton = {
        NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
    })
}

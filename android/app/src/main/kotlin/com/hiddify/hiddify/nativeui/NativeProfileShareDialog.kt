package com.hiddify.hiddify.nativeui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfile
import com.hiddify.hiddify.nativeprofile.NativeProfileTransfer
import com.hiddify.hiddify.nativeprofile.NativeQrImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NativeProfileShareDialog(
    profile: NativeProfile,
    onDismiss: () -> Unit,
    onCopyConfig: () -> Unit,
    onExportConfig: () -> Unit,
) {
    val context = LocalContext.current
    val link = remember(profile.url, profile.name) {
        profile.url?.takeIf { profile.isRemote }?.let { url ->
            runCatching { NativeProfileTransfer.subscriptionLink(url, profile.name) }.getOrNull()
        }
    }
    var showQr by remember(profile.id) { mutableStateOf(false) }
    var qrBitmap by remember(link) { mutableStateOf<Bitmap?>(null) }
    var qrError by remember(link) { mutableStateOf(false) }

    LaunchedEffect(link, showQr) {
        if (showQr && link != null && qrBitmap == null) {
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { NativeQrImages.create(link) }.getOrNull()
            }
            qrBitmap = bitmap
            qrError = bitmap == null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.native_profile_share_title, profile.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.native_profile_share_summary))
                if (link != null) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                require(link.toByteArray(Charsets.UTF_8).size <= NativeProfileTransfer.MAX_CLIPBOARD_BYTES)
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText(profile.name, link))
                            }.fold(
                                onSuccess = {
                                    Toast.makeText(context, R.string.native_profile_copied, Toast.LENGTH_SHORT).show()
                                },
                                onFailure = {
                                    Toast.makeText(context, R.string.native_profile_share_failed, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                    ) {
                        Text(stringResource(R.string.native_profile_copy_url))
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                require(link.toByteArray(Charsets.UTF_8).size <= NativeProfileTransfer.MAX_CLIPBOARD_BYTES)
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, link)
                                        },
                                        profile.name,
                                    ),
                                )
                            }.onFailure {
                                Toast.makeText(context, R.string.native_profile_share_failed, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.native_profile_share_url))
                    }
                    NativeTextButton(onClick = { showQr = !showQr }) {
                        Text(stringResource(R.string.native_profile_show_qr))
                    }
                    if (showQr) {
                        val bitmap = qrBitmap
                        when {
                            qrError -> Text(stringResource(R.string.native_profile_qr_too_large))
                            bitmap == null -> CircularProgressIndicator()
                            else -> Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.native_profile_show_qr),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onCopyConfig, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_profile_copy_config))
                }
                OutlinedButton(onClick = onExportConfig, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_profile_export_config))
                }
            }
        },
        confirmButton = {
            NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

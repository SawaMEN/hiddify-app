package com.hiddify.hiddify.nativeui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeQrImages
import com.hiddify.hiddify.nativeprofile.NativeTvLinkReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

internal fun isNativeAndroidTv(context: Context): Boolean {
    val mode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        mode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/** Starts a new short-lived pairing URL and closes it as soon as the sheet/panel goes away. */
@Composable
internal fun NativeTvImportPanel(onImport: (String) -> Unit) {
    val main = remember { Handler(Looper.getMainLooper()) }
    var receiver by remember { mutableStateOf<NativeTvLinkReceiver?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        val service = withContext(Dispatchers.IO) {
            runCatching { NativeTvLinkReceiver.open { link ->
                main.post { pending = link }
            } }.getOrNull()
        }
        receiver = service
        try { awaitCancellation() }
        finally {
            withContext(NonCancellable + Dispatchers.IO) { service?.close() }
        }
    }
    LaunchedEffect(receiver?.url) {
        bitmap = receiver?.url?.let { link ->
            withContext(Dispatchers.Default) { runCatching { NativeQrImages.create(link) }.getOrNull() }
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }

    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.native_tv_import), style = MaterialTheme.typography.titleMedium)
        val server = receiver
        when {
            server == null -> Text(stringResource(R.string.native_tv_pairing_unavailable))
            pending != null -> {
                Text(stringResource(R.string.native_tv_link_received))
                Text(pending.orEmpty(), style = MaterialTheme.typography.bodySmall)
                NativeButton(onClick = { pending?.let(onImport) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_tv_confirm_import))
                }
            }
            else -> {
                Text(stringResource(R.string.native_tv_scan_phone))
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.native_tv_scan_phone),
                    modifier = Modifier.size(220.dp)) } ?: CircularProgressIndicator()
                Text(server.url, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

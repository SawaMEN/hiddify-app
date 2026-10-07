package com.hiddify.hiddify.nativeui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import com.hiddify.hiddify.nativeui.NativeCard as Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails
import com.hiddify.hiddify.nativeprofile.NativeQrImages
import com.hiddify.hiddify.sharing.HotspotWifiQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NativeWifiCredentialsCard(details: NativeWifiSharingDetails) {
    if (details.ssid.isBlank() || details.wifiPassword.isBlank()) return
    var bitmap by remember(details.ssid, details.wifiPassword) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(details.ssid, details.wifiPassword) {
        bitmap = withContext(Dispatchers.Default) {
            NativeQrImages.create(HotspotWifiQr.encode(details.ssid, details.wifiPassword))
        }
    }
    DisposableEffect(bitmap) {
        val owned = bitmap
        onDispose { owned?.recycle() }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.native_wifi_credentials), style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Column {
                    Text("${stringResource(R.string.native_wifi_ssid)}: ${details.ssid}")
                    Text("${stringResource(R.string.native_wifi_password)}: ${details.wifiPassword}")
                }
            }
            bitmap?.let {
                Image(it.asImageBitmap(), contentDescription = stringResource(R.string.native_wifi_credentials),
                    modifier = Modifier.size(192.dp))
            }
        }
    }
}

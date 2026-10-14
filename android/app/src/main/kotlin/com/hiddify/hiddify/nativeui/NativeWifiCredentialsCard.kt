package com.hiddify.hiddify.nativeui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.CancellationException

@Composable
fun NativeWifiCredentialsCard(details: NativeWifiSharingDetails) {
    if (details.ssid.isBlank() || details.wifiPassword.isBlank()) return
    var bitmap by remember(details.ssid, details.wifiPassword) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(details.ssid, details.wifiPassword) {
        try {
            bitmap = withContext(Dispatchers.Default) {
                NativeQrImages.create(HotspotWifiQr.encode(details.ssid, details.wifiPassword))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            // Credentials remain usable even when their optional QR cannot be encoded.
            android.util.Log.w("WifiCredentials", "Unable to generate Wi-Fi QR", error)
        }
    }
    // Compose/RenderThread can still reference the bitmap during a closing animation.
    // Let GC reclaim it after the final rendering reference is released.
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

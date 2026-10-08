package com.hiddify.hiddify.nativeui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.hiddify.hiddify.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val appIconCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

/** Dart CheckboxListTile.secondary: load visible package artwork without blocking list scroll. */
@Composable
internal fun NativeInstalledAppIcon(packageName: String) {
    val context = LocalContext.current.applicationContext
    val pixels = with(LocalDensity.current) { 48.dp.roundToPx() }.coerceIn(48, 192)
    val key = "$packageName:$pixels"
    val bitmap by produceState<Bitmap?>(appIconCache.get(key), key) {
        value = withContext(Dispatchers.IO) {
            appIconCache.get(key) ?: runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(pixels, pixels)
            }.getOrNull()?.also { appIconCache.put(key, it) }
        }
    }
    NativeGlass(Modifier.size(48.dp), radius = 14) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.size(38.dp)) }
                ?: Icon(painterResource(R.drawable.privacy_apps), null, Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary)
        }
    }
}

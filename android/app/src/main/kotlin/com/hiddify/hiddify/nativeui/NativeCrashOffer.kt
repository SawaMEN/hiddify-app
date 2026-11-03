package com.hiddify.hiddify.nativeui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativelog.NativeLogFiles
import com.hiddify.hiddify.nativelog.NativeCoreDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Offer the existing local crash log once. No report is uploaded or shared automatically. */
@Composable
internal fun NativeCrashOffer(onOpenLogs: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var offered by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        offered = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.filesDir, "app.log")
                val coreCrash = try { NativeCoreDiagnostics.newestCrash() }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { null } // Older/offline cores cannot provide an inventory.
                val text = if (file.isFile && file.length() > 0L) NativeLogFiles.readTail(file, 64 * 1024) else ""
                val jvmCrash = text.takeIf { it.lineSequence().firstOrNull().orEmpty().contains(" ERROR JVM crash") }
                if (coreCrash == null && jvmCrash == null) return@runCatching false
                val fingerprint = MessageDigest.getInstance("SHA-256").digest((coreCrash.orEmpty() + "\n" + jvmCrash.orEmpty()).toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val prefs = context.getSharedPreferences("FlutterSharedPreferences", android.content.Context.MODE_PRIVATE)
                if (prefs.getString("flutter.last_seen_crash_report", "") == fingerprint) false
                else prefs.edit().putString("flutter.last_seen_crash_report", fingerprint).commit()
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                false
            }
        }
    }
    if (offered) AlertDialog(onDismissRequest = { offered = false },
        title = { Text(stringResource(R.string.native_crash_offer_title)) },
        text = { Text(stringResource(R.string.native_crash_offer_text)) },
        confirmButton = { NativeTextButton(onClick = { offered = false; onOpenLogs() }) {
            Text(stringResource(R.string.native_crash_offer_open))
        } },
        dismissButton = { NativeTextButton(onClick = { offered = false }) {
            Text(stringResource(android.R.string.cancel))
        } })
}

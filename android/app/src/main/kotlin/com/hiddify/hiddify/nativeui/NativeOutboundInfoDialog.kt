package com.hiddify.hiddify.nativeui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeOutbound
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NativeOutboundInfoDialog(outbound: NativeOutbound, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val unavailable = stringResource(R.string.native_outbound_map_unavailable)
    val yes = stringResource(R.string.native_outbound_yes)
    val no = stringResource(R.string.native_outbound_no)
    AlertDialog(onDismissRequest = onDismiss, title = { SelectionContainer { Text(outbound.name) } }, text = {
        SelectionContainer {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(R.string.native_outbound_full_tag, outbound.tag)
                InfoRow(R.string.native_outbound_type, outbound.type)
                if (outbound.testTimestampMs > 0) InfoRow(R.string.native_outbound_test_time,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(outbound.testTimestampMs)))
                InfoRow(R.string.native_outbound_test_delay,
                    if (outbound.delayMs > 65000) stringResource(R.string.native_outbound_test_failed)
                    else stringResource(R.string.native_outbounds_delay, outbound.delayMs))
                outbound.ipInfo?.let { info ->
                    InfoRow(R.string.native_outbound_ip, info.ip)
                    InfoRow(R.string.native_outbound_country, info.countryCode)
                    InfoRow(R.string.native_outbound_region, info.region)
                    InfoRow(R.string.native_outbound_city, info.city)
                    if (info.asn > 0) InfoRow(R.string.native_outbound_asn, info.asn.toString())
                    InfoRow(R.string.native_outbound_organization, info.organization)
                    if (info.latitude.isFinite() && info.longitude.isFinite() && info.latitude in -90.0..90.0 &&
                        info.longitude in -180.0..180.0 && (info.latitude != 0.0 || info.longitude != 0.0)) {
                        InfoRow(R.string.native_outbound_location, "${info.latitude}, ${info.longitude}") {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://maps.apple.com/?ll=${info.latitude},${info.longitude}")))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    InfoRow(R.string.native_outbound_postal, info.postalCode)
                }
                InfoRow(R.string.native_outbound_upload, outboundBytes(outbound.upload, locale))
                InfoRow(R.string.native_outbound_download, outboundBytes(outbound.download, locale))
                InfoRow(R.string.native_outbound_selected, if (outbound.selected) yes else no)
                InfoRow(R.string.native_outbound_group, if (outbound.isGroup) yes else no)
                InfoRow(R.string.native_outbound_secure, if (outbound.secure) yes else no)
                if (outbound.port > 0) InfoRow(R.string.native_outbound_port, outbound.port.toString())
                InfoRow(R.string.native_outbound_host, outbound.host)
                InfoRow(R.string.native_outbound_child, outbound.selectedChild.orEmpty())
            }
        }
    }, confirmButton = { NativeTextButton(onClick = onDismiss) { Text(stringResource(R.string.native_outbound_close)) } })
}

@Composable
private fun InfoRow(title: Int, value: String, onClick: (() -> Unit)? = null) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), Modifier.weight(.45f), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(.55f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (onClick != null) TextDecoration.Underline else null,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Match Dart's binary units and precision, including its TB upper bound. */
private fun outboundBytes(bytes: Long, locale: Locale): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    val decimals = when (unit) { 0, 1 -> 0; 2 -> 1; else -> 3 }
    return String.format(locale, "%.$decimals" + "f %s", value, units[unit])
}

package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.NativeOutboundsRepository
import com.hiddify.hiddify.nativecore.NativeProxySpeedPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun NativeProxySpeedTools(items: List<NativeOutbound>, connected: Boolean) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
        NativeTextButton(onClick = { open = true }, enabled = connected && NativeProxySpeedPolicy.targets(items).isNotEmpty()) {
            Icon(painterResource(R.drawable.native_proxy_speedometer), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.native_proxy_speed_test))
        }
    }
    if (open) NativeProxySpeedDialog(items, onDismiss = { open = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NativeProxySpeedBadge(outbound: NativeOutbound, enabled: Boolean = true) {
    var direction by remember { mutableStateOf<Int?>(null) }
    val download = proxyRate(outbound.downloadSpeed, outbound.downloadTesting, outbound.downloadSpeedCached)
    val upload = proxyRate(outbound.uploadSpeed, outbound.uploadTesting, outbound.uploadSpeedCached)
    Text("↓ $download   ↑ $upload", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.combinedClickable(enabled = enabled,
            onClickLabel = stringResource(R.string.native_proxy_upload_speed),
            onLongClickLabel = stringResource(R.string.native_proxy_download_speed),
            onClick = { direction = 1 }, onLongClick = { direction = 0 }).padding(vertical = 6.dp))
    direction?.let { NativeProxySpeedDialog(listOf(outbound), it, onDismiss = { direction = null }) }
}

@Composable
private fun proxyRate(rate: Long, testing: Boolean, cached: Boolean): String = when {
    testing -> stringResource(R.string.native_proxy_speed_testing)
    rate < 0 -> stringResource(R.string.native_proxy_speed_failed)
    rate == 0L -> "—"
    else -> String.format(Locale.getDefault(), "%.1f Mbps", rate.toDouble() * 8 / 1_000_000) +
        if (cached) " · " + stringResource(R.string.native_proxy_speed_cached) else ""
}

@Composable
internal fun NativeProxySpeedDialog(items: List<NativeOutbound>, initialDirection: Int = 2, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("FlutterSharedPreferences", 0) }
    var bytes by remember { mutableLongStateOf((prefs.all["flutter.proxies_speed_test_size"] as? Number)?.toLong()
        ?.takeIf { it in NativeProxySpeedPolicy.sizes } ?: NativeProxySpeedPolicy.MB) }
    var direction by remember { mutableIntStateOf(initialDirection) }
    var limit by remember { mutableIntStateOf(20) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val targets = remember(items, limit) { NativeProxySpeedPolicy.targets(items, if (items.size == 1) 1 else limit) }
    val total = NativeProxySpeedPolicy.estimateBytes(bytes, targets.size, direction != 0, direction != 1)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.native_proxy_speed_test)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.native_proxy_speed_budget, targets.size, total / NativeProxySpeedPolicy.MB))
                Text(stringResource(R.string.native_proxy_speed_budget_note), style = MaterialTheme.typography.bodySmall)
                SpeedChoice(stringResource(R.string.native_proxy_speed_direction), listOf(
                    stringResource(R.string.native_proxy_download_speed), stringResource(R.string.native_proxy_upload_speed),
                    stringResource(R.string.native_proxy_speed_both)), direction, !busy) { direction = it }
                SpeedChoice(stringResource(R.string.native_proxy_speed_size), NativeProxySpeedPolicy.sizes.map { "${it / NativeProxySpeedPolicy.MB} MB" },
                    NativeProxySpeedPolicy.sizes.indexOf(bytes), !busy) { bytes = NativeProxySpeedPolicy.sizes[it] }
                if (items.size > 1) {
                    val limits = listOf(5, 10, 20, 30, 0)
                    SpeedChoice(stringResource(R.string.native_proxy_speed_count), limits.map {
                        if (it == 0) stringResource(R.string.native_proxy_all) else it.toString()
                    }, limits.indexOf(limit), !busy) { limit = limits[it] }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                NativeTextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; error = null
                        try { NativeOutboundsRepository().clearSpeedTests() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.native_proxy_speed_clear)) }
            }
        },
        confirmButton = {
            NativeTextButton(enabled = !busy && targets.isNotEmpty(), onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        prefs.edit().putLong("flutter.proxies_speed_test_size", bytes).apply()
                        val repository = NativeOutboundsRepository()
                        for (target in targets) {
                            if (direction != 0) repository.speedTest(target.tag, bytes, upload = true)
                            if (direction != 1) repository.speedTest(target.tag, bytes, upload = false)
                        }
                        onDismiss()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.native_proxy_speed_start)) }
        },
        dismissButton = { NativeTextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.native_outbound_close)) } })
}

@Composable
private fun SpeedChoice(title: String, choices: List<String>, index: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$title: ${choices.getOrElse(index) { choices.first() }}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEachIndexed { i, value ->
                DropdownMenuItem(text = { Text(value) }, onClick = { expanded = false; onSelect(i) })
            }
        }
    }
}

package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.NativeProxyRowState
import com.hiddify.hiddify.nativecore.rowState
import com.hiddify.hiddify.nativeprofile.NativeProfile
import com.hiddify.hiddify.nativeprofile.NativeProfileOverrides

/** Temporary filters never remove a proxy from the persisted selection. */
@Composable
internal fun NativeKeepProxiesDialog(
    profile: NativeProfile,
    items: List<NativeOutbound>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?, List<String>?) -> Unit,
) {
    val proxies = remember(items) {
        items.filter { !it.isGroup && "§hide§" !in it.tag }.distinctBy { it.tag }
    }
    val saved = remember(profile.userOverride, profile.populatedHeaders) {
        NativeProfileOverrides.selection(profile.populatedHeaders, profile.userOverride)
    }
    val previousTags = saved.getAsJsonArray("keep-tags")?.map { it.asString }?.toSet().orEmpty()
    val initialTop = saved.get("keep-top")?.asInt ?: -1
    var manual by remember(profile.id) { mutableStateOf(previousTags.isNotEmpty()) }
    var top by remember(profile.id) { mutableIntStateOf(initialTop) }
    var chosen by remember(profile.id) {
        mutableStateOf(if (previousTags.isNotEmpty()) previousTags else proxies.map { it.tag }.toSet())
    }
    var onlyChosen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("all") }
    var protocol by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var protocolMenu by remember { mutableStateOf(false) }
    var countryMenu by remember { mutableStateOf(false) }
    val protocols = remember(proxies) {
        proxies.map { it.type.substringBefore(' ').trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val countries = remember(proxies) {
        proxies.mapNotNull { it.ipInfo?.countryCode?.uppercase()?.takeIf { code -> code.length == 2 } }.distinct().sorted()
    }
    val visible = proxies.filter {
        (!onlyChosen || it.tag in chosen) &&
            (status == "all" || (status == "ready") == (it.rowState() == NativeProxyRowState.READY)) &&
            (protocol.isEmpty() || it.type.substringBefore(' ').trim() == protocol) &&
            (country.isEmpty() || it.ipInfo?.countryCode?.equals(country, ignoreCase = true) == true) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.tag.contains(query, ignoreCase = true))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.native_keep_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.native_keep_hint), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = manual, onClick = { manual = true }, enabled = !busy,
                        label = { Text(stringResource(R.string.native_keep_manual)) })
                    FilterChip(selected = !manual, onClick = { manual = false }, enabled = !busy,
                        label = { Text(stringResource(R.string.native_keep_fastest)) })
                }
                if (!manual) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val choices = (listOf(-1, 3, 5, 10, 20, 30) +
                            (if (initialTop > 0) listOf(initialTop) else emptyList())).distinct()
                        choices.forEach { n ->
                            FilterChip(selected = top == n, onClick = { top = n }, enabled = !busy,
                                label = { Text(if (n < 0) stringResource(R.string.native_keep_all)
                                    else stringResource(R.string.native_keep_count, n)) })
                        }
                    }
                } else {
                    OutlinedTextField(value = query, onValueChange = { query = it.take(120) },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                        label = { Text(stringResource(R.string.native_outbounds_search)) })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = onlyChosen, onClick = { onlyChosen = !onlyChosen }, enabled = !busy,
                            label = { Text(stringResource(R.string.native_keep_selected)) })
                        FilterChip(selected = status == "ready", onClick = { status = if (status == "ready") "all" else "ready" }, enabled = !busy,
                            label = { Text(stringResource(R.string.native_keep_answering)) })
                        FilterChip(selected = status == "paused", onClick = { status = if (status == "paused") "all" else "paused" }, enabled = !busy,
                            label = { Text(stringResource(R.string.native_keep_paused)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            NativeTextButton(onClick = { protocolMenu = true }, enabled = !busy) {
                                Text(protocol.ifEmpty { stringResource(R.string.native_keep_protocol) })
                            }
                            DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_keep_all)) },
                                    onClick = { protocol = ""; protocolMenu = false })
                                protocols.forEach { item ->
                                    DropdownMenuItem(text = { Text(item) }, onClick = { protocol = item; protocolMenu = false })
                                }
                            }
                        }
                        Box {
                            NativeTextButton(onClick = { countryMenu = true }, enabled = !busy) {
                                Text(country.ifEmpty { stringResource(R.string.native_keep_country) })
                            }
                            DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.native_keep_all)) },
                                    onClick = { country = ""; countryMenu = false })
                                countries.forEach { item ->
                                    DropdownMenuItem(text = { Text(item) }, onClick = { country = item; countryMenu = false })
                                }
                            }
                        }
                    }
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        visible.forEach { proxy ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                chosen = if (proxy.tag in chosen) chosen - proxy.tag else chosen + proxy.tag
                            }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = proxy.tag in chosen, onCheckedChange = null, enabled = !busy)
                                Column(Modifier.weight(1f)) {
                                    Text(proxy.name, maxLines = 1)
                                    Text(proxy.type, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(if (proxy.delayMs > 0) proxy.delayMs.toString() + " ms" else "—",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(chosen.size.toString() + " / " + proxies.size.toString(),
                        style = MaterialTheme.typography.labelMedium)
                    if (chosen.isEmpty()) Text(stringResource(R.string.native_keep_choose),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            NativeTextButton(onClick = {
                if (manual) onSave(null, chosen.toList().sorted())
                else onSave(top, emptyList())
            }, enabled = !busy && (!manual || chosen.isNotEmpty())) {
                Text(stringResource(R.string.native_keep_apply))
            }
        },
        dismissButton = {
            NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

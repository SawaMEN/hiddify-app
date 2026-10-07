package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.NativeOutboundGroup
import com.hiddify.hiddify.nativecore.presentNativeOutbounds
import com.hiddify.hiddify.nativepreferences.NativeOutboundSort

@Composable
fun NativeOutboundsScreen(
    groups: List<NativeOutboundGroup>,
    connected: Boolean,
    sort: NativeOutboundSort,
    onChangeSort: (NativeOutboundSort) -> Unit,
    busyTag: String?,
    smartSelection: Boolean,
    smartSelectionBusy: Boolean,
    adaptiveNetwork: Boolean,
    onChangeSmartSelection: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String, String) -> Unit,
    onTest: (String) -> Unit,
    onTestActive: () -> Unit,
) {
    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(lifecycleOwner, connected) {
        if (connected) lifecycleOwner?.lifecycle?.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { refresh(); delay(3000) }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Pair<String, String>?>(null) }
    val clearLabel = stringResource(R.string.native_outbounds_clear_search)
    val presented = remember(groups, query, sort) {
        groups.map { it.copy(items = presentNativeOutbounds(it.items, query, sort)) }.filter { it.items.isNotEmpty() }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.native_outbounds_title), Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = busyTag == null) {
                Icon(painterResource(R.drawable.native_refresh), stringResource(R.string.native_logs_refresh))
            }
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.native_back_arrow), stringResource(R.string.native_back))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                singleLine = true, label = { Text(stringResource(R.string.native_outbounds_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.native_search), null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" },
                        modifier = Modifier.semantics { contentDescription = clearLabel }) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                })
            Box {
                IconButton(onClick = { sortOpen = true }, enabled = !smartSelectionBusy) {
                    Icon(painterResource(R.drawable.native_sort), stringResource(R.string.native_outbounds_sort))
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    NativeOutboundSort.entries.forEach { mode ->
                        DropdownMenuItem(text = { Text(stringResource(sortTitle(mode)),
                            fontWeight = if (sort == mode) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { sortOpen = false; onChangeSort(mode) }, enabled = !smartSelectionBusy)
                    }
                }
            }
        }
        NativeGlass(Modifier.fillMaxWidth()) {
            NativePreferenceSwitch(R.string.native_smart_selection_title,
                if (adaptiveNetwork) R.string.native_smart_selection_adaptive else R.string.native_smart_selection_summary,
                R.drawable.native_layers, smartSelection, !smartSelectionBusy && busyTag == null, onChangeSmartSelection)
        }
        if (groups.isEmpty()) {
            NativeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.native_outbounds_empty), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.native_outbounds_empty_summary), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            NativeOutlinedButton(onClick = onTestActive, enabled = busyTag == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.native_outbounds_test_active))
            }
            if (presented.isEmpty()) {
                Text(stringResource(R.string.native_outbounds_no_matches), Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = if (maxWidth < 600.dp) 1 else (maxWidth.value / 268).toInt().coerceAtLeast(1)
                LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    presented.forEach { group ->
                        item(key = listOf("group", group.tag), span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.native_outbounds_group_summary, group.type,
                                group.selectedTag.ifBlank { "—" }), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(group.items, key = { listOf("outbound", group.tag, it.tag) }) { outbound ->
                            OutboundTile(outbound, group.selectedTag == outbound.tag, group.selectable && busyTag == null,
                                busyTag == null, busyTag == outbound.tag, { onSelect(group.tag, outbound.tag) },
                                { onTest(outbound.tag) }, { details = group.tag to outbound.tag })
                        }
                    }
                }
            }
        }
    }
    details?.let { (groupTag, tag) ->
        val group = groups.firstOrNull { it.tag == groupTag }
        group?.items?.firstOrNull { it.tag == tag }?.let { outbound ->
            NativeOutboundInfoDialog(outbound.copy(selected = group.selectedTag == tag), onDismiss = { details = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutboundTile(outbound: NativeOutbound, selected: Boolean, selectable: Boolean, operationsEnabled: Boolean,
    busy: Boolean, onSelect: () -> Unit, onTest: () -> Unit, onInfo: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    NativeGlass(Modifier.fillMaxWidth(), radius = 20, accent = if (selected) scheme.primary else scheme.outline) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp)
            .semantics { this.selected = selected }
            .combinedClickable(role = Role.RadioButton, onClick = { if (selectable) onSelect() }, onLongClick = onInfo)
            .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            NativeOutboundCountryBadge(outbound.ipInfo?.countryCode.orEmpty())
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(outbound.name, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(outbound.type, outbound.selectedChild?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant)
                if (selected) Text(stringResource(R.string.native_outbounds_selected), style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary)
            }
            if (outbound.delayMs != 0) Text(if (outbound.delayMs > 65000) "×" else outbound.delayMs.toString(),
                style = MaterialTheme.typography.labelLarge, color = when {
                    outbound.delayMs < 800 -> if (dark) Color(0xFF8BC34A) else Color(0xFF4CAF50)
                    outbound.delayMs < 1500 -> if (dark) Color(0xFFFF9800) else Color(0xFFFF6E40)
                    else -> if (dark) Color(0xFFFF5252) else Color(0xFFF44336)
                })
            IconButton(onClick = onTest, enabled = operationsEnabled) {
                Icon(painterResource(R.drawable.native_flash), stringResource(if (busy) R.string.native_outbounds_testing else R.string.native_outbounds_test))
            }
            IconButton(onClick = onInfo) { Icon(painterResource(R.drawable.native_info), stringResource(R.string.native_outbounds_info)) }
        }
    }
}

private fun sortTitle(sort: NativeOutboundSort): Int = when (sort) {
    NativeOutboundSort.UNSORTED -> R.string.native_outbounds_sort_unsorted
    NativeOutboundSort.NAME -> R.string.native_outbounds_sort_name
    NativeOutboundSort.DELAY -> R.string.native_outbounds_sort_delay
    NativeOutboundSort.USAGE -> R.string.native_outbounds_sort_usage
}

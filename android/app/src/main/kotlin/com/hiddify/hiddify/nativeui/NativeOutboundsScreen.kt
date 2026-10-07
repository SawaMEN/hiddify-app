package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
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
import com.hiddify.hiddify.nativeui.NativeTextField as OutlinedTextField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
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
    val presented = remember(groups, query, sort) {
        groups.map { it.copy(items = presentNativeOutbounds(it.items, query, sort)) }.filter { it.items.isNotEmpty() }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.native_back_arrow), stringResource(R.string.native_back))
                }
                Text(stringResource(R.string.native_outbounds_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                Box {
                    IconButton(onClick = { sortOpen = true }, enabled = !smartSelectionBusy) {
                        Icon(painterResource(R.drawable.native_sort), stringResource(R.string.native_outbounds_sort))
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        NativeOutboundSort.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(stringResource(sortTitle(mode))) },
                                onClick = { sortOpen = false; onChangeSort(mode) }, enabled = !smartSelectionBusy)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp),
                singleLine = true, label = { Text(stringResource(R.string.native_outbounds_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.native_search), null) })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.native_smart_selection_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(if (adaptiveNetwork) R.string.native_smart_selection_adaptive
                        else R.string.native_smart_selection_summary), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = smartSelection, onCheckedChange = onChangeSmartSelection,
                    enabled = !smartSelectionBusy && busyTag == null)
            }
            if (groups.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.native_outbounds_empty))
                }
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = if (maxWidth < 600.dp) 1 else (maxWidth.value / 268).toInt().coerceAtLeast(1)
                LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 100.dp)) {
                    presented.forEach { group ->
                        items(group.items, key = { listOf("outbound", group.tag, it.tag) }) { outbound ->
                            OutboundTile(outbound, group.selectedTag == outbound.tag, group.selectable && busyTag == null,
                                { onSelect(group.tag, outbound.tag) }, { details = group.tag to outbound.tag })
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { if (busyTag == null) groups.firstOrNull()?.let { onTest(it.tag) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(painterResource(R.drawable.native_flash), stringResource(R.string.native_outbounds_test))
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
private fun OutboundTile(outbound: NativeOutbound, selected: Boolean, selectable: Boolean,
    onSelect: () -> Unit, onInfo: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    NativeGlass(Modifier.fillMaxWidth(), radius = 20, accent = if (selected) scheme.primary else scheme.outline) {
        Row(Modifier.fillMaxWidth().height((80 * LocalDensity.current.fontScale.coerceIn(1f, 2f)).dp)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .semantics { this.selected = selected }
            .combinedClickable(role = Role.RadioButton, onClick = { if (selectable) onSelect() }, onLongClick = onInfo)
            .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            NativeOutboundCountryBadge(outbound.ipInfo?.countryCode.orEmpty())
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(outbound.name, style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) scheme.primary else scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(outbound.type + if (outbound.isGroup) " (${outbound.selectedChild.orEmpty().trim()})" else "",
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (outbound.delayMs != 0) Text(if (outbound.delayMs > 65000) "×" else outbound.delayMs.toString(),
                    style = MaterialTheme.typography.bodyMedium, color = when {
                        outbound.delayMs < 800 -> if (dark) Color(0xFF8BC34A) else Color(0xFF4CAF50)
                        outbound.delayMs < 1500 -> if (dark) Color(0xFFFF9800) else Color(0xFFFF6E40)
                        else -> if (dark) Color(0xFFFF5252) else Color(0xFFF44336)
                    })
                if (outbound.download > 0) Text("⬩", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun sortTitle(sort: NativeOutboundSort): Int = when (sort) {
    NativeOutboundSort.UNSORTED -> R.string.native_outbounds_sort_unsorted
    NativeOutboundSort.NAME -> R.string.native_outbounds_sort_name
    NativeOutboundSort.DELAY -> R.string.native_outbounds_sort_delay
    NativeOutboundSort.USAGE -> R.string.native_outbounds_sort_usage
}

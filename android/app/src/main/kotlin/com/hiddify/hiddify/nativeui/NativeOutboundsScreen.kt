package com.hiddify.hiddify.nativeui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hiddify.hiddify.nativecore.typeWithoutStatus
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.currentIpInfo
import com.hiddify.hiddify.nativecore.NativeOutboundGroup
import com.hiddify.hiddify.nativecore.NativeOutboundsPresentationPolicy
import com.hiddify.hiddify.nativecore.nameWithDetour
import com.hiddify.hiddify.nativecore.presentNativeOutbounds
import com.hiddify.hiddify.nativepreferences.NativeOutboundSort

@Composable
internal fun NativeOutboundsScreen(
    connected: Boolean,
    sort: NativeOutboundSort,
    onChangeSort: (NativeOutboundSort) -> Unit,
    busyTag: String?,
    smartSelection: Boolean,
    smartSelectionBusy: Boolean,
    adaptiveNetwork: Boolean,
    onChangeSmartSelection: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSelect: (String, String) -> Unit,
    onTest: (String) -> Unit,
    operationError: String?,
    onDismissOperationError: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val dismissError by rememberUpdatedState(onDismissOperationError)
    LaunchedEffect(operationError) {
        if (operationError != null) { snackbar.showSnackbar(operationError); dismissError() }
    }
    val motion = LocalNativeMotionEnabled.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var presentation by remember { mutableStateOf(
        NativeOutboundsPresentationPolicy.initial(connected).let { initial ->
            if (connected) com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.lastKnownGroup()
                ?.let { cached -> initial.copy(group = cached, loading = false) } ?: initial
            else initial
        },
    ) }
    val group = presentation.group
    val failure = presentation.error
    val loading = presentation.loading
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleOwner, connected, retry) {
        // An explicit retry starts a fresh stream generation. Do not display a group
        // from the previous generation under the new connection.
        presentation = when {
            !connected -> NativeOutboundsPresentationPolicy.initial(false)
            retry > 0 -> NativeOutboundsPresentationPolicy.initial(true)
            else -> NativeOutboundsPresentationPolicy.resubscribing(presentation)
        }
        if (retry > 0) com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
        if (connected) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.updates.collect { update ->
                if (!com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.isCurrent(update)) return@collect
                presentation = NativeOutboundsPresentationPolicy.received(presentation, update)
            }
        }
    }
    var query by rememberSaveable(stateSaver = NativeStringDraftSaver) { mutableStateOf("") }
    val preferences = LocalContext.current.applicationContext.getSharedPreferences("FlutterSharedPreferences", android.content.Context.MODE_PRIVATE)
    var signalStyle by remember { mutableStateOf(preferences.getString("flutter.proxies_ping_style", "number") == "signal") }
    val toggleSignalStyle = {
        signalStyle = !signalStyle
        preferences.edit().putString("flutter.proxies_ping_style", if (signalStyle) "signal" else "number").apply()
    }
    var sortOpen by remember { mutableStateOf(false) }
    var detailsTag by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var previousOrder by remember(query, sort) { mutableStateOf<List<String>>(emptyList()) }
    val sorted = remember(group, query, sort) { presentNativeOutbounds(group?.items.orEmpty(), query, sort) }
    LaunchedEffect(sorted, gridState.isScrollInProgress) {
        if (!gridState.isScrollInProgress) previousOrder = sorted.map { it.tag }
    }
    val presented = if (gridState.isScrollInProgress)
        com.hiddify.hiddify.nativecore.keepNativeOutboundOrder(sorted, previousOrder) else sorted
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.native_back_arrow), stringResource(R.string.native_back))
                }
                Text(stringResource(R.string.native_outbounds_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                Box {
                    IconButton(onClick = { sortOpen = true }, enabled = !smartSelectionBusy) {
                        Icon(painterResource(R.drawable.native_outbound_sort), stringResource(R.string.native_outbounds_sort))
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_proxy_signal_style)) },
                            trailingIcon = { Checkbox(signalStyle, onCheckedChange = null) },
                            onClick = { toggleSignalStyle(); sortOpen = false })
                        HorizontalDivider()
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
            if (connected && failure != null && group != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(failure.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    NativeTextButton(onClick = { retry++ }) { Text(stringResource(R.string.native_profiles_retry)) }
                }
            }
            when {
                !connected -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.native_outbounds_disconnected))
                }
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                failure != null && group == null -> Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(failure.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    NativeTextButton(onClick = { retry++ }) { Text(stringResource(R.string.native_profiles_retry)) }
                }
                group == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.native_outbounds_empty))
                }
                else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = if (maxWidth < 600.dp) 1 else (maxWidth.value / 268).toInt().coerceAtLeast(1)
                    LazyVerticalGrid(columns = GridCells.Fixed(columns), state = gridState, modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 100.dp)) {
                        items(presented, key = { it.tag }) { outbound ->
                            OutboundTile(outbound, group?.selectedTag == outbound.tag,
                                group?.selectable == true && busyTag == null && !smartSelectionBusy,
                                { group?.let { onSelect(it.tag, outbound.tag) } }, { detailsTag = outbound.tag },
                                signalStyle = signalStyle, testEnabled = busyTag == null && !smartSelectionBusy,
                                onTest = { onTest(outbound.tag) }, onToggleStyle = toggleSignalStyle,
                                modifier = Modifier.animateItem(fadeInSpec = if (motion) androidx.compose.animation.core.tween(160) else null, placementSpec = if (motion && !gridState.isScrollInProgress) androidx.compose.animation.core.tween(220) else null, fadeOutSpec = if (motion) androidx.compose.animation.core.tween(120) else null))
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { if (connected && !loading && failure == null && busyTag == null) group?.let { onTest(it.tag) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(painterResource(R.drawable.native_flash), stringResource(R.string.native_outbounds_test))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    group?.items?.firstOrNull { it.tag == detailsTag }?.let { outbound ->
        NativeOutboundInfoDialog(outbound.copy(selected = group?.selectedTag == outbound.tag), onDismiss = { detailsTag = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutboundTile(outbound: NativeOutbound, selected: Boolean, selectable: Boolean,
    onSelect: () -> Unit, onInfo: () -> Unit, signalStyle: Boolean, testEnabled: Boolean,
    onTest: () -> Unit, onToggleStyle: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val duration = if (LocalNativeMotionEnabled.current) 220 else 0
    val selectedSurface by animateColorAsState(
        if (selected) scheme.primaryContainer else Color.Transparent,
        tween(duration), label = "selected proxy background",
    )
    val titleColor by animateColorAsState(
        if (selected) scheme.primary else scheme.onSurface,
        tween(duration), label = "selected proxy title",
    )
    val subtitleColor by animateColorAsState(
        if (selected) scheme.primary else scheme.onSurfaceVariant,
        tween(duration), label = "selected proxy subtitle",
    )
    NativeSurface(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp)
            .background(selectedSurface)
            .semantics { this.selected = selected }
            .combinedClickable(role = Role.RadioButton, onClick = { if (selectable) onSelect() }, onLongClick = onInfo)
            .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            NativeOutboundCountryBadge(outbound.currentIpInfo()?.countryCode.orEmpty(),
                organization = outbound.currentIpInfo()?.organization.orEmpty())
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(outbound.nameWithDetour(), style = MaterialTheme.typography.bodyLarge,
                    color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(outbound.typeWithoutStatus() + if (outbound.isGroup) " (${outbound.selectedChild.orEmpty().trim()})" else "",
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = subtitleColor)
            }
            NativeProxyStatusIndicator(outbound, signalStyle, testEnabled, onTest, onToggleStyle)
        }
    }
}

private fun sortTitle(sort: NativeOutboundSort): Int = when (sort) {
    NativeOutboundSort.UNSORTED -> R.string.native_outbounds_sort_unsorted
    NativeOutboundSort.NAME -> R.string.native_outbounds_sort_name
    NativeOutboundSort.DELAY -> R.string.native_outbounds_sort_delay
    NativeOutboundSort.USAGE -> R.string.native_outbounds_sort_usage
}

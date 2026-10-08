package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiddify.hiddify.R
import com.hiddify.hiddify.privacy.*
import kotlinx.coroutines.CancellationException

/** Shared entry points for both Routing settings and the VPN privacy page. */
@Composable
internal fun NativeCommunityPreferences(state: NativeSettingsState, enabled: Boolean,
    onSave: (Boolean, Boolean, String) -> Unit) {
    var editor by rememberSaveable { mutableStateOf<NativeCommunitySource?>(null) }
    for (source in NativeCommunitySource.entries) {
        val proxy = source == NativeCommunitySource.VPN
        val active = if (proxy) state.handbookProxy else state.handbookDirect
        val sites = if (proxy) state.handbookProxySites else state.handbookDirectSites
        val summary = when {
            !active -> stringResource(R.string.native_community_none)
            NativeCommunityLists.selected(sites).isEmpty() -> stringResource(R.string.native_community_all)
            else -> stringResource(R.string.native_community_selected, NativeCommunityLists.selected(sites).size)
        }
        NativePreferenceTile(if (proxy) R.string.native_privacy_handbook_proxy else R.string.native_privacy_handbook_direct,
            R.drawable.privacy_apps, summary, enabled) { editor = source }
    }
    editor?.let { source ->
        val proxy = source == NativeCommunitySource.VPN
        NativeCommunityListsDialog(source, if (proxy) state.handbookProxy else state.handbookDirect,
            if (proxy) state.handbookProxySites else state.handbookDirectSites, enabled,
            onDismiss = { editor = null }, onSave = { selection ->
                onSave(proxy, selection.enabled, selection.sites)
                editor = null
            })
    }
}

@Composable
private fun NativeCommunityListsDialog(source: NativeCommunitySource, active: Boolean, sites: String,
    enabled: Boolean, onDismiss: () -> Unit, onSave: (NativeCommunitySelection) -> Unit) {
    val context = LocalContext.current
    val repository = remember { NativeCommunityRepository(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var all by rememberSaveable { mutableStateOf(active && NativeCommunityLists.selected(sites).isEmpty()) }
    var selected by rememberSaveable { mutableStateOf(if (active) NativeCommunityLists.selected(sites).toList() else emptyList()) }
    var catalogue by remember { mutableStateOf<NativeCommunityCatalogue?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(source, attempt) {
        loading = true
        failed = false
        try { catalogue = repository.load(source) { catalogue = it } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val services = catalogue?.services.orEmpty()
    // Never silently discard saved selections missing from a refreshed catalogue.
    val known = services.map { it.id }.toSet()
    val rows = (services + selected.filter { it !in known }.map { NativeCommunityService(it, emptyList()) })
        .sortedBy { it.id.lowercase() }
        .filter { it.id.contains(query, true) || it.domains.any { domain -> domain.contains(query, true) } }
    val selection = runCatching { NativeCommunityLists.selection(all, selected.toSet()) }.getOrNull()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                NativePageHeader(stringResource(if (source == NativeCommunitySource.VPN)
                    R.string.native_privacy_handbook_proxy else R.string.native_privacy_handbook_direct), onDismiss)
                Text(source.host, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                NativeTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), singleLine = true,
                    placeholder = { Text(stringResource(R.string.native_community_search)) })
                Text(stringResource(R.string.native_community_apply_note), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (catalogue?.cached == true) Text(stringResource(if (loading) R.string.native_community_refreshing_cache else R.string.native_community_cached), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    NativeTextButton(onClick = { all = true; selected = emptyList() }, enabled = enabled && catalogue != null) {
                        Text(stringResource(R.string.native_community_select_all))
                    }
                    NativeTextButton(onClick = { all = false; selected = emptyList() }, enabled = enabled && (!loading || catalogue != null)) {
                        Text(stringResource(R.string.native_community_clear))
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.native_community_load_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    NativeTextButton(onClick = { attempt++ }) { Text(stringResource(R.string.native_profiles_retry)) }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(rows, key = { it.id }) { service ->
                        val checked = all || service.id in selected
                        Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled && (!loading || catalogue != null), role = Role.Checkbox) { value ->
                            val next = NativeCommunityLists.toggle(all, selected.toSet(), known, service.id, value)
                            all = false
                            selected = next.toList()
                        }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = null, enabled = enabled && (!loading || catalogue != null))
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(service.id, style = MaterialTheme.typography.bodyLarge)
                                Text(when {
                                    service.id !in known -> stringResource(R.string.native_community_saved_service)
                                    service.domains.isEmpty() -> stringResource(R.string.native_community_network_service)
                                    else -> service.domains.joinToString(", ")
                                }, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (!loading && !failed && rows.isEmpty()) item {
                        Text(stringResource(R.string.native_community_no_results), Modifier.padding(16.dp))
                    }
                }
                if (selection == null) Text(stringResource(R.string.native_community_invalid_saved), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (all) stringResource(R.string.native_community_all) else
                        stringResource(R.string.native_community_selected, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    NativeButton(onClick = { selection?.let(onSave) }, enabled = enabled && (!loading || catalogue != null) && selection != null) {
                        Text(stringResource(R.string.native_community_apply))
                    }
                }
            }
        }
    }
}

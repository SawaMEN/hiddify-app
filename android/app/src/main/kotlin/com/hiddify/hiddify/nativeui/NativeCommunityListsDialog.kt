package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
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
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var searchCollapsed by remember(query) { mutableStateOf(emptyList<String>()) }
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
    val rows = (services + selected.filter { it !in known }.map { NativeCommunityService(it, emptyList(), "_saved") })
        .sortedBy { it.id.lowercase() }
    val groups = rows.groupBy { it.group }.toSortedMap(compareBy<String> { it == "cdn" }.thenBy { it == "_saved" }.thenBy { it })
    val matches: (NativeCommunityService) -> Boolean = { service ->
        service.id.contains(query, true) || service.domains.any { it.contains(query, true) }
    }
    val interactive = enabled && (!loading || catalogue != null)
    val titles = groups.keys.associateWith { communityGroupTitle(it) }
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
                Text(stringResource(R.string.native_community_category_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                if (catalogue?.groupsUnavailable == true) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.native_community_groups_unavailable), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    NativeTextButton(onClick = { attempt++ }, enabled = !loading) { Text(stringResource(R.string.native_profiles_retry)) }
                }
                LazyColumn(Modifier.weight(1f)) {
                    var shown = false
                    groups.forEach { (group, members) ->
                        val title = titles.getValue(group)
                        val groupMatch = query.isNotBlank() && (title.contains(query, true) || group.contains(query, true))
                        val visible = if (groupMatch) members else members.filter(matches)
                        if (visible.isNotEmpty()) {
                            shown = true
                            val memberIds = members.map { it.id }.toSet()
                            val count = if (all) members.size else members.count { it.id in selected }
                            val checked = when (count) {
                                0 -> ToggleableState.Off
                                members.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            val open = if (query.isNotBlank()) group !in searchCollapsed else group in expanded
                            item(key = "group:$group") {
                                Row(Modifier.fillMaxWidth().clickable {
                                    if (query.isNotBlank()) searchCollapsed = if (group in searchCollapsed) searchCollapsed - group else searchCollapsed + group
                                    else expanded = if (group in expanded) expanded - group else expanded + group
                                }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TriStateCheckbox(state = checked, enabled = interactive, onClick = {
                                        selected = NativeCommunityLists.toggleGroup(all, selected.toSet(), known, memberIds,
                                            checked != ToggleableState.On).toList()
                                        all = false
                                    })
                                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                        Text(title, style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.native_community_group_count, count, members.size),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(painterResource(R.drawable.native_chevron),
                                        stringResource(if (open) R.string.native_community_collapse else R.string.native_community_expand, title),
                                        Modifier.size(24.dp).rotate(if (open) 90f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (open) items(visible, key = { "service:${it.id}" }) { service ->
                                val selectedService = all || service.id in selected
                                Row(Modifier.fillMaxWidth().toggleable(selectedService, enabled = interactive, role = Role.Checkbox) { value ->
                                    selected = NativeCommunityLists.toggle(all, selected.toSet(), known, service.id, value).toList()
                                    all = false
                                }.heightIn(min = 64.dp).padding(start = 40.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(selectedService, onCheckedChange = null, enabled = interactive)
                                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                        Text(service.id, style = MaterialTheme.typography.bodyLarge)
                                        Text(when {
                                            service.id !in known -> stringResource(R.string.native_community_saved_service)
                                            service.domains.isEmpty() -> stringResource(R.string.native_community_network_service)
                                            else -> service.domains.joinToString(", ")
                                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (!loading && !failed && !shown) item {
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

@Composable
private fun communityGroupTitle(group: String): String {
    val label = when (group) {
        "" -> R.string.native_community_group_other
        "_saved" -> R.string.native_community_group_saved
        "ai" -> R.string.native_community_group_ai
        "anime" -> R.string.native_community_group_anime
        "art" -> R.string.native_community_group_art
        "block" -> R.string.native_community_group_block
        "cdn" -> R.string.native_community_group_cdn
        "discord" -> R.string.native_community_group_discord
        "games" -> R.string.native_community_group_games
        "geoblock" -> R.string.native_community_group_geoblock
        "googleplay" -> R.string.native_community_group_googleplay
        "messengers" -> R.string.native_community_group_messengers
        "meta" -> R.string.native_community_group_meta
        "music" -> R.string.native_community_group_music
        "news" -> R.string.native_community_group_news
        "porn" -> R.string.native_community_group_porn
        "repo" -> R.string.native_community_group_repo
        "shop" -> R.string.native_community_group_shop
        "socials" -> R.string.native_community_group_socials
        "tools" -> R.string.native_community_group_tools
        "torrent" -> R.string.native_community_group_torrent
        "video" -> R.string.native_community_group_video
        "youtube" -> R.string.native_community_group_youtube
        else -> null
    }
    return if (label != null) stringResource(label) else group.replace('_', ' ').replace('-', ' ')
}

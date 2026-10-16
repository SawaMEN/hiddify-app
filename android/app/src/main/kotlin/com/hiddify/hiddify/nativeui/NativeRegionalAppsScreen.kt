package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.hiddify.hiddify.nativeui.NativeButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativerouting.NativeInstalledApp
import com.hiddify.hiddify.privacy.NativeRegionalAppKind
import com.hiddify.hiddify.privacy.NativeRegionalAppSnapshot

@Composable
fun NativeRegionalAppsScreen(
    kind: NativeRegionalAppKind,
    snapshot: NativeRegionalAppSnapshot?,
    revision: Int,
    busy: Boolean,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onSave: (com.hiddify.hiddify.privacy.NativeRegionalAppChoices) -> Unit,
    onReset: () -> Unit,
) {
    var search by rememberSaveable(kind, stateSaver = NativeStringDraftSaver) { mutableStateOf("") }
    var serviceSearch by rememberSaveable(kind, stateSaver = NativeStringDraftSaver) { mutableStateOf("") }
    // Only package names enter saved state, never PackageManager objects or icons.
    var draft by rememberSaveable(kind, stateSaver = NativeNullableStringListDraftSaver) { mutableStateOf<List<String>?>(null) }
    var serviceDraft by rememberSaveable(kind, stateSaver = NativeNullableStringListDraftSaver) { mutableStateOf<List<String>?>(null) }
    var networkDraft by rememberSaveable(kind) { mutableStateOf<Boolean?>(null) }
    var domainsDraft by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var loadRequested by remember(kind) { mutableStateOf(false) }
    LaunchedEffect(kind, busy) {
        if (snapshot == null && !busy && !loadRequested) {
            loadRequested = true
            onReload()
        }
    }
    var appliedRevision by rememberSaveable(kind) { mutableStateOf(revision) }
    LaunchedEffect(revision) {
        if (revision > 0 && revision != appliedRevision) { draft = null; serviceDraft = null; networkDraft = null; domainsDraft = null }
        appliedRevision = revision
    }
    val serviceIds = remember(serviceDraft, snapshot) { serviceDraft?.toSet() ?: snapshot?.selectedServices.orEmpty() }
    val services = snapshot?.services.orEmpty()
    val installedPackages = remember(snapshot) {
        snapshot?.apps.orEmpty().filter { it.packageName !in snapshot?.missing.orEmpty() }
            .mapTo(hashSetOf()) { it.packageName }
    }
    val legacyExtras = remember(snapshot) {
        snapshot?.selected.orEmpty() - com.hiddify.hiddify.privacy.NativeRegionalServices.packages(
            services, snapshot?.selectedServices.orEmpty()).toSet()
    }
    val selected = remember(kind, snapshot, draft, serviceDraft) {
        val configured = draft?.toSet() ?: if (serviceDraft != null && snapshot?.manual == false)
            com.hiddify.hiddify.privacy.NativeRegionalServices.packages(services, serviceIds)
                .filter { it in installedPackages }.toSet() + legacyExtras
            else snapshot?.selected.orEmpty()
        if (kind == NativeRegionalAppKind.PROXY) configured.intersect(installedPackages) else configured
    }
    val query = search.trim()
    val serviceQuery = serviceSearch.trim()
    val servicesMatchingSearch = remember(services, serviceQuery) {
        services.filter { service ->
            service.name.contains(serviceQuery, ignoreCase = true) ||
                service.id.contains(serviceQuery, ignoreCase = true) ||
                service.domains.any { it.contains(serviceQuery, ignoreCase = true) }
        }
    }
    val allServiceDomains = remember(services) { services.flatMap { it.domains }.toSet() }
    val selectedServiceDomains = remember(services, serviceIds) {
        services.filter { it.id in serviceIds }.flatMap { it.domains }.toSet()
    }

    val unavailableSelection = if (kind == NativeRegionalAppKind.DIRECT) selected else emptySet()
    val (missing, listedApps) = remember(kind, snapshot, unavailableSelection) {
        val loadedApps = snapshot?.apps.orEmpty()
        val additionalMissing = unavailableSelection - loadedApps.mapTo(hashSetOf()) { it.packageName }
        val missing = snapshot?.missing.orEmpty() + additionalMissing
        val available = loadedApps + additionalMissing.map { NativeInstalledApp(it, it, false) }
        // The catalogue suggests defaults; it must not restrict installed app choices.
        missing to if (kind == NativeRegionalAppKind.PROXY) available.filter { it.packageName !in missing } else available
    }
    val visible = remember(listedApps, query) {
        listedApps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    val canChange = !busy && snapshot != null
    val network = networkDraft ?: snapshot?.russianNetworkBypass ?: true
    val domains = domainsDraft ?: snapshot?.domains.orEmpty()
    val choices = com.hiddify.hiddify.privacy.NativeRegionalAppChoices(
        packages = if (draft != null || snapshot?.manual == true) selected else null,
        services = if (serviceDraft != null || snapshot?.servicesManual == true) serviceIds else null,
        russianNetworkBypass = if (kind == NativeRegionalAppKind.DIRECT) networkDraft else null,
        domains = domainsDraft,
    )
    val valid = remember(kind, networkDraft, domainsDraft) {
        runCatching {
            com.hiddify.hiddify.privacy.NativeRegionalServices.options(
                com.hiddify.hiddify.privacy.NativeRegionalOptions(), kind, choices)
        }.isSuccess
    }


    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                NativePageHeader(stringResource(if (kind == NativeRegionalAppKind.DIRECT)
                    R.string.native_regional_apps_direct_title else R.string.native_regional_apps_proxy_title), onBack)
            }
            if (busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            if (kind == NativeRegionalAppKind.PROXY) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NativeTextButton(enabled = canChange,
                            modifier = Modifier.weight(1f), onClick = {
                                search = ""
                                serviceSearch = ""
                                onReset()
                            }) {
                            Text(stringResource(R.string.native_regional_clear_all))
                        }
                        NativeTextButton(enabled = canChange,
                            modifier = Modifier.weight(1f), onClick = {
                                search = ""
                                serviceSearch = ""
                                onSave(com.hiddify.hiddify.privacy.NativeRegionalAppChoices(
                                    packages = emptySet(), services = emptySet(), domains = ""))
                            }) {
                            Text(stringResource(R.string.native_regional_uncheck_all))
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.native_blocked_services_title),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.native_blocked_domains_count,
                            selectedServiceDomains.size, allServiceDomains.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = serviceSearch, onValueChange = { serviceSearch = it.take(256) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.native_blocked_domains_search)) })
                    }
                }
                items(servicesMatchingSearch, key = { "service:${it.id}" }) { service ->
                    val checked = service.id in serviceIds
                    Row(Modifier.fillMaxWidth().clickable(enabled = canChange) {
                        serviceDraft = (if (checked) serviceIds - service.id else serviceIds + service.id).sorted()
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(service.name, style = MaterialTheme.typography.bodyLarge)
                            Text(service.domains.joinToString(", ").ifEmpty {
                                    stringResource(R.string.native_community_saved_service)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked, onCheckedChange = null, enabled = canChange)
                    }
                }
                if (snapshot != null && servicesMatchingSearch.isEmpty()) {
                    item { Text(stringResource(R.string.native_blocked_domains_empty)) }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.native_regional_apps_direct_hint),
                            style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.native_privacy_direct_network),
                                    style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.native_privacy_direct_network_summary),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(network, enabled = canChange, onCheckedChange = { networkDraft = it })
                        }
                        OutlinedTextField(value = domains,
                            onValueChange = { if (it.length <= 8192) domainsDraft = it },
                            enabled = canChange, modifier = Modifier.fillMaxWidth(), minLines = 2,
                            label = { Text(stringResource(R.string.native_regional_direct_domains)) },
                            supportingText = { Text(stringResource(R.string.native_regional_domains_hint)) }, isError = !valid)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (kind == NativeRegionalAppKind.PROXY)
                        R.string.native_blocked_apps_title else R.string.native_regional_apps_direct_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.native_blocked_apps_count,
                        selected.size, listedApps.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = search, onValueChange = { search = it.take(256) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.native_per_app_search)) })
                }
            }
            if (!busy && snapshot == null) {
                item { NativeTextButton(onClick = onReload) { Text(stringResource(R.string.native_profiles_retry)) } }
            }
            if (snapshot != null && visible.isEmpty()) {
                item { Text(stringResource(R.string.native_regional_apps_empty)) }
            }
            items(visible, key = { "app:${it.packageName}" }) { app ->
                val checked = app.packageName in selected
                val enabled = canChange && (checked || selected.size < 256)
                fun toggle() {
                    draft = (if (checked) selected - app.packageName else selected + app.packageName).sorted()
                }
                Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { toggle() }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NativeInstalledAppIcon(app.packageName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        if (app.packageName in missing) {
                            Text(stringResource(R.string.native_regional_apps_missing), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Checkbox(checked = checked, enabled = enabled, onCheckedChange = { toggle() })
                }
            }
            if (kind == NativeRegionalAppKind.DIRECT && snapshot != null && (snapshot.manual || snapshot.servicesManual)) {
                item {
                    NativeTextButton(enabled = canChange, onClick = onReset) {
                        Text(stringResource(R.string.native_regional_apps_reset))
                    }
                }
            }
        }
        Button(enabled = canChange && valid && selected.size <= 256 &&
            (draft != null || serviceDraft != null || networkDraft != null || domainsDraft != null),
            modifier = Modifier.fillMaxWidth(), onClick = { onSave(choices) }) {
            Text(stringResource(R.string.native_regional_save))
        }
    }
}

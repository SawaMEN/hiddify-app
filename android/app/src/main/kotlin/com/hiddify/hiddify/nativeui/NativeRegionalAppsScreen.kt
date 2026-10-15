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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    // Only package names enter saved state, never PackageManager objects or icons.
    var draft by rememberSaveable(kind, stateSaver = NativeNullableStringListDraftSaver) { mutableStateOf<List<String>?>(null) }
    var serviceDraft by rememberSaveable(kind, stateSaver = NativeNullableStringListDraftSaver) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(kind) { if (snapshot == null && !busy) onReload() }
    var appliedRevision by rememberSaveable(kind) { mutableStateOf(revision) }
    LaunchedEffect(revision) {
        if (revision > 0 && revision != appliedRevision) { draft = null; serviceDraft = null }
        appliedRevision = revision
    }
    val serviceIds = serviceDraft?.toSet() ?: snapshot?.selectedServices.orEmpty()
    val services = snapshot?.services.orEmpty()
    val installedPackages = snapshot?.apps.orEmpty().filter { it.packageName !in snapshot?.missing.orEmpty() }
        .map { it.packageName }.toSet()
    val legacyExtras = snapshot?.selected.orEmpty() - com.hiddify.hiddify.privacy.NativeRegionalServices.packages(
        services, snapshot?.selectedServices.orEmpty()).toSet()
    val selected = draft?.toSet() ?: if (serviceDraft != null && snapshot?.manual == false)
        com.hiddify.hiddify.privacy.NativeRegionalServices.packages(services, serviceIds)
            .filter { it in installedPackages }.toSet() + legacyExtras
        else snapshot?.selected.orEmpty()
    val query = search.trim()
    val loadedApps = snapshot?.apps.orEmpty()
    val additionalMissing = selected - loadedApps.map { it.packageName }.toSet()
    val missing = snapshot?.missing.orEmpty() + additionalMissing
    val visible = (loadedApps + additionalMissing.map { NativeInstalledApp(it, it, false) }).filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }
    val canChange = !busy && snapshot != null

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                NativePageHeader(stringResource(if (kind == NativeRegionalAppKind.DIRECT)
                    R.string.native_regional_apps_direct_title else R.string.native_regional_apps_proxy_title), onBack)
            }
            item {
                Text(stringResource(if (kind == NativeRegionalAppKind.DIRECT) R.string.native_regional_apps_direct_hint
                    else R.string.native_regional_apps_proxy_hint))
                Text(stringResource(R.string.native_regional_apps_save_hint), style = MaterialTheme.typography.bodySmall)
            }
            item {
                if (snapshot != null) {
                    Text(stringResource(if (draft != null || snapshot.manual) R.string.native_regional_apps_manual
                        else R.string.native_regional_apps_auto))
                }
                Text(stringResource(R.string.native_regional_apps_count, selected.size))
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            item {
                NativeTextButton(enabled = canChange, onClick = onReset) { Text(stringResource(R.string.native_regional_apps_reset)) }
                NativeTextButton(enabled = !busy, onClick = onReload) { Text(stringResource(R.string.native_regional_apps_reload)) }
            }
            item {
                OutlinedTextField(value = search, onValueChange = { search = it.take(256) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.native_per_app_search)) })
            }
            if (kind == NativeRegionalAppKind.PROXY) {
                item {
                    Text(stringResource(R.string.native_blocked_services_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.native_blocked_services_hint), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NativeTextButton(enabled = canChange, onClick = { serviceDraft = services.map { it.id } }) {
                            Text(stringResource(R.string.native_community_select_all))
                        }
                        NativeTextButton(enabled = canChange, onClick = { serviceDraft = emptyList() }) {
                            Text(stringResource(R.string.native_community_clear))
                        }
                    }
                }
                items(services.filter { service -> service.name.contains(query, true) || service.id.contains(query, true) ||
                    service.domains.any { it.contains(query, true) } }, key = { "service:${it.id}" }) { service ->
                    val checked = service.id in serviceIds
                    Row(Modifier.fillMaxWidth().clickable(enabled = canChange) {
                        serviceDraft = (if (checked) serviceIds - service.id else serviceIds + service.id).sorted()
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(service.name, style = MaterialTheme.typography.bodyLarge)
                            Text(service.domains.joinToString(", ").ifEmpty { stringResource(R.string.native_community_saved_service) },
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(checked, onCheckedChange = null, enabled = canChange)
                    }
                }
                item { Text(stringResource(R.string.native_blocked_apps_title), style = MaterialTheme.typography.titleMedium) }
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
        }
        Button(enabled = canChange && selected.size <= 256 && (draft != null || serviceDraft != null), modifier = Modifier.fillMaxWidth(),
            onClick = { onSave(com.hiddify.hiddify.privacy.NativeRegionalAppChoices(
                packages = if (draft != null || snapshot?.manual == true) selected else null,
                services = if (serviceDraft != null || snapshot?.servicesManual == true) serviceIds else null,
            )) }) { Text(stringResource(R.string.native_regional_save)) }
    }
}

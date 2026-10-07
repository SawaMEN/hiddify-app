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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onSave: (Set<String>) -> Unit,
    onReset: () -> Unit,
) {
    var search by rememberSaveable(kind) { mutableStateOf("") }
    // Only package names enter saved state, never PackageManager objects or icons.
    var draft by rememberSaveable(kind) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(kind) { if (snapshot == null && !busy) onReload() }
    LaunchedEffect(revision) { if (revision > 0) draft = null }
    val selected = draft?.toSet() ?: snapshot?.selected.orEmpty()
    val query = search.trim()
    val loadedApps = snapshot?.apps.orEmpty()
    val additionalMissing = selected - loadedApps.map { it.packageName }.toSet()
    val missing = snapshot?.missing.orEmpty() + additionalMissing
    val visible = (loadedApps + additionalMissing.map { NativeInstalledApp(it, it, false) }).filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }
    val canChange = !busy && snapshot != null

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (kind == NativeRegionalAppKind.DIRECT) R.string.native_regional_apps_direct_title
                    else R.string.native_regional_apps_proxy_title), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(stringResource(R.string.native_back)) }
            }
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
            Button(enabled = canChange && selected.size <= 256, modifier = Modifier.fillMaxWidth(),
                onClick = { onSave(selected) }) { Text(stringResource(R.string.native_regional_save)) }
            TextButton(enabled = canChange, onClick = onReset) { Text(stringResource(R.string.native_regional_apps_reset)) }
            TextButton(enabled = !busy, onClick = onReload) { Text(stringResource(R.string.native_regional_apps_reload)) }
        }
        item {
            OutlinedTextField(value = search, onValueChange = { search = it.take(256) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.native_per_app_search)) })
        }
        if (snapshot != null && visible.isEmpty()) {
            item { Text(stringResource(R.string.native_regional_apps_empty)) }
        }
        items(visible, key = { it.packageName }) { app ->
            val checked = app.packageName in selected
            val enabled = canChange && (checked || selected.size < 256)
            fun toggle() {
                draft = (if (checked) selected - app.packageName else selected + app.packageName).sorted()
            }
            Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { toggle() }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
}

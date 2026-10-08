package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeButton as Button
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeProfilesSheet(
    profiles: List<NativeProfile>, busyProfileId: String?, loading: Boolean, loadFailed: Boolean,
    sortByName: Boolean, ascending: Boolean, onSort: (Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit, onRetry: () -> Unit, onUpdateAll: () -> Unit,
    onSelect: (NativeProfile) -> Unit, onDelete: (NativeProfile) -> Unit,
    onRefresh: (NativeProfile) -> Unit, onEdit: (NativeProfile) -> Unit,
    onCopyConfig: (NativeProfile) -> Unit, onExportConfig: (NativeProfile) -> Unit,
) {
    LaunchedEffect(profiles, loading, loadFailed) {
        if (!loading && !loadFailed && profiles.isEmpty()) onDismiss()
    }
    ModalBottomSheet(modifier = Modifier.nativeGlassDecoration(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp, onDismissRequest = onDismiss, sheetMaxWidth = 456.dp,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), dragHandle = null) {
        NativeProfilesScreen(profiles, busyProfileId, onDismiss, {}, onSelect, onDelete, onRefresh,
            onEdit, onCopyConfig, onExportConfig, loading, loadFailed, onRetry, onUpdateAll,
            sortByName, ascending, onSort, isSheet = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NativeProfilesScreen(
    profiles: List<NativeProfile>,
    busyProfileId: String?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (NativeProfile) -> Unit,
    onDelete: (NativeProfile) -> Unit,
    onRefresh: (NativeProfile) -> Unit,
    onEdit: (NativeProfile) -> Unit,
    onCopyConfig: (NativeProfile) -> Unit,
    onExportConfig: (NativeProfile) -> Unit,
    loading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onUpdateAll: () -> Unit = {},
    sortByName: Boolean = false,
    ascending: Boolean = false,
    onSort: (Boolean, Boolean) -> Unit = { _, _ -> },
    isSheet: Boolean = false,
) {
    // Save IDs instead of stale profile objects; resolve dialogs from the refreshed snapshot.
    var shareId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val motion = LocalNativeMotionEnabled.current
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    val busy = busyProfileId != null
    val sorted = remember(profiles, sortByName, ascending) {
        val comparator = if (sortByName) compareBy<NativeProfile> { it.name }
            else compareBy<NativeProfile> { profileUpdateEpoch(it.lastUpdate) }
        // Dart keeps the active profile first, then usable subscriptions, before the chosen sort.
        profiles.sortedWith(compareByDescending<NativeProfile> { it.active }
            .thenByDescending { profileUsable(it) }
            .then(if (ascending) comparator else comparator.reversed()))
    }

    @Composable fun ProfileBody(modifier: Modifier) {
        when {
            loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            loadFailed -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.native_profiles_load_failed))
                NativeTextButton(onClick = onRetry) { Text(stringResource(R.string.native_profiles_retry)) }
            }
            else -> LazyColumn(modifier, contentPadding = PaddingValues(start = 12.dp, top = 12.dp,
                end = 12.dp, bottom = if (isSheet) 12.dp else 84.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sorted, key = { it.id }) { profile ->
                    NativeProfileTile(profile, modifier = Modifier.animateItem(fadeInSpec = if (motion) androidx.compose.animation.core.tween(160) else null, placementSpec = if (motion) androidx.compose.animation.core.spring() else null, fadeOutSpec = if (motion) androidx.compose.animation.core.tween(120) else null), busy = busy, onShare = { shareId = profile.id },
                        onClick = { onSelect(profile) }, onDelete = { deleteId = profile.id },
                        onRefresh = { onRefresh(profile) }, onEdit = { onEdit(profile) })
                }
            }
        }
    }
    if (isSheet) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Keep the expanded list bounded; the Material sheet supplies the drag/partial state.
            Column(Modifier.fillMaxWidth().height(maxHeight * .85f)) {
                ProfileBody(Modifier.weight(1f).fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Button(onClick = { sortOpen = true }) {
                        Icon(painterResource(R.drawable.native_sort), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.native_profiles_sort))
                    }
                    Button(onClick = onUpdateAll, enabled = !busy && !loading && !loadFailed && profiles.any { it.isRemote }) {
                        Icon(painterResource(R.drawable.native_profile_update), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.native_profiles_update_all))
                    }
                }
            }
        }
    } else Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.native_back_arrow), stringResource(R.string.native_back)) }
                Text(stringResource(R.string.native_profiles), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onUpdateAll, enabled = !busy && !loading && !loadFailed && profiles.any { it.isRemote }) {
                    Icon(painterResource(R.drawable.native_profile_update), stringResource(R.string.native_profiles_update_all))
                }
                IconButton(onClick = { sortOpen = true }) { Icon(painterResource(R.drawable.native_sort), stringResource(R.string.native_profiles_sort)) }
                Spacer(Modifier.width(8.dp))
            }
        }, floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { if (!busy) onAdd() },
                icon = { Icon(painterResource(R.drawable.native_add), null) },
                text = { Text(stringResource(R.string.native_profile_add)) })
        }) { padding -> ProfileBody(Modifier.fillMaxSize().padding(padding)) }

    if (sortOpen) AlertDialog(onDismissRequest = { sortOpen = false },
        title = { Text(stringResource(R.string.native_profiles_sort)) }, text = {
            Column {
                listOf(false, true).forEach { byName ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortByName == byName, onClick = { onSort(byName, ascending) })
                        NativeTextButton(onClick = { onSort(byName, ascending) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (byName) R.string.native_profiles_sort_name else R.string.native_profiles_sort_updated))
                        }
                        if (sortByName == byName) IconButton(onClick = { onSort(byName, !ascending) }) {
                            Icon(painterResource(R.drawable.native_arrow_up),
                                stringResource(if (ascending) R.string.native_profiles_sort_ascending else R.string.native_profiles_sort_descending),
                                modifier = Modifier.then(if (ascending) Modifier else Modifier.rotate(180f)))
                        }
                    }
                }
            }
        }, confirmButton = { NativeTextButton(onClick = { sortOpen = false }) { Text(stringResource(android.R.string.ok)) } })
    profiles.firstOrNull { it.id == shareId }?.let { profile ->
        NativeProfileShareDialog(profile, onDismiss = { shareId = null },
            onCopyConfig = { onCopyConfig(profile); shareId = null }, onExportConfig = { onExportConfig(profile); shareId = null })
    }
    profiles.firstOrNull { it.id == deleteId }?.let { profile ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.native_profile_delete_title)) },
            text = { Text(stringResource(R.string.native_profile_delete_message, profile.name)) },
            confirmButton = { NativeTextButton(enabled = !busy, onClick = { onDelete(profile); deleteId = null }) {
                Text(stringResource(R.string.native_profile_delete))
            } }, dismissButton = { NativeTextButton(onClick = { deleteId = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

private fun profileUpdateEpoch(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }
    .recoverCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    .getOrDefault(Long.MIN_VALUE)


private fun profileUsable(profile: NativeProfile): Boolean {
    val used = if (profile.upload != null && profile.download != null) profile.upload.toDouble() + profile.download.toDouble() else null
    val quotaAvailable = used == null || profile.total == null || profile.total == 0L || used / profile.total.toDouble() < 1.0
    val expiry = profile.expire?.let { profileUpdateEpoch(it) }
    return quotaAvailable && (expiry == null || expiry == Long.MIN_VALUE || expiry > System.currentTimeMillis())
}

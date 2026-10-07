package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfile
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.text.NumberFormat
import kotlinx.coroutines.delay

/** ProfileTile / ProfileSubscriptionInfo at the last Dart reference, shared by home and list. */
@Composable
internal fun NativeProfileTile(
    profile: NativeProfile,
    isMain: Boolean = false,
    busy: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable(profile.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (profile.active) scheme.primary.copy(alpha = .55f) else scheme.outlineVariant),
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (profile.isRemote || !isMain) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (isMain) {
                        IconButton(onClick = onRefresh, enabled = !busy) {
                            Icon(painterResource(R.drawable.native_profile_update), stringResource(R.string.native_profile_update))
                        }
                    } else {
                        IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                            Icon(painterResource(R.drawable.native_more), stringResource(R.string.native_profile_actions))
                        }
                        DropdownMenu(expanded = menuOpen && !busy, onDismissRequest = { menuOpen = false }) {
                            if (profile.isRemote) DropdownMenuItem(
                                text = { Text(stringResource(R.string.native_profile_update)) },
                                onClick = { menuOpen = false; onRefresh() },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.native_profile_share)) },
                                onClick = { menuOpen = false; onShare() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.native_profile_edit)) },
                                onClick = { menuOpen = false; onEdit() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.native_profile_delete)) },
                                onClick = { menuOpen = false; onDelete() })
                        }
                    }
                }
                if (profile.active) VerticalDivider(Modifier.height(48.dp)) else Spacer(Modifier.width(1.dp))
            }
            val clickLabel = stringResource(if (isMain) R.string.native_profiles else R.string.native_profile_use)
            Column(
                Modifier.weight(1f).clickable(enabled = !busy, role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { if (isMain) liveRegion = LiveRegionMode.Polite },
            ) {
                Row(Modifier.fillMaxWidth().then(if (isMain) Modifier.padding(vertical = 4.dp) else Modifier),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (isMain) Icon(painterResource(R.drawable.native_drop_down), null)
                }
                if (profile.isRemote && profile.upload != null && profile.download != null && profile.total != null && profile.expire != null) {
                    NativeProfileSubscriptionInfo(profile)
                }
            }
        }
    }
}

@Composable
private fun NativeProfileSubscriptionInfo(profile: NativeProfile) {
    // Time-based labels must change even when the subscription itself is not downloaded again.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(profile.id, profile.expire) {
        while (true) { now = Instant.now(); delay(60_000) }
    }
    val consumed = profile.consumed ?: 0L
    val total = profile.total ?: 0L
    val ratio = if (total > 0) (consumed.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
    val expiration = remember(profile.expire) { parseProfileExpiration(profile.expire) }
    val expired = expiration?.let { !it.isAfter(now) } == true
    val days = expiration?.let { Duration.between(now, it).toDays() }
    val remaining = when {
        expired -> stringResource(R.string.native_subscription_expired)
        ratio >= 1f -> stringResource(R.string.native_subscription_no_traffic)
        days != null -> stringResource(R.string.native_subscription_remaining_days, if (days > 365) "∞" else days.toString())
        else -> ""
    }
    val formatter = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 } }
    val consumedText = formatter.format(consumed / 1_073_741_824.0) + " GiB"
    val totalText = formatter.format(total / 1_073_741_824.0) + " GiB"
    val trafficLabel = stringResource(R.string.native_subscription_traffic_semantics, consumedText, totalText)
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(16.dp)))
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (total > 10L * 1_099_511_627_776L) "∞ GiB" else "$consumedText / $totalText",
            Modifier.weight(1f).semantics { contentDescription = trafficLabel },
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(remaining, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall,
            color = if (expired || ratio >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(4.dp))
}

private fun parseProfileExpiration(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
}

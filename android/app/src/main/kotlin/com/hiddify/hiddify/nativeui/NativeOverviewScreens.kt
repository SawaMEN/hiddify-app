package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun NativePageHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    androidx.compose.material3.TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.native_back_arrow), stringResource(R.string.native_back))
            }
        },
        actions = actions,
        // The page frame already consumes status bar insets.
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
internal fun NativeSettingsLink(title: Int, icon: Int, onClick: () -> Unit, summary: Int? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.native_chevron), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun NativePreferenceValueRow(title: Int, value: String, enabled: Boolean, icon: Int = R.drawable.native_dns, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .38f).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.native_chevron), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

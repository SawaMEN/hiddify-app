package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun NativePreferenceTile(title: Int, icon: Int, value: String?, enabled: Boolean,
    checked: Boolean? = null, onToggle: ((Boolean) -> Unit)? = null,
    switchEnabled: Boolean = enabled, onClick: () -> Unit) {
    val interaction = if (checked == null || onToggle != null) Modifier.clickable(enabled = enabled, onClick = onClick)
        else Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
    Row(Modifier.fillMaxWidth().then(interaction).heightIn(min = if (value == null) 56.dp else 72.dp)
        .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else tint)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
        if (checked != null) Switch(checked, onCheckedChange = onToggle, enabled = switchEnabled)
    }
}

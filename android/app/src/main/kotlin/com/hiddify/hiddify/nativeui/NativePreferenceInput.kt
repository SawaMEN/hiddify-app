package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/** Editable preference with Dart TypeAhead's filtering and fallback suggestions. */
@Composable
internal fun NativePreferenceInput(
    value: String, onValueChange: (String) -> Unit, possibleValues: List<String>,
    enabled: Boolean, valid: Boolean, invalidMessage: String,
    keyboardOptions: KeyboardOptions, onDone: () -> Unit, modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val matches = possibleValues.filter { it.contains(value, ignoreCase = true) }
    val suggestions = if (matches.size <= 1) (listOf(value) + possibleValues).distinct() else matches
    Box(Modifier.fillMaxWidth()) {
        NativeTextField(value, {
            dismissed = false
            onValueChange(it)
        }, modifier = modifier.fillMaxWidth().onSizeChanged { width = it.width }.onFocusChanged {
            focused = it.isFocused
            if (!focused) dismissed = false
        }, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { if (valid && enabled) onDone() }),
            isError = !valid, supportingText = { if (!valid) Text(invalidMessage) })
        if (possibleValues.isNotEmpty()) DropdownMenu(
            expanded = enabled && focused && !dismissed,
            onDismissRequest = { dismissed = true },
            modifier = Modifier.width(with(density) { width.toDp() }),
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { suggestion ->
                Text(suggestion, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        onValueChange(suggestion)
                        dismissed = true
                    }.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

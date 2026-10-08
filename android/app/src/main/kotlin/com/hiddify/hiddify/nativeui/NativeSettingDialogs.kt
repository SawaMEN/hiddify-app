package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

/** SettingInputDialog: validate on confirmation; Enter moves focus to the OK action. */
@Composable
internal fun NativeSettingInputDialog(
    title: String, value: String, onValueChange: (String) -> Unit, valid: Boolean,
    enabled: Boolean, invalidMessage: String, onDismiss: () -> Unit, onConfirm: () -> Unit,
    onReset: () -> Unit, possibleValues: List<String> = emptyList(),
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
) {
    var attempted by rememberSaveable { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val okFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            if (attempted && !valid) Text(invalidMessage, color = MaterialTheme.colorScheme.error)
            NativePreferenceInput(value, onValueChange, possibleValues, enabled, valid, invalidMessage,
                keyboardOptions, onDone = { okFocus.requestFocus() },
                modifier = Modifier.focusRequester(inputFocus).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter, Key.Tab, Key.DirectionCenter)) {
                        okFocus.requestFocus()
                        true
                    } else false
                }, showInvalid = attempted && possibleValues.isNotEmpty(),
                hint = if (possibleValues.isEmpty()) title else null)
        }
    }, dismissButton = {
        Row {
            NativeTextButton(onClick = onReset, enabled = enabled) { Text(stringResource(R.string.native_quick_reset)) }
            NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel).uppercase()) }
        }
    }, confirmButton = {
        NativeTextButton(onClick = { attempted = true; if (valid) onConfirm() },
            enabled = enabled, modifier = Modifier.focusRequester(okFocus)) {
            Text(stringResource(android.R.string.ok).uppercase())
        }
    })
}

@Composable
internal fun NativeSettingPickerDialog(
    title: String, selected: String, choices: List<Pair<String, String>>, enabled: Boolean,
    onSelect: (String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            choices.forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().selectable(selected == value, enabled, Role.RadioButton) { onSelect(value) }
                    .heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == value, onClick = null, enabled = enabled)
                    Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }, confirmButton = {}, dismissButton = {
        Row {
            NativeTextButton(onClick = onReset, enabled = enabled) { Text(stringResource(R.string.native_quick_reset)) }
            NativeTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    })
}

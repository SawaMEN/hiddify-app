package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

/** Material 3 outlined input with shared keyboard and validation behavior. */
@Composable
internal fun NativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = LocalTextStyle.current,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, interactionSource = interaction,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, textStyle = textStyle, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
    )
}

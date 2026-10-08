package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Opaque inputs with distinct focus/error outlines. */
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
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, interactionSource = interaction,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, textStyle = textStyle, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            errorContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedBorderColor = scheme.outline,
            focusedTextColor = scheme.onSurface, unfocusedTextColor = scheme.onSurface,
            errorTextColor = scheme.onSurface,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            focusedLabelColor = scheme.primary,
            focusedPlaceholderColor = scheme.onSurfaceVariant,
            unfocusedPlaceholderColor = scheme.onSurfaceVariant,
        ),
    )
}

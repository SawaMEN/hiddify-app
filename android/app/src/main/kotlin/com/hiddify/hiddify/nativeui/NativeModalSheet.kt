package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiddify.hiddify.R

/** A stable viewport: no drag anchors, sheet offset or content-dependent window height. */
@Composable
internal fun NativeModalSheet(
    onDismissRequest: () -> Unit,
    sheetMaxWidth: Dp = 680.dp,
    canDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = { if (canDismiss) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnClickOutside = false, dismissOnBackPress = canDismiss)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Box(Modifier.fillMaxSize().clickable(enabled = canDismiss, onClick = onDismissRequest))
            Surface(Modifier.align(Alignment.BottomCenter).widthIn(max = sheetMaxWidth)
                .fillMaxWidth().height(maxHeight * .88f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End) {
                        IconButton(enabled = canDismiss, onClick = onDismissRequest) {
                            Icon(painterResource(R.drawable.native_close), stringResource(android.R.string.cancel))
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                }
            }
        }
    }
}

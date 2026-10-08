package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.nativepreferences.NativeOutboundSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeOutboundsSheet(
    connected: Boolean,
    sort: NativeOutboundSort,
    onChangeSort: (NativeOutboundSort) -> Unit,
    busyTag: String?,
    smartSelection: Boolean,
    smartSelectionBusy: Boolean,
    adaptiveNetwork: Boolean,
    onChangeSmartSelection: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit,
    onTest: (String) -> Unit,
    operationError: String?,
    onDismissOperationError: () -> Unit,
) {
    val height = (LocalConfiguration.current.screenHeightDp * .85f).dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = 900.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxWidth().height(height)) {
            NativeOutboundsScreen(connected = connected, sort = sort, onChangeSort = onChangeSort,
                busyTag = busyTag, smartSelection = smartSelection, smartSelectionBusy = smartSelectionBusy,
                adaptiveNetwork = adaptiveNetwork, onChangeSmartSelection = onChangeSmartSelection,
                onBack = onDismiss, onSelect = onSelect, onTest = onTest,
                operationError = operationError, onDismissOperationError = onDismissOperationError)
        }
    }
}

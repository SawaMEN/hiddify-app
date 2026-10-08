package com.hiddify.hiddify.nativeui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dart's autoDispose provider keeps the reveal state for 20 seconds without listeners. */
class NativeIpVisibilitySession : ViewModel() {
    var visible by mutableStateOf(false)
        private set
    private var listeners = 0
    private var expiration: Job? = null

    fun attach() {
        listeners++
        expiration?.cancel()
        expiration = null
    }

    fun detach() {
        listeners = (listeners - 1).coerceAtLeast(0)
        if (listeners != 0) return
        expiration?.cancel()
        expiration = viewModelScope.launch {
            delay(20_000)
            visible = false
        }
    }

    fun toggle() { visible = !visible }
}

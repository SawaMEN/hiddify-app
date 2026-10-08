package com.hiddify.hiddify.nativeconnection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** VPN-service owned state; an Activity only observes it. */
internal object NativeServiceState {
    private val mutableHealth = MutableStateFlow(NativeInternetHealth.UNCHECKED)
    private val mutableRecovery = MutableStateFlow(0)
    val health = mutableHealth.asStateFlow()
    val recovery = mutableRecovery.asStateFlow()
    fun health(value: NativeInternetHealth) { mutableHealth.value = value }
    fun recovery(attempt: Int) { mutableRecovery.value = attempt }
    fun reset() { health(NativeInternetHealth.UNCHECKED); recovery(0) }
}

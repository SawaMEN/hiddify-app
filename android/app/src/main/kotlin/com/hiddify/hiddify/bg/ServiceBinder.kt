package com.hiddify.hiddify.bg

import android.os.RemoteCallbackList
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.hiddify.hiddify.IService
import com.hiddify.hiddify.IServiceCallback
import com.hiddify.hiddify.constant.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ServiceBinder(private val status: MutableLiveData<Status>) : IService.Stub() {
    private val callbacks = RemoteCallbackList<IServiceCallback>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var closed = false
    private val observer = Observer<Status> { value -> broadcast { it.onServiceStatusChanged(value.ordinal) } }

    init { status.observeForever(observer) }

    fun broadcast(work: (IServiceCallback) -> Unit) {
        scope.launch {
            if (closed) return@launch
            val count = callbacks.beginBroadcast()
            try {
                repeat(count) { runCatching { work(callbacks.getBroadcastItem(it)) } }
            } finally {
                callbacks.finishBroadcast()
            }
        }
    }

    override fun getStatus(): Int = (status.value ?: Status.Stopped).ordinal
    override fun registerCallback(callback: IServiceCallback) { if (!closed) callbacks.register(callback) }
    override fun unregisterCallback(callback: IServiceCallback?) { callbacks.unregister(callback) }

    fun close() {
        if (closed) return
        closed = true
        status.removeObserver(observer)
        scope.cancel()
        callbacks.kill()
    }
}

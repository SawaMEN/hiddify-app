package com.hiddify.hiddify.bg

import com.hiddify.hiddify.IService
import com.hiddify.hiddify.IServiceCallback
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.Action
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.Status
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class ServiceConnection(private val context: Context, callback: Callback, private val register: Boolean = true) : ServiceConnection {
    companion object {
        private const val TAG = "ServiceConnection"
    }

    private val callback = ServiceCallback(callback)
    private var service: IService? = null

    val status: Status
        get() {
            val currentService = service ?: return Status.Stopped
            return try {
                Status.values().getOrNull(currentService.status) ?: Status.Stopped
            } catch (e: RemoteException) {
                Log.w(TAG, "failed to read status from disconnected service", e)
                Status.Stopped
            }
        }

    fun connect() {
        val intent =
            runBlocking {
                withContext(Dispatchers.IO) {
                    Intent(context, Settings.serviceClass()).setAction(Action.SERVICE)
                }
            }
        context.bindService(intent, this, AppCompatActivity.BIND_AUTO_CREATE)
        Log.d(TAG, "request connect")
    }

    fun disconnect() {
        clearService()
        try {
            context.unbindService(this)
        } catch (_: IllegalArgumentException) {
        }
        Log.d(TAG, "request disconnect")
    }

    fun reconnect() {
        clearService()
        try {
            context.unbindService(this)
        } catch (_: IllegalArgumentException) {
        }
        val intent =
            runBlocking {
                withContext(Dispatchers.IO) {
                    Intent(context, Settings.serviceClass()).setAction(Action.SERVICE)
                }
            }
        context.bindService(intent, this, AppCompatActivity.BIND_AUTO_CREATE)
        Log.d(TAG, "request reconnect")
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val service = IService.Stub.asInterface(binder)
        this.service = service
        try {
            if (register) service.registerCallback(callback)
            callback.onServiceStatusChanged(service.status)
        } catch (e: RemoteException) {
            Log.e(TAG, "initialize service connection", e)
            clearService()
        }
        Log.d(TAG, "service connected")
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        clearService()
        callback.onServiceStatusChanged(Status.Stopped.ordinal)
        Log.d(TAG, "service disconnected")
    }

    override fun onBindingDied(name: ComponentName?) {
        clearService()
        reconnect()
        Log.d(TAG, "service dead")
    }

    override fun onNullBinding(name: ComponentName?) {
        clearService()
        callback.onServiceStatusChanged(Status.Stopped.ordinal)
        Log.w(TAG, "service returned a null binding")
    }

    private fun clearService() {
        val currentService = service ?: return
        service = null
        if (!register) return
        try {
            currentService.unregisterCallback(callback)
        } catch (e: RemoteException) {
            Log.w(TAG, "failed to unregister callback from disconnected service", e)
        }
    }

    interface Callback {
        fun onServiceStatusChanged(status: Status)

        fun onServiceAlert(type: Alert, message: String?) {
        }
    }

    class ServiceCallback(private val callback: Callback) : IServiceCallback.Stub() {
        override fun onServiceStatusChanged(status: Int) {
            callback.onServiceStatusChanged(Status.values()[status])
        }

        override fun onServiceAlert(type: Int, message: String?) {
            callback.onServiceAlert(Alert.values()[type], message)
        }

        override fun onServiceWriteLog(message: String?) {
            //TODO("Not yet implemented")
        }

        override fun onServiceResetLogs(messages: List<String?>?) {
            //TODO("Not yet implemented")
        }
    }
}

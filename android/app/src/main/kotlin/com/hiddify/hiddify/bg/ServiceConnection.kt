package com.hiddify.hiddify.bg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.hiddify.hiddify.IService
import com.hiddify.hiddify.IServiceCallback
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.Action
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.Status

class ServiceConnection(
    private val context: Context,
    callback: Callback,
    private val register: Boolean = true,
) : ServiceConnection {
    companion object {
        private const val TAG = "ServiceConnection"
    }

    private val callback = ServiceCallback(callback)
    @Volatile
    private var service: IService? = null

    val status: Status
        get() {
            val currentService = service ?: return Status.Stopped
            return try {
                Status.values().getOrNull(currentService.status) ?: Status.Stopped
            } catch (e: RemoteException) {
                Log.w(TAG, "failed to read status from disconnected service", e)
                Status.Stopped
            } catch (e: RuntimeException) {
                Log.w(TAG, "invalid status from service", e)
                Status.Stopped
            }
        }

    fun connect() {
        val intent = Intent(context, Settings.serviceClass()).setAction(Action.SERVICE)
        runCatching {
            context.bindService(intent, this, AppCompatActivity.BIND_AUTO_CREATE)
        }.onFailure {
            Log.e(TAG, "failed to bind service", it)
            callback.onServiceStatusChanged(Status.Stopped.ordinal)
        }
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
        val intent = Intent(context, Settings.serviceClass()).setAction(Action.SERVICE)
        runCatching {
            context.bindService(intent, this, AppCompatActivity.BIND_AUTO_CREATE)
        }.onFailure {
            Log.e(TAG, "failed to rebind service", it)
            callback.onServiceStatusChanged(Status.Stopped.ordinal)
        }
        Log.d(TAG, "request reconnect")
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val service = IService.Stub.asInterface(binder)
        this.service = service
        try {
            if (register) service.registerCallback(callback)
            val initialStatus = Status.values().getOrNull(service.status) ?: Status.Stopped
            callback.onServiceStatusChanged(initialStatus.ordinal)
        } catch (e: RemoteException) {
            Log.e(TAG, "initialize service connection", e)
            clearService()
            callback.onServiceStatusChanged(Status.Stopped.ordinal)
        } catch (e: RuntimeException) {
            Log.e(TAG, "invalid service state while binding", e)
            clearService()
            callback.onServiceStatusChanged(Status.Stopped.ordinal)
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
        } catch (e: RuntimeException) {
            Log.w(TAG, "failed to clear service callback", e)
        }
    }

    interface Callback {
        fun onServiceStatusChanged(status: Status)

        fun onServiceAlert(type: Alert, message: String?) {
        }
    }

    class ServiceCallback(private val callback: Callback) : IServiceCallback.Stub() {
        override fun onServiceStatusChanged(status: Int) {
            val safeStatus = Status.values().getOrNull(status)
            if (safeStatus == null) {
                Log.w(TAG, "ignoring invalid service status ordinal: $status")
                callback.onServiceStatusChanged(Status.Stopped)
                return
            }
            callback.onServiceStatusChanged(safeStatus)
        }

        override fun onServiceAlert(type: Int, message: String?) {
            val safeAlert = Alert.values().getOrNull(type)
            if (safeAlert == null) {
                Log.w(TAG, "ignoring invalid service alert ordinal: $type")
                return
            }
            callback.onServiceAlert(safeAlert, message)
        }

        override fun onServiceWriteLog(message: String?) {
            // TODO: expose native service logs when needed.
        }

        override fun onServiceResetLogs(messages: List<String?>?) {
            // TODO: expose native service logs when needed.
        }
    }
}

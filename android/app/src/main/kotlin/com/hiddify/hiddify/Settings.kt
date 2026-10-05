package com.hiddify.hiddify

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hiddify.hiddify.bg.ProxyService
import com.hiddify.hiddify.bg.VPNService
import com.hiddify.hiddify.constant.PerAppProxyMode
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.constant.SettingsKey
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream

object Settings {

    var connectionDesired: Boolean
        get() = getBoolean("flutter.connection_desired", getBoolean("flutter.started_by_user", false))
        set(value) = preferences.edit().putBoolean("flutter.connection_desired", value).apply()

    private const val TAG = "A/Settings"

    private val preferences by lazy {
        val context = Application.application.applicationContext
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    }

    private const val LIST_IDENTIFIER = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

    private fun getString(key: String, defaultValue: String): String {
        val value = preferences.all[key]
        if (value == null) return defaultValue
        if (value is String) return value
        Log.w(TAG, "ignoring preference with invalid type: $key (${value.javaClass.simpleName})")
        return defaultValue
    }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val value = preferences.all[key]
        if (value == null) return defaultValue
        if (value is Boolean) return value
        Log.w(TAG, "ignoring preference with invalid type: $key (${value.javaClass.simpleName})")
        return defaultValue
    }

    private fun getInt(key: String, defaultValue: Int): Int {
        val value = preferences.all[key]
        if (value == null) return defaultValue
        if (value is Int) return value
        Log.w(TAG, "ignoring preference with invalid type: $key (${value.javaClass.simpleName})")
        return defaultValue
    }

    var perAppProxyMode: String
        get() = getString(SettingsKey.PER_APP_PROXY_MODE, PerAppProxyMode.OFF)
        set(value) = preferences.edit().putString(SettingsKey.PER_APP_PROXY_MODE, value).apply()

    val perAppProxyEnabled: Boolean
        get() = perAppProxyMode != PerAppProxyMode.OFF

    val perAppProxyList: List<String>
        get() {
            val stringValue = if (perAppProxyMode == PerAppProxyMode.INCLUDE) {
                getString(SettingsKey.PER_APP_PROXY_INCLUDE_LIST, "")
            } else {
                getString(SettingsKey.PER_APP_PROXY_EXCLUDE_LIST, "")
            }
            if (!stringValue.startsWith(LIST_IDENTIFIER)) {
                return stringValue.split(";").filter { it.isNotBlank() }
            }
            return try {
                decodeListString(stringValue.substring(LIST_IDENTIFIER.length))
            } catch (e: Exception) {
                Log.w(TAG, "failed to decode per-app proxy list", e)
                emptyList()
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun decodeListString(listString: String): List<String> {
        ObjectInputStream(ByteArrayInputStream(Base64.decode(listString, Base64.DEFAULT))).use { stream ->
            return (stream.readObject() as? List<*>)?.filterIsInstance<String>().orEmpty()
        }
    }

    var activeConfigPath: String
        get() = getString(SettingsKey.ACTIVE_CONFIG_PATH, "")
        set(value) = preferences.edit().putString(SettingsKey.ACTIVE_CONFIG_PATH, value).apply()

    var activeProfileName: String
        get() = getString(SettingsKey.ACTIVE_PROFILE_NAME, "")
        set(value) = preferences.edit().putString(SettingsKey.ACTIVE_PROFILE_NAME, value).apply()

    var serviceMode: String
        get() = getString(SettingsKey.SERVICE_MODE, ServiceMode.VPN)
        set(value) = preferences.edit().putString(SettingsKey.SERVICE_MODE, value).apply()

    var configOptions: String
        get() = getString(SettingsKey.CONFIG_OPTIONS, "")
        set(value) = preferences.edit().putString(SettingsKey.CONFIG_OPTIONS, value).apply()

    var debugMode: Boolean
        get() = getBoolean(SettingsKey.DEBUG_MODE, false)
        set(value) = preferences.edit().putBoolean(SettingsKey.DEBUG_MODE, value).apply()

    var disableMemoryLimit: Boolean
        get() = getBoolean(SettingsKey.DISABLE_MEMORY_LIMIT, false)
        set(value) = preferences.edit().putBoolean(SettingsKey.DISABLE_MEMORY_LIMIT, value).apply()

    var dynamicNotification: Boolean
        get() = getBoolean(SettingsKey.DYNAMIC_NOTIFICATION, true)
        set(value) = preferences.edit().putBoolean(SettingsKey.DYNAMIC_NOTIFICATION, value).apply()

    var systemProxyEnabled: Boolean
        get() = getBoolean(SettingsKey.SYSTEM_PROXY_ENABLED, true)
        set(value) = preferences.edit().putBoolean(SettingsKey.SYSTEM_PROXY_ENABLED, value).apply()

    var startedByUser: Boolean
        get() = getBoolean(SettingsKey.STARTED_BY_USER, false)
        set(value) = preferences.edit().putBoolean(SettingsKey.STARTED_BY_USER, value).apply()

    fun serviceClass(): Class<*> = when (serviceMode) {
        ServiceMode.VPN -> VPNService::class.java
        else -> ProxyService::class.java
    }

    private var currentServiceMode: String? = null

    suspend fun rebuildServiceMode(): Boolean {
        val newMode = if (serviceMode == ServiceMode.VPN) ServiceMode.VPN else ServiceMode.NORMAL
        if (currentServiceMode == newMode) return false
        currentServiceMode = newMode
        return true
    }

    var workingDir: String
        get() = getString(SettingsKey.WORKING_DIR, "./")
        set(value) = preferences.edit().putString(SettingsKey.WORKING_DIR, value).apply()

    var tempDir: String
        get() = getString(SettingsKey.TMP_DIR, "./")
        set(value) = preferences.edit().putString(SettingsKey.TMP_DIR, value).apply()

    var baseDir: String
        get() = getString(SettingsKey.BASE_DIR, "./")
        set(value) = preferences.edit().putString(SettingsKey.BASE_DIR, value).apply()

    var grpcFlutterPublicKey: ByteArray
        get() {
            val encoded = getString(SettingsKey.GRPC_FLUTTER_PUBLIC_KEY, "")
            if (encoded.isBlank()) return ByteArray(0)
            return try {
                Base64.decode(encoded, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "invalid stored gRPC public key", e)
                ByteArray(0)
            }
        }
        set(value) {
            val encoded = Base64.encodeToString(value, Base64.DEFAULT)
            preferences.edit().putString(SettingsKey.GRPC_FLUTTER_PUBLIC_KEY, encoded).apply()
        }

    var grpcServiceModePort: Int
        get() = getInt(SettingsKey.GRPC_PORT, 17079).takeIf { it in 1..65535 && it != 17078 } ?: 17079
        set(value) = preferences.edit().putInt(SettingsKey.GRPC_PORT, value).apply()

    var startCoreAfterStartingService: Boolean
        get() = getBoolean(SettingsKey.START_CORE_ON_STARTING_SERVICE, false)
        set(value) = preferences.edit().putBoolean(SettingsKey.START_CORE_ON_STARTING_SERVICE, value).apply()
}

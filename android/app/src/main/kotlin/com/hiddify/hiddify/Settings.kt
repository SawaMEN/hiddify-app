package com.hiddify.hiddify

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hiddify.hiddify.bg.ProxyService
import com.hiddify.hiddify.bg.VPNService
import com.hiddify.hiddify.constant.PerAppProxyMode
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.constant.SettingsKey
import com.hiddify.hiddify.privacy.NativeProxyPrivacy
import com.hiddify.hiddify.privacy.VpnServiceVisibility
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object Settings {

    val privacyUseRoot get() = getBoolean("flutter.privacy-use-root", false) && serviceMode == ServiceMode.VPN
    val wifiVpnSharing get() = getBoolean("flutter.wifi-vpn-sharing", false)
    val handbookRouting get() = getBoolean("flutter.handbook-routing", false)
    val handbookProxy get() = getBoolean("flutter.handbook-proxy", true)
    val handbookDirect get() = getBoolean("flutter.handbook-direct", true)
    val handbookProxySites get() = getString("flutter.handbook-proxy-sites", "")
    val handbookDirectSites get() = getString("flutter.handbook-direct-sites", "")
    val privacyRoutingMode get() = getString("flutter.privacy-routing-mode", "ru-bypass")
    val privacyRussianNetworkBypass get() = getBoolean("flutter.privacy-russian-network-bypass", true)
    val privacyRussianAppsBypass get() = getBoolean("flutter.privacy-russian-apps-bypass", true)
    val privacyRestrictedServicesProxy get() = getBoolean("flutter.privacy-restricted-services-proxy", true)
    val privacyDirectPackages get() = getString("flutter.privacy-direct-packages", "")
    val privacyProxyPackages get() = getString("flutter.privacy-proxy-packages", "")
    val privacyDirectDomains get() = getString("flutter.privacy-direct-domains", "")
    val privacyProxyDomains get() = getString("flutter.privacy-proxy-domains", "")
    val rootRouteTable: Int
        @Synchronized get() {
            val existing = getInt("privacy_root_route_table", 0)
            if (existing in 12000..29999) return existing
            val table = 12000 + java.security.SecureRandom().nextInt(18000)
            check(preferences.edit().putInt("privacy_root_route_table", table).commit())
            return table
        }
    val privacyFullTunnel get() = getBoolean("flutter.privacy-full-tunnel", false)
    val privacyHideLocalProxy get() = NativeProxyPrivacy.hideLocalProxyForCore(
        getBoolean(NativeProxyPrivacy.HIDE_LOCAL_PROXY_KEY, true), wifiVpnSharing || getBoolean("flutter.allow-connection-from-lan", false), privacyUseRoot,
    )
    val privacyHideClashApi get() = getBoolean(NativeProxyPrivacy.HIDE_CLASH_API_KEY, true)
    val privacyEncryptedDns get() = getBoolean("flutter.privacy-encrypted-dns", true)
    val privacyPublicDns get() = getBoolean("flutter.privacy-public-dns", false)
    val privacyDisableSystemProxy get() = getBoolean(NativeProxyPrivacy.DISABLE_SYSTEM_PROXY_KEY, true)
    val privacyDisableIpv6 get() = getString("flutter.ipv6-mode", "ipv4_only") == "ipv4_only"
    val privacyUseRootRequested get() = getBoolean("flutter.privacy-use-root", false)

    fun setPrivacyUseRoot(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.privacy-use-root", enabled).apply()
        VpnServiceVisibility.sync(Application.application, enabled && serviceMode == ServiceMode.VPN)
    }

    fun setWifiVpnSharing(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.wifi-vpn-sharing", enabled).apply()
    }

    fun setPrivacyFullTunnel(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.privacy-full-tunnel", enabled).apply()
    }

    fun setPrivacyEncryptedDns(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.privacy-encrypted-dns", enabled).apply()
    }

    fun setPrivacyPublicDns(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.privacy-public-dns", enabled).apply()
    }

    fun setPrivacyDisableSystemProxy(disabled: Boolean) {
        preferences.edit().putBoolean(NativeProxyPrivacy.DISABLE_SYSTEM_PROXY_KEY, disabled).apply()
    }

    fun setPrivacyDisableIpv6(disabled: Boolean) {
        preferences.edit().putString("flutter.ipv6-mode", if (disabled) "ipv4_only" else "auto").apply()
    }

    fun setPrivacyRoutingMode(mode: String) {
        preferences.edit().putString("flutter.privacy-routing-mode", mode).apply()
    }

    fun setHandbookRouting(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.handbook-routing", enabled).apply()
    }

    fun setHandbookProxy(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.handbook-proxy", enabled).apply()
    }

    fun setHandbookDirect(enabled: Boolean) {
        preferences.edit().putBoolean("flutter.handbook-direct", enabled).apply()
    }

    fun setHandbookProxySites(value: String) {
        preferences.edit().putString("flutter.handbook-proxy-sites", value).apply()
    }

    fun setHandbookDirectSites(value: String) {
        preferences.edit().putString("flutter.handbook-direct-sites", value).apply()
    }
    /** One preference transaction keeps the empty/all/disabled distinction intact. */
    fun setCommunitySelection(proxy: Boolean, enabled: Boolean, sites: String) {
        val prefix = if (proxy) "flutter.handbook-proxy" else "flutter.handbook-direct"
        preferences.edit().putBoolean(prefix, enabled).putString("$prefix-sites", sites).apply()
    }

    val grpcFrontPort get() = getInt("local_control_front_port", 17078).takeIf { it in 1..65535 } ?: 17078
    val grpcBackPort get() = getInt("local_control_back_port", 17079).takeIf { it in 1..65535 && it != grpcFrontPort } ?: 17079
    val grpcAuthToken: String
        @Synchronized get() {
            val saved = getString("local_control_token", "")
            if (saved.isNotEmpty()) return saved
            val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
            check(preferences.edit().putString("local_control_token", token).commit())
            return token
        }

    var connectionDesired: Boolean
        get() = getBoolean("flutter.connection_desired", getBoolean("flutter.started_by_user", false))
        set(value) = preferences.edit().putBoolean("flutter.connection_desired", value).apply()

    var nativeAppliedQuickSettings: String
        get() = getString("native_applied_quick_settings", "")
        set(value) = preferences.edit().putString("native_applied_quick_settings", value).apply()

    fun quickSettingsSignature(context: Context): String {
        val inbound = com.hiddify.hiddify.nativecore.NativeInboundOptionsRepository(context).load()
        val chain = com.hiddify.hiddify.nativecore.NativeChainRepository().load()
        val general = com.hiddify.hiddify.nativecore.NativeGeneralOptionsRepository(context).load()
        val dns = com.hiddify.hiddify.nativecore.NativeDnsOptionsRepository(context).load()
        val tls = com.hiddify.hiddify.nativecore.NativeTlsOptionsRepository(context).load()
        return com.google.gson.Gson().toJson(listOf(serviceMode, handbookRouting, handbookProxy, handbookDirect, handbookProxySites, handbookDirectSites, inbound.allowLan, inbound.lanPassword, chain, general, dns, tls, perAppProxyMode, (if (perAppProxyEnabled) perAppProxyList else emptyList<String>()).toSortedSet(), com.hiddify.hiddify.privacy.NetworkPrivacySettings.loadFilters(context)))
    }

    var nativeReconnectRequired: Boolean
        get() = getBoolean("native_reconnect_required", false)
        set(value) = preferences.edit().putBoolean("native_reconnect_required", value).apply()

    private const val TAG = "A/Settings"

    private val preferences by lazy {
        val context = Application.application.applicationContext
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    }

    private const val LIST_IDENTIFIER = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

    private fun getString(key: String, defaultValue: String): String = try {
        preferences.getString(key, defaultValue) ?: defaultValue
    } catch (_: ClassCastException) { Log.w(TAG, "invalid string preference: $key"); defaultValue }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean = try {
        preferences.getBoolean(key, defaultValue)
    } catch (_: ClassCastException) { Log.w(TAG, "invalid boolean preference: $key"); defaultValue }

    private fun getInt(key: String, defaultValue: Int): Int = try {
        preferences.getInt(key, defaultValue)
    } catch (_: ClassCastException) { Log.w(TAG, "invalid integer preference: $key"); defaultValue }

    var perAppProxyMode: String
        get() = getString(SettingsKey.PER_APP_PROXY_MODE, PerAppProxyMode.OFF)
        set(value) = preferences.edit().putString(SettingsKey.PER_APP_PROXY_MODE, value).apply()

    fun setPerAppProxyPackages(mode: String, packages: Collection<String>) {
        val key = when (mode) {
            PerAppProxyMode.INCLUDE -> SettingsKey.PER_APP_PROXY_INCLUDE_LIST
            PerAppProxyMode.EXCLUDE -> SettingsKey.PER_APP_PROXY_EXCLUDE_LIST
            else -> return
        }
        val normalized = ArrayList(packages.filter { it.isNotBlank() }.distinct())
        val encoded = ByteArrayOutputStream().use { bytes ->
            ObjectOutputStream(bytes).use { it.writeObject(normalized) }
            LIST_IDENTIFIER + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }
        // This is the same legacy shared_preferences encoding that Dart getStringList() expects.
        preferences.edit().putString(key, encoded).apply()
    }

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

    internal fun encodeListString(values: List<String>): String {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { stream ->
            stream.writeObject(ArrayList(values))
        }
        return LIST_IDENTIFIER + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
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

    var notificationPermissionAsked: Boolean
        get() = getBoolean("notification_permission_asked", false)
        set(value) = preferences.edit().putBoolean("notification_permission_asked", value).apply()

    var dynamicNotification: Boolean
        get() = getBoolean(SettingsKey.DYNAMIC_NOTIFICATION, true)
        set(value) = preferences.edit().putBoolean(SettingsKey.DYNAMIC_NOTIFICATION, value).apply()

    var systemProxyEnabled: Boolean
        get() = getBoolean(SettingsKey.SYSTEM_PROXY_ENABLED, true)
        set(value) = preferences.edit().putBoolean(SettingsKey.SYSTEM_PROXY_ENABLED, value).apply()

    var startedByUser: Boolean
        get() = getBoolean(SettingsKey.STARTED_BY_USER, false)
        set(value) = preferences.edit().putBoolean(SettingsKey.STARTED_BY_USER, value).apply()

    fun serviceClass(): Class<*> {
        val rootMode = privacyUseRoot
        // BoxService.start() is used by the activity, tile, shortcut and boot paths. Syncing
        // here guarantees that switching away from root mode cannot leave VpnService disabled.
        VpnServiceVisibility.sync(Application.application, rootMode)
        return when (serviceMode) {
            ServiceMode.VPN -> if (rootMode) ProxyService::class.java else VPNService::class.java
            else -> ProxyService::class.java
        }
    }

    private var currentServiceMode: String? = null

    suspend fun rebuildServiceMode(): Boolean {
        val rootMode = privacyUseRoot
        // This runs immediately before Android starts the selected service. Re-enable the
        // VpnService component before normal VPN mode so hiding it in root mode can never
        // make a later non-root connection unavailable.
        VpnServiceVisibility.sync(Application.application, rootMode)
        val newMode = if (rootMode) "root" else if (serviceMode == ServiceMode.VPN) ServiceMode.VPN else ServiceMode.NORMAL
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
        get() = getInt(SettingsKey.GRPC_PORT, grpcBackPort).takeIf { it in 1..65535 && it != grpcFrontPort } ?: grpcBackPort
        set(value) = preferences.edit().putInt(SettingsKey.GRPC_PORT, value).apply()

    var startCoreAfterStartingService: Boolean
        get() = getBoolean(SettingsKey.START_CORE_ON_STARTING_SERVICE, false)
        set(value) = preferences.edit().putBoolean(SettingsKey.START_CORE_ON_STARTING_SERVICE, value).apply()
}

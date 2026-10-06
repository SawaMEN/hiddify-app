package com.hiddify.hiddify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.hiddify.hiddify.bg.BoxService
import com.hiddify.hiddify.bg.ServiceConnection
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.constant.Status
import com.hiddify.hiddify.nativeprofile.NativeProfile
import com.hiddify.hiddify.nativeprofile.NativeProfileEditor
import com.hiddify.hiddify.nativeprofile.NativeProfileRepository
import com.hiddify.hiddify.nativerouting.NativePerAppRepository
import com.hiddify.hiddify.nativerouting.NativePerAppSnapshot
import com.hiddify.hiddify.nativeui.NativeApp
import com.hiddify.hiddify.nativeui.NativeSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Native Android entry point used during the Dart -> Kotlin migration.
 *
 * The normal Android launcher, VPN lifecycle, profiles and core network settings are native.
 * [MainActivity] remains available only as a temporary compatibility surface for advanced screens
 * that have not been migrated yet.
 */
class NativeMainActivity : ComponentActivity(), ServiceConnection.Callback {

    companion object {
        private const val TAG = "NativeMainActivity"
        private const val IMPORT_BUSY_ID = "__import__"
        private const val PROFILE_UPDATE_INTERVAL_MS = 15L * 60L * 1000L
    }

    private val serviceStatus = mutableStateOf(Status.Stopped)
    private val activeProfileName = mutableStateOf("")
    private val activeProfilePath = mutableStateOf("")
    private val profiles = mutableStateOf<List<NativeProfile>>(emptyList())
    private val busyProfileId = mutableStateOf<String?>(null)
    private val profileEditor = mutableStateOf<NativeProfileEditor?>(null)
    private val profileEditorBusy = mutableStateOf(false)
    private val perAppSnapshot =
        mutableStateOf(
            NativePerAppSnapshot(
                mode = Settings.perAppProxyMode,
                apps = emptyList(),
                selectedPackages = emptySet(),
            ),
        )
    private val perAppBusy = mutableStateOf(false)
    private val errorMessage = mutableStateOf<String?>(null)
    private val nativeSettings = mutableStateOf(readNativeSettings())

    private val profileRepository by lazy { NativeProfileRepository(applicationContext) }
    private val perAppRepository by lazy { NativePerAppRepository(applicationContext) }
    private val profileOperationMutex = Mutex()
    private val connection = ServiceConnection(this, this)

    private var profileUpdateJob: Job? = null
    private var pendingStartAfterVpnPermission = false
    private var notificationRequestInFlight = false
    private var batteryPromptOpen = false

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val shouldContinue = pendingStartAfterVpnPermission
            pendingStartAfterVpnPermission = false
            if (!shouldContinue) return@registerForActivityResult

            if (result.resultCode == RESULT_OK) {
                startForegroundVpn()
            } else {
                Settings.connectionDesired = false
                errorMessage.value = getString(R.string.native_vpn_permission_denied)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationRequestInFlight = false
            com.hiddify.hiddify.bg.ServiceNotification.refreshActive()
            maybePromptBatteryOptimization()
        }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        val migrationError =
            runCatching { com.hiddify.hiddify.privacy.PackageIdentity.importMigration(this) }
                .exceptionOrNull()

        super.onCreate(savedInstanceState)

        if (migrationError != null) {
            errorMessage.value = migrationError.message ?: migrationError.javaClass.simpleName
        }

        enableEdgeToEdge()
        refreshProfileSnapshot()
        refreshSettingsSnapshot()
        refreshProfiles(syncActive = true)
        refreshPerApp()

        setContent {
            NativeApp(
                status = serviceStatus.value,
                activeProfileName = activeProfileName.value,
                hasActiveProfile = activeProfilePath.value.isNotBlank(),
                rootMode = Settings.privacyUseRoot,
                settingsState = nativeSettings.value,
                profiles = profiles.value,
                busyProfileId = busyProfileId.value,
                profileEditor = profileEditor.value,
                profileEditorBusy = profileEditorBusy.value,
                perAppSnapshot = perAppSnapshot.value,
                perAppBusy = perAppBusy.value,
                errorMessage = errorMessage.value,
                onDismissError = { errorMessage.value = null },
                onToggleConnection = ::toggleConnection,
                onSelectProfile = ::selectProfile,
                onDeleteProfile = ::deleteProfile,
                onRefreshProfile = ::refreshRemoteProfile,
                onOpenProfileEditor = ::openProfileEditor,
                onSaveProfileEditor = ::saveProfileEditor,
                onImportProfile = ::importProfile,
                onPerAppModeChanged = ::setPerAppMode,
                onTogglePerAppPackage = ::togglePerAppPackage,
                onClearPerApp = ::clearPerAppPackages,
                onOpenLegacy = ::openLegacyUi,
                onProxyOnlyChanged = { proxyOnly ->
                    if (serviceStatus.value != Status.Stopped) {
                        errorMessage.value = getString(R.string.native_settings_disconnect_required)
                    } else {
                        updateSettings {
                            Settings.serviceMode = if (proxyOnly) ServiceMode.NORMAL else ServiceMode.VPN
                        }
                        connection.reconnect()
                    }
                },
                onRootModeChanged = { value ->
                    if (serviceStatus.value != Status.Stopped) {
                        errorMessage.value = getString(R.string.native_profile_disconnect_required)
                    } else {
                        updateSettings { Settings.setPrivacyUseRoot(value) }
                        connection.reconnect()
                    }
                },
                onWifiSharingChanged = { value ->
                    if (serviceStatus.value != Status.Stopped) {
                        errorMessage.value = getString(R.string.native_profile_disconnect_required)
                    } else {
                        updateSettings { Settings.setWifiVpnSharing(value) }
                    }
                },
                onFullTunnelChanged = { value -> updateSettings { Settings.setPrivacyFullTunnel(value) } },
                onEncryptedDnsChanged = { value -> updateSettings { Settings.setPrivacyEncryptedDns(value) } },
                onPublicDnsChanged = { value -> updateSettings { Settings.setPrivacyPublicDns(value) } },
                onDisableSystemProxyChanged = { value -> updateSettings { Settings.setPrivacyDisableSystemProxy(value) } },
                onDisableIpv6Changed = { value -> updateSettings { Settings.setPrivacyDisableIpv6(value) } },
                onHandbookRoutingChanged = { value -> updateSettings { Settings.setHandbookRouting(value) } },
                onHandbookProxyChanged = { value -> updateSettings { Settings.setHandbookProxy(value) } },
                onHandbookDirectChanged = { value -> updateSettings { Settings.setHandbookDirect(value) } },
                onHandbookProxySitesChanged = { value -> updateSettings { Settings.setHandbookProxySites(value) } },
                onHandbookDirectSitesChanged = { value -> updateSettings { Settings.setHandbookDirectSites(value) } },
                onDynamicNotificationChanged = { value ->
                    updateSettings { Settings.dynamicNotification = value }
                    com.hiddify.hiddify.bg.ServiceNotification.refreshActive()
                },
                onDebugModeChanged = { value -> updateSettings { Settings.debugMode = value } },
                onDisableMemoryLimitChanged = { value -> updateSettings { Settings.disableMemoryLimit = value } },
            )
        }

        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        refreshProfileSnapshot()
        refreshSettingsSnapshot()
        refreshProfiles()
        refreshPerApp()
        startProfileUpdateLoop()
        connection.connect()
    }

    override fun onResume() {
        super.onResume()
        refreshProfileSnapshot()
        refreshSettingsSnapshot()
        refreshProfiles()
        refreshPerApp()
        if (serviceStatus.value == Status.Started) {
            maybeRequestNotificationPermission()
            maybePromptBatteryOptimization()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStop() {
        profileUpdateJob?.cancel()
        profileUpdateJob = null
        connection.disconnect()
        super.onStop()
    }

    private fun refreshProfileSnapshot() {
        activeProfileName.value = Settings.activeProfileName
        activeProfilePath.value = Settings.activeConfigPath
    }

    private fun refreshProfiles(syncActive: Boolean = false) {
        lifecycleScope.launch {
            try {
                val loaded =
                    withContext(Dispatchers.IO) {
                        if (syncActive) profileRepository.synchronizeActiveProfile()
                        profileRepository.listProfiles()
                    }
                profiles.value = loaded
                refreshProfileSnapshot()
            } catch (error: Exception) {
                if (syncActive) {
                    errorMessage.value = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    private fun refreshPerApp() {
        if (perAppBusy.value) return
        lifecycleScope.launch {
            try {
                perAppSnapshot.value = withContext(Dispatchers.IO) { perAppRepository.snapshot() }
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun setPerAppMode(mode: String) {
        runPerAppOperation { perAppRepository.setMode(mode) }
    }

    private fun togglePerAppPackage(packageName: String) {
        val mode = perAppSnapshot.value.mode
        runPerAppOperation { perAppRepository.togglePackage(mode, packageName) }
    }

    private fun clearPerAppPackages() {
        val mode = perAppSnapshot.value.mode
        runPerAppOperation { perAppRepository.clear(mode) }
    }

    private fun runPerAppOperation(operation: () -> NativePerAppSnapshot) {
        if (perAppBusy.value) return
        if (serviceStatus.value != Status.Stopped) {
            errorMessage.value = getString(R.string.native_per_app_disconnect)
            return
        }
        perAppBusy.value = true
        lifecycleScope.launch {
            try {
                perAppSnapshot.value = withContext(Dispatchers.IO) { operation() }
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                perAppBusy.value = false
            }
        }
    }

    private fun openProfileEditor(profile: NativeProfile) {
        if (profileEditorBusy.value) return
        profileEditor.value = null
        profileEditorBusy.value = true
        lifecycleScope.launch {
            try {
                profileEditor.value =
                    withContext(Dispatchers.IO) {
                        profileOperationMutex.withLock { profileRepository.loadEditor(profile.id) }
                    }
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                profileEditorBusy.value = false
            }
        }
    }

    private fun saveProfileEditor(
        name: String,
        disableAutoUpdate: Boolean,
        updateIntervalHours: Int?,
        content: String,
    ) {
        val editor = profileEditor.value ?: return
        if (profileEditorBusy.value) return
        profileEditorBusy.value = true
        lifecycleScope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        profileOperationMutex.withLock {
                            profileRepository.saveEditedProfile(
                                id = editor.profile.id,
                                content = content,
                                name = name,
                                disableAutoUpdate = disableAutoUpdate,
                                updateIntervalHours = updateIntervalHours,
                            )
                            profileRepository.synchronizeActiveProfile()
                            profileRepository.loadEditor(editor.profile.id) to profileRepository.listProfiles()
                        }
                    }
                profileEditor.value = result.first
                profiles.value = result.second
                refreshProfileSnapshot()
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                profileEditorBusy.value = false
            }
        }
    }

    private fun startProfileUpdateLoop() {
        if (profileUpdateJob?.isActive == true) return
        profileUpdateJob =
            lifecycleScope.launch {
                while (isActive) {
                    try {
                        updateDueProfiles()
                    } catch (error: Exception) {
                        Log.w(TAG, "automatic profile update cycle failed", error)
                    }
                    delay(PROFILE_UPDATE_INTERVAL_MS)
                }
            }
    }

    private suspend fun updateDueProfiles() {
        if (busyProfileId.value != null || profileEditorBusy.value) return
        val loaded =
            withContext(Dispatchers.IO) {
                profileOperationMutex.withLock {
                    val due = profileRepository.dueRemoteProfileIds()
                    for (id in due) {
                        runCatching { profileRepository.refreshRemote(id) }
                            .onFailure { Log.w(TAG, "automatic profile update failed for $id", it) }
                    }
                    profileRepository.synchronizeActiveProfile()
                    profileRepository.listProfiles()
                }
            }
        profiles.value = loaded
        refreshProfileSnapshot()
    }

    private fun selectProfile(profile: NativeProfile) {
        runProfileOperation(profile.id, requireDisconnected = true) {
            profileRepository.setActive(profile.id)
        }
    }

    private fun deleteProfile(profile: NativeProfile) {
        runProfileOperation(profile.id, requireDisconnected = true) {
            profileRepository.delete(profile.id)
        }
    }

    private fun refreshRemoteProfile(profile: NativeProfile) {
        if (!profile.isRemote) return
        runProfileOperation(profile.id, requireDisconnected = false) {
            profileRepository.refreshRemote(profile.id)
        }
    }

    private fun importProfile(
        raw: String,
        name: String?,
        intervalHours: Int?,
        disableAutoUpdate: Boolean,
    ) {
        runProfileOperation(IMPORT_BUSY_ID, requireDisconnected = true) {
            profileRepository.importInput(
                rawInput = raw,
                name = name,
                updateIntervalHours = intervalHours,
                disableAutoUpdate = disableAutoUpdate,
            )
        }
    }

    private fun runProfileOperation(
        operationId: String,
        requireDisconnected: Boolean,
        operation: () -> Unit,
    ) {
        if (busyProfileId.value != null) return
        if (requireDisconnected && serviceStatus.value != Status.Stopped) {
            errorMessage.value = getString(R.string.native_profile_disconnect_required)
            return
        }

        busyProfileId.value = operationId
        lifecycleScope.launch {
            try {
                val loaded =
                    withContext(Dispatchers.IO) {
                        profileOperationMutex.withLock {
                            operation()
                            profileRepository.synchronizeActiveProfile()
                            profileRepository.listProfiles()
                        }
                    }
                profiles.value = loaded
                refreshProfileSnapshot()
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                busyProfileId.value = null
            }
        }
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val payload =
            when (incoming?.action) {
                Intent.ACTION_VIEW -> incoming.dataString
                Intent.ACTION_SEND -> incoming.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() } ?: return

        // Avoid re-importing the launch intent after Activity recreation/resume.
        incoming.action = null
        importProfile(payload, null, null, false)
    }

    private fun readNativeSettings() =
        NativeSettingsState(
            serviceMode = Settings.serviceMode,
            rootRequested = Settings.privacyUseRootRequested,
            wifiSharing = Settings.wifiVpnSharing,
            fullTunnel = Settings.privacyFullTunnel,
            encryptedDns = Settings.privacyEncryptedDns,
            publicDns = Settings.privacyPublicDns,
            disableSystemProxy = Settings.privacyDisableSystemProxy,
            disableIpv6 = Settings.privacyDisableIpv6,
            handbookRouting = Settings.handbookRouting,
            handbookProxy = Settings.handbookProxy,
            handbookDirect = Settings.handbookDirect,
            handbookProxySites = Settings.handbookProxySites,
            handbookDirectSites = Settings.handbookDirectSites,
            dynamicNotification = Settings.dynamicNotification,
            debugMode = Settings.debugMode,
            disableMemoryLimit = Settings.disableMemoryLimit,
        )

    private fun refreshSettingsSnapshot() {
        nativeSettings.value = readNativeSettings()
    }

    private inline fun updateSettings(action: () -> Unit) {
        action()
        refreshSettingsSnapshot()
    }

    private fun toggleConnection() {
        when (serviceStatus.value) {
            Status.Stopped -> requestStart()
            Status.Started -> {
                Settings.connectionDesired = false
                BoxService.stop()
            }

            Status.Starting, Status.Stopping -> Unit
        }
    }

    private fun requestStart() {
        errorMessage.value = null
        refreshProfileSnapshot()

        if (Settings.activeConfigPath.isBlank()) {
            errorMessage.value = getString(R.string.native_no_active_profile)
            return
        }

        Settings.connectionDesired = true
        // Kotlin owns startup. The background service loads the selected raw profile directly.
        Settings.startCoreAfterStartingService = true

        if (Settings.serviceMode == ServiceMode.VPN && !Settings.privacyUseRoot) {
            val permissionIntent =
                try {
                    VpnService.prepare(this)
                } catch (error: Exception) {
                    Settings.connectionDesired = false
                    errorMessage.value = error.message ?: error.javaClass.simpleName
                    return
                }

            if (permissionIntent != null) {
                pendingStartAfterVpnPermission = true
                vpnPermissionLauncher.launch(permissionIntent)
                return
            }
        }

        startForegroundVpn()
    }

    private fun startForegroundVpn() {
        try {
            Settings.connectionDesired = true
            Settings.startCoreAfterStartingService = true
            BoxService.start()
        } catch (error: Exception) {
            Settings.connectionDesired = false
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun openLegacyUi() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            notificationRequestInFlight ||
            Settings.notificationPermissionAsked ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return
        }

        notificationRequestInFlight = true
        Settings.notificationPermissionAsked = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun maybePromptBatteryOptimization() {
        if (batteryPromptOpen ||
            notificationRequestInFlight ||
            serviceStatus.value != Status.Started ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            Application.powerManager.isIgnoringBatteryOptimizations(packageName)
        ) {
            return
        }

        val prefs = getSharedPreferences("background_permissions", MODE_PRIVATE)
        if (prefs.getBoolean("battery_prompt_shown", false)) return

        batteryPromptOpen = true
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.native_background_title)
            .setMessage(R.string.native_background_message)
            .setPositiveButton(R.string.native_background_allow) { _, _ ->
                prefs.edit().putBoolean("battery_prompt_shown", true).apply()
                batteryPromptOpen = false
                runCatching {
                    startActivity(
                        Intent(
                            AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                }.onFailure {
                    errorMessage.value = it.message ?: it.javaClass.simpleName
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                prefs.edit().putBoolean("battery_prompt_shown", true).apply()
                batteryPromptOpen = false
            }
            .setOnCancelListener {
                prefs.edit().putBoolean("battery_prompt_shown", true).apply()
                batteryPromptOpen = false
            }
            .show()
    }

    override fun onServiceStatusChanged(status: Status) {
        runOnUiThread {
            serviceStatus.value = status
            if (status == Status.Started) {
                maybeRequestNotificationPermission()
                maybePromptBatteryOptimization()
            }
        }
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        runOnUiThread {
            Settings.connectionDesired = false
            errorMessage.value =
                message ?: getString(R.string.native_service_error, type.name)
        }
    }
}

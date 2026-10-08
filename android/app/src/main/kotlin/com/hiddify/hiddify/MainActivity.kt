package com.hiddify.hiddify

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
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
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticSnapshot
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticReport
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticsRepository
import com.hiddify.hiddify.nativediagnostics.NativeVpnProtection
import com.hiddify.hiddify.nativeprofile.NativeProfile
import com.hiddify.hiddify.nativeprofile.NativeProfileEditor
import com.hiddify.hiddify.nativeprofile.NativeProfileTransfer
import com.hiddify.hiddify.nativeprofile.NativeProfileImportCancellation
import com.hiddify.hiddify.nativeprofile.NativeProfileRepository
import com.hiddify.hiddify.nativelog.NativeLogRepository
import com.hiddify.hiddify.nativelog.NativeLogSnapshot
import com.hiddify.hiddify.nativecore.NativeChainOptions
import com.hiddify.hiddify.nativecore.NativeChainRepository
import com.hiddify.hiddify.nativecore.NativeOutboundGroup
import com.hiddify.hiddify.nativecore.NativeOutboundsRepository
import com.hiddify.hiddify.nativecore.NativeStatsRepository
import com.hiddify.hiddify.nativecore.NativeSettingsTransferRepository
import com.hiddify.hiddify.nativecore.NativeSystemStats
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails
import com.hiddify.hiddify.nativecore.NativeWifiSharingRepository
import com.hiddify.hiddify.nativecore.NativeUpdateRepository
import com.hiddify.hiddify.nativerouting.NativePerAppBackup
import com.hiddify.hiddify.nativerouting.NativePerAppBackupCodec
import com.hiddify.hiddify.nativerouting.NativePerAppRepository
import com.hiddify.hiddify.nativerouting.NativePerAppSnapshot
import com.hiddify.hiddify.nativepreferences.NativeAppearanceRepository
import com.hiddify.hiddify.nativepreferences.NativeThemeMode
import com.hiddify.hiddify.nativeconnection.NativeRecoveryPolicy
import com.hiddify.hiddify.nativeconnection.NativeRecoveryDecision
import com.hiddify.hiddify.nativeconnection.NativeConnectionOptions
import com.hiddify.hiddify.nativeconnection.NativeHealthProbePolicy
import com.hiddify.hiddify.nativeconnection.NativeHealthRepository
import com.hiddify.hiddify.nativeconnection.NativeInternetHealth
import com.hiddify.hiddify.privacy.NativeRegionalAppKind
import com.hiddify.hiddify.privacy.NativeRegionalAppSnapshot
import com.hiddify.hiddify.privacy.NativeRegionalOptions
import com.hiddify.hiddify.privacy.NativeRegionalRepository
import com.hiddify.hiddify.privacy.NativeTrafficFilters
import com.hiddify.hiddify.privacy.NetworkPrivacySettings
import com.hiddify.hiddify.nativeui.NativeApp
import com.hiddify.hiddify.nativeui.NativeSettingsState
import com.hiddify.hiddify.sharing.AutomaticHotspot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Standalone Kotlin/Compose Android entry point. */
class MainActivity : ComponentActivity(), ServiceConnection.Callback {

    companion object {
        private const val TAG = "MainActivity"
        private const val IMPORT_BUSY_ID = "__import__"
        private const val PROFILE_UPDATE_INTERVAL_MS = 15L * 60L * 1000L
        private const val LOG_REFRESH_INTERVAL_MS = 2_000L
        private const val MAX_SERVICE_LOG_LINES = 200
    }

    private val serviceStatus = mutableStateOf(Status.Stopped)
    private val homeConnectionFailed = mutableStateOf(false)
    private val homeSmartSelected = mutableStateOf(false)
    private val activeProfileName = mutableStateOf("")
    private val activeProfilePath = mutableStateOf("")
    private val profilesLoadFailed = mutableStateOf(false)
    private val profiles = mutableStateOf<List<NativeProfile>>(emptyList())
    private val profilesLoading = mutableStateOf(true)
    private val profileImportRevision = mutableStateOf(0)
    private val profileSelectionRevision = mutableStateOf(0)
    private val busyProfileId = mutableStateOf<String?>(null)
    private val ipVisibilitySession by lazy { androidx.lifecycle.ViewModelProvider(this)[com.hiddify.hiddify.nativeui.NativeIpVisibilitySession::class.java] }
    private val editorSession by lazy { androidx.lifecycle.ViewModelProvider(this)[com.hiddify.hiddify.nativeprofile.NativeProfileEditorSession::class.java] }
    private var profileEditorId: String? = null
    private val profileEditorLoadFailed = mutableStateOf(false)
    private val profileEditorSavedRevision = mutableStateOf(0)
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
    private val pendingPerAppImport = mutableStateOf<NativePerAppBackup?>(null)
    private val logSnapshot = mutableStateOf(NativeLogSnapshot(emptyList(), emptyList()))
    private val logBusy = mutableStateOf(false)
    private val serviceLogLines = ArrayDeque<String>()
    private val tunnelRepository by lazy { com.hiddify.hiddify.nativecore.NativeTunnelOptionsRepository(applicationContext) }
    private val tunnelOptions = mutableStateOf<com.hiddify.hiddify.nativecore.NativeTunnelOptions?>(null)
    private val tunnelBusy = mutableStateOf(false)
    private var tunnelSnapshotJob: Job? = null
    private val generalOptionsRepository by lazy { com.hiddify.hiddify.nativecore.NativeGeneralOptionsRepository(applicationContext) }
    private val generalOptions = mutableStateOf<com.hiddify.hiddify.nativecore.NativeGeneralOptions?>(null)
    private val generalOptionsBusy = mutableStateOf(false)
    private val generalOptionsLoadFailed = mutableStateOf(false)
    private var generalOptionsSnapshotJob: Job? = null
    private val tlsRepository by lazy { com.hiddify.hiddify.nativecore.NativeTlsOptionsRepository(applicationContext) }
    private val tlsOptions = mutableStateOf<com.hiddify.hiddify.nativecore.NativeTlsOptions?>(null)
    private val tlsBusy = mutableStateOf(false)
    private val tlsLoadFailed = mutableStateOf(false)
    private var tlsSnapshotJob: Job? = null
    private val dnsRepository by lazy { com.hiddify.hiddify.nativecore.NativeDnsOptionsRepository(applicationContext) }
    private val dnsOptions = mutableStateOf<com.hiddify.hiddify.nativecore.NativeDnsOptions?>(null)
    private val dnsBusy = mutableStateOf(false)
    private val dnsLoadFailed = mutableStateOf(false)
    private var dnsSnapshotJob: Job? = null
    private val inboundRepository by lazy { com.hiddify.hiddify.nativecore.NativeInboundOptionsRepository(applicationContext) }
    private val inboundOptions = mutableStateOf(com.hiddify.hiddify.nativecore.NativeInboundOptions())
    private val inboundBusy = mutableStateOf(false)
    private var inboundSnapshotJob: Job? = null
    private val chainOptions = mutableStateOf(NativeChainRepository().load())
    private val chainBusy = mutableStateOf(false)
    private val outboundGroups = mutableStateOf<List<NativeOutboundGroup>>(emptyList())
    private val requiresReconnect = mutableStateOf(Settings.nativeReconnectRequired)
    private val reconnectBusy = mutableStateOf(false)
    private val activeOutbound = mutableStateOf<com.hiddify.hiddify.nativecore.NativeOutbound?>(null)
    private val outboundOperationRevision = mutableStateOf(0)
    private val outboundBusyTag = mutableStateOf<String?>(null)
    private val systemStats = mutableStateOf(NativeSystemStats())
    private val wifiSharingDetails = mutableStateOf(NativeWifiSharingDetails())
    private val wifiSharingBusy = mutableStateOf(false)
    private var hotspotPermission: CompletableDeferred<Boolean>? = null
    private val hotspotPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hotspotPermission?.complete(granted)
        }
    private val hotspotObserver: (Map<String, Any?>) -> Unit = { state ->
        if (state["active"] != true && !wifiSharingBusy.value && Settings.wifiVpnSharing) {
            Settings.setWifiVpnSharing(false)
        }
        refreshSettingsSnapshot()
        refreshWifiSharingDetails()
    }
    private var wifiDetailsRefreshPending = false
    private val wifiSharingDetailsBusy = mutableStateOf(false)
    private val updateChecking = mutableStateOf(false)
    private val updateMessage = mutableStateOf<String?>(null)
    private val updateUrl = mutableStateOf<String?>(null)
    private val diagnosticSnapshot = mutableStateOf<NativeDiagnosticSnapshot?>(null)
    private val diagnosticBusy = mutableStateOf(false)
    private val vpnProtection = mutableStateOf(NativeVpnProtection())
    private val errorMessage = mutableStateOf<String?>(null)
    private var appliedLanguage = com.hiddify.hiddify.nativepreferences.NativeLanguage.SYSTEM
    private val generalPreferences = mutableStateOf(com.hiddify.hiddify.nativepreferences.NativeGeneralPreferences())
    private val generalPreferencesBusy = mutableStateOf(false)
    private val generalPreferencesRepository by lazy { com.hiddify.hiddify.nativepreferences.NativeGeneralPreferencesRepository(applicationContext) }
    private val themeMode = mutableStateOf(NativeThemeMode.SYSTEM)
    private val themeBusy = mutableStateOf(false)
    private val privacySetupRepository by lazy { com.hiddify.hiddify.nativeprivacy.NativePrivacySetupRepository(applicationContext) }
    private var privacySetupSnapshotJob: Job? = null
    private val proxyPrivacy = mutableStateOf(com.hiddify.hiddify.privacy.NativeProxyPrivacy())
    private val proxyPrivacyBusy = mutableStateOf(false)
    private var proxyPrivacySnapshotJob: Job? = null
    private val privacySetupBusy = mutableStateOf(false)
    private val privacyConfigured = mutableStateOf(false)
    private val privacyCanRestore = mutableStateOf(false)
    private val appearanceRepository by lazy { NativeAppearanceRepository(applicationContext) }
    private val connectionOptions = mutableStateOf(NativeConnectionOptions())
    private val connectionOptionsBusy = mutableStateOf(false)
    private val internetHealth = mutableStateOf(NativeInternetHealth.UNCHECKED)
    private val healthRepository = NativeHealthRepository()
    private var healthJob: Job? = null
    private var smartSelectionJob: Job? = null
    private val serverHistoryRepository by lazy { com.hiddify.hiddify.nativeconnection.NativeServerHistoryRepository(applicationContext) }
    private var nativeForeground = false
    private var recoveryPolicy = NativeRecoveryPolicy()
    private var recoveryJob: Job? = null
    private var stableConnectionJob: Job? = null
    private val recoveryAttempt = mutableStateOf(0)
    private var nativeStartPending = false
    private val serviceStartTracker = ServiceStartTracker()
    private var reconnectJob: Job? = null
    private var serviceStartWatchdog: Job? = null
    private val regionalAppsRevision = mutableStateOf(0)
    private val regionalApps = mutableStateOf<NativeRegionalAppSnapshot?>(null)
    private val regionalOperationMutex = Mutex()
    private val regionalOptions = mutableStateOf(NativeRegionalOptions())
    private val regionalBusy = mutableStateOf(false)
    private val regionalRepository by lazy { NativeRegionalRepository(applicationContext) }
    private val trafficFilters = mutableStateOf(NativeTrafficFilters())
    private val trafficFiltersBusy = mutableStateOf(false)
    private val nativeSettings = mutableStateOf(readNativeSettings())

    private val profileRepository by lazy { NativeProfileRepository(applicationContext) }
    private val perAppRepository by lazy { NativePerAppRepository(applicationContext) }
    private val logRepository by lazy { NativeLogRepository(applicationContext) }
    private val chainRepository by lazy { NativeChainRepository() }
    private val outboundsRepository by lazy { NativeOutboundsRepository() }
    private val statsRepository by lazy { NativeStatsRepository() }
    private val wifiSharingRepository by lazy { NativeWifiSharingRepository() }
    private val settingsTransferRepository by lazy { NativeSettingsTransferRepository(this) }
    private val updateRepository by lazy { NativeUpdateRepository() }
    private val diagnosticsRepository by lazy { NativeDiagnosticsRepository() }
    private val perAppOperationMutex = Mutex()
    private val profileOperationMutex = Mutex()
    private val connection = ServiceConnection(this, this)

    private var diagnosticJob: Job? = null
    private var profileUpdateJob: Job? = null
    private var profileImportCancellation: NativeProfileImportCancellation? = null
    private var logRefreshJob: Job? = null
    private var statsRefreshJob: Job? = null
    private var activeOutboundRefreshJob: Job? = null
    private var pendingStartAfterVpnPermission = false
    private var notificationRequestInFlight = false
    private var batteryPromptOpen = false
    private var pendingSettingsExport: String? = null
    private var pendingProfileExportId: String? = null

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val shouldContinue = pendingStartAfterVpnPermission
            pendingStartAfterVpnPermission = false
            if (!shouldContinue) return@registerForActivityResult

            if (result.resultCode == RESULT_OK) {
                startForegroundVpn()
            } else {
                Settings.connectionDesired = false
                Settings.startedByUser = false
                serviceStartTracker.reset()
                serviceStatus.value = Status.Stopped
                homeConnectionFailed.value = true
                errorMessage.value = getString(R.string.native_vpn_permission_denied)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationRequestInFlight = false
            com.hiddify.hiddify.bg.ServiceNotification.refreshActive()
            maybePromptBatteryOptimization()
        }

    private val settingsImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openInputStream(uri)
                                ?.use(NativeProfileTransfer::readText)
                                ?: throw java.io.FileNotFoundException(uri.toString())
                        }
                    }
                result.fold(
                    onSuccess = ::applyImportedSettings,
                    onFailure = { error ->
                        errorMessage.value = error.message ?: error.javaClass.simpleName
                    },
                )
            }
        }

    private val settingsExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val payload = pendingSettingsExport
            pendingSettingsExport = null
            if (uri == null || payload == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri, "wt")
                            ?.bufferedWriter(Charsets.UTF_8)
                            ?.use { it.write(payload) }
                            ?: throw java.io.FileNotFoundException(uri.toString())
                    }
                    Toast.makeText(
                        this@MainActivity,
                        R.string.native_settings_export_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (error: Exception) {
                    errorMessage.value = error.message ?: error.javaClass.simpleName
                }
            }
        }

    private val profileExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val id = pendingProfileExportId
            pendingProfileExportId = null
            if (uri == null || id == null) return@registerForActivityResult
            exportProfile(id) { content ->
                contentResolver.openOutputStream(uri, "wt")
                    ?.bufferedWriter(Charsets.UTF_8)
                    ?.use { it.write(content) }
                    ?: throw java.io.FileNotFoundException(uri.toString())
            }
        }

    private val perAppImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) preparePerAppImport {
                contentResolver.openInputStream(uri)?.use(NativePerAppBackupCodec::read)
                    ?: throw java.io.FileNotFoundException(uri.toString())
            }
        }

    private val perAppExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) runPerAppTransfer {
                val text = withContext(Dispatchers.IO) { perAppRepository.exportBackup() }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                        ?: throw java.io.FileNotFoundException(uri.toString())
                }
                Toast.makeText(this@MainActivity, R.string.native_per_app_backup_exported, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putInt("native_recovery_attempts", recoveryPolicy.attempts)
        outState.putString("native_profile_export_id", pendingProfileExportId)
        outState.putString("native_profile_editor_id", profileEditorId)
        super.onSaveInstanceState(outState)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        appliedLanguage = com.hiddify.hiddify.nativepreferences.NativeGeneralPreferencesRepository(newBase).load().language
        super.attachBaseContext(com.hiddify.hiddify.nativepreferences.NativeGeneralPreferencesRepository.localizedContext(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        val migrationError =
            runCatching { com.hiddify.hiddify.privacy.PackageIdentity.importMigration(this) }
                .exceptionOrNull()

        super.onCreate(savedInstanceState)
        AutomaticHotspot.addObserver(hotspotObserver)
        pendingProfileExportId = savedInstanceState?.getString("native_profile_export_id")
        savedInstanceState?.getString("native_profile_editor_id")?.let(::loadProfileEditor)
        recoveryPolicy = NativeRecoveryPolicy(savedInstanceState?.getInt("native_recovery_attempts") ?: 0)

        if (migrationError != null) {
            errorMessage.value = migrationError.message ?: migrationError.javaClass.simpleName
        }

        enableEdgeToEdge()
        refreshProfileSnapshot()
        refreshSettingsSnapshot()
        refreshWifiSharingDetails()
        refreshProfiles(syncActive = true)
        refreshPerApp()

        setContent {
            val dark = themeMode.value.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            NativeApp(
                generalPreferences = generalPreferences.value,
                generalPreferencesBusy = generalPreferencesBusy.value,
                onChangeLanguage = ::saveLanguage,
                onChangeHapticFeedback = ::saveHapticFeedback,
                onChangeSmartSelection = ::saveSmartSelection,
                onChangeOutboundSort = ::saveOutboundSort,
                tunnelOptions = tunnelOptions.value,
                tunnelBusy = tunnelBusy.value,
                onSaveTunnelOptions = ::saveTunnelOptions,
                generalOptions = generalOptions.value,
                generalOptionsBusy = generalOptionsBusy.value,
                generalOptionsLoadFailed = generalOptionsLoadFailed.value,
                onReloadGeneralOptions = ::refreshGeneralOptions,
                onSaveGeneralOption = ::saveGeneralOption,
                tlsOptions = tlsOptions.value,
                tlsBusy = tlsBusy.value,
                tlsLoadFailed = tlsLoadFailed.value,
                onReloadTlsOptions = ::refreshTlsOptions,
                onSaveTlsOption = ::saveTlsOption,
                dnsOptions = dnsOptions.value,
                dnsBusy = dnsBusy.value,
                dnsLoadFailed = dnsLoadFailed.value,
                onReloadDnsOptions = ::refreshDnsOptions,
                onSaveDnsOption = ::saveDnsOption,
                inboundOptions = inboundOptions.value,
                inboundBusy = inboundBusy.value,
                onSaveInboundOptions = ::saveInboundOptions,
                proxyPrivacy = proxyPrivacy.value,
                proxyPrivacyBusy = proxyPrivacyBusy.value,
                onSaveProxyPrivacy = ::saveProxyPrivacy,
                privacySetupBusy = privacySetupBusy.value,
                privacyConfigured = privacyConfigured.value,
                privacyCanRestore = privacyCanRestore.value,
                onConfigurePrivacy = { applyPrivacySetup(false) },
                onRestorePrivacy = { applyPrivacySetup(true) },
                themeMode = themeMode.value,
                themeBusy = themeBusy.value,
                onChangeTheme = ::saveTheme,
                onOpenNotificationSettings = { openSystemSettings(com.hiddify.hiddify.bg.ServiceNotification.settingsIntent()) },
                onOpenBatterySettings = { openSystemSettings(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                status = serviceStatus.value,
                connectionFailed = homeConnectionFailed.value,
                smartSelected = homeSmartSelected.value,
                connectionOptions = connectionOptions.value,
                connectionOptionsBusy = connectionOptionsBusy.value,
                internetHealth = internetHealth.value,
                recoveryAttempt = recoveryAttempt.value,
                onSaveConnectionOptions = ::saveConnectionOptions,
                activeProfileName = activeProfileName.value,
                hasActiveProfile = activeProfilePath.value.isNotBlank(),
                rootMode = Settings.privacyUseRoot,
                settingsState = nativeSettings.value,
                regionalApps = regionalApps.value,
                regionalAppsRevision = regionalAppsRevision.value,
                onOpenRegionalApps = ::loadRegionalApps,
                onSaveRegionalApps = ::saveRegionalApps,
                onResetRegionalApps = ::resetRegionalApps,
                regionalOptions = regionalOptions.value,
                regionalBusy = regionalBusy.value,
                onSaveRegionalOptions = ::saveRegionalOptions,
                trafficFilters = trafficFilters.value,
                trafficFiltersBusy = trafficFiltersBusy.value,
                onSaveTrafficFilters = ::saveTrafficFilters,
                profiles = profiles.value,
                profilesLoading = profilesLoading.value,
                profilesLoadFailed = profilesLoadFailed.value,
                onRetryProfiles = { profilesLoading.value = true; refreshProfiles() },
                onUpdateAllProfiles = ::refreshAllRemoteProfiles,
                profileImportRevision = profileImportRevision.value,
                profileSelectionRevision = profileSelectionRevision.value,
                busyProfileId = busyProfileId.value,
                profileEditor = profileEditor.value,
                editorSession = editorSession,
                profileEditorLoadFailed = profileEditorLoadFailed.value,
                profileEditorSavedRevision = profileEditorSavedRevision.value,
                onRetryProfileEditor = { profileEditorId?.let(::loadProfileEditor) },
                profileEditorBusy = profileEditorBusy.value,
                perAppSnapshot = perAppSnapshot.value,
                perAppBusy = perAppBusy.value,
                pendingPerAppImport = pendingPerAppImport.value,
                logSnapshot = logSnapshot.value,
                logBusy = logBusy.value,
                chainOptions = chainOptions.value,
                chainBusy = chainBusy.value,
                activeOutbound = activeOutbound.value,
                requiresReconnect = requiresReconnect.value,
                reconnectBusy = reconnectBusy.value,
                onQuickServiceMode = ::saveQuickServiceMode,
                onQuickLanSharing = ::saveQuickLanSharing,
                onQuickChainMode = ::saveQuickChainMode,
                onResolveLanSharing = { withContext(Dispatchers.IO) { wifiSharingRepository.load() } },
                outboundBusyTag = outboundBusyTag.value,
                outboundOperationRevision = outboundOperationRevision.value,
                systemStats = systemStats.value,
                wifiSharingDetails = wifiSharingDetails.value,
                wifiSharingDetailsBusy = wifiSharingDetailsBusy.value,
                wifiSharingBusy = wifiSharingBusy.value,
                updateChecking = updateChecking.value,
                updateMessage = updateMessage.value,
                updateUrl = updateUrl.value,
                diagnosticSnapshot = diagnosticSnapshot.value,
                diagnosticBusy = diagnosticBusy.value,
                diagnosticReport = diagnosticSnapshot.value?.let {
                    NativeDiagnosticReport.create(BuildConfig.VERSION_NAME, it, vpnProtection.value)
                }.orEmpty(),
                vpnProtection = vpnProtection.value,
                onRunDiagnostics = ::runDiagnostics,
                onCancelDiagnostics = ::cancelDiagnostics,
                onShareDiagnosticReport = ::shareDiagnosticReport,
                onRefreshVpnProtection = ::refreshVpnProtection,
                onOpenVpnSettings = ::openVpnSettings,
                errorMessage = errorMessage.value,
                onDismissError = { errorMessage.value = null },
                onToggleConnection = ::toggleConnection,
                ipVisibilitySession = ipVisibilitySession,
                onSelectProfile = ::selectProfile,
                onDeleteProfile = ::deleteProfile,
                onRefreshProfile = ::refreshRemoteProfile,
                onOpenProfileEditor = ::openProfileEditor,
                onSaveProfileEditor = ::saveProfileEditor,
                onCancelProfileImport = { profileImportCancellation?.cancel() },
                onImportProfile = ::importProfile,
                onImportFreeProfile = ::importFreeProfile,
                onCopyProfileConfig = ::copyProfileConfig,
                onExportProfileConfig = ::exportProfileConfig,
                onPerAppModeChanged = ::setPerAppMode,
                onTogglePerAppPackage = ::togglePerAppPackage,
                onClearPerApp = ::clearPerAppPackages,
                onImportPerAppClipboard = ::importPerAppClipboard,
                onImportPerAppFile = { perAppImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onExportPerAppClipboard = ::exportPerAppClipboard,
                onExportPerAppFile = { perAppExportLauncher.launch("per-app-proxy.json") },
                onConfirmPerAppImport = ::confirmPerAppImport,
                onDismissPerAppImport = { pendingPerAppImport.value = null },
                onRefreshLogs = ::refreshLogs,
                onClearLogs = ::clearLogs,
                onSaveChainOptions = ::saveChainOptions,
                onSelectOutbound = ::selectOutbound,
                onTestOutbound = ::testOutbound,
                onRefreshWifiSharingDetails = ::refreshWifiSharingDetails,
                onImportSettingsClipboard = ::importSettingsFromClipboard,
                onImportSettingsFile = ::importSettingsFromFile,
                onExportSettingsClipboard = ::exportSettingsToClipboard,
                onExportSettingsFile = ::exportSettingsToFile,
                onResetSettings = ::resetCoreSettings,
                onCheckUpdate = ::checkForUpdate,
                onOpenUpdate = { updateUrl.value?.let(::openExternalUrl) },
                onOpenFork = { openExternalUrl(NativeUpdateRepository.FORK_URL) },
                onOpenUpstream = { openExternalUrl(NativeUpdateRepository.UPSTREAM_URL) },
                onOpenTerms = { openExternalUrl(NativeUpdateRepository.TERMS_URL) },
                onOpenPrivacy = { openExternalUrl(NativeUpdateRepository.PRIVACY_URL) },
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
                onWifiSharingChanged = ::changeWifiSharing,
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
        nativeForeground = true
        refreshProfileSnapshot()
        refreshSettingsSnapshot()
        refreshCoreOptions()
        refreshChainOptions()
        refreshProfiles()
        refreshPerApp()
        if (serviceStatus.value == Status.Started) refreshOutbounds(showError = false)
        startProfileUpdateLoop()
        startLogRefreshLoop()
        startStatsRefreshLoop()
        startActiveOutboundRefreshLoop()
        connection.connect()
        restartHealthMonitor()
        startSmartSelectionLoop()
    }

    override fun onResume() {
        super.onResume()
        if (!generalPreferencesBusy.value && !pendingStartAfterVpnPermission && !nativeStartPending &&
            !tlsBusy.value && !generalOptionsBusy.value && !tunnelBusy.value && !dnsBusy.value && !inboundBusy.value && !privacySetupBusy.value &&
            generalPreferencesRepository.load().language != appliedLanguage) {
            recreate()
            return
        }
        refreshVpnProtection()
        refreshProfileSnapshot()
        refreshSettingsSnapshot()
        refreshCoreOptions()
        refreshProfiles()
        refreshPerApp()
        if (serviceStatus.value == Status.Started) {
            refreshOutbounds(showError = false)
            maybeRequestNotificationPermission()
            maybePromptBatteryOptimization()
        }
        restartHealthMonitor()
        startSmartSelectionLoop()
        if (serviceStatus.value == Status.Started) trackStableConnection() else scheduleRecovery()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStop() {
        nativeForeground = false
        smartSelectionJob?.cancel()
        smartSelectionJob = null
        stopHealthMonitor()
        cancelRecovery()
        cancelDiagnostics()
        profileUpdateJob?.cancel()
        profileUpdateJob = null
        logRefreshJob?.cancel()
        logRefreshJob = null
        statsRefreshJob?.cancel()
        statsRefreshJob = null
        activeOutboundRefreshJob?.cancel()
        activeOutboundRefreshJob = null
        activeOutbound.value = null
        connection.disconnect()
        super.onStop()
    }

    private fun refreshVpnProtection() {
        vpnProtection.value = diagnosticsRepository.protection()
    }

    private fun openVpnSettings() {
        try {
            val intent = Intent(AndroidSettings.ACTION_VPN_SETTINGS)
            startActivity(if (intent.resolveActivity(packageManager) != null) intent else Intent(AndroidSettings.ACTION_SETTINGS))
        } catch (_: Exception) {
            errorMessage.value = getString(R.string.native_protection_open_failed)
        }
    }

    private fun runDiagnostics() {
        if (diagnosticBusy.value) return
        diagnosticBusy.value = true
        diagnosticSnapshot.value = null
        refreshVpnProtection()
        diagnosticJob = lifecycleScope.launch {
            try {
                diagnosticSnapshot.value = diagnosticsRepository.run { progress ->
                    diagnosticSnapshot.value = progress
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                errorMessage.value = getString(R.string.native_diagnostics_error)
            } finally {
                diagnosticBusy.value = false
                diagnosticJob = null
            }
        }
    }

    private fun cancelDiagnostics() {
        diagnosticJob?.cancel()
    }

    private fun shareDiagnosticReport(report: String) {
        runCatching {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                },
                getString(R.string.native_diagnostics_title),
            ))
        }.onFailure {
            errorMessage.value = getString(R.string.native_profile_share_failed)
        }
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
                        profileOperationMutex.withLock {
                            if (syncActive) profileRepository.synchronizeActiveProfile()
                            profileRepository.listProfiles()
                        }
                    }
                profiles.value = loaded
                profilesLoadFailed.value = false
                refreshProfileSnapshot()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                profilesLoadFailed.value = true
                if (syncActive) {
                    errorMessage.value = error.message ?: error.javaClass.simpleName
                }
            } finally {
                profilesLoading.value = false
            }
        }
    }

    private fun refreshLogs() {
        val serviceLines = serviceLogLines.toList()
        lifecycleScope.launch {
            try {
                logSnapshot.value =
                    withContext(Dispatchers.IO) {
                        logRepository.readRecent(serviceLines)
                    }
            } catch (error: Exception) {
                Log.w(TAG, "failed to refresh native logs", error)
            }
        }
    }

    private fun clearLogs() {
        if (logBusy.value) return
        logBusy.value = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { logRepository.clear() }
                serviceLogLines.clear()
                logSnapshot.value = NativeLogSnapshot(emptyList(), emptyList())
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                logBusy.value = false
            }
        }
    }

    private fun refreshOutbounds(showError: Boolean) {
        if (serviceStatus.value != Status.Started) {
            outboundGroups.value = emptyList()
            outboundBusyTag.value = null
            return
        }
        if (outboundBusyTag.value != null) return

        outboundBusyTag.value = "__refresh__"
        lifecycleScope.launch {
            try {
                outboundGroups.value =
                    withContext(Dispatchers.IO) {
                        outboundsRepository.load()
                    }
            } catch (error: Exception) {
                if (showError) {
                    errorMessage.value = error.message ?: error.javaClass.simpleName
                } else {
                    Log.w(TAG, "failed to refresh native outbounds", error)
                }
            } finally {
                outboundBusyTag.value = null
            }
        }
    }

    private fun selectOutbound(groupTag: String, outboundTag: String) {
        runOutboundOperation(outboundTag) {
            val preferences = generalPreferencesRepository.saveSmartSelection(false)
            runOnUiThread { generalPreferences.value = preferences; connectionHaptic(stopping = false); restartHealthMonitor() }
            outboundsRepository.select(groupTag, outboundTag)
        }
    }

    private fun testOutbound(tag: String) {
        runOutboundOperation(tag) {
            runOnUiThread { connectionHaptic(stopping = false) }
            outboundsRepository.test(tag)
        }
    }

    private fun testActiveOutbounds() {
        runOutboundOperation("__test_active__") {
            outboundsRepository.testActive()
        }
    }

    private fun runOutboundOperation(
        busyTag: String,
        operation: () -> Unit,
    ) {
        if (serviceStatus.value != Status.Started) {
            errorMessage.value = getString(R.string.native_outbounds_disconnected)
            outboundGroups.value = emptyList()
            return
        }
        if (outboundBusyTag.value != null) return

        outboundBusyTag.value = busyTag
        lifecycleScope.launch {
            try {
                val refreshed =
                    withContext(Dispatchers.IO) {
                        operation()
                        outboundsRepository.load()
                    }
                outboundGroups.value = refreshed
                val active = withContext(Dispatchers.IO) { outboundsRepository.loadActive() }
                if (nativeForeground && serviceStatus.value == Status.Started) activeOutbound.value = active
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                outboundOperationRevision.value++
                outboundBusyTag.value = null
            }
        }
    }

    private fun startLogRefreshLoop() {
        if (logRefreshJob?.isActive == true) return
        logRefreshJob =
            lifecycleScope.launch {
                while (isActive) {
                    refreshLogs()
                    delay(LOG_REFRESH_INTERVAL_MS)
                }
            }
    }

    private fun startActiveOutboundRefreshLoop() {
        if (activeOutboundRefreshJob?.isActive == true) return
        activeOutboundRefreshJob = lifecycleScope.launch {
            while (isActive && nativeForeground) {
                if (serviceStatus.value == Status.Started) {
                    try {
                        val active = withContext(Dispatchers.IO) { outboundsRepository.loadActive() }
                        if (nativeForeground && serviceStatus.value == Status.Started) activeOutbound.value = active
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "failed to refresh active native outbound", error)
                    }
                } else activeOutbound.value = null
                delay(3000)
            }
        }
    }

    private fun startStatsRefreshLoop() {
        if (statsRefreshJob?.isActive == true) return
        statsRefreshJob =
            lifecycleScope.launch {
                while (isActive) {
                    if (serviceStatus.value == Status.Started) {
                        try {
                            systemStats.value =
                                withContext(Dispatchers.IO) {
                                    statsRepository.load()
                                }
                        } catch (error: Exception) {
                            Log.w(TAG, "failed to refresh native core statistics", error)
                        }
                    } else if (systemStats.value != NativeSystemStats()) {
                        systemStats.value = NativeSystemStats()
                    }
                    delay(1_000L)
                }
            }
    }

    private fun appendServiceLog(message: String) {
        if (message.isBlank()) return
        serviceLogLines.addLast(logRepository.decorateServiceLine(message))
        while (serviceLogLines.size > MAX_SERVICE_LOG_LINES) serviceLogLines.removeFirst()
        refreshLogs()
    }

    private fun refreshPerApp() {
        if (perAppBusy.value) return
        perAppBusy.value = true
        lifecycleScope.launch {
            try {
                perAppSnapshot.value = withContext(Dispatchers.IO) {
                    perAppOperationMutex.withLock { perAppRepository.snapshot() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                perAppBusy.value = false
            }
        }
    }

    private fun importPerAppClipboard() {
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            errorMessage.value = getString(R.string.native_settings_clipboard_empty)
            return
        }
        preparePerAppImport { text }
    }

    private fun preparePerAppImport(read: () -> String) {
        if (serviceStatus.value != Status.Stopped) {
            errorMessage.value = getString(R.string.native_per_app_disconnect)
            return
        }
        pendingPerAppImport.value = null
        runPerAppTransfer {
            val backup = withContext(Dispatchers.IO) { NativePerAppBackupCodec.decode(read()) }
            pendingPerAppImport.value = backup
        }
    }

    private fun confirmPerAppImport() {
        val backup = pendingPerAppImport.value ?: return
        if (perAppBusy.value) return
        pendingPerAppImport.value = null
        runPerAppOperation { perAppRepository.importBackup(backup) }
    }

    private fun exportPerAppClipboard() {
        runPerAppTransfer {
            val text = withContext(Dispatchers.IO) { perAppRepository.exportBackup() }
            require(text.toByteArray(Charsets.UTF_8).size <= NativeProfileTransfer.MAX_CLIPBOARD_BYTES) {
                getString(R.string.native_per_app_backup_clipboard_large)
            }
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("VetrOFF per-app routing", text))
            Toast.makeText(this@MainActivity, R.string.native_profile_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun runPerAppTransfer(action: suspend () -> Unit) {
        if (perAppBusy.value) {
            errorMessage.value = getString(R.string.native_per_app_backup_busy)
            return
        }
        perAppBusy.value = true
        lifecycleScope.launch {
            try {
                perAppOperationMutex.withLock { action() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: getString(R.string.native_per_app_backup_invalid)
            } finally {
                perAppBusy.value = false
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
                perAppSnapshot.value = withContext(Dispatchers.IO) {
                    perAppOperationMutex.withLock {
                        BoxService.withNativeLifecycle {
                            check(!BoxService.hasActiveCore()) { getString(R.string.native_per_app_disconnect) }
                            operation()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                perAppBusy.value = false
            }
        }
    }

    private fun openProfileEditor(profile: NativeProfile) {
        if (profileEditorBusy.value) return
        editorSession.reset()
        loadProfileEditor(profile.id)
    }

    private fun loadProfileEditor(id: String) {
        if (profileEditorBusy.value) return
        profileEditorId = id
        profileEditor.value = null
        profileEditorLoadFailed.value = false
        profileEditorBusy.value = true
        lifecycleScope.launch {
            try {
                profileEditor.value =
                    withContext(Dispatchers.IO) {
                        profileOperationMutex.withLock { profileRepository.loadEditor(id) }
                    }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                profileEditorLoadFailed.value = profileEditor.value == null
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
                profileEditorSavedRevision.value += 1
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                profileEditorLoadFailed.value = profileEditor.value == null
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
        runProfileOperation(profile.id, requireDisconnected = true,
            onSuccess = { profileSelectionRevision.value += 1 }) {
            profileRepository.setActive(profile.id)
        }
    }

    private fun deleteProfile(profile: NativeProfile) {
        runProfileOperation(profile.id, requireDisconnected = false, stopBeforeDeletingId = profile.id) {
            profileRepository.delete(profile.id)
        }
    }

    private fun refreshRemoteProfile(profile: NativeProfile) {
        if (!profile.isRemote) return
        runProfileOperation(profile.id, requireDisconnected = false) {
            profileRepository.refreshRemote(profile.id)
        }
    }

    private fun refreshAllRemoteProfiles() {
        val remoteIds = profiles.value.filter { it.isRemote }.map { it.id }
        if (remoteIds.isEmpty()) return
        runProfileOperation("update-all", requireDisconnected = false) {
            val failures = mutableListOf<String>()
            for (id in remoteIds) {
                runCatching { profileRepository.refreshRemote(id) }.onFailure { failures += it.message ?: it.javaClass.simpleName }
            }
            // Keep successful updates even if another subscription failed, and publish that snapshot.
            if (failures.isNotEmpty()) throw IllegalStateException(failures.distinct().joinToString("\n"))
        }
    }

    private fun importFreeProfile(profile: com.hiddify.hiddify.nativeprofile.NativeFreeProfile, title: String) {
        val cancellation = NativeProfileImportCancellation()
        runProfileOperation(IMPORT_BUSY_ID, requireDisconnected = true, importCancellation = cancellation,
            onSuccess = { profileImportRevision.value += 1 }) {
            profileRepository.importRemote(profile.url, requestedName = title, updateIntervalHours = 12,
                replaceFeatures = true, neededFeatures = profile.neededFeatures, cancellation = cancellation)
        }
    }

    private fun importProfile(
        raw: String,
        name: String?,
        intervalHours: Int?,
        disableAutoUpdate: Boolean,
    ) {
        val cancellation = NativeProfileImportCancellation()
        runProfileOperation(IMPORT_BUSY_ID, requireDisconnected = true, importCancellation = cancellation,
            onSuccess = { profileImportRevision.value += 1 }) {
            profileRepository.importInput(
                rawInput = raw,
                cancellation = cancellation,
                name = name,
                updateIntervalHours = intervalHours,
                disableAutoUpdate = disableAutoUpdate,
            )
        }
    }

    private fun runProfileOperation(
        operationId: String,
        requireDisconnected: Boolean,
        onSuccess: () -> Unit = {},
        stopBeforeDeletingId: String? = null,
        importCancellation: NativeProfileImportCancellation? = null,
        operation: () -> Unit,
    ) {
        if (busyProfileId.value != null) return
        if (requireDisconnected && (serviceStatus.value != Status.Stopped || nativeStartPending ||
                pendingStartAfterVpnPermission || BoxService.hasActiveCore())) {
            errorMessage.value = getString(R.string.native_profile_disconnect_required)
            return
        }

        busyProfileId.value = operationId
        profileImportCancellation = importCancellation
        lifecycleScope.launch {
            try {
                val deletingUsedProfile = stopBeforeDeletingId != null && withContext(Dispatchers.IO) {
                    profileOperationMutex.withLock {
                        val chain = runCatching { chainRepository.load() }.getOrNull()
                        profileRepository.activeProfile()?.id == stopBeforeDeletingId ||
                            chain?.extraProfileId == stopBeforeDeletingId || chain?.unblockerProfileId == stopBeforeDeletingId
                    }
                }
                if (deletingUsedProfile) {
                    requestStop()
                    awaitServiceStopped()
                }
                val loaded =
                    withContext(Dispatchers.IO) {
                        suspend fun mutate() = profileOperationMutex.withLock {
                            currentCoroutineContext().ensureActive()
                            importCancellation?.ensureActive()
                            operation()
                            profileRepository.synchronizeActiveProfile()
                            profileRepository.listProfiles()
                        }
                        if (requireDisconnected || deletingUsedProfile) BoxService.withNativeLifecycle {
                            check(!BoxService.hasActiveCore()) { getString(R.string.native_profile_disconnect_required) }
                            mutate()
                        } else mutate()
                    }
                profiles.value = loaded
                profilesLoadFailed.value = false
                refreshProfileSnapshot()
                if (stopBeforeDeletingId != null) runCatching { refreshChainOptions() }
                onSuccess()
            } catch (cancelled: CancellationException) {
                // User cancellation returns to the retained import draft without an error dialog.
                // Lifecycle cancellation still propagates to the Activity scope.
                currentCoroutineContext().ensureActive()
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                    errorMessage.value = getString(R.string.native_connection_stop_timeout)
                } else if (importCancellation == null) throw cancelled
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                runCatching { withContext(Dispatchers.IO) { profileOperationMutex.withLock {
                    profileRepository.synchronizeActiveProfile()
                    profileRepository.listProfiles()
                } } }.onSuccess { profiles.value = it; profilesLoadFailed.value = false; refreshProfileSnapshot() }
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                profileImportCancellation = null
                busyProfileId.value = null
            }
        }
    }

    private fun copyProfileConfig(profile: NativeProfile) {
        exportProfile(profile.id, toClipboard = true)
    }

    private fun exportProfileConfig(profile: NativeProfile) {
        if (pendingProfileExportId != null || busyProfileId.value != null) return
        pendingProfileExportId = profile.id
        try {
            // A raw profile may be JSON, YAML or protocol links; do not mislabel it as JSON.
            profileExportLauncher.launch("profile-${profile.id}.txt")
        } catch (error: Exception) {
            pendingProfileExportId = null
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun exportProfile(
        id: String,
        toClipboard: Boolean = false,
        writeFile: ((String) -> Unit)? = null,
    ) {
        if (busyProfileId.value != null) {
            errorMessage.value = getString(R.string.native_profile_export_busy)
            return
        }
        busyProfileId.value = id
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    profileOperationMutex.withLock {
                        val raw = profileRepository.loadEditor(id).content
                        if (toClipboard) {
                            require(raw.toByteArray(Charsets.UTF_8).size <= NativeProfileTransfer.MAX_CLIPBOARD_BYTES) {
                                getString(R.string.native_profile_clipboard_too_large)
                            }
                        }
                        writeFile?.invoke(raw)
                        raw
                    }
                }
                if (toClipboard) {
                    getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("VetrOFF profile", content))
                }
                Toast.makeText(
                    this@MainActivity,
                    if (toClipboard) R.string.native_profile_copied else R.string.native_profile_exported,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                busyProfileId.value = null
            }
        }
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming == null) return

        val textPayload =
            when (incoming.action) {
                Intent.ACTION_VIEW -> incoming.dataString
                Intent.ACTION_SEND -> incoming.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() }

        val sharedDocument =
            if (incoming.action == Intent.ACTION_SEND) {
                sharedStreamUri(incoming)
            } else {
                null
            }

        if (textPayload == null && sharedDocument == null) return

        // Avoid re-importing the launch intent after Activity recreation/resume.
        incoming.action = null

        if (textPayload != null) {
            importProfile(textPayload, null, null, false)
        } else if (sharedDocument != null) {
            importSharedProfile(sharedDocument)
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedStreamUri(incoming: Intent): Uri? {
        val extra =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
        return extra
            ?: incoming.clipData
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.uri
    }

    private fun importSharedProfile(uri: Uri) {
        lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openInputStream(uri)
                            ?.use(NativeProfileTransfer::readText)
                            ?: throw java.io.FileNotFoundException(uri.toString())
                    }
                }

            result.fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        errorMessage.value = getString(R.string.native_profile_file_empty)
                    } else {
                        importProfile(text, null, null, false)
                    }
                },
                onFailure = { error ->
                    errorMessage.value =
                        getString(
                            R.string.native_profile_file_read_failed,
                            error.message ?: error.javaClass.simpleName,
                        )
                },
            )
        }
    }

    private fun readNativeSettings() =
        NativeSettingsState(
            serviceMode = Settings.serviceMode,
            rootRequested = Settings.privacyUseRootRequested,
            wifiSharing = AutomaticHotspot.snapshot()["active"] == true,
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
        refreshProxyPrivacyState()
        refreshPrivacySetupState()
        generalPreferences.value = generalPreferencesRepository.load()
        themeMode.value = appearanceRepository.load()
        connectionOptions.value = NetworkPrivacySettings.loadConnection(this)
        regionalOptions.value = regionalRepository.load()
        trafficFilters.value = NetworkPrivacySettings.loadFilters(this)
    }

    private fun refreshProxyPrivacyState() {
        proxyPrivacySnapshotJob?.cancel()
        proxyPrivacySnapshotJob = lifecycleScope.launch {
            proxyPrivacy.value = withContext(Dispatchers.IO) { NetworkPrivacySettings.loadProxyPrivacy(applicationContext) }
        }
    }

    private fun saveProxyPrivacy(options: com.hiddify.hiddify.privacy.NativeProxyPrivacy) {
        if (proxyPrivacyBusy.value || privacySetupBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value || inboundBusy.value) return
        proxyPrivacyBusy.value = true
        proxyPrivacySnapshotJob?.cancel()
        lifecycleScope.launch {
            try {
                proxyPrivacy.value = withContext(Dispatchers.IO) {
                    NetworkPrivacySettings.saveProxyPrivacy(applicationContext, options)
                    NetworkPrivacySettings.loadProxyPrivacy(applicationContext)
                }
                refreshSettingsSnapshot()
                Toast.makeText(this@MainActivity, R.string.native_proxy_saved, Toast.LENGTH_LONG).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                proxyPrivacyBusy.value = false
                refreshSettingsSnapshot()
            }
        }
    }

    private fun refreshPrivacySetupState() {
        privacySetupSnapshotJob?.cancel()
        privacySetupSnapshotJob = lifecycleScope.launch {
            val (configured, canRestore) = withContext(Dispatchers.IO) {
                privacySetupRepository.isConfigured() to privacySetupRepository.canRestore()
            }
            privacyConfigured.value = configured
            privacyCanRestore.value = canRestore
        }
    }

    private fun applyPrivacySetup(restore: Boolean) {
        if (privacySetupBusy.value || proxyPrivacyBusy.value || inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value || regionalBusy.value || connectionOptionsBusy.value || wifiSharingBusy.value) return
        if (serviceStatus.value != Status.Stopped || pendingStartAfterVpnPermission || nativeStartPending) {
            errorMessage.value = getString(R.string.native_privacy_setup_disconnect)
            return
        }
        privacySetupBusy.value = true
        cancelRecovery()
        lifecycleScope.launch {
            try {
                BoxService.withNativeLifecycle {
                    check(serviceStatus.value == Status.Stopped && !BoxService.hasActiveCore() && !BoxService.isRunning() &&
                        !pendingStartAfterVpnPermission && !nativeStartPending) {
                        getString(R.string.native_privacy_setup_disconnect)
                    }
                    withContext(Dispatchers.IO) {
                        regionalOperationMutex.withLock {
                            privacySetupRepository.apply(restore)
                            com.hiddify.hiddify.privacy.VpnServiceVisibility.sync(applicationContext, Settings.privacyUseRoot)
                        }
                    }
                }
                refreshImportedSettingsSnapshots()
                regionalApps.value = null
                regionalAppsRevision.value += 1
                Toast.makeText(this@MainActivity,
                    if (restore) R.string.native_privacy_setup_restored else R.string.native_privacy_setup_saved,
                    Toast.LENGTH_LONG).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                privacySetupBusy.value = false
                refreshSettingsSnapshot()
            }
        }
    }

    private fun saveLanguage(language: com.hiddify.hiddify.nativepreferences.NativeLanguage) {
        if (generalPreferencesBusy.value || themeBusy.value || proxyPrivacyBusy.value || privacySetupBusy.value || inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value ||
            chainBusy.value || wifiSharingBusy.value || pendingStartAfterVpnPermission || nativeStartPending) return
        generalPreferencesBusy.value = true
        lifecycleScope.launch {
            try {
                generalPreferences.value = withContext(Dispatchers.IO) { generalPreferencesRepository.saveLanguage(language) }
                if (language != appliedLanguage) recreate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                generalPreferencesBusy.value = false
            }
        }
    }

    private fun saveOutboundSort(sort: com.hiddify.hiddify.nativepreferences.NativeOutboundSort) {
        if (generalPreferencesBusy.value) return
        generalPreferencesBusy.value = true
        lifecycleScope.launch {
            try {
                generalPreferences.value = withContext(Dispatchers.IO) { generalPreferencesRepository.saveOutboundSort(sort) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { errorMessage.value = error.message ?: error.javaClass.simpleName }
            finally { generalPreferencesBusy.value = false }
        }
    }

    private fun saveSmartSelection(enabled: Boolean) {
        if (generalPreferencesBusy.value || outboundBusyTag.value != null) return
        generalPreferencesBusy.value = true
        lifecycleScope.launch {
            try {
                generalPreferences.value = withContext(Dispatchers.IO) { generalPreferencesRepository.saveSmartSelection(enabled) }
                startSmartSelectionLoop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally { generalPreferencesBusy.value = false }
        }
    }

    /** The core balancer also works in background; raw-profile ranking only runs while visible. */
    private fun startSmartSelectionLoop() {
        if (!nativeForeground || serviceStatus.value != Status.Started) {
            smartSelectionJob?.cancel()
            smartSelectionJob = null
            if (serviceStatus.value != Status.Started) homeSmartSelected.value = false
            return
        }
        if (smartSelectionJob?.isActive == true) return
        val busyToken = "__smart_selection_${java.util.UUID.randomUUID()}__"
        smartSelectionJob = lifecycleScope.launch {
            var wasEnabled = generalPreferences.value.smartSelection || connectionOptions.value.adaptiveNetwork
            var leaveBalancer = false
            var profile: String? = null
            var ranker = com.hiddify.hiddify.nativeconnection.NativeServerRanker()
            var historyId: String? = null
            var savedRevision = 0L
            var saveAt = 0L
            suspend fun flushHistory() {
                val id = historyId ?: return
                if (savedRevision == ranker.revision) return
                val snapshot = ranker.snapshot()
                saveAt = System.currentTimeMillis()
                try {
                    withContext(Dispatchers.IO) { serverHistoryRepository.save(id, snapshot) }
                    savedRevision = ranker.revision
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { Log.w(TAG, "could not save native server history", error) }
            }
            try {
                while (isActive && nativeForeground && serviceStatus.value == Status.Started) {
                    delay(3000)
                    if (savedRevision != ranker.revision && System.currentTimeMillis() - saveAt >= 10000) flushHistory()
                    val enabled = generalPreferences.value.smartSelection || connectionOptions.value.adaptiveNetwork
                    if (wasEnabled && !enabled) leaveBalancer = true
                    if (enabled) leaveBalancer = false
                    if (wasEnabled != enabled) homeSmartSelected.value = false
                    wasEnabled = enabled
                    if (!enabled && !leaveBalancer) continue
                    if (outboundBusyTag.value != null || generalPreferencesBusy.value || connectionOptionsBusy.value) continue
                    outboundBusyTag.value = busyToken
                    try {
                        val path = Settings.activeConfigPath
                        if (path != profile) {
                            homeSmartSelected.value = false
                            flushHistory()
                            val nextRanker = com.hiddify.hiddify.nativeconnection.NativeServerRanker()
                            val nextId = withContext(Dispatchers.IO) {
                                if (path.isBlank()) null else profileRepository.activeProfile()?.id
                                    ?.takeIf { java.io.File(path).name == "$it.json" }
                            }
                            nextId?.let { id ->
                                val history = withContext(Dispatchers.IO) { serverHistoryRepository.load(id) }
                                nextRanker.restore(history, System.currentTimeMillis())
                            }
                            ranker = nextRanker
                            historyId = nextId
                            savedRevision = ranker.revision
                            saveAt = System.currentTimeMillis()
                            profile = path
                        }
                        if (path.isBlank()) continue
                        val groups = withContext(Dispatchers.IO) { outboundsRepository.load() }
                        if (!nativeForeground || serviceStatus.value != Status.Started || path != Settings.activeConfigPath) continue
                        outboundGroups.value = groups
                        val group = groups.firstOrNull()?.takeIf { it.selectable } ?: continue
                        val balancer = group.items.firstOrNull { it.isGroup && it.tag == "lowest" }
                        val direct = group.items.filter { !it.isGroup }.take(128)
                        val now = System.currentTimeMillis()
                        val target = if (enabled) {
                            if (balancer != null) balancer.tag.takeIf { it != group.selectedTag }
                            else {
                                direct.forEach { ranker.observe(it.tag, it.delayMs, it.testTimestampMs, now) }
                                ranker.recommend(group.selectedTag, direct.map { it.tag }.toSet(), now)
                            }
                        } else if (leaveBalancer && balancer != null && group.selectedTag == balancer.tag) {
                            balancer.selectedChildTag?.takeIf { tag -> direct.any { it.tag == tag } }
                                ?: direct.minByOrNull { if (it.delayMs in 1..64999) it.delayMs else 65535 }?.tag
                        } else null
                        if (enabled && balancer != null && group.selectedTag == balancer.tag) homeSmartSelected.value = true
                        if (target == null && !enabled) leaveBalancer = false
                        if (target != null) {
                            BoxService.withNativeLifecycle {
                                if (nativeForeground && serviceStatus.value == Status.Started && path == Settings.activeConfigPath &&
                                    enabled == (generalPreferences.value.smartSelection || connectionOptions.value.adaptiveNetwork)) {
                                    outboundsRepository.selectForeground(group.tag, target)
                                    homeSmartSelected.value = enabled
                                    leaveBalancer = false
                                    ranker.switched(now)
                                    restartHealthMonitor()
                                }
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(TAG, "native smart server selection failed", error)
                    } finally {
                        if (outboundBusyTag.value == busyToken) outboundBusyTag.value = null
                    }
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    try { flushHistory() }
                    catch (error: Exception) { Log.w(TAG, "could not save native server history", error) }
                }
            }
        }
    }

    private fun saveHapticFeedback(enabled: Boolean) {
        if (generalPreferencesBusy.value) return
        generalPreferencesBusy.value = true
        lifecycleScope.launch {
            try {
                generalPreferences.value = withContext(Dispatchers.IO) { generalPreferencesRepository.saveHapticFeedback(enabled) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                generalPreferencesBusy.value = false
            }
        }
    }

    private fun connectionHaptic(stopping: Boolean) {
        if (!generalPreferences.value.hapticFeedback) return
        runCatching {
            window.decorView.performHapticFeedback(if (stopping) android.view.HapticFeedbackConstants.CONTEXT_CLICK
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun saveTheme(mode: NativeThemeMode) {
        if (themeBusy.value || generalPreferencesBusy.value) return
        themeBusy.value = true
        lifecycleScope.launch {
            try {
                themeMode.value = withContext(Dispatchers.IO) { appearanceRepository.save(mode) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                themeBusy.value = false
            }
        }
    }

    private fun openSystemSettings(preferred: Intent) {
        try {
            val fallback = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            val intent = if (preferred.resolveActivity(packageManager) != null) preferred else fallback
            try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { startActivity(fallback) }
        } catch (error: Exception) {
            errorMessage.value = error.message ?: getString(R.string.native_general_settings_failed)
        }
    }

    private fun saveConnectionOptions(options: NativeConnectionOptions) {
        if (connectionOptionsBusy.value) return
        connectionOptionsBusy.value = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { NetworkPrivacySettings.saveConnection(applicationContext, options) }
                connectionOptions.value = NetworkPrivacySettings.loadConnection(applicationContext)
                refreshPrivacySetupState()
                restartHealthMonitor()
                startSmartSelectionLoop()
                cancelRecovery()
                if (serviceStatus.value == Status.Started) trackStableConnection() else scheduleRecovery()
                Toast.makeText(this@MainActivity, R.string.native_connection_saved, Toast.LENGTH_SHORT).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                connectionOptionsBusy.value = false
            }
        }
    }

    private fun cancelRecovery() {
        recoveryJob?.cancel()
        recoveryJob = null
        stableConnectionJob?.cancel()
        stableConnectionJob = null
        recoveryAttempt.value = 0
    }

    private fun scheduleRecovery() {
        if (!nativeForeground || reconnectBusy.value || recoveryJob != null || !connectionOptions.value.recoveryEnabled ||
            !Settings.connectionDesired || !Settings.startedByUser || serviceStatus.value != Status.Stopped ||
            pendingStartAfterVpnPermission || nativeStartPending || wifiSharingBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value) return
        stableConnectionJob?.cancel()
        recoveryJob = lifecycleScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                while (isActive && nativeForeground && Settings.connectionDesired && Settings.startedByUser &&
                    connectionOptions.value.recoveryEnabled && serviceStatus.value == Status.Stopped) {
                    if (BoxService.isStarted()) {
                        connection.reconnect()
                        break
                    }
                    val adaptive = connectionOptions.value.adaptiveNetwork
                    val wait = recoveryPolicy.nextDelaySeconds(adaptive)
                    if (wait == null) {
                        Settings.connectionDesired = false
                        Settings.startedByUser = false
                        errorMessage.value = getString(R.string.native_recovery_exhausted)
                        break
                    }
                    if (adaptive && !withContext(Dispatchers.IO) { healthRepository.underlyingNetworkAvailable() }) {
                        recoveryAttempt.value = recoveryPolicy.attempts + 1
                        delay(15_000)
                        continue
                    }
                    recoveryAttempt.value = recoveryPolicy.attempts + 1
                    delay(wait * 1_000)
                    val result = BoxService.withNativeLifecycle {
                        val permission = Settings.serviceMode != ServiceMode.VPN || Settings.privacyUseRoot ||
                            VpnService.prepare(this@MainActivity) == null
                        val network = !adaptive || withContext(Dispatchers.IO) { healthRepository.underlyingNetworkAvailable() }
                        val decision = NativeRecoveryPolicy.decision(
                            Settings.connectionDesired && Settings.startedByUser,
                            connectionOptions.value.recoveryEnabled, nativeForeground, permission,
                            pendingStartAfterVpnPermission || nativeStartPending || wifiSharingBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value ||
                                serviceStatus.value != Status.Stopped,
                            adaptive, network, BoxService.hasActiveCore(),
                        )
                        if (!permission) {
                            Settings.connectionDesired = false
                            Settings.startedByUser = false
                            errorMessage.value = getString(R.string.native_recovery_permission)
                        }
                        if (decision == NativeRecoveryDecision.START) {
                            if (Settings.activeConfigPath.isBlank()) {
                                Settings.connectionDesired = false
                                Settings.startedByUser = false
                                errorMessage.value = getString(R.string.native_no_active_profile)
                                NativeRecoveryDecision.STOP
                            } else {
                                recoveryPolicy.recordAttempt()
                                Settings.startCoreAfterStartingService = true
                                nativeStartPending = true
                                observeIssuedServiceStart()
                                serviceStatus.value = Status.Starting
                                try { BoxService.start() } catch (error: Exception) {
                                    nativeStartPending = false
                                    serviceStartTracker.reset()
                                    serviceStatus.value = Status.Stopped
                                    errorMessage.value = error.message ?: error.javaClass.simpleName
                                }
                                NativeRecoveryDecision.START
                            }
                        } else decision
                    }
                    if (result == NativeRecoveryDecision.STOP) break
                    if (BoxService.isStarted()) {
                        connection.reconnect()
                        break
                    }
                    // Observe ownership/binder state without stopping a potentially healthy core.
                    delay(15_000)
                    if (nativeStartPending && !BoxService.hasActiveCore() && !BoxService.isRunning()) nativeStartPending = false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Settings.connectionDesired = false
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                if (recoveryJob == currentCoroutineContext()[Job]) {
                    recoveryJob = null
                    recoveryAttempt.value = 0
                }
            }
        }
        recoveryJob?.start()
    }

    private fun trackStableConnection() {
        cancelRecovery()
        stableConnectionJob = lifecycleScope.launch {
            delay(60_000)
            if (Settings.connectionDesired && BoxService.isStarted()) recoveryPolicy.reset()
        }
    }

    private fun stopHealthMonitor() {
        healthJob?.cancel()
        healthJob = null
        internetHealth.value = NativeInternetHealth.UNCHECKED
    }

    private fun restartHealthMonitor() {
        stopHealthMonitor()
        if (!nativeForeground || serviceStatus.value != Status.Started) return
        val policy = NativeHealthProbePolicy(connectionOptions.value.adaptiveNetwork)
        val url = generalOptions.value?.testUrl ?: return
        healthJob = lifecycleScope.launch {
            delay(3_000)
            while (isActive && nativeForeground && serviceStatus.value == Status.Started) {
                val network = withContext(Dispatchers.IO) { healthRepository.underlyingNetworkAvailable() }
                if (policy.adaptive && !network) {
                    internetHealth.value = NativeInternetHealth.UNAVAILABLE
                    delay(15_000)
                    continue
                }
                internetHealth.value = NativeInternetHealth.CHECKING
                val started = android.os.SystemClock.elapsedRealtime()
                val healthy = healthRepository.probe(url, policy.timeoutSeconds)
                currentCoroutineContext().ensureActive()
                policy.record(healthy, android.os.SystemClock.elapsedRealtime() - started)
                internetHealth.value = if (healthy) NativeInternetHealth.AVAILABLE else NativeInternetHealth.UNAVAILABLE
                delay(policy.intervalSeconds * 1_000)
            }
        }
    }

    private fun loadRegionalApps(kind: NativeRegionalAppKind) {
        if (regionalBusy.value) return
        regionalApps.value = null
        regionalBusy.value = true
        lifecycleScope.launch {
            try {
                regionalApps.value = withContext(Dispatchers.IO) {
                    regionalOperationMutex.withLock { regionalRepository.applicationSnapshot(kind) }
                }
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                regionalBusy.value = false
            }
        }
    }

    private fun saveRegionalApps(kind: NativeRegionalAppKind, selected: Set<String>) {
        changeRegionalApps(kind, selected)
    }

    private fun resetRegionalApps(kind: NativeRegionalAppKind) {
        changeRegionalApps(kind, null)
    }

    private fun changeRegionalApps(kind: NativeRegionalAppKind, selected: Set<String>?) {
        if (regionalBusy.value || regionalApps.value?.kind != kind) return
        regionalBusy.value = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    regionalOperationMutex.withLock { regionalRepository.saveApplications(kind, selected) }
                }
                regionalApps.value = result
                regionalAppsRevision.value += 1
                regionalOptions.value = regionalRepository.load()
                Toast.makeText(this@MainActivity, R.string.native_regional_apps_saved, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                regionalBusy.value = false
            }
        }
    }

    private fun saveRegionalOptions(value: NativeRegionalOptions) {
        if (regionalBusy.value) return
        regionalBusy.value = true
        lifecycleScope.launch {
            try {
                regionalOptions.value = withContext(Dispatchers.IO) { regionalOperationMutex.withLock { regionalRepository.save(value) } }
                refreshPrivacySetupState()
                Toast.makeText(this@MainActivity, R.string.native_regional_saved, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                regionalBusy.value = false
            }
        }
    }

    private fun saveTrafficFilters(value: NativeTrafficFilters) {
        if (trafficFiltersBusy.value) return
        trafficFiltersBusy.value = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    NetworkPrivacySettings.saveFilters(applicationContext, value)
                }
                trafficFilters.value = NetworkPrivacySettings.loadFilters(applicationContext)
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                trafficFiltersBusy.value = false
            }
        }
    }

    private fun refreshTunnelOptions() {
        tunnelSnapshotJob?.cancel()
        tunnelSnapshotJob = lifecycleScope.launch {
            try {
                tunnelOptions.value = withContext(Dispatchers.IO) { tunnelRepository.load() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun saveTunnelOptions(value: com.hiddify.hiddify.nativecore.NativeTunnelOptions) {
        if (tunnelBusy.value || generalOptionsBusy.value || tlsBusy.value || dnsBusy.value || inboundBusy.value || chainBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || wifiSharingBusy.value) return
        if (serviceStatus.value != Status.Stopped || pendingStartAfterVpnPermission || nativeStartPending) {
            errorMessage.value = getString(R.string.native_tunnel_disconnect)
            return
        }
        tunnelBusy.value = true
        tunnelSnapshotJob?.cancel()
        cancelRecovery()
        lifecycleScope.launch {
            try {
                tunnelOptions.value = BoxService.withNativeLifecycle {
                    check(serviceStatus.value == Status.Stopped && !BoxService.hasActiveCore() && !nativeStartPending &&
                        !pendingStartAfterVpnPermission) { getString(R.string.native_tunnel_disconnect) }
                    withContext(Dispatchers.IO) { tunnelRepository.save(value) }
                }
                refreshImportedSettingsSnapshots()
                Toast.makeText(this@MainActivity, R.string.native_tunnel_saved, Toast.LENGTH_LONG).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                tunnelBusy.value = false
            }
        }
    }

    private fun refreshGeneralOptions() {
        if (generalOptionsBusy.value) return
        generalOptionsLoadFailed.value = false
        generalOptionsSnapshotJob?.cancel()
        generalOptionsSnapshotJob = lifecycleScope.launch {
            try {
                generalOptions.value = withContext(Dispatchers.IO) { generalOptionsRepository.load() }
                restartHealthMonitor()
                startSmartSelectionLoop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                generalOptionsLoadFailed.value = true
                errorMessage.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun saveGeneralOption(field: com.hiddify.hiddify.nativecore.NativeGeneralOptionField, input: String) {
        if (generalOptionsBusy.value || reconnectBusy.value || tunnelBusy.value || tlsBusy.value || dnsBusy.value || inboundBusy.value || chainBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || wifiSharingBusy.value ||
            serviceStatus.value == Status.Starting || serviceStatus.value == Status.Stopping || pendingStartAfterVpnPermission || nativeStartPending) return
        generalOptionsBusy.value = true
        generalOptionsSnapshotJob?.cancel()
        lifecycleScope.launch {
            try {
                generalOptions.value = BoxService.withNativeLifecycle {
                    check(serviceStatus.value != Status.Starting && serviceStatus.value != Status.Stopping &&
                        !nativeStartPending && !pendingStartAfterVpnPermission)
                    withContext(Dispatchers.IO) { generalOptionsRepository.saveField(field, input) }
                }
                coreChangesSaved()
                restartHealthMonitor()
                startSmartSelectionLoop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                generalOptionsBusy.value = false
            }
        }
    }

    private fun refreshTlsOptions() {
        if (tlsBusy.value) return
        tlsLoadFailed.value = false
        tlsSnapshotJob?.cancel()
        tlsSnapshotJob = lifecycleScope.launch {
            try {
                tlsOptions.value = withContext(Dispatchers.IO) { tlsRepository.load() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                tlsLoadFailed.value = true
                errorMessage.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun saveTlsOption(field: com.hiddify.hiddify.nativecore.NativeTlsOptionField, input: String) {
        if (tlsBusy.value || reconnectBusy.value || generalOptionsBusy.value || tunnelBusy.value || dnsBusy.value || inboundBusy.value || chainBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || wifiSharingBusy.value ||
            serviceStatus.value == Status.Starting || serviceStatus.value == Status.Stopping || pendingStartAfterVpnPermission || nativeStartPending) return
        tlsBusy.value = true
        tlsSnapshotJob?.cancel()
        lifecycleScope.launch {
            try {
                tlsOptions.value = BoxService.withNativeLifecycle {
                    check(serviceStatus.value != Status.Starting && serviceStatus.value != Status.Stopping &&
                        !nativeStartPending && !pendingStartAfterVpnPermission)
                    // Match the page dependency against fresh state, rather than a stale switch snapshot.
                    check(field == com.hiddify.hiddify.nativecore.NativeTlsOptionField.FRAGMENT ||
                        withContext(Dispatchers.IO) { tlsRepository.load().fragment })
                    withContext(Dispatchers.IO) { tlsRepository.saveField(field, input) }
                }
                coreChangesSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                tlsBusy.value = false
            }
        }
    }

    private fun refreshDnsOptions() {
        if (dnsBusy.value) return
        dnsLoadFailed.value = false
        dnsSnapshotJob?.cancel()
        dnsSnapshotJob = lifecycleScope.launch {
            try {
                dnsOptions.value = withContext(Dispatchers.IO) { dnsRepository.load() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                dnsLoadFailed.value = true
                errorMessage.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun saveDnsOption(field: com.hiddify.hiddify.nativecore.NativeDnsOptionField, input: String) {
        if (dnsBusy.value || reconnectBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value || inboundBusy.value || chainBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || wifiSharingBusy.value ||
            serviceStatus.value == Status.Starting || serviceStatus.value == Status.Stopping || pendingStartAfterVpnPermission || nativeStartPending) return
        dnsBusy.value = true
        dnsSnapshotJob?.cancel()
        lifecycleScope.launch {
            try {
                dnsOptions.value = BoxService.withNativeLifecycle {
                    check(serviceStatus.value != Status.Starting && serviceStatus.value != Status.Stopping &&
                        !nativeStartPending && !pendingStartAfterVpnPermission)
                    withContext(Dispatchers.IO) { dnsRepository.saveField(field, input) }
                }
                coreChangesSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                dnsBusy.value = false
            }
        }
    }

    private fun refreshInboundOptions() {
        inboundSnapshotJob?.cancel()
        inboundSnapshotJob = lifecycleScope.launch {
            try {
                inboundOptions.value = withContext(Dispatchers.IO) { inboundRepository.load() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun coreChangesSaved() {
        if (serviceStatus.value == Status.Started) {
            val applied = Settings.nativeAppliedQuickSettings
            Settings.nativeReconnectRequired = applied.isBlank() || Settings.quickSettingsSignature(applicationContext) != applied
        }
        requiresReconnect.value = Settings.nativeReconnectRequired
    }

    private fun saveQuickServiceMode(proxyOnly: Boolean) {
        if (reconnectBusy.value || wifiSharingBusy.value || inboundBusy.value || chainBusy.value ||
            dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value || privacySetupBusy.value ||
            proxyPrivacyBusy.value || pendingStartAfterVpnPermission || nativeStartPending ||
            serviceStatus.value == Status.Starting || serviceStatus.value == Status.Stopping) return
        val mode = if (proxyOnly) ServiceMode.NORMAL else ServiceMode.VPN
        if (mode == Settings.serviceMode) return
        updateSettings { Settings.serviceMode = mode }
        coreChangesSaved()
        if (serviceStatus.value == Status.Stopped) connection.reconnect()
    }

    private fun saveQuickLanSharing(enabled: Boolean, password: String) {
        if (reconnectBusy.value || inboundBusy.value || wifiSharingBusy.value || chainBusy.value ||
            privacySetupBusy.value || proxyPrivacyBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value ||
            tunnelBusy.value || pendingStartAfterVpnPermission || nativeStartPending || serviceStatus.value == Status.Starting ||
            serviceStatus.value == Status.Stopping) return
        inboundBusy.value = true
        inboundSnapshotJob?.cancel()
        lifecycleScope.launch {
            try {
                inboundOptions.value = withContext(Dispatchers.IO) { inboundRepository.saveLanFields(enabled, password) }
                coreChangesSaved()
                refreshSettingsSnapshot()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { errorMessage.value = error.message ?: error.javaClass.simpleName }
            finally { inboundBusy.value = false }
        }
    }

    private fun saveQuickChainMode(extra: Boolean, mode: String?) {
        if (reconnectBusy.value || chainBusy.value || inboundBusy.value || wifiSharingBusy.value ||
            privacySetupBusy.value || proxyPrivacyBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value ||
            tunnelBusy.value || pendingStartAfterVpnPermission || nativeStartPending ||
            serviceStatus.value == Status.Starting || serviceStatus.value == Status.Stopping) return
        chainBusy.value = true
        lifecycleScope.launch {
            try {
                chainOptions.value = withContext(Dispatchers.IO) { chainRepository.saveSelection(extra, mode) }
                coreChangesSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { errorMessage.value = error.message ?: error.javaClass.simpleName }
            finally { chainBusy.value = false }
        }
    }

    private fun reconnectWithSavedSettings() {
        if (reconnectBusy.value) return
        reconnectBusy.value = true
        cancelRecovery()
        reconnectJob = lifecycleScope.launch {
            try {
                BoxService.stop(preserveIntent = true)
                withTimeout(30_000) {
                    while (serviceStatus.value != Status.Stopped || BoxService.hasActiveCore()) delay(100)
                }
                connection.reconnect()
                requestStart()
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                Settings.connectionDesired = false
                errorMessage.value = getString(R.string.native_connection_stop_timeout)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Settings.connectionDesired = false
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                if (reconnectJob == currentCoroutineContext()[Job]) {
                    reconnectBusy.value = false
                    reconnectJob = null
                }
            }
        }
    }

    private fun saveInboundOptions(value: com.hiddify.hiddify.nativecore.NativeInboundOptions) {
        if (inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value || chainBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || wifiSharingBusy.value) return
        if (serviceStatus.value != Status.Stopped || pendingStartAfterVpnPermission || nativeStartPending) {
            errorMessage.value = getString(R.string.native_inbound_disconnect)
            return
        }
        inboundBusy.value = true
        inboundSnapshotJob?.cancel()
        cancelRecovery()
        lifecycleScope.launch {
            try {
                inboundOptions.value = BoxService.withNativeLifecycle {
                    check(serviceStatus.value == Status.Stopped && !BoxService.hasActiveCore() && !nativeStartPending &&
                        !pendingStartAfterVpnPermission) { getString(R.string.native_inbound_disconnect) }
                    withContext(Dispatchers.IO) { inboundRepository.save(value) }
                }
                refreshImportedSettingsSnapshots()
                Toast.makeText(this@MainActivity, R.string.native_inbound_saved, Toast.LENGTH_LONG).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                inboundBusy.value = false
            }
        }
    }

    private fun refreshCoreOptions() {
        refreshTunnelOptions()
        refreshGeneralOptions()
        refreshTlsOptions()
        refreshDnsOptions()
        refreshInboundOptions()
    }

    private fun refreshChainOptions() {
        chainOptions.value = chainRepository.load()
    }

    private fun saveChainOptions(value: NativeChainOptions) {
        if (chainBusy.value || reconnectBusy.value || inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value ||
            tunnelBusy.value || privacySetupBusy.value || proxyPrivacyBusy.value || wifiSharingBusy.value ||
            pendingStartAfterVpnPermission || nativeStartPending || serviceStatus.value == Status.Starting ||
            serviceStatus.value == Status.Stopping) return
        chainBusy.value = true
        lifecycleScope.launch {
            try {
                chainOptions.value =
                    withContext(Dispatchers.IO) {
                        chainRepository.save(value)
                    }
                coreChangesSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                chainBusy.value = false
            }
        }
    }

    override fun onDestroy() {
        profileImportCancellation?.cancel()
        AutomaticHotspot.removeObserver(hotspotObserver)
        hotspotPermission?.cancel()
        super.onDestroy()
    }

    private fun changeWifiSharing(enabled: Boolean) {
        if (wifiSharingBusy.value || inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value || chainBusy.value || privacySetupBusy.value ||
            serviceStatus.value == Status.Starting || serviceStatus.value == Status.Stopping) return
        wifiSharingBusy.value = true
        lifecycleScope.launch {
            var startupRequested = false
            try {
                profileOperationMutex.withLock {
                    if (enabled) {
                        check(Settings.activeConfigPath.isNotBlank()) { getString(R.string.native_no_active_profile) }
                        if (Settings.privacyUseRootRequested) AutomaticHotspot.prepareRoot(applicationContext)
                        val permission = AutomaticHotspot.permission()
                        if (ContextCompat.checkSelfPermission(this@MainActivity, permission) != PackageManager.PERMISSION_GRANTED) {
                            val pending = CompletableDeferred<Boolean>()
                            hotspotPermission = pending
                            try {
                                hotspotPermissionLauncher.launch(permission)
                                check(pending.await()) { getString(R.string.native_wifi_permission_denied) }
                            } finally { hotspotPermission = null }
                        }
                        AutomaticHotspot.start(applicationContext, Settings.privacyUseRootRequested)
                    } else {
                        AutomaticHotspot.stop()
                    }
                    val wasRunning = serviceStatus.value == Status.Started
                    Settings.setWifiVpnSharing(enabled)
                    BoxService.withNativeLifecycle {
                        withContext(Dispatchers.IO) { inboundRepository.setLanSharing(enabled) }
                    }
                    if (wasRunning) {
                        BoxService.stop(preserveIntent = true)
                        withTimeout(30_000) {
                            while (serviceStatus.value != Status.Stopped) delay(100)
                        }
                    }
                    if (enabled) Settings.serviceMode = ServiceMode.VPN
                    if (enabled || wasRunning) {
                        connection.reconnect()
                        startupRequested = true
                        requestStart()
                        withTimeout(60_000) {
                            while (serviceStatus.value != Status.Started) {
                                check(Settings.connectionDesired) { errorMessage.value ?: getString(R.string.native_wifi_vpn_failed) }
                                delay(100)
                            }
                        }
                    }
                    if (enabled) check(AutomaticHotspot.snapshot()["active"] == true) { getString(R.string.native_wifi_vpn_failed) }
                }
            } catch (error: Exception) {
                if (startupRequested) {
                    pendingStartAfterVpnPermission = false
                    if (serviceStatus.value != Status.Started) {
                        Settings.connectionDesired = false
                        BoxService.stop()
                    }
                }
                withContext(NonCancellable) {
                    runCatching { AutomaticHotspot.stop() }
                    Settings.setWifiVpnSharing(false)
                    runCatching {
                        BoxService.withNativeLifecycle {
                            withContext(Dispatchers.IO) { inboundRepository.setLanSharing(false) }
                        }
                    }
                }
                if (error is CancellationException) throw error
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                wifiSharingBusy.value = false
                refreshInboundOptions()
                refreshSettingsSnapshot()
                refreshWifiSharingDetails()
            }
        }
    }

    private fun refreshWifiSharingDetails() {
        if (wifiSharingDetailsBusy.value) {
            wifiDetailsRefreshPending = true
            return
        }
        wifiSharingDetailsBusy.value = true
        lifecycleScope.launch {
            try {
                wifiSharingDetails.value =
                    withContext(Dispatchers.IO) {
                        wifiSharingRepository.load()
                    }
            } catch (error: Exception) {
                errorMessage.value = error.message ?: error.javaClass.simpleName
            } finally {
                wifiSharingDetailsBusy.value = false
                if (wifiDetailsRefreshPending) {
                    wifiDetailsRefreshPending = false
                    refreshWifiSharingDetails()
                }
            }
        }
    }

    private fun importSettingsFromClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val text =
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else {
                null
            }
        if (text.isNullOrBlank()) {
            errorMessage.value = getString(R.string.native_settings_clipboard_empty)
            return
        }
        applyImportedSettings(text)
    }

    private fun importSettingsFromFile() {
        settingsImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
    }

    private fun applyImportedSettings(input: String) {
        if (serviceStatus.value != Status.Stopped) {
            errorMessage.value = getString(R.string.native_settings_import_disconnect)
            return
        }
        try {
            settingsTransferRepository.importJson(input)
            refreshImportedSettingsSnapshots()
            Toast.makeText(this, R.string.native_settings_import_success, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun exportSettingsToClipboard(includePrivate: Boolean) {
        try {
            val payload = settingsTransferRepository.exportJson(includePrivate)
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("VetrOFF options.json", payload))
            Toast.makeText(this, R.string.native_settings_export_success, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun exportSettingsToFile(includePrivate: Boolean) {
        try {
            pendingSettingsExport = settingsTransferRepository.exportJson(includePrivate)
            settingsExportLauncher.launch("options.json")
        } catch (error: Exception) {
            pendingSettingsExport = null
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun resetCoreSettings() {
        if (serviceStatus.value != Status.Stopped) {
            errorMessage.value = getString(R.string.native_settings_import_disconnect)
            return
        }
        try {
            settingsTransferRepository.resetCoreSettings()
            refreshImportedSettingsSnapshots()
            Toast.makeText(this, R.string.native_settings_reset_success, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun refreshImportedSettingsSnapshots() {
        refreshCoreOptions()
        refreshChainOptions()
        refreshSettingsSnapshot()
        refreshWifiSharingDetails()
    }

    private fun checkForUpdate() {
        if (updateChecking.value) return
        updateChecking.value = true
        updateMessage.value = null
        updateUrl.value = null

        lifecycleScope.launch {
            try {
                val release =
                    withContext(Dispatchers.IO) {
                        updateRepository.latestCompatible()
                    }
                when {
                    release == null -> {
                        updateMessage.value = getString(R.string.native_about_update_none)
                    }

                    updateRepository.isNewer(
                        release,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ) -> {
                        updateMessage.value =
                            getString(R.string.native_about_update_available, release.version)
                        updateUrl.value = release.pageUrl
                    }

                    else -> {
                        updateMessage.value = getString(R.string.native_about_update_current)
                    }
                }
            } catch (error: Exception) {
                updateMessage.value =
                    getString(
                        R.string.native_about_update_error,
                        error.message ?: error.javaClass.simpleName,
                    )
            } finally {
                updateChecking.value = false
            }
        }
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error ->
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
    }

    private inline fun updateSettings(action: () -> Unit) {
        action()
        refreshSettingsSnapshot()
    }

    private fun requestStop() {
        val waitingForService = nativeStartPending || BoxService.hasActiveCore() || BoxService.isRunning() ||
            serviceStatus.value == Status.Started || serviceStatus.value == Status.Stopping ||
            (serviceStatus.value == Status.Starting && !pendingStartAfterVpnPermission)
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectBusy.value = false
        cancelRecovery()
        Settings.connectionDesired = false
        Settings.startedByUser = false
        nativeStartPending = false
        pendingStartAfterVpnPermission = false
        serviceStartWatchdog?.cancel()
        serviceStartWatchdog = null
        serviceStartTracker.reset()
        homeConnectionFailed.value = false
        serviceStatus.value = if (waitingForService) Status.Stopping else Status.Stopped
        BoxService.stop()
    }

    private suspend fun awaitServiceStopped() {
        withTimeout(30_000) {
            while (serviceStatus.value != Status.Stopped || BoxService.hasActiveCore() || BoxService.isRunning()) delay(100)
        }
    }

    private fun toggleConnection() {
        // Stop must remain available while startup, recovery or settings reconnect is pending.
        if (reconnectBusy.value || recoveryJob != null || recoveryAttempt.value > 0 ||
            nativeStartPending || pendingStartAfterVpnPermission || serviceStatus.value == Status.Starting) {
            connectionHaptic(stopping = true)
            requestStop()
            return
        }
        when (serviceStatus.value) {
            Status.Stopped -> {
                if (BoxService.hasActiveCore()) { requestStop(); return }
                connectionHaptic(stopping = false)
                requestStart()
            }
            Status.Started -> {
                if (requiresReconnect.value) { reconnectWithSavedSettings(); return }
                if (BoxService.vpnProtection()["alwaysOn"] == true) { openVpnSettings(); return }
                connectionHaptic(stopping = true)
                requestStop()
            }
            Status.Starting -> requestStop()
            Status.Stopping -> Unit
        }
    }

    private fun requestStart() {
        if (privacySetupBusy.value || proxyPrivacyBusy.value || inboundBusy.value || dnsBusy.value || tlsBusy.value || generalOptionsBusy.value || tunnelBusy.value ||
            chainBusy.value || busyProfileId.value != null || profileEditorBusy.value || nativeStartPending ||
            pendingStartAfterVpnPermission || serviceStatus.value != Status.Stopped || BoxService.hasActiveCore()) return
        cancelRecovery()
        recoveryPolicy.reset()
        nativeStartPending = false
        homeConnectionFailed.value = false
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
                    homeConnectionFailed.value = true
                    errorMessage.value = error.message ?: error.javaClass.simpleName
                    return
                }

            if (permissionIntent != null) {
                pendingStartAfterVpnPermission = true
                serviceStatus.value = Status.Starting
                vpnPermissionLauncher.launch(permissionIntent)
                return
            }
        }

        startForegroundVpn()
    }

    private fun observeIssuedServiceStart() {
        serviceStartTracker.markIssued()
        serviceStartWatchdog?.cancel()
        serviceStartWatchdog = lifecycleScope.launch {
            delay(20_000)
            if (nativeStartPending && !BoxService.hasActiveCore() && !BoxService.isRunning()) {
                nativeStartPending = false
                serviceStartTracker.reset()
                Settings.connectionDesired = false
                Settings.startedByUser = false
                serviceStatus.value = Status.Stopped
                homeConnectionFailed.value = true
                errorMessage.value = getString(R.string.native_connection_service_unavailable)
            }
        }
    }

    private fun startForegroundVpn() {
        try {
            Settings.connectionDesired = true
            Settings.startCoreAfterStartingService = true
            nativeStartPending = true
            observeIssuedServiceStart()
            serviceStatus.value = Status.Starting
            BoxService.start()
        } catch (error: Exception) {
            nativeStartPending = false
            serviceStartTracker.reset()
            serviceStatus.value = Status.Stopped
            Settings.connectionDesired = false
            Settings.startedByUser = false
            homeConnectionFailed.value = true
            errorMessage.value = error.message ?: error.javaClass.simpleName
        }
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
            // Binding delivers Stopped before a queued foreground start has reached onStartCommand.
            if (pendingStartAfterVpnPermission && status == Status.Stopped) return@runOnUiThread
            val startResult = serviceStartTracker.onStatus(status)
            if (nativeStartPending && status == Status.Stopped && startResult == null) return@runOnUiThread
            if (startResult != null) serviceStartTracker.reset()
            // A queued Starting/Started callback must never undo a user's stop intent.
            if (!Settings.connectionDesired && serviceStatus.value == Status.Stopping &&
                (status == Status.Starting || status == Status.Started)) return@runOnUiThread
            serviceStatus.value = status
            if (status == Status.Starting || status == Status.Started) homeConnectionFailed.value = false
            requiresReconnect.value = Settings.nativeReconnectRequired
            if (status == Status.Started || status == Status.Stopped) {
                nativeStartPending = false
                serviceStartWatchdog?.cancel()
                serviceStartWatchdog = null
            }
            if (status == Status.Started) trackStableConnection()
            else {
                stableConnectionJob?.cancel()
                if (status == Status.Stopped) scheduleRecovery()
            }
            restartHealthMonitor()
            startSmartSelectionLoop()
            refreshVpnProtection()
            if (status != Status.Started) cancelDiagnostics()
            if (status == Status.Started) {
                refreshOutbounds(showError = false)
                maybeRequestNotificationPermission()
                maybePromptBatteryOptimization()
            } else if (status == Status.Stopped) {
                outboundGroups.value = emptyList()
                outboundBusyTag.value = null
                systemStats.value = NativeSystemStats()
            }
        }
    }

    override fun onServiceWriteLog(message: String) {
        runOnUiThread { appendServiceLog(message) }
    }

    override fun onServiceResetLogs(messages: List<String>) {
        runOnUiThread {
            serviceLogLines.clear()
            messages.takeLast(MAX_SERVICE_LOG_LINES).forEach { message ->
                if (message.isNotBlank()) {
                    serviceLogLines.addLast(logRepository.decorateServiceLine(message))
                }
            }
            refreshLogs()
        }
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        runOnUiThread {
            homeConnectionFailed.value = true
            nativeStartPending = false
            serviceStartTracker.reset()
            serviceStartWatchdog?.cancel()
            serviceStartWatchdog = null
            if (type != Alert.StartService && type != Alert.CreateService) {
                Settings.connectionDesired = false
                cancelRecovery()
            }
            errorMessage.value = message ?: getString(R.string.native_service_error, type.name)
            scheduleRecovery()
        }
    }
}

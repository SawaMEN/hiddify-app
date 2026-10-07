package com.hiddify.hiddify.nativeui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.text.style.TextOverflow
import com.hiddify.hiddify.BuildConfig
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticSnapshot
import com.hiddify.hiddify.nativediagnostics.NativeVpnProtection
import com.hiddify.hiddify.nativepreferences.NativeThemeMode
import com.hiddify.hiddify.nativeconnection.NativeConnectionOptions
import com.hiddify.hiddify.nativeconnection.NativeInternetHealth
import com.hiddify.hiddify.privacy.NativeRegionalAppKind
import com.hiddify.hiddify.privacy.NativeRegionalAppSnapshot
import com.hiddify.hiddify.privacy.NativeRegionalOptions
import com.hiddify.hiddify.privacy.NativeTrafficFilters
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.Status
import com.hiddify.hiddify.nativelog.NativeLogSnapshot
import com.hiddify.hiddify.nativecore.NativeChainOptions
import com.hiddify.hiddify.nativecore.NativeOutboundGroup
import com.hiddify.hiddify.nativecore.NativeSystemStats
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails
import com.hiddify.hiddify.nativeprofile.NativeProfile
import com.hiddify.hiddify.nativeprofile.NativeProfileEditor
import com.hiddify.hiddify.nativerouting.NativePerAppBackup
import com.hiddify.hiddify.nativerouting.NativePerAppSnapshot

private const val PAGE_HOME = "home"
private const val PAGE_PROFILES = "profiles"
private const val PAGE_SETTINGS = "settings"
private const val PAGE_PREFERENCES = "preferences"
private const val PAGE_PRIVACY = "privacy"
private const val PAGE_PROXY_PRIVACY = "proxy_privacy"
private const val PAGE_TUNNEL = "tunnel"
private const val PAGE_GENERAL_OPTIONS = "general_options"
private const val PAGE_TLS = "tls"
private const val PAGE_DNS = "dns"
private const val PAGE_INBOUND = "inbound"
private const val PAGE_PER_APP = "per_app"
private const val PAGE_PER_APP_BACKUP = "per_app_backup"
private const val PAGE_PROFILE_DETAILS = "profile_details"
private const val PAGE_LOGS = "logs"
private const val PAGE_CORE_OPTIONS = "core_options"
private const val PAGE_OUTBOUNDS = "outbounds"
private const val PAGE_WIFI_GUIDE = "wifi_guide"
private const val PAGE_ABOUT = "about"
private const val PAGE_CHAIN = "chain"
private const val PAGE_DIAGNOSTICS = "diagnostics"
private const val PAGE_CONNECTION_POLICY = "connection_policy"
private const val PAGE_REGIONAL_APPS = "regional_apps"
private const val PAGE_REGIONAL = "regional"
private const val PAGE_TRAFFIC_FILTERS = "traffic_filters"
private const val PAGE_PROTECTION = "protection"

@Composable
fun NativeApp(
    tunnelOptions: com.hiddify.hiddify.nativecore.NativeTunnelOptions?,
    tunnelBusy: Boolean,
    onSaveTunnelOptions: (com.hiddify.hiddify.nativecore.NativeTunnelOptions) -> Unit,
    generalOptions: com.hiddify.hiddify.nativecore.NativeGeneralOptions?,
    generalOptionsBusy: Boolean,
    onSaveGeneralOptions: (com.hiddify.hiddify.nativecore.NativeGeneralOptions) -> Unit,
    generalPreferences: com.hiddify.hiddify.nativepreferences.NativeGeneralPreferences,
    generalPreferencesBusy: Boolean,
    onChangeLanguage: (com.hiddify.hiddify.nativepreferences.NativeLanguage) -> Unit,
    onChangeHapticFeedback: (Boolean) -> Unit,
    onChangeSmartSelection: (Boolean) -> Unit,
    onChangeOutboundSort: (com.hiddify.hiddify.nativepreferences.NativeOutboundSort) -> Unit,
    tlsOptions: com.hiddify.hiddify.nativecore.NativeTlsOptions?,
    tlsBusy: Boolean,
    onSaveTlsOptions: (com.hiddify.hiddify.nativecore.NativeTlsOptions) -> Unit,
    dnsOptions: com.hiddify.hiddify.nativecore.NativeDnsOptions?,
    dnsBusy: Boolean,
    onSaveDnsOptions: (com.hiddify.hiddify.nativecore.NativeDnsOptions) -> Unit,
    inboundOptions: com.hiddify.hiddify.nativecore.NativeInboundOptions,
    inboundBusy: Boolean,
    onSaveInboundOptions: (com.hiddify.hiddify.nativecore.NativeInboundOptions) -> Unit,
    proxyPrivacy: com.hiddify.hiddify.privacy.NativeProxyPrivacy,
    proxyPrivacyBusy: Boolean,
    onSaveProxyPrivacy: (com.hiddify.hiddify.privacy.NativeProxyPrivacy) -> Unit,
    privacySetupBusy: Boolean,
    privacyConfigured: Boolean,
    privacyCanRestore: Boolean,
    onConfigurePrivacy: () -> Unit,
    onRestorePrivacy: () -> Unit,
    themeMode: NativeThemeMode,
    themeBusy: Boolean,
    onChangeTheme: (NativeThemeMode) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    status: Status,
    connectionOptions: NativeConnectionOptions,
    connectionOptionsBusy: Boolean,
    internetHealth: NativeInternetHealth,
    recoveryAttempt: Int,
    onSaveConnectionOptions: (NativeConnectionOptions) -> Unit,
    activeProfileName: String,
    hasActiveProfile: Boolean,
    rootMode: Boolean,
    settingsState: NativeSettingsState,
    regionalApps: NativeRegionalAppSnapshot?,
    regionalAppsRevision: Int,
    onOpenRegionalApps: (NativeRegionalAppKind) -> Unit,
    onSaveRegionalApps: (NativeRegionalAppKind, Set<String>) -> Unit,
    onResetRegionalApps: (NativeRegionalAppKind) -> Unit,
    regionalOptions: NativeRegionalOptions,
    regionalBusy: Boolean,
    onSaveRegionalOptions: (NativeRegionalOptions) -> Unit,
    trafficFilters: NativeTrafficFilters,
    trafficFiltersBusy: Boolean,
    onSaveTrafficFilters: (NativeTrafficFilters) -> Unit,
    profiles: List<NativeProfile>,
    profilesLoading: Boolean,
    profilesLoadFailed: Boolean,
    onRetryProfiles: () -> Unit,
    onUpdateAllProfiles: () -> Unit,
    profileImportRevision: Int,
    profileSelectionRevision: Int,
    busyProfileId: String?,
    profileEditor: NativeProfileEditor?,
    editorSession: com.hiddify.hiddify.nativeprofile.NativeProfileEditorSession,
    profileEditorLoadFailed: Boolean,
    profileEditorSavedRevision: Int,
    onRetryProfileEditor: () -> Unit,
    profileEditorBusy: Boolean,
    perAppSnapshot: NativePerAppSnapshot,
    perAppBusy: Boolean,
    pendingPerAppImport: NativePerAppBackup?,
    logSnapshot: NativeLogSnapshot,
    logBusy: Boolean,
    chainOptions: NativeChainOptions,
    chainBusy: Boolean,
    outboundGroups: List<NativeOutboundGroup>,
    activeOutbound: com.hiddify.hiddify.nativecore.NativeOutbound?,
    requiresReconnect: Boolean,
    reconnectBusy: Boolean,
    onQuickServiceMode: (Boolean) -> Unit,
    onQuickLanSharing: (Boolean, String) -> Unit,
    outboundBusyTag: String?,
    systemStats: NativeSystemStats,
    wifiSharingDetails: NativeWifiSharingDetails,
    wifiSharingDetailsBusy: Boolean,
    wifiSharingBusy: Boolean,
    updateChecking: Boolean,
    updateMessage: String?,
    updateUrl: String?,
    diagnosticSnapshot: NativeDiagnosticSnapshot?,
    diagnosticBusy: Boolean,
    diagnosticReport: String,
    vpnProtection: NativeVpnProtection,
    errorMessage: String?,
    onRunDiagnostics: () -> Unit,
    onCancelDiagnostics: () -> Unit,
    onShareDiagnosticReport: (String) -> Unit,
    onRefreshVpnProtection: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    onDismissError: () -> Unit,
    onToggleConnection: () -> Unit,
    onSelectProfile: (NativeProfile) -> Unit,
    onDeleteProfile: (NativeProfile) -> Unit,
    onRefreshProfile: (NativeProfile) -> Unit,
    onOpenProfileEditor: (NativeProfile) -> Unit,
    onSaveProfileEditor: (String, Boolean, Int?, String) -> Unit,
    onImportProfile: (String, String?, Int?, Boolean) -> Unit,
    onImportFreeProfile: (com.hiddify.hiddify.nativeprofile.NativeFreeProfile, String) -> Unit,
    onCopyProfileConfig: (NativeProfile) -> Unit,
    onExportProfileConfig: (NativeProfile) -> Unit,
    onPerAppModeChanged: (String) -> Unit,
    onTogglePerAppPackage: (String) -> Unit,
    onClearPerApp: () -> Unit,
    onImportPerAppClipboard: () -> Unit,
    onImportPerAppFile: () -> Unit,
    onExportPerAppClipboard: () -> Unit,
    onExportPerAppFile: () -> Unit,
    onConfirmPerAppImport: () -> Unit,
    onDismissPerAppImport: () -> Unit,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onSaveChainOptions: (NativeChainOptions) -> Unit,
    onRefreshOutbounds: () -> Unit,
    onSelectOutbound: (String, String) -> Unit,
    onTestOutbound: (String) -> Unit,
    onTestActiveOutbounds: () -> Unit,
    onRefreshWifiSharingDetails: () -> Unit,
    onImportSettingsClipboard: () -> Unit,
    onImportSettingsFile: () -> Unit,
    onExportSettingsClipboard: (Boolean) -> Unit,
    onExportSettingsFile: (Boolean) -> Unit,
    onResetSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenFork: () -> Unit,
    onOpenUpstream: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onProxyOnlyChanged: (Boolean) -> Unit,
    onRootModeChanged: (Boolean) -> Unit,
    onWifiSharingChanged: (Boolean) -> Unit,
    onFullTunnelChanged: (Boolean) -> Unit,
    onEncryptedDnsChanged: (Boolean) -> Unit,
    onPublicDnsChanged: (Boolean) -> Unit,
    onDisableSystemProxyChanged: (Boolean) -> Unit,
    onDisableIpv6Changed: (Boolean) -> Unit,
    onHandbookRoutingChanged: (Boolean) -> Unit,
    onHandbookProxyChanged: (Boolean) -> Unit,
    onHandbookDirectChanged: (Boolean) -> Unit,
    onHandbookProxySitesChanged: (String) -> Unit,
    onHandbookDirectSitesChanged: (String) -> Unit,
    onDynamicNotificationChanged: (Boolean) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onDisableMemoryLimitChanged: (Boolean) -> Unit,
) {
    var profilesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var profileSortByName by rememberSaveable { mutableStateOf(false) }
    var profileSortAscending by rememberSaveable { mutableStateOf(false) }
    var addProfileOpen by rememberSaveable { mutableStateOf(false) }
    var observedEditorSavedRevision by remember { mutableStateOf(profileEditorSavedRevision) }
    var observedImportRevision by remember { mutableStateOf(profileImportRevision) }
    var observedSelectionRevision by remember { mutableStateOf(profileSelectionRevision) }
    var quickSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(PAGE_HOME) }
    var regionalAppKind by rememberSaveable { mutableStateOf(NativeRegionalAppKind.DIRECT.name) }

    // Only internal page names are stored, so this trail also survives Activity recreation.
    var privacyExpanded by rememberSaveable { mutableStateOf("") }
    var pageTrail by rememberSaveable { mutableStateOf("") }
    var settingsCategory by rememberSaveable { mutableStateOf(NativeSettingsCategory.APP.name) }
    fun openPage(destination: String) {
        if (privacySetupBusy || proxyPrivacyBusy || inboundBusy || dnsBusy || tlsBusy || generalOptionsBusy || tunnelBusy || generalPreferencesBusy) return
        pageTrail += "|$page"
        page = destination
    }
    fun goBack() {
        if (privacySetupBusy || proxyPrivacyBusy || inboundBusy || dnsBusy || tlsBusy || generalOptionsBusy || tunnelBusy || generalPreferencesBusy) return
        if (page == PAGE_DIAGNOSTICS) onCancelDiagnostics()
        if (page == PAGE_PER_APP_BACKUP) onDismissPerAppImport()
        page = pageTrail.substringAfterLast('|', PAGE_HOME)
        pageTrail = pageTrail.substringBeforeLast('|', "")
    }
    fun openCategory(category: NativeSettingsCategory) {
        settingsCategory = category.name
        openPage(PAGE_PREFERENCES)
    }

    LaunchedEffect(profileImportRevision) {
        if (profileImportRevision != observedImportRevision) {
            observedImportRevision = profileImportRevision
            addProfileOpen = false
        }
    }
    LaunchedEffect(profileEditorSavedRevision) {
        if (profileEditorSavedRevision != observedEditorSavedRevision) {
            observedEditorSavedRevision = profileEditorSavedRevision
            if (page == PAGE_PROFILE_DETAILS) goBack()
        }
    }
    LaunchedEffect(profileSelectionRevision) {
        if (profileSelectionRevision != observedSelectionRevision) {
            observedSelectionRevision = profileSelectionRevision
            profilesSheetOpen = false
            if (page == PAGE_PROFILES) goBack()
        }
    }

    BackHandler(enabled = privacySetupBusy || proxyPrivacyBusy || inboundBusy || dnsBusy || tlsBusy || generalOptionsBusy || tunnelBusy || generalPreferencesBusy || page != PAGE_HOME) { goBack() }

    NativeAppTheme(themeMode) {
        NativeAtmosphere {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = if (page == PAGE_OUTBOUNDS || page == PAGE_HOME || page == PAGE_PROFILES || page == PAGE_PROFILE_DETAILS) 0.dp else 8.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box((if (page == PAGE_OUTBOUNDS || page == PAGE_HOME || page == PAGE_PROFILES || page == PAGE_PROFILE_DETAILS) Modifier else Modifier.widthIn(max = 680.dp))
                        .fillMaxWidth().padding(horizontal = when (page) { PAGE_OUTBOUNDS, PAGE_HOME, PAGE_PROFILES, PAGE_PROFILE_DETAILS -> 0.dp; PAGE_PRIVACY -> 16.dp; else -> 20.dp })) {
                        when (page) {
                            PAGE_DIAGNOSTICS ->
                                NativeDiagnosticsScreen(
                                    snapshot = diagnosticSnapshot,
                                    busy = diagnosticBusy,
                                    report = diagnosticReport,
                                    onBack = { goBack() },
                                    onRun = onRunDiagnostics,
                                    onCancel = onCancelDiagnostics,
                                    onShareReport = onShareDiagnosticReport,
                                )

                            PAGE_CONNECTION_POLICY -> NativeConnectionPolicyScreen(
                                options = connectionOptions,
                                busy = connectionOptionsBusy,
                                onBack = { goBack() },
                                onSave = onSaveConnectionOptions,
                            )

                            PAGE_REGIONAL_APPS -> {
                                val kind = NativeRegionalAppKind.valueOf(regionalAppKind)
                                NativeRegionalAppsScreen(
                                    kind = kind,
                                    snapshot = regionalApps?.takeIf { it.kind == kind },
                                    revision = regionalAppsRevision,
                                    busy = regionalBusy,
                                    onBack = { goBack() },
                                    onReload = { onOpenRegionalApps(kind) },
                                    onSave = { onSaveRegionalApps(kind, it) },
                                    onReset = { onResetRegionalApps(kind) },
                                )
                            }

                            PAGE_REGIONAL ->
                                NativeRegionalRoutingScreen(
                                    options = regionalOptions,
                                    busy = regionalBusy,
                                    fullTunnel = settingsState.fullTunnel,
                                    handbookRouting = settingsState.handbookRouting,
                                    onBack = { goBack() },
                                    onSave = onSaveRegionalOptions,
                                    onOpenApps = { kind ->
                                        regionalAppKind = kind.name
                                        onOpenRegionalApps(kind)
                                        openPage(PAGE_REGIONAL_APPS)
                                    },
                                )

                            PAGE_TRAFFIC_FILTERS ->
                                NativeTrafficFiltersScreen(
                                    filters = trafficFilters,
                                    busy = trafficFiltersBusy,
                                    onBack = { goBack() },
                                    onSave = onSaveTrafficFilters,
                                )

                            PAGE_PROTECTION ->
                                NativeVpnProtectionScreen(
                                    protection = vpnProtection,
                                    onBack = { goBack() },
                                    onRefresh = onRefreshVpnProtection,
                                    onOpenSettings = onOpenVpnSettings,
                                )

                            PAGE_PROFILES ->
                                NativeProfilesScreen(
                                    profiles = profiles,
                                    busyProfileId = busyProfileId,
                                    onBack = { goBack() },
                                    onAdd = { addProfileOpen = true },
                                    onSelect = onSelectProfile,
                                    onDelete = onDeleteProfile,
                                    onRefresh = onRefreshProfile,
                                    onEdit = { profile ->
                                        onOpenProfileEditor(profile)
                                        openPage(PAGE_PROFILE_DETAILS)
                                    },
                                    onCopyConfig = onCopyProfileConfig,
                                    onExportConfig = onExportProfileConfig,
                                    loading = profilesLoading, loadFailed = profilesLoadFailed,
                                    onRetry = onRetryProfiles, onUpdateAll = onUpdateAllProfiles,
                                    sortByName = profileSortByName, ascending = profileSortAscending,
                                    onSort = { byName, ascending -> profileSortByName = byName; profileSortAscending = ascending },
                                )

                            PAGE_PROFILE_DETAILS ->
                                NativeProfileDetailsScreen(
                                    editor = profileEditor,
                                    session = editorSession, loadFailed = profileEditorLoadFailed, onRetry = onRetryProfileEditor,
                                    busy = profileEditorBusy,
                                    onBack = { goBack() },
                                    onSave = onSaveProfileEditor,
                                )

                            PAGE_PER_APP_BACKUP ->
                                NativePerAppBackupScreen(
                                    busy = perAppBusy,
                                    canImport = status == Status.Stopped,
                                    pendingImport = pendingPerAppImport,
                                    onBack = { goBack() },
                                    onImportClipboard = onImportPerAppClipboard,
                                    onImportFile = onImportPerAppFile,
                                    onExportClipboard = onExportPerAppClipboard,
                                    onExportFile = onExportPerAppFile,
                                    onConfirmImport = onConfirmPerAppImport,
                                    onDismissImport = onDismissPerAppImport,
                                )

                            PAGE_PER_APP ->
                                NativePerAppScreen(
                                    snapshot = perAppSnapshot,
                                    canChange = status == Status.Stopped,
                                    busy = perAppBusy,
                                    onBack = { goBack() },
                                    onModeChanged = onPerAppModeChanged,
                                    onTogglePackage = onTogglePerAppPackage,
                                    onClear = onClearPerApp,
                                    onOpenBackup = { openPage(PAGE_PER_APP_BACKUP) },
                                )

                            PAGE_LOGS ->
                                NativeLogsScreen(
                                    snapshot = logSnapshot,
                                    busy = logBusy,
                                    onBack = { goBack() },
                                    onRefresh = onRefreshLogs,
                                    onClear = onClearLogs,
                                )

                            PAGE_OUTBOUNDS ->
                                NativeOutboundsScreen(
                                    groups = outboundGroups,
                                    connected = status == Status.Started,
                                    sort = generalPreferences.outboundSort,
                                    onChangeSort = onChangeOutboundSort,
                                    busyTag = outboundBusyTag,
                                    smartSelection = generalPreferences.smartSelection,
                                    smartSelectionBusy = generalPreferencesBusy,
                                    adaptiveNetwork = connectionOptions.adaptiveNetwork,
                                    onChangeSmartSelection = onChangeSmartSelection,
                                    onBack = { goBack() },
                                    onRefresh = onRefreshOutbounds,
                                    onSelect = onSelectOutbound,
                                    onTest = onTestOutbound,
                                    onTestActive = onTestActiveOutbounds,
                                )

                            PAGE_WIFI_GUIDE ->
                                NativeWifiSharingGuideScreen(
                                    rootMode = rootMode,
                                    details = wifiSharingDetails,
                                    busy = wifiSharingDetailsBusy,
                                    onBack = { goBack() },
                                    onRefresh = onRefreshWifiSharingDetails,
                                )

                            PAGE_ABOUT ->
                                NativeAboutScreen(
                                    versionName = com.hiddify.hiddify.BuildConfig.VERSION_NAME,
                                    versionCode = com.hiddify.hiddify.BuildConfig.VERSION_CODE,
                                    updateChecking = updateChecking,
                                    updateMessage = updateMessage,
                                    updateUrl = updateUrl,
                                    onBack = { goBack() },
                                    onCheckUpdate = onCheckUpdate,
                                    onOpenUpdate = onOpenUpdate,
                                    onOpenFork = onOpenFork,
                                    onOpenUpstream = onOpenUpstream,
                                    onOpenTerms = onOpenTerms,
                                    onOpenPrivacy = onOpenPrivacy,
                                )

                            PAGE_CORE_OPTIONS ->
                                NativeCoreOptionsScreen(
                                    onBack = { goBack() },
                                    onOpenDns = { openPage(PAGE_DNS) },
                                    onOpenTls = { openPage(PAGE_TLS) },
                                    onOpenTunnel = { openPage(PAGE_TUNNEL) },
                                    onOpenInbound = { openPage(PAGE_INBOUND) },
                                    onOpenGeneralOptions = { openPage(PAGE_GENERAL_OPTIONS) },
                                )

                            PAGE_CHAIN ->
                                NativeChainScreen(
                                    options = chainOptions,
                                    profiles = profiles,
                                    busy = chainBusy,
                                    onBack = { goBack() },
                                    onSave = onSaveChainOptions,
                                )

                            PAGE_SETTINGS -> NativeSettingsOverviewScreen(
                                onOpenProfiles = { openPage(PAGE_PROFILES) },
                                onOpenCategory = { openCategory(it) },
                                onOpenCoreOptions = { openPage(PAGE_CORE_OPTIONS) },
                                onOpenDns = { openPage(PAGE_DNS) },
                                onOpenTls = { openPage(PAGE_TLS) },
                                onOpenInbound = { openPage(PAGE_INBOUND) },
                                onOpenChain = { openPage(PAGE_CHAIN) },
                                onOpenLogs = { openPage(PAGE_LOGS) },
                                onOpenAbout = { openPage(PAGE_ABOUT) },
                            )

                            PAGE_TUNNEL -> NativeTunnelOptionsScreen(
                                options = tunnelOptions, busy = tunnelBusy,
                                canSave = status == Status.Stopped && !wifiSharingBusy && !chainBusy && !inboundBusy && !dnsBusy && !tlsBusy && !generalOptionsBusy,
                                onBack = { goBack() }, onSave = onSaveTunnelOptions,
                            )

                            PAGE_GENERAL_OPTIONS -> NativeGeneralOptionsScreen(
                                options = generalOptions, busy = generalOptionsBusy,
                                canSave = status == Status.Stopped && !wifiSharingBusy && !chainBusy && !inboundBusy && !dnsBusy && !tlsBusy,
                                onBack = { goBack() }, onSave = onSaveGeneralOptions,
                            )

                            PAGE_TLS -> NativeTlsOptionsScreen(
                                options = tlsOptions, busy = tlsBusy,
                                canSave = status == Status.Stopped && !wifiSharingBusy && !chainBusy && !inboundBusy && !dnsBusy,
                                onBack = { goBack() }, onSave = onSaveTlsOptions,
                            )

                            PAGE_DNS -> NativeDnsOptionsScreen(
                                options = dnsOptions, busy = dnsBusy,
                                canSave = status == Status.Stopped && !wifiSharingBusy && !chainBusy && !inboundBusy && !tlsBusy,
                                onBack = { goBack() }, onSave = onSaveDnsOptions,
                                onOpenPrivacy = { openCategory(NativeSettingsCategory.DNS) },
                            )

                            PAGE_INBOUND -> NativeInboundOptionsScreen(
                                options = inboundOptions, busy = inboundBusy,
                                canSave = status == Status.Stopped && !wifiSharingBusy && !chainBusy && !dnsBusy && !tlsBusy && !generalOptionsBusy && !tunnelBusy && !generalPreferencesBusy,
                                onBack = { goBack() }, onSave = onSaveInboundOptions,
                                onOpenVpnOptions = { openCategory(NativeSettingsCategory.VPN) },
                            )

                            PAGE_PROXY_PRIVACY -> NativeProxyPrivacyScreen(
                                options = proxyPrivacy,
                                busy = proxyPrivacyBusy,
                                wifiSharing = settingsState.wifiSharing,
                                rootMode = rootMode,
                                onBack = { goBack() },
                                onSave = onSaveProxyPrivacy,
                                onOpenCoreOptions = { openPage(PAGE_CORE_OPTIONS) },
                            )

                            PAGE_PRIVACY -> NativePrivacyOverviewScreen(
                                configured = privacyConfigured,
                                canRestore = privacyCanRestore,
                                busy = privacySetupBusy,
                                canApply = status == Status.Stopped && !wifiSharingBusy && !regionalBusy && !connectionOptionsBusy && !proxyPrivacyBusy,
                                onConfigure = onConfigurePrivacy,
                                onRestore = onRestorePrivacy,
                                expandedCategories = privacyExpanded.split('|').filter { it.isNotEmpty() }.toSet(),
                                onToggleCategory = { category ->
                                    val expanded = privacyExpanded.split('|').filter { it.isNotEmpty() }.toMutableSet()
                                    if (!expanded.add(category)) expanded.remove(category)
                                    privacyExpanded = expanded.sorted().joinToString("|")
                                },
                                onOpenRegional = { openPage(PAGE_REGIONAL) },
                                onOpenPerApp = { openPage(PAGE_PER_APP) },
                                onOpenTunnel = { openPage(PAGE_TUNNEL) },
                                onOpenPolicy = { openPage(PAGE_CONNECTION_POLICY) },
                                onOpenProxyPrivacy = { openPage(PAGE_PROXY_PRIVACY) },
                                onOpenProtection = {
                                    onRefreshVpnProtection()
                                    openPage(PAGE_PROTECTION)
                                },
                                onOpenFilters = { openPage(PAGE_TRAFFIC_FILTERS) },
                                onOpenDns = { openPage(PAGE_DNS) },
                                onOpenCoreOptions = { openPage(PAGE_CORE_OPTIONS) },
                                onOpenCategory = { openCategory(it) },
                            )

                            PAGE_PREFERENCES ->
                                NativeSettingsScreen(
                                    generalPreferences = generalPreferences,
                                    generalPreferencesBusy = generalPreferencesBusy,
                                    onChangeLanguage = onChangeLanguage,
                                    onChangeHapticFeedback = onChangeHapticFeedback,
                                    onChangeSmartSelection = onChangeSmartSelection,
                                    onOpenTunnel = { openPage(PAGE_TUNNEL) },
                                    onOpenGeneralOptions = { openPage(PAGE_GENERAL_OPTIONS) },
                                    category = NativeSettingsCategory.valueOf(settingsCategory),
                                    state = settingsState,
                                    themeMode = themeMode,
                                    themeBusy = themeBusy,
                                    onChangeTheme = onChangeTheme,
                                    onOpenNotificationSettings = onOpenNotificationSettings,
                                    onOpenBatterySettings = onOpenBatterySettings,
                                    canChangeServiceMode = status == Status.Stopped && !wifiSharingBusy,
                                    wifiSharingBusy = wifiSharingBusy || status == Status.Starting || status == Status.Stopping,
                                    wifiSharingDetails = wifiSharingDetails,
                                    onBack = { goBack() },
                                    onOpenVpnProtection = {
                                        onRefreshVpnProtection()
                                        openPage(PAGE_PROTECTION)
                                    },
                                    onOpenConnectionPolicy = { openPage(PAGE_CONNECTION_POLICY) },
                                    onOpenRegionalRouting = { openPage(PAGE_REGIONAL) },
                                    onOpenTrafficFilters = { openPage(PAGE_TRAFFIC_FILTERS) },
                                    onOpenPerAppRouting = { openPage(PAGE_PER_APP) },
                                    onOpenCoreOptions = { openPage(PAGE_CORE_OPTIONS) },
                                    onOpenChain = { openPage(PAGE_CHAIN) },
                                    onOpenWifiSharingGuide = {
                                        onRefreshWifiSharingDetails()
                                        openPage(PAGE_WIFI_GUIDE)
                                    },
                                    onImportSettingsClipboard = onImportSettingsClipboard,
                                    onImportSettingsFile = onImportSettingsFile,
                                    onExportSettingsClipboard = onExportSettingsClipboard,
                                    onExportSettingsFile = onExportSettingsFile,
                                    onResetSettings = onResetSettings,
                                    onProxyOnlyChanged = onProxyOnlyChanged,
                                    onRootModeChanged = onRootModeChanged,
                                    onWifiSharingChanged = onWifiSharingChanged,
                                    onFullTunnelChanged = onFullTunnelChanged,
                                    onEncryptedDnsChanged = onEncryptedDnsChanged,
                                    onPublicDnsChanged = onPublicDnsChanged,
                                    onDisableSystemProxyChanged = onDisableSystemProxyChanged,
                                    onDisableIpv6Changed = onDisableIpv6Changed,
                                    onHandbookRoutingChanged = onHandbookRoutingChanged,
                                    onHandbookProxyChanged = onHandbookProxyChanged,
                                    onHandbookDirectChanged = onHandbookDirectChanged,
                                    onHandbookProxySitesChanged = onHandbookProxySitesChanged,
                                    onHandbookDirectSitesChanged = onHandbookDirectSitesChanged,
                                    onDynamicNotificationChanged = onDynamicNotificationChanged,
                                    onDebugModeChanged = onDebugModeChanged,
                                    onDisableMemoryLimitChanged = onDisableMemoryLimitChanged,
                                )

                            else ->
                                HomeScreen(
                                    status = status,
                                    internetHealth = internetHealth,
                                    recoveryAttempt = recoveryAttempt,
                                    activeProfileName = activeProfileName,
                                    hasActiveProfile = hasActiveProfile,
                                    activeProfile = profiles.firstOrNull { it.active },
                                    hasProfiles = profiles.isNotEmpty(),
                                    profilesLoading = profilesLoading,
                                    profilesLoadFailed = profilesLoadFailed, onRetryProfiles = onRetryProfiles,
                                    busyProfileId = busyProfileId,
                                    onRefreshProfile = onRefreshProfile,
                                    onAddProfile = { addProfileOpen = true },
                                    systemStats = systemStats,
                                    activeOutbound = activeOutbound,
                                    outboundBusy = outboundBusyTag != null,
                                    requiresReconnect = requiresReconnect,
                                    reconnectBusy = reconnectBusy,
                                    onTestActive = { onTestOutbound("") },
                                    onToggleConnection = onToggleConnection,
                                    onOpenProfiles = { profilesSheetOpen = true },
                                    onOpenSettings = { onRefreshWifiSharingDetails(); quickSettingsOpen = true },
                                    onOpenDiagnostics = { openPage(PAGE_DIAGNOSTICS) },
                                    onOpenOutbounds = {
                                        onRefreshOutbounds()
                                        openPage(PAGE_OUTBOUNDS)
                                    },
                                )
                        }
                    }
                }
                if (page in setOf(PAGE_HOME, PAGE_SETTINGS, PAGE_PRIVACY)) {
                    NativeGlass(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), radius = 24) {
                        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, modifier = Modifier.height(72.dp)) {
                            listOf(
                                Triple(PAGE_HOME, R.drawable.native_power, R.string.native_home),
                                Triple(PAGE_PRIVACY, R.drawable.native_shield, R.string.native_privacy_title),
                                Triple(PAGE_SETTINGS, R.drawable.native_settings, R.string.native_settings),
                            ).forEach { (destination, icon, label) ->
                                NavigationBarItem(selected = page == destination, enabled = !privacySetupBusy && !proxyPrivacyBusy && !inboundBusy && !dnsBusy && !tlsBusy && !generalOptionsBusy && !tunnelBusy && !generalPreferencesBusy, onClick = { pageTrail = ""; page = destination },
                                    icon = { Icon(painterResource(icon), contentDescription = null) },
                                    label = { Text(stringResource(label), maxLines = 1) })
                            }
                        }
                    }
                }
            }
        }

        if (profilesSheetOpen) {
            NativeProfilesSheet(profiles = profiles, busyProfileId = busyProfileId,
                loading = profilesLoading, loadFailed = profilesLoadFailed,
                sortByName = profileSortByName, ascending = profileSortAscending,
                onSort = { byName, ascending -> profileSortByName = byName; profileSortAscending = ascending },
                onDismiss = { profilesSheetOpen = false }, onRetry = onRetryProfiles, onUpdateAll = onUpdateAllProfiles,
                onSelect = onSelectProfile, onDelete = onDeleteProfile, onRefresh = onRefreshProfile,
                onEdit = { profile -> profilesSheetOpen = false; onOpenProfileEditor(profile); openPage(PAGE_PROFILE_DETAILS) },
                onCopyConfig = onCopyProfileConfig, onExportConfig = onExportProfileConfig)
        }
        if (addProfileOpen) {
            NativeAddProfileSheet(
                busy = busyProfileId != null,
                onDismiss = { addProfileOpen = false },
                onImportFree = onImportFreeProfile,
                onImport = { raw, name, interval, disabled ->
                    onImportProfile(raw, name, interval, disabled)
                },
            )
        }
        if (quickSettingsOpen) {
            NativeQuickSettingsSheet(
                serviceMode = settingsState.serviceMode, wifiSharing = settingsState.wifiSharing,
                inbound = inboundOptions, chain = chainOptions, activeProfileName = activeProfileName,
                busy = reconnectBusy || inboundBusy || chainBusy || wifiSharingBusy || privacySetupBusy || proxyPrivacyBusy ||
                    status == Status.Starting || status == Status.Stopping,
                details = wifiSharingDetails, detailsBusy = wifiSharingDetailsBusy,
                onServiceMode = onQuickServiceMode, onLanSharing = onQuickLanSharing,
                onChain = onSaveChainOptions,
                onOpenChain = { quickSettingsOpen = false; openPage(PAGE_CHAIN) },
                onDismiss = { quickSettingsOpen = false },
            )
        }
        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = onDismissError,
                confirmButton = {
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.native_error_title)) },
                text = { Text(errorMessage) },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    status: Status,
    internetHealth: NativeInternetHealth,
    recoveryAttempt: Int,
    activeProfileName: String,
    hasActiveProfile: Boolean,
    activeProfile: NativeProfile? = null,
    hasProfiles: Boolean = hasActiveProfile,
    profilesLoading: Boolean = false,
    profilesLoadFailed: Boolean = false,
    onRetryProfiles: () -> Unit = {},
    busyProfileId: String? = null,
    onRefreshProfile: (NativeProfile) -> Unit = {},
    onAddProfile: () -> Unit = {},
    systemStats: NativeSystemStats,
    activeOutbound: com.hiddify.hiddify.nativecore.NativeOutbound? = null,
    outboundBusy: Boolean = false,
    requiresReconnect: Boolean = false,
    reconnectBusy: Boolean = false,
    onTestActive: () -> Unit = {},
    onToggleConnection: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenOutbounds: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.vetroff_app_logo), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.app_name), modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(if (BuildConfig.CHANNEL == "prod") BuildConfig.VERSION_NAME
                else "${BuildConfig.VERSION_NAME} ${BuildConfig.CHANNEL}",
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall,
                maxLines = 1)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenDiagnostics) {
                Icon(painterResource(R.drawable.native_shield), stringResource(R.string.native_diagnostics_title))
            }
            FilledTonalIconButton(onClick = onAddProfile, enabled = busyProfileId == null) {
                Icon(painterResource(R.drawable.native_add), stringResource(R.string.native_profile_add))
            }
        }
        if (!hasProfiles && !hasActiveProfile && !profilesLoading && !profilesLoadFailed) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(painterResource(R.drawable.native_shield), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.native_profile_help_message), style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = onAddProfile, enabled = busyProfileId == null) {
                    Icon(painterResource(R.drawable.native_add), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.native_profile_add))
                }
            }
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp)) {
                    when {
                        profilesLoading -> Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        profilesLoadFailed -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.native_profiles_load_failed), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetryProfiles) { Text(stringResource(R.string.native_profiles_retry)) }
                        }
                        activeProfile != null -> NativeProfileTile(profile = activeProfile, isMain = true,
                            busy = busyProfileId != null,
                            onClick = onOpenProfiles, onRefresh = { onRefreshProfile(activeProfile) })
                        else -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.native_no_profile_selected), Modifier.weight(1f))
                            IconButton(onClick = onOpenProfiles) {
                                Icon(painterResource(R.drawable.native_list), stringResource(R.string.native_profiles))
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        ConnectionCard(status = status, recovering = recoveryAttempt > 0, internetHealth = internetHealth,
                            requiresReconnect = requiresReconnect, reconnectBusy = reconnectBusy,
                            onToggleConnection = onToggleConnection)
                        Spacer(Modifier.height(12.dp))
                        if (status == Status.Started && activeOutbound != null) {
                            NativeActiveProxyDelay(activeOutbound, outboundBusy, onTestActive)
                        }
                        TextButton(onClick = onOpenDiagnostics, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(if (recoveryAttempt > 0) stringResource(R.string.native_recovery_attempt, recoveryAttempt)
                                else stringResource(if (status != Status.Started) R.string.native_home_health_stopped else when (internetHealth) {
                                    NativeInternetHealth.UNCHECKED -> R.string.native_health_unchecked
                                    NativeInternetHealth.CHECKING -> R.string.native_health_checking
                                    NativeInternetHealth.AVAILABLE -> R.string.native_health_available
                                    NativeInternetHealth.UNAVAILABLE -> R.string.native_health_unavailable
                                }), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    if (status == Status.Started && activeOutbound != null) {
                        NativeActiveProxyFooter(activeOutbound, outboundBusy, onOpenOutbounds, onTestActive)
                    }
                    Spacer(Modifier.height(12.dp))
                    ConnectionStatsCard(systemStats)
                    Spacer(Modifier.height(16.dp))
                    NativeGlass(Modifier.fillMaxWidth().clickable(onClick = onOpenSettings), radius = 20) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.native_tune), null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.native_quick_settings), Modifier.weight(1f).padding(start = 12.dp),
                                style = MaterialTheme.typography.titleSmall)
                            Icon(painterResource(R.drawable.native_arrow_up), null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatsCard(stats: NativeSystemStats) {
    NativeGlass(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("↓ ${formatTraffic(stats.downlink)}/s", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("↑ ${formatTraffic(stats.uplink)}/s", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun formatTraffic(value: Long): String {
    val safe = value.coerceAtLeast(0)
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var scaled = safe.toDouble()
    var unit = 0
    while (scaled >= 1024.0 && unit < units.lastIndex) {
        scaled /= 1024.0
        unit++
    }
    return if (unit == 0 || scaled >= 100.0) {
        "%.0f %s".format(scaled, units[unit])
    } else {
        "%.1f %s".format(scaled, units[unit])
    }
}

@Composable
private fun ConnectionCard(
    status: Status,
    recovering: Boolean,
    internetHealth: NativeInternetHealth,
    onToggleConnection: () -> Unit,
    requiresReconnect: Boolean = false,
    reconnectBusy: Boolean = false,
) {
    val label = stringResource(if (status == Status.Started && requiresReconnect) R.string.native_quick_reconnect else if (recovering) R.string.native_recovery_cancel else when (status) {
        Status.Stopped -> R.string.native_status_stopped
        Status.Starting -> R.string.native_status_starting
        Status.Started -> R.string.native_status_started
        Status.Stopping -> R.string.native_status_stopping
    })
    val actionLabel = stringResource(if (status == Status.Started && requiresReconnect) R.string.native_quick_reconnect else if (recovering) R.string.native_recovery_cancel else when (status) {
        Status.Stopped -> R.string.native_connect
        Status.Starting -> R.string.native_connecting
        Status.Started -> R.string.native_disconnect
        Status.Stopping -> R.string.native_disconnecting
    })
    val scheme = MaterialTheme.colorScheme
    val accent = when {
        status == Status.Started && requiresReconnect -> scheme.secondary
        status == Status.Started && internetHealth == NativeInternetHealth.UNAVAILABLE -> Color(0xFFFFC857)
        status == Status.Started -> scheme.tertiary
        else -> scheme.primary
    }
    val enabled = !reconnectBusy && (recovering || status == Status.Stopped || status == Status.Started)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(176.dp).background(
            Brush.radialGradient(listOf(accent.copy(alpha = .12f), Color.Transparent)), CircleShape,
        ).border(2.dp, accent.copy(alpha = .28f), CircleShape), contentAlignment = Alignment.Center) {
            NativeGlass(Modifier.padding(14.dp).fillMaxSize(), radius = 100, accent = accent) {
                Box(Modifier.fillMaxSize().clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onToggleConnection)
                    .semantics { contentDescription = actionLabel }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.native_power), null, Modifier.size(64.dp), tint = accent)
                }
            }
            if (!enabled) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp)
        }
        Text(label, Modifier.padding(top = 24.dp), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Dart appearance · dark", widthDp = 390, heightDp = 844)
@Composable
private fun DarkHomePreview() = NativeHomePreview(NativeThemeMode.DARK)

@androidx.compose.ui.tooling.preview.Preview(name = "Dart appearance · light", widthDp = 390, heightDp = 844)
@Composable
private fun LightHomePreview() = NativeHomePreview(NativeThemeMode.LIGHT)

@androidx.compose.ui.tooling.preview.Preview(name = "Dart appearance · black", widthDp = 390, heightDp = 844)
@Composable
private fun BlackHomePreview() = NativeHomePreview(NativeThemeMode.BLACK)

@Composable
private fun NativeHomePreview(mode: NativeThemeMode) {
    NativeAppTheme(mode) {
        NativeAtmosphere {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    status = Status.Started, internetHealth = NativeInternetHealth.AVAILABLE,
                    recoveryAttempt = 0, activeProfileName = "VetrOFF", hasActiveProfile = true,
                    systemStats = NativeSystemStats(downlink = 1258291, uplink = 52428, trafficAvailable = true),
                    onToggleConnection = {}, onOpenProfiles = {}, onOpenSettings = {},
                    onOpenDiagnostics = {}, onOpenOutbounds = {},
                )
            }
        }
    }
}

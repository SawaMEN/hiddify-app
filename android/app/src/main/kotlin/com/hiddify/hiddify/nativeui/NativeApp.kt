package com.hiddify.hiddify.nativeui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.stateDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.hiddify.hiddify.nativecore.nativeTrafficAmount
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import com.hiddify.hiddify.BuildConfig
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    generalOptionsLoadFailed: Boolean,
    onReloadGeneralOptions: () -> Unit,
    onSaveGeneralOption: (com.hiddify.hiddify.nativecore.NativeGeneralOptionField, String) -> Unit,
    generalPreferences: com.hiddify.hiddify.nativepreferences.NativeGeneralPreferences,
    generalPreferencesBusy: Boolean,
    onChangeLanguage: (com.hiddify.hiddify.nativepreferences.NativeLanguage) -> Unit,
    onChangeHapticFeedback: (Boolean) -> Unit,
    onChangeSmartSelection: (Boolean) -> Unit,
    onChangeOutboundSort: (com.hiddify.hiddify.nativepreferences.NativeOutboundSort) -> Unit,
    tlsOptions: com.hiddify.hiddify.nativecore.NativeTlsOptions?,
    tlsBusy: Boolean,
    tlsLoadFailed: Boolean,
    onReloadTlsOptions: () -> Unit,
    onSaveTlsOption: (com.hiddify.hiddify.nativecore.NativeTlsOptionField, String) -> Unit,
    dnsOptions: com.hiddify.hiddify.nativecore.NativeDnsOptions?,
    dnsBusy: Boolean,
    dnsLoadFailed: Boolean,
    onReloadDnsOptions: () -> Unit,
    onSaveDnsOption: (com.hiddify.hiddify.nativecore.NativeDnsOptionField, String) -> Unit,
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
    accentColor: com.hiddify.hiddify.nativepreferences.NativeAccentColor,
    onChangeAccent: (com.hiddify.hiddify.nativepreferences.NativeAccentColor) -> Unit,
    themeMode: NativeThemeMode,
    themeBusy: Boolean,
    onChangeTheme: (NativeThemeMode) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    status: Status,
    connectionFailed: Boolean,
    smartSelected: Boolean,
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
    profileImportPreview: com.hiddify.hiddify.nativeprofile.NativeImportSummary?,
    onConfirmProfileImport: (Boolean) -> Unit,
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
    activeOutbound: com.hiddify.hiddify.nativecore.NativeOutbound?,
    requiresReconnect: Boolean,
    reconnectBusy: Boolean,
    onQuickServiceMode: (Boolean) -> Unit,
    onQuickLanSharing: (Boolean, String) -> Unit,
    onQuickChainMode: (Boolean, String?) -> Unit,
    onResolveLanSharing: suspend () -> NativeWifiSharingDetails,
    outboundBusyTag: String?,
    outboundOperationRevision: Int,
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
    ipVisibilitySession: NativeIpVisibilitySession,
    onSelectProfile: (NativeProfile) -> Unit,
    onDeleteProfile: (NativeProfile) -> Unit,
    onRefreshProfile: (NativeProfile) -> Unit,
    onOpenProfileEditor: (NativeProfile) -> Unit,
    onSaveProfileEditor: (String, Boolean, Int?, String) -> Unit,
    onCancelProfileImport: () -> Unit,
    onImportProfile: (String, String?, Int?, Boolean) -> Unit,
    onImportFreeProfile: (com.hiddify.hiddify.nativeprofile.NativeFreeProfile, String) -> Unit,
    onCopyProfileConfig: (NativeProfile) -> Unit,
    onExportProfileConfig: (NativeProfile) -> Unit,
    onPerAppModeChanged: (String) -> Unit,
    onTogglePerAppPackage: (String) -> Unit,
    onClearPerApp: () -> Unit,
    onReloadPerApp: () -> Unit,
    onImportPerAppClipboard: () -> Unit,
    onImportPerAppFile: () -> Unit,
    onExportPerAppClipboard: () -> Unit,
    onExportPerAppFile: () -> Unit,
    onConfirmPerAppImport: () -> Unit,
    onDismissPerAppImport: () -> Unit,
    onClearLogs: () -> Unit,
    onShareLogs: (Boolean) -> Unit,
    onSaveChainOptions: (NativeChainOptions) -> Unit,
    onSelectOutbound: (String, String) -> Unit,
    onTestOutbound: (String) -> Unit,
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
    onCopyAppInfo: () -> Unit,
    onProxyOnlyChanged: (Boolean) -> Unit,
    onRootModeChanged: (Boolean) -> Unit,
    onWifiSharingChanged: (Boolean) -> Unit,
    onFullTunnelChanged: (Boolean) -> Unit,
    onEncryptedDnsChanged: (Boolean) -> Unit,
    onPublicDnsChanged: (Boolean) -> Unit,
    onDisableSystemProxyChanged: (Boolean) -> Unit,
    onHandbookRoutingChanged: (Boolean) -> Unit,
    onHandbookProxyChanged: (Boolean) -> Unit,
    onHandbookDirectChanged: (Boolean) -> Unit,
    onHandbookProxySitesChanged: (String) -> Unit,
    onHandbookDirectSitesChanged: (String) -> Unit,
    onSaveCommunitySelection: (Boolean, Boolean, String) -> Unit,
    onDynamicNotificationChanged: (Boolean) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onDisableMemoryLimitChanged: (Boolean) -> Unit,
    onLogVisibilityChange: (Boolean) -> Unit = {},
) {
    var profilesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var profileSortByName by rememberSaveable { mutableStateOf(false) }
    var profileSortAscending by rememberSaveable { mutableStateOf(false) }
    var addProfileOpen by rememberSaveable { mutableStateOf(false) }
    var observedEditorSavedRevision by remember { mutableStateOf(profileEditorSavedRevision) }
    var observedImportRevision by remember { mutableStateOf(profileImportRevision) }
    var observedSelectionRevision by remember { mutableStateOf(profileSelectionRevision) }
    var quickSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var outboundsOpen by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(PAGE_HOME) }
    var navigationDirection by remember { mutableStateOf(0) }
    val pageStateHolder = rememberSaveableStateHolder()
    var regionalAppKind by rememberSaveable { mutableStateOf(NativeRegionalAppKind.DIRECT.name) }

    // Only internal page names are stored, so this trail also survives Activity recreation.
    var privacyExpanded by rememberSaveable { mutableStateOf("") }
    var pageTrail by rememberSaveable { mutableStateOf("") }
    var settingsCategory by rememberSaveable { mutableStateOf(NativeSettingsCategory.APP.name) }
    fun openPage(destination: String) {
        if (privacySetupBusy || proxyPrivacyBusy || inboundBusy || dnsBusy || tlsBusy || generalOptionsBusy || tunnelBusy || generalPreferencesBusy) return
        if (page == destination) return
        navigationDirection = 1
        pageTrail += "|$page"
        page = destination
    }
    fun goBack() {
        if (privacySetupBusy || proxyPrivacyBusy || inboundBusy || dnsBusy || tlsBusy || generalOptionsBusy || tunnelBusy || generalPreferencesBusy) return
        if (page == PAGE_DIAGNOSTICS) onCancelDiagnostics()
        if (page == PAGE_PER_APP_BACKUP || page == PAGE_PER_APP) onDismissPerAppImport()
        navigationDirection = -1
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

    NativeAppTheme(themeMode, accentColor) {
        NativeBackground {
            var navigationBarHeight by remember { mutableStateOf(80.dp) }
            val density = LocalDensity.current
            Box(
                modifier =
                    Modifier
                        .semantics { testTagsAsResourceId = true }
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                NativePageTransition(page, navigationDirection, Modifier.fillMaxSize()) { visiblePage ->
                    pageStateHolder.SaveableStateProvider(visiblePage) {
                        NativePageFrame(visiblePage, navigationBarHeight) {
                            when (visiblePage) {
                                PAGE_DIAGNOSTICS ->
                                    NativeDiagnosticsScreen(
                                        snapshot = diagnosticSnapshot,
                                        busy = diagnosticBusy,
                                        report = diagnosticReport,
                                        onBack = { goBack() },
                                        onRun = onRunDiagnostics,
                                        onShareReport = onShareDiagnosticReport,
                                        connected = status == Status.Started, sessionKey = activeOutbound?.tag.orEmpty(),
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
                                        canChange = status == Status.Stopped || status == Status.Started,
                                        busy = perAppBusy,
                                        onBack = { goBack() },
                                        onModeChanged = onPerAppModeChanged,
                                        onTogglePackage = onTogglePerAppPackage,
                                        onClear = onClearPerApp,
                                        onRetry = onReloadPerApp,
                                        pendingImport = pendingPerAppImport,
                                        onImportClipboard = onImportPerAppClipboard,
                                        onImportFile = onImportPerAppFile,
                                        onExportClipboard = onExportPerAppClipboard,
                                        onExportFile = onExportPerAppFile,
                                        onConfirmImport = onConfirmPerAppImport,
                                        onDismissImport = onDismissPerAppImport,
                                    )

                                PAGE_LOGS ->
                                    NativeLogsScreen(
                                        snapshot = logSnapshot,
                                        busy = logBusy,
                                        onBack = { goBack() },
                                        onClear = onClearLogs,
                                        sharingEnabled = settingsState.debugMode,
                                        onPollingChanged = onLogVisibilityChange,
                                        onShare = onShareLogs,
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
                                        onCopyAppInfo = onCopyAppInfo,
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
                                    hasProfiles = profiles.isNotEmpty(),
                                    busy = privacySetupBusy || proxyPrivacyBusy || inboundBusy || dnsBusy || tlsBusy ||
                                        generalOptionsBusy || tunnelBusy || generalPreferencesBusy || regionalBusy ||
                                        connectionOptionsBusy || wifiSharingBusy || chainBusy || reconnectBusy,
                                    onOpenProfiles = { openPage(PAGE_PROFILES) },
                                    onOpenGeneral = { openCategory(NativeSettingsCategory.APP) },
                                    onOpenDns = { openPage(PAGE_DNS) },
                                    onOpenTls = { openPage(PAGE_TLS) },
                                    onOpenInbound = { openPage(PAGE_INBOUND) },
                                    onOpenChain = { openPage(PAGE_CHAIN) },
                                    onOpenLogs = { openPage(PAGE_LOGS) },
                                    onOpenAbout = { openPage(PAGE_ABOUT) },
                                    onImportClipboard = onImportSettingsClipboard,
                                    onImportFile = onImportSettingsFile,
                                    onExportClipboard = onExportSettingsClipboard,
                                    onExportFile = onExportSettingsFile,
                                    onReset = onResetSettings,
                                )

                                PAGE_TUNNEL -> NativeTunnelOptionsScreen(
                                    options = tunnelOptions, busy = tunnelBusy,
                                    canSave = status == Status.Stopped && !wifiSharingBusy && !chainBusy && !inboundBusy && !dnsBusy && !tlsBusy && !generalOptionsBusy,
                                    onBack = { goBack() }, onSave = onSaveTunnelOptions,
                                )

                                PAGE_GENERAL_OPTIONS -> NativeGeneralOptionsScreen(
                                    options = generalOptions, busy = generalOptionsBusy,
                                    loadFailed = generalOptionsLoadFailed, onRetry = onReloadGeneralOptions,
                                    canSave = status != Status.Starting && status != Status.Stopping && !reconnectBusy && !wifiSharingBusy && !chainBusy && !inboundBusy && !dnsBusy && !tlsBusy && !tunnelBusy && !privacySetupBusy && !proxyPrivacyBusy,
                                    onBack = { goBack() }, onSave = onSaveGeneralOption,
                                )

                                PAGE_TLS -> NativeTlsOptionsScreen(
                                    options = tlsOptions, busy = tlsBusy,
                                    canSave = status != Status.Starting && status != Status.Stopping && !reconnectBusy && !wifiSharingBusy && !chainBusy && !inboundBusy && !dnsBusy && !generalOptionsBusy && !tunnelBusy && !privacySetupBusy && !proxyPrivacyBusy,
                                    loadFailed = tlsLoadFailed, onRetry = onReloadTlsOptions,
                                    onBack = { goBack() }, onSave = onSaveTlsOption,
                                )

                                PAGE_DNS -> NativeDnsOptionsScreen(
                                    options = dnsOptions, busy = dnsBusy,
                                    canSave = status != Status.Starting && status != Status.Stopping && !reconnectBusy && !wifiSharingBusy && !chainBusy && !inboundBusy && !tlsBusy && !generalOptionsBusy && !tunnelBusy && !privacySetupBusy && !proxyPrivacyBusy,
                                    loadFailed = dnsLoadFailed, onRetry = onReloadDnsOptions,
                                    onBack = { goBack() }, onSave = onSaveDnsOption,
                                )

                                PAGE_INBOUND -> NativeInboundOptionsScreen(
                                    options = inboundOptions, busy = inboundBusy,
                                    canSave = status != Status.Starting && status != Status.Stopping && !reconnectBusy && !wifiSharingBusy && !chainBusy && !dnsBusy && !tlsBusy && !generalOptionsBusy && !tunnelBusy && !generalPreferencesBusy && !privacySetupBusy && !proxyPrivacyBusy,
                                    onBack = { goBack() }, onSave = onSaveInboundOptions,
                                    serviceMode = settingsState.serviceMode, wifiSharing = settingsState.wifiSharing,
                                    onServiceMode = onQuickServiceMode, onResolveLanSharing = onResolveLanSharing,
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
                                    busy = status == Status.Starting || status == Status.Stopping || reconnectBusy || privacySetupBusy || regionalBusy || connectionOptionsBusy || proxyPrivacyBusy || dnsBusy || tlsBusy || generalOptionsBusy || tunnelBusy || inboundBusy || wifiSharingBusy,
                                    canApply = status == Status.Stopped && !wifiSharingBusy && !regionalBusy && !connectionOptionsBusy && !proxyPrivacyBusy,
                                    onConfigure = onConfigurePrivacy,
                                    onRestore = onRestorePrivacy,
                                    expandedCategories = privacyExpanded.split('|').filter { it.isNotEmpty() }.toSet(),
                                    onToggleCategory = { category ->
                                        val expanded = privacyExpanded.split('|').filter { it.isNotEmpty() }.toMutableSet()
                                        if (!expanded.add(category)) expanded.remove(category)
                                        privacyExpanded = expanded.sorted().joinToString("|")
                                    },
                                    settings = settingsState,
                                    regional = regionalOptions,
                                    regionalRevision = regionalAppsRevision,
                                    connection = connectionOptions,
                                    proxy = proxyPrivacy,
                                    canChangeRoot = status == Status.Stopped && !wifiSharingBusy,
                                    onSaveRegional = onSaveRegionalOptions,
                                    onSaveConnection = onSaveConnectionOptions,
                                    onSaveProxy = onSaveProxyPrivacy,
                                    onOpenApps = { kind -> onOpenRegionalApps(kind); openPage(PAGE_REGIONAL_APPS) },
                                    onFullTunnel = onFullTunnelChanged,
                                    onRoot = onRootModeChanged,
                                    onEncryptedDns = onEncryptedDnsChanged,
                                    onPublicDns = onPublicDnsChanged,
                                    onHandbook = onHandbookRoutingChanged,
                                    onHandbookProxy = onHandbookProxyChanged,
                                    onHandbookDirect = onHandbookDirectChanged,
                                    onHandbookProxySites = onHandbookProxySitesChanged,
                                    onHandbookDirectSites = onHandbookDirectSitesChanged,
                                    onSaveCommunitySelection = onSaveCommunitySelection,
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
                                        accentColor = accentColor,
                                        onChangeAccent = onChangeAccent,
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
                                        onHandbookRoutingChanged = onHandbookRoutingChanged,
                                        onSaveCommunitySelection = onSaveCommunitySelection,
                                        onDynamicNotificationChanged = onDynamicNotificationChanged,
                                        onDebugModeChanged = onDebugModeChanged,
                                        onDisableMemoryLimitChanged = onDisableMemoryLimitChanged,
                                    )

                                else ->
                                    HomeScreen(
                                        status = status,
                                        connectionFailed = connectionFailed,
                                        smartSelected = smartSelected,
                                        internetHealth = internetHealth,
                                        recoveryAttempt = recoveryAttempt,
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
                                        outboundOperationRevision = outboundOperationRevision,
                                        requiresReconnect = requiresReconnect,
                                        reconnectBusy = reconnectBusy,
                                        onTestActive = { onTestOutbound("") },
                                        ipVisibilitySession = ipVisibilitySession,
                                        hapticFeedback = generalPreferences.hapticFeedback,
                                        onToggleConnection = onToggleConnection,
                                        onOpenProfiles = { profilesSheetOpen = true },
                                        onOpenSettings = { onRefreshWifiSharingDetails(); quickSettingsOpen = true },
                                        onOpenDiagnostics = { openPage(PAGE_DIAGNOSTICS) },
                                        onOpenOutbounds = {
                                            outboundsOpen = true
                                        },
                                    )
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = page in setOf(PAGE_HOME, PAGE_SETTINGS, PAGE_PRIVACY),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(if (LocalNativeMotionEnabled.current) 180 else 0)),
                    exit = fadeOut(tween(if (LocalNativeMotionEnabled.current) 80 else 0))) {
                    Box(Modifier.fillMaxWidth()
                        .onSizeChanged { navigationBarHeight = with(density) { it.height.toDp() } }
                        .padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        NativeSurface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                            NavigationBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    Triple(PAGE_HOME, R.drawable.native_power, R.string.native_home),
                                    Triple(PAGE_PRIVACY, R.drawable.native_shield, R.string.native_privacy_title),
                                    Triple(PAGE_SETTINGS, R.drawable.native_settings, R.string.native_settings),
                                ).forEach { (destination, icon, label) ->
                                    NavigationBarItem(modifier = Modifier.testTag("nav_$destination"), selected = page == destination, enabled = !privacySetupBusy && !proxyPrivacyBusy && !inboundBusy && !dnsBusy && !tlsBusy && !generalOptionsBusy && !tunnelBusy && !generalPreferencesBusy, onClick = {
                                        if (page != destination) {
                                            navigationDirection = 0
                                            pageTrail = ""
                                            page = destination
                                        }
                                    },
                                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        icon = { Icon(painterResource(icon), contentDescription = null, Modifier.size(26.dp)) },
                                        label = { Text(stringResource(label), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (outboundsOpen) {
            NativeOutboundsSheet(
                connected = status == Status.Started, sort = generalPreferences.outboundSort,
                onChangeSort = onChangeOutboundSort, busyTag = outboundBusyTag,
                smartSelection = generalPreferences.smartSelection, smartSelectionBusy = generalPreferencesBusy,
                adaptiveNetwork = connectionOptions.adaptiveNetwork, onChangeSmartSelection = onChangeSmartSelection,
                operationError = errorMessage, onDismissOperationError = onDismissError,
                onDismiss = { outboundsOpen = false }, onSelect = onSelectOutbound, onTest = onTestOutbound,
            )
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
                canCancelImport = busyProfileId == "__import__",
                onCancelImport = onCancelProfileImport,
                onDismiss = { addProfileOpen = false },
                onImportFree = onImportFreeProfile,
                onImport = { raw, name, interval, disabled ->
                    onImportProfile(raw, name, interval, disabled)
                },
            )
        }
        profileImportPreview?.let { summary ->
            NativeImportPreviewDialog(summary, onConfirmProfileImport)
        }
        if (quickSettingsOpen) {
            NativeQuickSettingsSheet(
                serviceMode = settingsState.serviceMode, wifiSharing = settingsState.wifiSharing,
                inbound = inboundOptions, chain = chainOptions, activeProfileName = activeProfileName,
                busy = reconnectBusy || inboundBusy || chainBusy || wifiSharingBusy || privacySetupBusy || proxyPrivacyBusy ||
                    dnsBusy || tlsBusy || generalOptionsBusy || tunnelBusy || status == Status.Starting || status == Status.Stopping,
                detailsBusy = wifiSharingDetailsBusy,
                onServiceMode = onQuickServiceMode, onLanSharing = onQuickLanSharing,
                onChain = onQuickChainMode,
                onResolveLanSharing = onResolveLanSharing,
                onOpenChain = { quickSettingsOpen = false; openPage(PAGE_CHAIN) },
                onDismiss = { quickSettingsOpen = false },
            )
        }
        if (errorMessage != null && !outboundsOpen) {
            AlertDialog(
                onDismissRequest = onDismissError,
                confirmButton = {
                    NativeTextButton(onClick = onDismissError) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.native_error_title)) },
                text = { Text(errorMessage) },
            )
        }
    }
}

/** Padding belongs to the displayed screen, never to the incoming navigation target. */
@Composable
private fun NativePageFrame(page: String, navigationBarHeight: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val edgeToEdge = page in setOf(PAGE_HOME, PAGE_PROFILES, PAGE_PROFILE_DETAILS,
        PAGE_SETTINGS, PAGE_ABOUT, PAGE_LOGS, PAGE_DIAGNOSTICS, PAGE_PROTECTION,
        PAGE_TRAFFIC_FILTERS, PAGE_WIFI_GUIDE, PAGE_PER_APP, PAGE_DNS, PAGE_TLS,
        PAGE_INBOUND, PAGE_GENERAL_OPTIONS)
    Box(Modifier.fillMaxSize().testTag("page_$page")
        .padding(top = if (edgeToEdge) 0.dp else 8.dp,
            bottom = if (page in setOf(PAGE_HOME, PAGE_SETTINGS, PAGE_PRIVACY)) navigationBarHeight else 0.dp),
        contentAlignment = Alignment.TopCenter) {
        Box((if (page in setOf(PAGE_HOME, PAGE_PROFILES, PAGE_PROFILE_DETAILS, PAGE_PRIVACY, PAGE_SETTINGS)) Modifier
            else Modifier.widthIn(max = 680.dp)).fillMaxSize()
            .padding(horizontal = if (edgeToEdge) 0.dp else if (page == PAGE_PRIVACY) 16.dp else 20.dp)) { content() }
    }
}

@Composable
private fun HomeScreen(
    status: Status,
    connectionFailed: Boolean = false,
    smartSelected: Boolean = false,
    internetHealth: NativeInternetHealth,
    recoveryAttempt: Int,
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
    outboundOperationRevision: Int = 0,
    requiresReconnect: Boolean = false,
    reconnectBusy: Boolean = false,
    onTestActive: () -> Unit = {},
    onToggleConnection: () -> Unit,
    ipVisibilitySession: NativeIpVisibilitySession? = null,
    hapticFeedback: Boolean = true,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenOutbounds: () -> Unit,
) {
    val versionLabel = stringResource(R.string.native_home_version)
    var noProfileNotice by rememberSaveable { mutableStateOf(false) }
    if (noProfileNotice) AlertDialog(
        onDismissRequest = { noProfileNotice = false; onAddProfile() },
        title = { Text(stringResource(R.string.native_home_choose_profile)) },
        text = { Text(stringResource(R.string.native_profile_help_message)) },
        confirmButton = { NativeTextButton(onClick = { noProfileNotice = false; onAddProfile() }) { Text(stringResource(android.R.string.ok)) } },
    )
    val homeAccent by animateColorAsState(
        connectionAccent(status, recoveryAttempt > 0, reconnectBusy, connectionFailed),
        animationSpec = tween(if (LocalNativeMotionEnabled.current) 300 else 0), label = "homeGlowColor",
    )
    Column(Modifier.fillMaxSize().drawWithCache {
        val glow = Brush.radialGradient(
            listOf(homeAccent.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.34f),
            radius = size.width * 0.85f,
        )
        onDrawBehind { drawRect(glow) }
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.vetroff_app_logo), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            Text(if (BuildConfig.CHANNEL == "prod") BuildConfig.VERSION_NAME
                else "${BuildConfig.VERSION_NAME} ${BuildConfig.CHANNEL}",
                modifier = Modifier.widthIn(max = 80.dp).semantics { contentDescription = versionLabel },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onOpenDiagnostics) {
                Icon(painterResource(R.drawable.home_health), stringResource(R.string.native_diagnostics_title))
            }
            FilledTonalIconButton(onClick = onAddProfile, enabled = busyProfileId == null) {
                Icon(painterResource(R.drawable.home_add), stringResource(R.string.native_profile_add))
            }
        }
        if (!hasProfiles && !hasActiveProfile && !profilesLoading && !profilesLoadFailed) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(painterResource(R.drawable.home_add_moderator), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.native_profile_help_message), style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                NativeButton(onClick = onAddProfile, enabled = busyProfileId == null) {
                    Icon(painterResource(R.drawable.home_add), null)
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
                            NativeTextButton(onClick = onRetryProfiles) { Text(stringResource(R.string.native_profiles_retry)) }
                        }
                        activeProfile != null -> NativeProfileTile(profile = activeProfile, isMain = true,
                            busy = busyProfileId != null,
                            onClick = onOpenProfiles, onRefresh = { onRefreshProfile(activeProfile) })
                        else -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.native_profile_help_message), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = onOpenProfiles) {
                                Icon(painterResource(R.drawable.settings_view_list), stringResource(R.string.native_profiles))
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        ConnectionCard(status = status, recovering = recoveryAttempt > 0, hapticFeedback = hapticFeedback,
                            requiresReconnect = requiresReconnect, reconnectBusy = reconnectBusy, failed = connectionFailed,
                            onToggleConnection = {
                                if (status == Status.Stopped && !hasActiveProfile && !profilesLoading && !profilesLoadFailed) noProfileNotice = true
                                else onToggleConnection()
                            })
                        Spacer(Modifier.height(12.dp))
                        if (status == Status.Started && activeOutbound != null) {
                            NativeActiveProxyDelay(activeOutbound, outboundBusy, onTestActive)
                        }
                        NativeTextButton(onClick = onOpenDiagnostics, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(if (recoveryAttempt > 0) stringResource(R.string.native_recovery_attempt, recoveryAttempt)
                                else stringResource(if (status != Status.Started) R.string.native_home_health_stopped else when (internetHealth) {
                                    NativeInternetHealth.UNCHECKED -> R.string.native_health_unchecked
                                    NativeInternetHealth.CHECKING -> R.string.native_health_checking
                                    NativeInternetHealth.AVAILABLE -> R.string.native_health_available
                                    NativeInternetHealth.UNAVAILABLE -> R.string.native_health_unavailable
                                    NativeInternetHealth.PROBE_FAILED -> R.string.native_health_probe_failed
                                }), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    if (status == Status.Started && activeOutbound != null) {
                        NativeActiveProxyFooter(activeOutbound, outboundBusy, onOpenOutbounds, onTestActive,
                            ipVisibilitySession = ipVisibilitySession, hapticFeedback = hapticFeedback,
                            operationRevision = outboundOperationRevision)
                    }
                    Spacer(Modifier.height(12.dp))
                    ConnectionStatsCard(systemStats, smartSelected)
                    Spacer(Modifier.height(16.dp))
                    NativeSurface(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenSettings).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.home_tune), null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.native_quick_settings), Modifier.weight(1f).padding(start = 12.dp),
                                style = MaterialTheme.typography.titleSmall)
                            Icon(painterResource(R.drawable.home_arrow_up), null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatsCard(stats: NativeSystemStats, smartSelected: Boolean) {
    NativeSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < (280 * LocalDensity.current.fontScale).dp
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    TrafficMetric(R.string.native_speed_download, stats.downlink, stats.downlinkTotal, stats)
                    TrafficMetric(R.string.native_speed_upload, stats.uplink, stats.uplinkTotal, stats)
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TrafficMetric(R.string.native_speed_download, stats.downlink, stats.downlinkTotal, stats, Modifier.weight(1f))
                    TrafficMetric(R.string.native_speed_upload, stats.uplink, stats.uplinkTotal, stats, Modifier.weight(1f))
                }
            }
            if (smartSelected) Text(stringResource(R.string.native_home_selection_reason),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrafficMetric(label: Int, rate: Long, total: Long, stats: NativeSystemStats, modifier: Modifier = Modifier) {
    val amount = nativeTrafficAmount(rate)
    val units = androidx.compose.ui.res.stringArrayResource(R.array.native_traffic_units)
    val session = nativeTrafficAmount(total)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(if (label == R.string.native_speed_download) R.drawable.native_arrow_down else R.drawable.native_arrow_up),
                null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (stats.speedAvailable) amount.value else "—", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
        Text(if (stats.speedAvailable) stringResource(R.string.native_speed_unit, units[amount.unitIndex]) else stringResource(R.string.native_speed_waiting),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (stats.trafficAvailable) Text(stringResource(R.string.native_traffic_session, session.value, units[session.unitIndex]),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun connectionAccent(status: Status, recovering: Boolean, reconnectBusy: Boolean, failed: Boolean): Color = when {
    status == Status.Starting || status == Status.Stopping || reconnectBusy || recovering -> Color(0xFFF2B84B)
    failed && status == Status.Stopped -> MaterialTheme.colorScheme.error
    status == Status.Started -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun ConnectionCard(
    status: Status,
    recovering: Boolean,
    hapticFeedback: Boolean,
    onToggleConnection: () -> Unit,
    requiresReconnect: Boolean = false,
    reconnectBusy: Boolean = false,
    failed: Boolean = false,
) {
    val transitioning = status == Status.Starting || status == Status.Stopping || reconnectBusy || recovering
    val showError = failed && status == Status.Stopped && !transitioning
    val label = stringResource(if (reconnectBusy || recovering) R.string.native_status_starting else if (status == Status.Started && requiresReconnect) R.string.native_quick_reconnect else if (showError) R.string.native_status_failed else when (status) {
        Status.Stopped -> R.string.native_status_stopped
        Status.Starting -> R.string.native_status_starting
        Status.Started -> R.string.native_status_started
        Status.Stopping -> R.string.native_status_stopping
    })
    val actionLabel = stringResource(if (reconnectBusy) R.string.native_connecting else if (recovering) R.string.native_recovery_cancel else if (status == Status.Started && requiresReconnect) R.string.native_quick_reconnect else when (status) {
        Status.Stopped -> R.string.native_connect
        Status.Starting -> R.string.native_connecting
        Status.Started -> R.string.native_disconnect
        Status.Stopping -> R.string.native_disconnecting
    })
    val scheme = MaterialTheme.colorScheme
    val enabled = !reconnectBusy && (recovering || status != Status.Stopping)
    val view = LocalView.current
    val animationDuration = if (LocalNativeMotionEnabled.current) 300 else 0
    val stateColor by animateColorAsState(connectionAccent(status, recovering, reconnectBusy, failed),
        animationSpec = tween(animationDuration), label = "connectionColor")
    val contentColor by animateColorAsState(when {
        transitioning -> Color(0xFF302100)
        showError -> scheme.onError
        status == Status.Started -> scheme.onPrimary
        else -> scheme.onPrimaryContainer
    }, animationSpec = tween(animationDuration), label = "connectionContentColor")
    // Grow the circle with system text size so the action remains readable.
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val buttonSize = minOf((176 * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)).dp, maxWidth - 20.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(buttonSize + 20.dp)
                .background(stateColor.copy(alpha = 0.10f), CircleShape)
                .border(1.dp, stateColor.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center) {
                androidx.compose.material3.Button(
                    onClick = {
                        if (hapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleConnection()
                    }, enabled = enabled,
                    modifier = Modifier.size(buttonSize).semantics {
                        contentDescription = actionLabel
                        stateDescription = label
                    },
                    shape = CircleShape,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = stateColor, contentColor = contentColor,
                        disabledContainerColor = stateColor, disabledContentColor = contentColor,
                    ),
                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
                    contentPadding = PaddingValues(20.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(if (showError) R.drawable.privacy_error else R.drawable.home_power),
                            null, Modifier.size(48.dp))
                        Text(actionLabel, style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                if (transitioning) CircularProgressIndicator(Modifier.size(buttonSize + 12.dp),
                    strokeWidth = 3.dp, color = stateColor)
            }
            Text(label, Modifier.heightIn(min = 28.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = if (showError) scheme.error else scheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Readable · dark", widthDp = 390, heightDp = 844)
@Composable
private fun DarkHomePreview() = NativeHomePreview(NativeThemeMode.DARK)

@androidx.compose.ui.tooling.preview.Preview(name = "Readable · light", widthDp = 390, heightDp = 844)
@Composable
private fun LightHomePreview() = NativeHomePreview(NativeThemeMode.LIGHT)

@androidx.compose.ui.tooling.preview.Preview(name = "Readable · black", widthDp = 390, heightDp = 844)
@Composable
private fun BlackHomePreview() = NativeHomePreview(NativeThemeMode.BLACK)

@Composable
private fun NativeHomePreview(mode: NativeThemeMode, empty: Boolean = false, failed: Boolean = false,
    previewStatus: Status = if (empty || failed) Status.Stopped else Status.Started) {
    val profile = NativeProfile("preview", "remote", true, "VetrOFF", "https://example.invalid", "", 3600L,
        0L, 1073741824L, 10737418240L, null, null, null, null, null)
    val outbound = com.hiddify.hiddify.nativecore.NativeOutbound("preview", "Netherlands", "VLESS", true, true,
        42, "example.invalid", 443, 0L, 0L, null,
        ipInfo = com.hiddify.hiddify.nativecore.NativeOutboundIpInfo(ip = "192.0.2.1", countryCode = "NL"))
    NativeAppTheme(mode) {
        NativeBackground {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    status = previewStatus,
                    connectionFailed = failed, smartSelected = !empty && !failed,
                    internetHealth = NativeInternetHealth.AVAILABLE,
                    recoveryAttempt = 0, hasActiveProfile = !empty,
                    activeProfile = if (empty) null else profile,
                    activeOutbound = if (empty || failed) null else outbound,
                    systemStats = NativeSystemStats(downlink = 1258291, uplink = 52428, trafficAvailable = true, speedAvailable = true),
                    onToggleConnection = {}, onOpenProfiles = {}, onOpenSettings = {},
                    onOpenDiagnostics = {}, onOpenOutbounds = {},
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Home · empty", widthDp = 390, heightDp = 844)
@Composable
private fun EmptyHomePreview() = NativeHomePreview(NativeThemeMode.LIGHT, empty = true)

@androidx.compose.ui.tooling.preview.Preview(name = "Home · connection error", widthDp = 390, heightDp = 844)
@Composable
private fun FailedHomePreview() = NativeHomePreview(NativeThemeMode.DARK, failed = true)

@androidx.compose.ui.tooling.preview.Preview(name = "Home · compact large text", widthDp = 320, heightDp = 720, fontScale = 1.6f)
@Composable
private fun CompactHomePreview() = NativeHomePreview(NativeThemeMode.DARK)

@androidx.compose.ui.tooling.preview.Preview(name = "Home · connecting", widthDp = 390, heightDp = 844)
@Composable
private fun ConnectingHomePreview() = NativeHomePreview(NativeThemeMode.DARK, previewStatus = Status.Starting)

@androidx.compose.ui.tooling.preview.Preview(name = "Home · disconnected", widthDp = 390, heightDp = 844)
@Composable
private fun DisconnectedHomePreview() = NativeHomePreview(NativeThemeMode.LIGHT, previewStatus = Status.Stopped)

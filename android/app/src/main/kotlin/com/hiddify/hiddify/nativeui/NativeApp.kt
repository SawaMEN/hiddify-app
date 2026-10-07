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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.constant.Status
import com.hiddify.hiddify.nativelog.NativeLogSnapshot
import com.hiddify.hiddify.nativecore.NativeChainOptions
import com.hiddify.hiddify.nativecore.NativeCoreOptions
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
    busyProfileId: String?,
    profileEditor: NativeProfileEditor?,
    profileEditorBusy: Boolean,
    perAppSnapshot: NativePerAppSnapshot,
    perAppBusy: Boolean,
    pendingPerAppImport: NativePerAppBackup?,
    logSnapshot: NativeLogSnapshot,
    logBusy: Boolean,
    coreOptions: NativeCoreOptions,
    coreOptionsBusy: Boolean,
    chainOptions: NativeChainOptions,
    chainBusy: Boolean,
    outboundGroups: List<NativeOutboundGroup>,
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
    onSaveCoreOptions: (NativeCoreOptions) -> Unit,
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
    onOpenLegacy: () -> Unit,
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
    var page by rememberSaveable { mutableStateOf(PAGE_HOME) }
    var regionalAppKind by rememberSaveable { mutableStateOf(NativeRegionalAppKind.DIRECT.name) }

    // Only internal page names are stored, so this trail also survives Activity recreation.
    var privacyExpanded by rememberSaveable { mutableStateOf("") }
    var pageTrail by rememberSaveable { mutableStateOf("") }
    var settingsCategory by rememberSaveable { mutableStateOf(NativeSettingsCategory.APP.name) }
    fun openPage(destination: String) {
        if (privacySetupBusy || proxyPrivacyBusy) return
        pageTrail += "|$page"
        page = destination
    }
    fun goBack() {
        if (privacySetupBusy || proxyPrivacyBusy) return
        if (page == PAGE_DIAGNOSTICS) onCancelDiagnostics()
        if (page == PAGE_PER_APP_BACKUP) onDismissPerAppImport()
        page = pageTrail.substringAfterLast('|', PAGE_HOME)
        pageTrail = pageTrail.substringBeforeLast('|', "")
    }
    fun openCategory(category: NativeSettingsCategory) {
        settingsCategory = category.name
        openPage(PAGE_PREFERENCES)
    }

    BackHandler(enabled = privacySetupBusy || proxyPrivacyBusy || page != PAGE_HOME) { goBack() }

    NativeAppTheme(themeMode) {
        NativeAtmosphere {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 8.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = if (page == PAGE_PRIVACY) 16.dp else 20.dp)) {
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
                                    onSelect = onSelectProfile,
                                    onDelete = onDeleteProfile,
                                    onRefresh = onRefreshProfile,
                                    onEdit = { profile ->
                                        onOpenProfileEditor(profile)
                                        openPage(PAGE_PROFILE_DETAILS)
                                    },
                                    onImport = onImportProfile,
                                    onCopyConfig = onCopyProfileConfig,
                                    onExportConfig = onExportProfileConfig,
                                )

                            PAGE_PROFILE_DETAILS ->
                                NativeProfileDetailsScreen(
                                    editor = profileEditor,
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
                                    busyTag = outboundBusyTag,
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
                                    options = coreOptions,
                                    busy = coreOptionsBusy,
                                    onBack = { goBack() },
                                    onSave = onSaveCoreOptions,
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
                                onOpenChain = { openPage(PAGE_CHAIN) },
                                onOpenLogs = { openPage(PAGE_LOGS) },
                                onOpenAbout = { openPage(PAGE_ABOUT) },
                                onOpenLegacy = onOpenLegacy,
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
                                onOpenPolicy = { openPage(PAGE_CONNECTION_POLICY) },
                                onOpenProxyPrivacy = { openPage(PAGE_PROXY_PRIVACY) },
                                onOpenProtection = {
                                    onRefreshVpnProtection()
                                    openPage(PAGE_PROTECTION)
                                },
                                onOpenFilters = { openPage(PAGE_TRAFFIC_FILTERS) },
                                onOpenCoreOptions = { openPage(PAGE_CORE_OPTIONS) },
                                onOpenCategory = { openCategory(it) },
                            )

                            PAGE_PREFERENCES ->
                                NativeSettingsScreen(
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
                                    serviceMode = settingsState.serviceMode,
                                    rootMode = rootMode,
                                    wifiSharing = settingsState.wifiSharing,
                                    systemStats = systemStats,
                                    onToggleConnection = onToggleConnection,
                                    onOpenProfiles = { openPage(PAGE_PROFILES) },
                                    onOpenSettings = { openCategory(NativeSettingsCategory.VPN) },
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
                                NavigationBarItem(selected = page == destination, enabled = !privacySetupBusy && !proxyPrivacyBusy, onClick = { pageTrail = ""; page = destination },
                                    icon = { Icon(painterResource(icon), contentDescription = null) },
                                    label = { Text(stringResource(label), maxLines = 1) })
                            }
                        }
                    }
                }
            }
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
    serviceMode: String,
    rootMode: Boolean,
    wifiSharing: Boolean,
    systemStats: NativeSystemStats,
    onToggleConnection: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenOutbounds: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.vetroff_app_logo), contentDescription = null, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.app_name), modifier = Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onOpenDiagnostics) {
                Icon(painterResource(R.drawable.native_shield), stringResource(R.string.native_diagnostics_title))
            }
            IconButton(onClick = onOpenProfiles) {
                Icon(painterResource(R.drawable.native_add), stringResource(R.string.native_profiles))
            }
        }
        NativeGlass(Modifier.fillMaxWidth().clickable(onClick = onOpenProfiles), radius = 24) {
            Text(if (hasActiveProfile) activeProfileName.ifBlank { stringResource(R.string.native_active_profile_unnamed) }
                else stringResource(R.string.native_no_profile_selected),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
        }
        ConnectionCard(
            status = status, recovering = recoveryAttempt > 0, internetHealth = internetHealth,
            onToggleConnection = onToggleConnection,
        )

        if (recoveryAttempt > 0) {
            Text(stringResource(R.string.native_recovery_attempt, recoveryAttempt))
        }
        if (status == Status.Started) {
            Text(stringResource(when (internetHealth) {
                NativeInternetHealth.UNCHECKED -> R.string.native_health_unchecked
                NativeInternetHealth.CHECKING -> R.string.native_health_checking
                NativeInternetHealth.AVAILABLE -> R.string.native_health_available
                NativeInternetHealth.UNAVAILABLE -> R.string.native_health_unavailable
            }), modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodyMedium)
        }
        NativeGlass(Modifier.fillMaxWidth()) {
            NativeSettingsLink(R.string.native_outbounds_open, R.drawable.native_route,
                onOpenOutbounds, enabled = status == Status.Started)
        }
        ConnectionStatsCard(systemStats)

        NativeGlass(Modifier.fillMaxWidth().clickable(onClick = onOpenSettings)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.native_settings), null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.native_quick_settings), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(when {
                        serviceMode != ServiceMode.VPN -> R.string.native_mode_proxy
                        rootMode -> R.string.native_mode_root
                        else -> R.string.native_mode_android_vpn
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (wifiSharing) Text(stringResource(R.string.native_wifi_sharing_on), style = MaterialTheme.typography.bodySmall)
                }
                Icon(painterResource(R.drawable.native_chevron), null, Modifier.rotate(270f))
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
            if (stats.currentOutbound.isNotBlank()) {
                Text(stats.currentOutbound, Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stats.trafficAvailable) {
                Text(stringResource(R.string.native_stats_total, formatTraffic(stats.uplinkTotal), formatTraffic(stats.downlinkTotal)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) {
    val label = stringResource(if (recovering) R.string.native_recovery_cancel else when (status) {
        Status.Stopped -> R.string.native_status_stopped
        Status.Starting -> R.string.native_status_starting
        Status.Started -> R.string.native_status_started
        Status.Stopping -> R.string.native_status_stopping
    })
    val actionLabel = stringResource(if (recovering) R.string.native_recovery_cancel else when (status) {
        Status.Stopped -> R.string.native_connect
        Status.Starting -> R.string.native_connecting
        Status.Started -> R.string.native_disconnect
        Status.Stopping -> R.string.native_disconnecting
    })
    val scheme = MaterialTheme.colorScheme
    val accent = when {
        status == Status.Started && internetHealth == NativeInternetHealth.UNAVAILABLE -> Color(0xFFFFC857)
        status == Status.Started -> scheme.tertiary
        else -> scheme.primary
    }
    val enabled = recovering || status == Status.Stopped || status == Status.Started
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Box(Modifier.fillMaxSize().padding(20.dp)) {
                HomeScreen(
                    status = Status.Started, internetHealth = NativeInternetHealth.AVAILABLE,
                    recoveryAttempt = 0, activeProfileName = "VetrOFF", hasActiveProfile = true,
                    serviceMode = ServiceMode.VPN, rootMode = false, wifiSharing = false,
                    systemStats = NativeSystemStats(downlink = 1258291, uplink = 52428, trafficAvailable = true),
                    onToggleConnection = {}, onOpenProfiles = {}, onOpenSettings = {},
                    onOpenDiagnostics = {}, onOpenOutbounds = {},
                )
            }
        }
    }
}

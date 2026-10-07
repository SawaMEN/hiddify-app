package com.hiddify.hiddify.nativeui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticSnapshot
import com.hiddify.hiddify.nativediagnostics.NativeVpnProtection
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

    BackHandler(enabled = page != PAGE_HOME) {
        if (page == PAGE_DIAGNOSTICS) onCancelDiagnostics()
        if (page == PAGE_PER_APP_BACKUP) onDismissPerAppImport()
        page =
            when (page) {
                PAGE_PER_APP -> PAGE_SETTINGS
                PAGE_PER_APP_BACKUP -> PAGE_PER_APP
                PAGE_PROFILE_DETAILS -> PAGE_PROFILES
                PAGE_CORE_OPTIONS -> PAGE_SETTINGS
                PAGE_CHAIN -> PAGE_SETTINGS
                PAGE_WIFI_GUIDE -> PAGE_SETTINGS
                PAGE_CONNECTION_POLICY -> PAGE_SETTINGS
                PAGE_REGIONAL_APPS -> PAGE_REGIONAL
                PAGE_REGIONAL -> PAGE_SETTINGS
                PAGE_TRAFFIC_FILTERS -> PAGE_SETTINGS
                PAGE_PROTECTION -> PAGE_SETTINGS
                else -> PAGE_HOME
            }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                when (page) {
                    PAGE_DIAGNOSTICS ->
                        NativeDiagnosticsScreen(
                            snapshot = diagnosticSnapshot,
                            busy = diagnosticBusy,
                            report = diagnosticReport,
                            onBack = {
                                onCancelDiagnostics()
                                page = PAGE_HOME
                            },
                            onRun = onRunDiagnostics,
                            onCancel = onCancelDiagnostics,
                            onShareReport = onShareDiagnosticReport,
                        )

                    PAGE_CONNECTION_POLICY -> NativeConnectionPolicyScreen(
                        options = connectionOptions,
                        busy = connectionOptionsBusy,
                        onBack = { page = PAGE_SETTINGS },
                        onSave = onSaveConnectionOptions,
                    )

                    PAGE_REGIONAL_APPS -> {
                        val kind = NativeRegionalAppKind.valueOf(regionalAppKind)
                        NativeRegionalAppsScreen(
                            kind = kind,
                            snapshot = regionalApps?.takeIf { it.kind == kind },
                            revision = regionalAppsRevision,
                            busy = regionalBusy,
                            onBack = { page = PAGE_REGIONAL },
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
                            onBack = { page = PAGE_SETTINGS },
                            onSave = onSaveRegionalOptions,
                            onOpenApps = { kind ->
                                regionalAppKind = kind.name
                                onOpenRegionalApps(kind)
                                page = PAGE_REGIONAL_APPS
                            },
                        )

                    PAGE_TRAFFIC_FILTERS ->
                        NativeTrafficFiltersScreen(
                            filters = trafficFilters,
                            busy = trafficFiltersBusy,
                            onBack = { page = PAGE_SETTINGS },
                            onSave = onSaveTrafficFilters,
                        )

                    PAGE_PROTECTION ->
                        NativeVpnProtectionScreen(
                            protection = vpnProtection,
                            onBack = { page = PAGE_SETTINGS },
                            onRefresh = onRefreshVpnProtection,
                            onOpenSettings = onOpenVpnSettings,
                        )

                    PAGE_PROFILES ->
                        NativeProfilesScreen(
                            profiles = profiles,
                            busyProfileId = busyProfileId,
                            onBack = { page = PAGE_HOME },
                            onSelect = onSelectProfile,
                            onDelete = onDeleteProfile,
                            onRefresh = onRefreshProfile,
                            onEdit = { profile ->
                                onOpenProfileEditor(profile)
                                page = PAGE_PROFILE_DETAILS
                            },
                            onImport = onImportProfile,
                            onCopyConfig = onCopyProfileConfig,
                            onExportConfig = onExportProfileConfig,
                        )

                    PAGE_PROFILE_DETAILS ->
                        NativeProfileDetailsScreen(
                            editor = profileEditor,
                            busy = profileEditorBusy,
                            onBack = { page = PAGE_PROFILES },
                            onSave = onSaveProfileEditor,
                        )

                    PAGE_PER_APP_BACKUP ->
                        NativePerAppBackupScreen(
                            busy = perAppBusy,
                            canImport = status == Status.Stopped,
                            pendingImport = pendingPerAppImport,
                            onBack = {
                                onDismissPerAppImport()
                                page = PAGE_PER_APP
                            },
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
                            onBack = { page = PAGE_SETTINGS },
                            onModeChanged = onPerAppModeChanged,
                            onTogglePackage = onTogglePerAppPackage,
                            onClear = onClearPerApp,
                            onOpenBackup = { page = PAGE_PER_APP_BACKUP },
                        )

                    PAGE_LOGS ->
                        NativeLogsScreen(
                            snapshot = logSnapshot,
                            busy = logBusy,
                            onBack = { page = PAGE_HOME },
                            onRefresh = onRefreshLogs,
                            onClear = onClearLogs,
                        )

                    PAGE_OUTBOUNDS ->
                        NativeOutboundsScreen(
                            groups = outboundGroups,
                            busyTag = outboundBusyTag,
                            onBack = { page = PAGE_HOME },
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
                            onBack = { page = PAGE_SETTINGS },
                            onRefresh = onRefreshWifiSharingDetails,
                        )

                    PAGE_ABOUT ->
                        NativeAboutScreen(
                            versionName = com.hiddify.hiddify.BuildConfig.VERSION_NAME,
                            versionCode = com.hiddify.hiddify.BuildConfig.VERSION_CODE,
                            updateChecking = updateChecking,
                            updateMessage = updateMessage,
                            updateUrl = updateUrl,
                            onBack = { page = PAGE_HOME },
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
                            onBack = { page = PAGE_SETTINGS },
                            onSave = onSaveCoreOptions,
                        )

                    PAGE_CHAIN ->
                        NativeChainScreen(
                            options = chainOptions,
                            profiles = profiles,
                            busy = chainBusy,
                            onBack = { page = PAGE_SETTINGS },
                            onSave = onSaveChainOptions,
                        )

                    PAGE_SETTINGS ->
                        NativeSettingsScreen(
                            state = settingsState,
                            canChangeServiceMode = status == Status.Stopped && !wifiSharingBusy,
                            wifiSharingBusy = wifiSharingBusy || status == Status.Starting || status == Status.Stopping,
                            wifiSharingDetails = wifiSharingDetails,
                            onBack = { page = PAGE_HOME },
                            onOpenVpnProtection = {
                                onRefreshVpnProtection()
                                page = PAGE_PROTECTION
                            },
                            onOpenConnectionPolicy = { page = PAGE_CONNECTION_POLICY },
                            onOpenRegionalRouting = { page = PAGE_REGIONAL },
                            onOpenTrafficFilters = { page = PAGE_TRAFFIC_FILTERS },
                            onOpenPerAppRouting = { page = PAGE_PER_APP },
                            onOpenCoreOptions = { page = PAGE_CORE_OPTIONS },
                            onOpenChain = { page = PAGE_CHAIN },
                            onOpenWifiSharingGuide = {
                                onRefreshWifiSharingDetails()
                                page = PAGE_WIFI_GUIDE
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
                            onOpenProfiles = { page = PAGE_PROFILES },
                            onOpenSettings = { page = PAGE_SETTINGS },
                            onOpenLogs = { page = PAGE_LOGS },
                            onOpenDiagnostics = { page = PAGE_DIAGNOSTICS },
                            onOpenAbout = { page = PAGE_ABOUT },
                            onOpenOutbounds = {
                                onRefreshOutbounds()
                                page = PAGE_OUTBOUNDS
                            },
                            onOpenLegacy = onOpenLegacy,
                        )
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
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenOutbounds: () -> Unit,
    onOpenLegacy: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        ConnectionCard(
            status = status,
            recovering = recoveryAttempt > 0,
            activeProfileName = activeProfileName,
            hasActiveProfile = hasActiveProfile,
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
            }), style = MaterialTheme.typography.bodyMedium)
            ConnectionStatsCard(systemStats)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.native_current_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        when {
                            serviceMode != ServiceMode.VPN ->
                                stringResource(R.string.native_mode_proxy)
                            rootMode ->
                                stringResource(R.string.native_mode_root)
                            else ->
                                stringResource(R.string.native_mode_android_vpn)
                        },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text =
                        if (wifiSharing) {
                            stringResource(R.string.native_wifi_sharing_on)
                        } else {
                            stringResource(R.string.native_wifi_sharing_off)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onOpenProfiles,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.native_profiles))
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.native_settings))
            }
        }

        OutlinedButton(
            onClick = onOpenOutbounds,
            enabled = status == Status.Started,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.native_outbounds_open))
        }

        OutlinedButton(
            onClick = onOpenLogs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.native_logs_title))
        }

        OutlinedButton(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.native_about_open))
        }

        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_diagnostics_title))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.native_migration_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpenLegacy,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.native_advanced_legacy))
        }
    }
}

@Composable
private fun ConnectionStatsCard(stats: NativeSystemStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.native_stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (stats.currentOutbound.isNotBlank()) {
                Text(
                    text = stringResource(R.string.native_stats_outbound, stats.currentOutbound),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (stats.trafficAvailable) {
                Text(
                    text =
                        stringResource(
                            R.string.native_stats_speed,
                            formatTraffic(stats.uplink),
                            formatTraffic(stats.downlink),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        stringResource(
                            R.string.native_stats_total,
                            formatTraffic(stats.uplinkTotal),
                            formatTraffic(stats.downlinkTotal),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.native_stats_connections,
                        stats.connectionsIn,
                        stats.connectionsOut,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    activeProfileName: String,
    hasActiveProfile: Boolean,
    onToggleConnection: () -> Unit,
) {
    val statusText =
        when (status) {
            Status.Stopped -> stringResource(R.string.native_status_stopped)
            Status.Starting -> stringResource(R.string.native_status_starting)
            Status.Started -> stringResource(R.string.native_status_started)
            Status.Stopping -> stringResource(R.string.native_status_stopping)
        }

    val buttonText = if (recovering) stringResource(R.string.native_recovery_cancel) else
        when (status) {
            Status.Started -> stringResource(R.string.native_disconnect)
            Status.Stopped -> stringResource(R.string.native_connect)
            Status.Starting -> stringResource(R.string.native_connecting)
            Status.Stopping -> stringResource(R.string.native_disconnecting)
        }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text =
                    if (hasActiveProfile) {
                        activeProfileName.ifBlank {
                            stringResource(R.string.native_active_profile_unnamed)
                        }
                    } else {
                        stringResource(R.string.native_no_profile_selected)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onToggleConnection,
                enabled = recovering || status == Status.Stopped || status == Status.Started,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonText)
            }
        }
    }
}

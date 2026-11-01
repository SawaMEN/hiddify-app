package com.hiddify.hiddify.bg

import com.hiddify.hiddify.nativecore.NativeCoreControl

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.libbox.PlatformInterface
import com.hiddify.core.mobile.Mobile
import com.hiddify.core.mobile.SetupOptions
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.R
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.Action
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.Status
import com.hiddify.hiddify.nativelog.NativeVpnProcessPhase
import java.io.File
import android.net.VpnService
import com.hiddify.hiddify.nativeconnection.NativeHealthProbePolicy
import com.hiddify.hiddify.nativeconnection.NativeHealthRepository
import com.hiddify.hiddify.nativeconnection.NativeInternetHealth
import com.hiddify.hiddify.nativeconnection.NativeStartupDeadline
import com.hiddify.hiddify.nativeconnection.NativeRecoveryPolicy
import com.hiddify.hiddify.nativeconnection.NativeServiceState
import com.hiddify.hiddify.nativecore.NativeStatsFeed
import com.hiddify.hiddify.privacy.NetworkPrivacySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BoxService(
    private val service: Service,
    private val platformInterface: PlatformInterface,
) {

    companion object {
        private const val TAG = "A/BoxService"

        private val lifecycleMutex = Mutex()
        @Volatile private var coreOwner: BoxService? = null
        private var initializeOnce = false
        private lateinit var workingDir: File

        private fun initialize() {
            if (initializeOnce) return

            com.hiddify.hiddify.nativecore.NativeCoreLibrary.ensureLoaded()

            // The Go LevelDB under workingDir/data needs reliable file locks. External app
            // storage (FUSE/scoped storage) is not suitable, even if canWrite() succeeds.
            // Use a separate private directory for non-root mode. Root mode already has
            // its own filesDir/root-core directory, with no shared database files.
            val baseDir = Application.application.filesDir.canonicalFile.apply { mkdirs() }
            workingDir = File(baseDir, "core-working").apply { mkdirs() }
            val tempDir = Application.application.cacheDir.canonicalFile.apply { mkdirs() }

            // Migrate legacy persisted paths on every first setup. Profile metadata is
            // stored separately; do not delete the old external working directory.
            Settings.baseDir = baseDir.path
            Settings.workingDir = workingDir.path
            Settings.tempDir = tempDir.path

            Log.d(TAG, "base dir: ${Settings.baseDir}")
            Log.d(TAG, "working dir: ${Settings.workingDir}")
            Log.d(TAG, "temp dir: ${Settings.tempDir}")

            for (path in listOf(Settings.baseDir, Settings.workingDir, Settings.tempDir)) {
                val directory = File(path)
                check((directory.isDirectory || directory.mkdirs()) && directory.canWrite()) { "unwritable core directory: $path" }
            }
            Libbox.redirectStderr(File(Settings.workingDir, "stderr.log").path)
            initializeOnce = true
        }

        suspend fun <T> withNativeLifecycle(action: suspend () -> T): T = lifecycleMutex.withLock { action() }

        fun currentPlatformInterface(): PlatformInterface? = coreOwner?.platformInterface

        fun isRunning(): Boolean = coreOwner?.status?.value in listOf(Status.Started, Status.Starting)

        fun isStarted(): Boolean = coreOwner?.status?.value == Status.Started

        // Unlike Activity/AIDL state, ownership is set before Mobile.setup() and cleared only
        // after Mobile.close() finishes. Use it as the native lifecycle barrier.
        fun hasActiveCore(): Boolean = coreOwner != null

        fun vpnProtection(): Map<String, Boolean?> {
            val vpn = coreOwner?.service as? VPNService
            return if (vpn != null) {
                mapOf(
                    "alwaysOn" to runCatching { vpn.isAlwaysOn }
                        .onFailure { Log.w(TAG, "Always-on VPN state unavailable", it) }.getOrNull(),
                    "lockdown" to runCatching { vpn.isLockdownEnabled }
                        .onFailure { Log.w(TAG, "VPN lockdown state unavailable", it) }.getOrNull(),
                )
            } else mapOf("alwaysOn" to null, "lockdown" to null)
        }

        fun refreshConnectionPolicy() { coreOwner?.let { owner -> owner.serviceScope.launch { owner.startConnectionMonitor() } } }

        fun start() {
            val intent = Intent(Application.application, Settings.serviceClass()).putExtra("started_by_app", true)
            Settings.connectionDesired = true
            Settings.startCoreAfterStartingService = true
            ContextCompat.startForegroundService(Application.application, intent)
        }

        fun stop(preserveIntent: Boolean = false) {
            // Publish intent before the broadcast. A pending start that is holding the native
            // lifecycle mutex must see cancellation even if the receiver runs later.
            if (!preserveIntent) Settings.connectionDesired = false
            Application.application.sendBroadcast(
                Intent(Action.SERVICE_CLOSE).setPackage(Application.application.packageName).putExtra("preserve_intent", preserveIntent),
            )
        }
    }

    @Volatile var fileDescriptor: ParcelFileDescriptor? = null

    private val failureBoundary = com.hiddify.hiddify.nativeconnection.NativeServiceFailureBoundary(
        onFailure = ::handleUnexpectedFailure,
        onFatal = { error ->
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), error)
                ?: throw error
        },
    )
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + failureBoundary)
    @Volatile private var destroyed = false
    private val status = MutableLiveData(Status.Stopped)
    private val binder = ServiceBinder(status)
    private val notification = ServiceNotification(service)
    private var receiverRegistered = false
    private var packagesReceiverRegistered = false
    private val packagesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val perApp = Settings.perAppProxyEnabled
            if (!perApp && (Settings.privacyRoutingMode == "off" || Settings.privacyFullTunnel)) return
            serviceScope.launch {
                try {
                    if (perApp) com.hiddify.hiddify.nativerouting.NativePerAppRepository(service).snapshot()
                    Settings.startCoreAfterStartingService = true
                    serviceReload()
                } catch (error: Exception) { Log.w(TAG, "Cannot update application routing after package change", error) }
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.SERVICE_CLOSE -> {
                    val preserveIntent = intent.getBooleanExtra("preserve_intent", false)
                    if (!preserveIntent) Settings.connectionDesired = false
                    stopService(preserveIntent)
                }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    serviceUpdateIdleMode()
                }
            }
        }
    }

    private var monitorJob: Job? = null
    private var recoveryJob: Job? = null
    private val recoveryPolicy = NativeRecoveryPolicy()
    private var lastGoodProfile: String? = null
    private var lastGoodOptions: String? = null
    private var connectedAt = 0L
    private var activeProfileName = ""
    @Volatile private var stopRequested = false
    @Volatile private var nativeStarting = false
    @Volatile private var nativeStartupTimedOut = false
    private val startCancellation = com.hiddify.hiddify.nativeconnection.NativeStartupCancellationTask(serviceScope)
    @Volatile private var profilePreparationCancellation: com.hiddify.hiddify.nativeprofile.NativeProfileImportCancellation? = null
    @Volatile private var rootActive = false
    private var rootMonitor: kotlinx.coroutines.Job? = null

    private suspend fun publishConnectionStatus(value: Status, @androidx.annotation.StringRes text: Int): Boolean =
        withContext(Dispatchers.Main) {
            // Stop/revoke runs on Main. Do not queue a postValue that can later overwrite
            // its Stopping/Stopped state after the user has cancelled the connection.
            if (destroyed || stopRequested || !Settings.connectionDesired) return@withContext false
            notification.show(activeProfileName, text)
            status.value = value
            true
        }

    private fun handleUnexpectedFailure(error: Exception) {
        Log.e(TAG, "Unexpected VPN service task failure; stopping the connection", error)
        if (destroyed || (coreOwner != null && coreOwner !== this)) return
        stopRequested = true
        cancelProfilePreparation()
        cancelNativeStartup()
        recoveryJob?.cancel(); recoveryJob = null
        monitorJob?.cancel(); monitorJob = null
        serviceScope.launch {
            com.hiddify.hiddify.nativelog.NativeCrashRecorder.recordServiceFailure(
                File(service.filesDir, "service-failure.log"), error,
            )
            lifecycleMutex.withLock {
                // An old service's failure must never stop its replacement's core.
                if (destroyed || (coreOwner != null && coreOwner !== this@BoxService)) return@withLock
                runCatching {
                    Settings.connectionDesired = false
                    Settings.startedByUser = false
                }.onFailure { Log.e(TAG, "Could not persist disconnect intent after service failure", it) }
                startCancellation.join()
                NativeServiceState.reset()
                NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
                runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
                    .onFailure { Log.w(TAG, "Hotspot cleanup after service failure failed", it) }
                runCatching { releaseNative("unexpected task failure") }
                    .onFailure { Log.e(TAG, "Native cleanup after service failure failed", it) }
                closeTun("unexpected task failure")
                withContext(Dispatchers.Main) {
                    if (destroyed) return@withContext
                    unregisterReceiver()
                    notification.close()
                    status.value = Status.Stopped
                    binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal,
                        error.message ?: error.javaClass.simpleName) }
                    runCatching { service.stopSelf() }
                        .onFailure { Log.e(TAG, "Android service stop after task failure failed", it) }
                }
            }
        }
    }

    private suspend fun finishCancelledStart(reason: String) {
        startCancellation.join()
        if (!Settings.connectionDesired) Settings.startedByUser = false
        NativeServiceState.reset()
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
        runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
        val closeError = runCatching { releaseNative(reason) }.exceptionOrNull()
        closeTun(reason)
        withContext(Dispatchers.Main) {
            if (destroyed) return@withContext
            unregisterReceiver()
            notification.close()
            if (closeError != null) {
                Log.e(TAG, "failed to close cancelled native start", closeError)
                binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
            }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    /**
     * A port can become occupied after the preflight socket check but before Go binds
     * it. Retry only that specific error, after closing the failed native start.
     * This is called under lifecycleMutex, so no second app-owned setup races us.
     */
    private suspend fun startWithGrpcPortFallback(
        cleanupFailedAttempt: suspend () -> Unit,
        setup: suspend () -> Unit,
    ) {
        for (attempt in 1..3) {
            if (destroyed || stopRequested || !Settings.connectionDesired)
                throw CancellationException("VPN startup cancelled")
            try {
                setup()
                return
            } catch (error: Exception) {
                if (attempt == 3 || !com.hiddify.hiddify.nativecore.NativeControlPortPolicy.isBindConflict(error))
                    throw error

                try {
                    cleanupFailedAttempt()
                } catch (cleanupError: Exception) {
                    // Never start a replacement if the previous runtime may still own its port.
                    error.addSuppressed(cleanupError)
                    throw error
                }

                val oldPort = Settings.grpcServiceModePort
                val newPort = Settings.prepareGrpcServicePort(excludedPorts = setOf(oldPort))
                com.hiddify.hiddify.utils.GrpcClientProvider.resetConnections()
                Log.w(TAG, "Local core port $oldPort was taken during setup; retrying on $newPort")
            }
        }
    }

    private suspend fun startService() {
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
        try {
            if (destroyed) return
            coreOwner?.takeIf { it !== this }?.let { previous ->
                previous.stopRequested = true
                previous.recoveryJob?.cancel(); previous.recoveryJob = null
                previous.monitorJob?.cancel(); previous.monitorJob = null
                // A concurrent Mobile.stop() issued by the old startup must finish before
                // a replacement calls Mobile.setup()/start() on the shared Go runtime.
                previous.startCancellation.join()
                previous.releaseNative("replacement")
                withContext(Dispatchers.Main) {
                    previous.unregisterReceiver()
                    previous.notification.close()
                    previous.status.value = Status.Stopped
                    previous.service.stopSelf()
                }
            }
            if (!Settings.connectionDesired || stopRequested) {
                finishCancelledStart("cancelled before setup")
                return
            }
            NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.LOADING_CORE)
            initialize()
            Log.d(TAG, "starting service")
            if (!publishConnectionStatus(Status.Starting, R.string.status_starting)) {
                finishCancelledStart("cancelled before preparing profile")
                return
            }

            // Temporary Opera credentials must be renewed even when starting from a tile,
            // always-on VPN or after a long period with the Activity closed.
            NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.PREPARING_PROFILE)
            withContext(Dispatchers.IO) {
                val repository = com.hiddify.hiddify.nativeprofile.NativeProfileRepository(service)
                repository.activeProfile()?.let { profile ->
                    if (com.hiddify.hiddify.nativeprofile.NativeOperaProfiles.region(profile.url) != null) {
                        repository.refreshRemote(profile.id)
                    }
                }
                val cancellation = com.hiddify.hiddify.nativeprofile.NativeProfileImportCancellation()
                profilePreparationCancellation = cancellation
                try {
                    if (stopRequested || destroyed || !Settings.connectionDesired) cancellation.cancel()
                    repository.repairLegacyWarp(cancellation)
                } finally { profilePreparationCancellation = null }
            }
            if (!Settings.connectionDesired || stopRequested) {
                finishCancelledStart("cancelled during profile renewal")
                return
            }
            val selectedConfigPath = Settings.activeConfigPath
            if (selectedConfigPath.isBlank()) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }
            if (!File(selectedConfigPath).isFile) {
                stopAndAlert(Alert.EmptyConfiguration, "Selected profile file is missing: refresh or re-import the profile")
                return
            }

            activeProfileName = Settings.activeProfileName

            if (!publishConnectionStatus(Status.Starting, R.string.status_starting)) {
                finishCancelledStart("cancelled before selecting native profile")
                return
            }

            if (!Settings.connectionDesired || stopRequested) {
                finishCancelledStart("cancelled before native setup")
                return
            }
            // Called only while no core is active. Avoid the old globally shared 17079
            // default and rotate an occupied per-install port before starting the server.
            Settings.prepareGrpcServicePort()
            coreOwner = this
            DefaultNetworkMonitor.start()
            if (Settings.privacyUseRoot) {
                rootActive = true
                NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.STARTING_CORE)
                startWithGrpcPortFallback(
                    cleanupFailedAttempt = { com.hiddify.hiddify.privacy.RootCore.stop() },
                    setup = { com.hiddify.hiddify.privacy.RootCore.start(service) },
                )
                if (!Settings.connectionDesired || stopRequested) {
                    finishCancelledStart("cancelled during root setup")
                    return
                }
                rootMonitor = serviceScope.launch {
                    while (com.hiddify.hiddify.privacy.RootCore.isAlive()) kotlinx.coroutines.delay(1000)
                    serviceScope.launch {
                        lifecycleMutex.withLock {
                            if (rootActive && !destroyed && coreOwner === this@BoxService) {
                                stopAndAlert(Alert.StartService, "Root core exited; connection stopped")
                            }
                        }
                    }
                }
            } else {
                Libbox.setMemoryLimit(!Settings.disableMemoryLimit)

                try {
                    NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.SETTING_UP_CORE)
                    startWithGrpcPortFallback(
                        cleanupFailedAttempt = { Mobile.close(4L) },
                    ) {
                        Mobile.setup(
                            SetupOptions().also {
                                val memoryClass = (service.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
                                it.memoryLimit = memoryClass.coerceIn(128, 512).toLong() * 1024L * 1024L
                                it.basePath = Settings.baseDir
                                it.workingDir = Settings.workingDir
                                it.tempDir = Settings.tempDir
                                // Required by the Go runtime on every supported Android version.
                                it.fixAndroidStack = true
                                it.mode = 4L
                                it.listen = "127.0.0.1:${Settings.grpcServiceModePort}"
                                it.secret = Settings.grpcAuthToken
                                it.debug = Settings.debugMode
                            },
                            platformInterface,
                        )
                    }
                    if (destroyed) return
                } catch (e: Exception) {
                    // setup() can fail after allocating native state. Always make a best-effort close
                    // before tearing down Android network callbacks so a later setup starts cleanly.
                    stopAndAlert(Alert.CreateService, e.message)
                    return
                }

                if (!Settings.connectionDesired || stopRequested) {
                    finishCancelledStart("cancelled during native setup")
                    return
                }
                try {
                    NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.APPLYING_SETTINGS)
                    NativeCoreControl.applyStoredSettings(service)
                } catch (error: Exception) {
                    stopAndAlert(Alert.CreateService, "Unable to apply core settings: ${error.message ?: error.javaClass.simpleName}")
                    return
                }
                val proxyPrivacy = com.hiddify.hiddify.privacy.NetworkPrivacySettings.loadProxyPrivacy(service)
                Mobile.applyDevicePrivacy(Settings.privacyFullTunnel,
                    Settings.privacyHideLocalProxy,
                    proxyPrivacy.hideClashApi, proxyPrivacy.disableSystemProxy, Settings.privacyEncryptedDns)
                val policy = org.json.JSONObject()
                com.hiddify.hiddify.privacy.RegionalRouting.policy(service).forEach { (key, value) ->
                    policy.put(key, if (value is List<*>) org.json.JSONArray(value) else value)
                }
                Mobile.applyRegionalPrivacy(policy.toString())
                if (Settings.startCoreAfterStartingService) {
                    // Stop() cancels the Go startup context before taking its lifecycle lock.
                    // It must be issued concurrently: waiting for our mutex first cannot abort Start().
                    nativeStarting = true
                    nativeStartupTimedOut = false
                    NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.STARTING_CORE)
                    // Mobile.start() is a blocking JNI call. A separate IO coroutine can
                    // cancel Go's startup context even when Psiphon bootstrap stalls.
                    val startupWatchdog = serviceScope.launch {
                        delay(NativeStartupDeadline.TIMEOUT_MS)
                        if (NativeStartupDeadline.shouldAbort(
                                starting = nativeStarting,
                                stopRequested = stopRequested,
                                connectionDesired = Settings.connectionDesired,
                                ownsCore = coreOwner === this@BoxService,
                            )) {
                            nativeStartupTimedOut = true
                            Log.e(TAG, "Native VPN core startup exceeded ${NativeStartupDeadline.TIMEOUT_MS} ms; cancelling")
                            // Cover the race where the JNI call has not installed its Go cancel context yet.
                            cancelNativeStartup()
                        }
                    }
                    try {
                        if (!Settings.connectionDesired || stopRequested) {
                            nativeStarting = false
                            finishCancelledStart("cancelled before native start")
                            return
                        }
                        startWithWarpEndpointRetry(selectedConfigPath)
                        if (nativeStartupTimedOut) error("VPN core connection timed out during startup (90 seconds); check Psiphon bootstrap/network logs")
                        // Mobile.start builds a StartRequest with disable_memory_limit=false,
                        // overriding the flag applied before setup. Restore the requested policy.
                        Libbox.setMemoryLimit(!Settings.disableMemoryLimit)
                        // A successful core start is not enough for Android VPN mode:
                        // when enable-tun is missing the core can start only proxy listeners.
                        // Never display Connected unless Android actually established a TUN.
                        if (service is VPNService && fileDescriptor == null) {
                            error("Android VPN interface was not created by the core (missing TUN inbound)")
                        }
                    } finally {
                        nativeStarting = false
                        startupWatchdog.cancel()
                    }
                    if (!Settings.connectionDesired || stopRequested) {
                        finishCancelledStart("cancelled during native start")
                        return
                    }
                }

            }

            if (destroyed) return
            if (stopRequested || !Settings.connectionDesired) {
                finishCancelledStart("cancelled before publishing started")
                return
            }
            Settings.nativeAppliedQuickSettings = Settings.quickSettingsSignature(service)
            Settings.nativeReconnectRequired = false
            NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.CONNECTED)
            if (!publishConnectionStatus(Status.Started, R.string.status_started)) {
                finishCancelledStart("cancelled while publishing connected state")
                return
            }
            notification.start()
            lastGoodProfile = Settings.activeConfigPath
            lastGoodOptions = Settings.configOptions
            connectedAt = android.os.SystemClock.elapsedRealtime()
            NativeServiceState.recovery(0)
            startConnectionMonitor()
        } catch (e: Exception) {
            if (stopRequested || !Settings.connectionDesired) finishCancelledStart("cancelled startup")
            else stopAndAlert(Alert.StartService, if (nativeStartupTimedOut) {
                "VPN core startup timed out after 90 seconds; inspect Psiphon and network logs"
            } else if (e is com.hiddify.hiddify.nativeprofile.NativeWarpGenerationException) {
                service.getString(R.string.native_free_warp_generation_failed, e.detail)
            } else e.message)
        }
    }

    fun serviceReload() {
        serviceScope.launch {
            try { serviceReload0() } catch (e: Exception) {
                Log.e(TAG, "reload failed", e)
                lifecycleMutex.withLock { stopAndAlert(Alert.StartService, e.message) }
            }
        }
    }

    suspend fun serviceReload0() {
        lifecycleMutex.withLock {
            if (destroyed || stopRequested || !Settings.connectionDesired || coreOwner !== this@BoxService) return
            monitorJob?.cancel(); monitorJob = null
            NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
            if (!publishConnectionStatus(Status.Starting, R.string.status_starting)) {
                finishCancelledStart("cancelled before reload")
                return
            }

            DefaultNetworkMonitor.detachCoreListener()
            val closeError = runCatching { closeCore() }.exceptionOrNull()
            if (closeError != null) {
                Log.e(TAG, "failed to close mobile core for reload", closeError)
                stopAndAlert(Alert.StartService, closeError.message)
                return
            }
            closeTun("reload")
            coreOwner = null
            runCatching { DefaultNetworkMonitor.stop() }
                .onFailure { Log.w(TAG, "failed to stop network monitor for reload", it) }
            startService()
        }
    }

    private fun serviceUpdateIdleMode() {
        serviceScope.launch {
            lifecycleMutex.withLock {
                runCatching {
                    if (!destroyed && coreOwner === this@BoxService && !rootActive &&
                        !Application.powerManager.isDeviceIdleMode) Mobile.wake()
                }.onFailure { Log.w(TAG, "failed to inspect idle state or wake mobile core", it) }
            }
        }
    }

    private fun cancelNativeStartup() {
        startCancellation.request(
            isStarting = { nativeStarting && coreOwner === this@BoxService && !rootActive },
            stopNative = {
                runCatching { Mobile.stop() }
                    .onFailure { Log.w(TAG, "native startup cancellation failed", it) }
            },
        )
    }

    private fun cancelProfilePreparation() {
        runCatching { profilePreparationCancellation?.cancel() }
            .onFailure { Log.w(TAG, "Could not cancel profile preparation", it) }
    }

    private fun stopService(preserveIntent: Boolean = false) {
        if (destroyed || status.value == Status.Stopping) return
        if (status.value == Status.Stopped && coreOwner !== this) return

        stopRequested = true
        cancelProfilePreparation()
        recoveryJob?.cancel(); recoveryJob = null
        monitorJob?.cancel(); monitorJob = null
        NativeServiceState.reset()
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
        status.value = Status.Stopping
        cancelNativeStartup()
        serviceScope.launch {
            lifecycleMutex.withLock {
                if (destroyed) return@withLock
                startCancellation.join()

                // Keep Android network discovery alive until gomobile has actually stopped using it.
                if (coreOwner !== this@BoxService) {
                    if (!preserveIntent) Settings.startedByUser = false
                    withContext(Dispatchers.Main) {
                        status.value = Status.Stopped
                        unregisterReceiver()
                        notification.close()
                        service.stopSelf()
                    }
                    return@withLock
                }
                if (!preserveIntent) runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
                    .onFailure { Log.w(TAG, "hotspot cleanup deferred", it) }
                DefaultNetworkMonitor.detachCoreListener()
                val closeError = runCatching { closeCore() }.exceptionOrNull()
                closeTun("stop")
                runCatching { DefaultNetworkMonitor.stop() }
                    .onFailure { Log.w(TAG, "failed to stop network monitor", it) }
                if (!preserveIntent) Settings.startedByUser = false

                if (closeError == null) {
                    coreOwner = null
                } else {
                    // Keep ownership until onDestroy retries the native close, but never keep a
                    // dead TUN/network monitor alive after the user requested a stop.
                    Log.e(TAG, "failed to close mobile core", closeError)
                }

                withContext(Dispatchers.Main) {
                    if (closeError != null) {
                        binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, closeError.message) }
                    }
                    unregisterReceiver()
                    notification.close()
                    status.value = Status.Stopped
                    service.stopSelf()
                }
            }
        }
    }

    private fun recoveryAllowed(type: Alert): Boolean {
        val options = NetworkPrivacySettings.loadConnection(service)
        val permission = service !is VPNService || Settings.privacyUseRoot || VpnService.prepare(service) == null
        // Never loop on a first-use error or on newly edited invalid configuration.
        return type == Alert.StartService && NativeRecoveryPolicy.canRecover(
            lastGoodProfile != null,
            lastGoodProfile == Settings.activeConfigPath && lastGoodOptions == Settings.configOptions,
            options.recoveryEnabled, Settings.connectionDesired, Settings.startedByUser, permission, stopRequested, destroyed)
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        if (destroyed) return
        if (stopRequested || !Settings.connectionDesired) {
            finishCancelledStart("cancelled before reporting error")
            return
        }
        monitorJob?.cancel(); monitorJob = null
        NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset()
        startCancellation.join()
        Log.e(TAG, "VPN/core failed ($type): ${message ?: "unknown error"}")
        val recover = recoveryAllowed(type) && recoveryPolicy.nextDelaySeconds(NetworkPrivacySettings.loadConnection(service).adaptiveNetwork) != null
        if (!recover) { Settings.connectionDesired = false; Settings.startedByUser = false }
        val closeError = runCatching { releaseNative("service error") }.exceptionOrNull()
        closeTun("service error")
        if (recover && closeError == null && Settings.connectionDesired && !stopRequested) {
            if (!publishConnectionStatus(Status.Starting, R.string.status_starting)) {
                finishCancelledStart("cancelled before recovery")
                return
            }
            scheduleServiceRecovery()
            return
        }
        Settings.connectionDesired = false
        Settings.startedByUser = false
        NativeServiceState.reset()
        runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
        withContext(Dispatchers.Main) {
            unregisterReceiver()
            notification.close()
            binder.broadcast { callback -> callback.onServiceAlert(type.ordinal, message ?: closeError?.message) }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    private fun scheduleServiceRecovery() {
        if (recoveryJob?.isActive == true) return
        recoveryJob = serviceScope.launch {
            try {
                while (isActive && !destroyed && !stopRequested && Settings.connectionDesired) {
                    val options = NetworkPrivacySettings.loadConnection(service)
                    val wait = recoveryPolicy.nextDelaySeconds(options.adaptiveNetwork)
                    if (!recoveryAllowed(Alert.StartService) || wait == null) {
                        lifecycleMutex.withLock {
                            stopAndAlert(Alert.StartService, service.getString(R.string.native_recovery_exhausted))
                        }
                        break
                    }
                    NativeServiceState.recovery(recoveryPolicy.attempts + 1)
                    if (!NativeHealthRepository().underlyingNetworkAvailable()) { delay(15_000); continue }
                    delay(wait * 1000)
                    lifecycleMutex.withLock {
                        if (!recoveryAllowed(Alert.StartService) || coreOwner != null || !isActive) return@withLock
                        recoveryPolicy.recordAttempt()
                        if (!publishConnectionStatus(Status.Starting, R.string.status_starting)) {
                            finishCancelledStart("cancelled before recovery start")
                            return@withLock
                        }
                        startService()
                    }
                    if (coreOwner === this@BoxService && status.value == Status.Started) break
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: Exception) {
                Log.e(TAG, "service recovery failed", error)
                lifecycleMutex.withLock {
                    stopAndAlert(Alert.CreateService, error.message)
                }
            } finally { NativeServiceState.recovery(0) }
        }
    }

    private fun startConnectionMonitor() {
        if (destroyed || stopRequested || !Settings.connectionDesired) return
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val repository = NativeHealthRepository()
            var coreFailures = 0
            var policy = NativeHealthProbePolicy(false)
            delay(3000)
            while (isActive && !destroyed && !stopRequested && Settings.connectionDesired &&
                coreOwner === this@BoxService && status.value == Status.Started) {
                try {
                if (android.os.SystemClock.elapsedRealtime() - connectedAt >= 60_000) recoveryPolicy.reset()
                val options = NetworkPrivacySettings.loadConnection(service)
                if (policy.adaptive != options.adaptiveNetwork) policy = NativeHealthProbePolicy(options.adaptiveNetwork)
                if (!repository.underlyingNetworkAvailable()) {
                    NativeServiceState.health(NativeInternetHealth.UNAVAILABLE)
                    coreFailures = 0
                    delay(15_000)
                    continue
                }
                NativeServiceState.health(NativeInternetHealth.CHECKING)
                val url = runCatching { com.hiddify.hiddify.nativecore.NativeGeneralOptionsRepository(service).load().testUrl }
                    .getOrDefault("https://www.gstatic.com/generate_204")
                val started = android.os.SystemClock.elapsedRealtime()
                val healthy = repository.probe(url, policy.timeoutSeconds)
                policy.record(healthy, android.os.SystemClock.elapsedRealtime() - started)
                NativeServiceState.health(policy.observedHealth())
                if (healthy) coreFailures = 0 else {
                    // Probe-site failure alone must never tear down a working VPN.
                    val responds = try { com.hiddify.hiddify.nativecore.NativeStatsRepository().load(); true }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { false }
                    coreFailures = if (responds) 0 else coreFailures + 1
                }
                if (options.recoveryEnabled && coreFailures >= 3 && recoveryAllowed(Alert.StartService)) {
                    serviceScope.launch {
                        try {
                            lifecycleMutex.withLock {
                                if (!destroyed && coreOwner === this@BoxService && !stopRequested && Settings.connectionDesired)
                                    stopAndAlert(Alert.StartService, "Core control channel is unavailable")
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                          catch (error: Exception) { Log.e(TAG, "Could not recover failed core control channel", error) }
                    }
                    break
                }
                delay(if (healthy) maxOf(60L, policy.intervalSeconds) * 1000 else policy.intervalSeconds * 1000)
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (error: Exception) {
                    // A transient Android connectivity/permission exception is not a
                    // fatal core error. Retry the health probe rather than crash the app.
                    Log.w(TAG, "Connection health monitor failed; retrying", error)
                    NativeServiceState.health(NativeInternetHealth.UNCHECKED)
                    coreFailures = 0
                    delay(15_000)
                }
            }
        }
    }

    private suspend fun closeCore() {
        NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.STOPPING_CORE)
        rootMonitor?.cancel(); rootMonitor = null
        try {
            if (rootActive) {
                com.hiddify.hiddify.privacy.RootCore.stop()
                rootActive = false
            } else {
                try { Mobile.close(4L) } catch (firstError: Exception) {
                    // Go closes the control server even when Stop returns a teardown error. A second
                    // idempotent close can release ownership instead of waiting for a bound service
                    // to be destroyed (stopSelf alone does not destroy a service still bound by UI).
                    Log.w(TAG, "retrying native close after teardown error", firstError)
                    try { Mobile.close(4L) } catch (retryError: Exception) {
                        firstError.addSuppressed(retryError)
                        throw firstError
                    }
                }
            }
            NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.STOPPED)
        } finally {
            // Connections to the previous server must not survive a core restart.
            com.hiddify.hiddify.utils.GrpcClientProvider.resetConnections()
        }
    }

    /** Probe through the core before accepting a built-in WARP endpoint. Stop between attempts,
     * keeping setup/RPC alive; no attempt falls back to the physical network for its probe. */
    private suspend fun startWithWarpEndpointRetry(configPath: String) {
        val repository = com.hiddify.hiddify.nativeprofile.NativeProfileRepository(service)
        val profile = repository.activeProfile()?.takeIf { !it.isRemote }
        val editor = profile?.let { repository.loadEditor(it.id) }
        val candidates = editor?.let {
            com.hiddify.hiddify.nativeprofile.NativeWarpEndpoints.candidates(it.content, it.name)
        }.orEmpty()
        if (candidates.isEmpty()) {
            Mobile.start(configPath, "")
            return
        }
        val selected = com.hiddify.hiddify.nativeconnection.NativeWarpStartupPolicy.select(
            candidates = candidates,
            checkDesired = {
                if (nativeStartupTimedOut) error(service.getString(R.string.native_warp_no_traffic))
                if (stopRequested || destroyed || !Settings.connectionDesired)
                    throw CancellationException("WARP startup cancelled")
            },
            start = { content ->
                // The core persists its StartRequest for restart/hot reload. A deleted
                // temporary path leaves an established WARP session unable to restart.
                NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.STARTING_CORE)
                Mobile.start("", content)
                Libbox.setMemoryLimit(!Settings.disableMemoryLimit)
                if (service is VPNService && fileDescriptor == null)
                    error("Android VPN interface was not created by the core (missing TUN inbound)")
            },
            probe = {
                NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.PROBING_WARP)
                NativeHealthRepository().probeStartingCore()
            },
            stop = {
                NativeVpnProcessDiagnostics.phase(NativeVpnProcessPhase.STOPPING_CORE)
                Mobile.stop()
                DefaultNetworkMonitor.detachCoreListener()
                closeTun("WARP endpoint retry")
            },
        ) ?: error(service.getString(R.string.native_warp_no_traffic))
        if (selected > 0) repository.saveEditedProfile(profile!!.id, candidates[selected], editor!!.name,
            editor.disableAutoUpdate, editor.updateIntervalHours, expectedLastUpdate = profile.lastUpdate)
    }

    private fun closeTun(reason: String) {
        runCatching { fileDescriptor?.close() }
            .onFailure { Log.w(TAG, "failed to close TUN during $reason", it) }
        fileDescriptor = null
    }

    @Suppress("SameReturnValue")
    internal fun onStartCommand(): Int {
        if (destroyed) return Service.START_NOT_STICKY
        if (status.value != Status.Stopped) return if (Settings.connectionDesired) Service.START_STICKY else Service.START_NOT_STICKY
        failureBoundary.reset()
        stopRequested = false
        status.value = Status.Starting
        try {
            // Android's foreground deadline starts before IO/setup, not after it.
            notification.show(Settings.activeProfileName, R.string.status_starting)
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(
                    service, receiver,
                    IntentFilter().apply {
                        addAction(Action.SERVICE_CLOSE)
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                receiverRegistered = true
                ContextCompat.registerReceiver(service, packagesReceiver, IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
                packagesReceiverRegistered = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "foreground registration failed", e)
            Settings.connectionDesired = false
            Settings.startedByUser = false
            binder.broadcast { it.onServiceAlert(Alert.StartService.ordinal, e.message ?: e.javaClass.simpleName) }
            status.value = Status.Stopped
            unregisterReceiver()
            notification.close()
            service.stopSelf()
            return Service.START_NOT_STICKY
        }
        serviceScope.launch {
            lifecycleMutex.withLock {
                if (destroyed) return@withLock
                try {
                    if (!Settings.connectionDesired) {
                        finishCancelledStart("cancelled before lifecycle start")
                        return@withLock
                    }
                    Settings.startedByUser = true
                    startService()
                } catch (e: Exception) {
                    Log.e(TAG, "service initialization failed", e)
                    stopAndAlert(Alert.CreateService, e.message)
                }
            }
        }
        return if (Settings.connectionDesired) Service.START_STICKY else Service.START_NOT_STICKY
    }

    // All owners share the mutex. An old onDestroy must never close a new owner's core.
    private suspend fun releaseNative(reason: String) {
        if (coreOwner !== this@BoxService) return
        DefaultNetworkMonitor.detachCoreListener()
        var closeError: Exception? = null
        try {
            closeCore()
        } catch (e: Exception) {
            closeError = e
            Log.e(TAG, "failed to close native core during $reason", e)
        } finally {
            // Android resources must always be released even when gomobile close fails. Keeping
            // the owner on failure lets onDestroy/replacement retry the native close later.
            closeTun(reason)
            runCatching { DefaultNetworkMonitor.stop() }
                .onFailure { Log.w(TAG, "network cleanup failed", it) }
            if (closeError == null) coreOwner = null
        }
        closeError?.let { throw it }
    }

    fun onBind(intent: Intent): IBinder = binder

    fun onDestroy() {
        if (destroyed) return
        stopRequested = true
        cancelProfilePreparation()
        cancelNativeStartup()
        destroyed = true
        recoveryJob?.cancel(); recoveryJob = null
        monitorJob?.cancel(); monitorJob = null
        if (coreOwner === this) { NativeServiceState.reset(); NativeStatsFeed.reset(); com.hiddify.hiddify.nativecore.NativePrimaryOutboundsFeed.reset() }
        unregisterReceiver()
        notification.destroy()
        binder.close()
        // Never block Android main, and do not cancel a blocking setup before its cleanup
        // has acquired the lifecycle lock and released native state.
        serviceScope.launch {
            try {
                lifecycleMutex.withLock {
                    startCancellation.join()
                    if (coreOwner === this@BoxService && !Settings.connectionDesired) {
                        runCatching { com.hiddify.hiddify.sharing.AutomaticHotspot.stop() }
                    }
                    releaseNative("destroy")
                    closeTun("destroy")
                }
            } catch (e: Exception) {
                Log.e(TAG, "destroy cleanup failed", e)
            } finally {
                serviceScope.cancel()
            }
        }
    }

    fun onRevoke() {
        Settings.connectionDesired = false
        stopService()
    }

    private fun unregisterReceiver() {
        if (packagesReceiverRegistered) {
            runCatching { service.unregisterReceiver(packagesReceiver) }
            packagesReceiverRegistered = false
        }
        if (!receiverRegistered) return
        runCatching { service.unregisterReceiver(receiver) }
            .onFailure { Log.w(TAG, "receiver was already unregistered", it) }
        receiverRegistered = false
    }

}

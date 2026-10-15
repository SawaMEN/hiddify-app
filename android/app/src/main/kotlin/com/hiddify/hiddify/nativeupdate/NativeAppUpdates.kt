package com.hiddify.hiddify.nativeupdate

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.apksig.ApkVerifier
import com.hiddify.hiddify.BuildConfig
import com.hiddify.hiddify.MainActivity
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeUpdateRepository
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-owned state; downloads belong to Android and survive Activity/process recreation. */
internal object NativeAppUpdates {
    const val OPEN_UPDATES = "open_app_updates"
    const val SKIP_UPDATE = "com.hiddify.hiddify.SKIP_UPDATE"
    private const val NOTIFICATION_ID = 4702
    private const val NOTIFICATION_CHANNEL = "app_updates"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val downloads get() = requireNotNull(context.getSystemService(DownloadManager::class.java)) { "Android DownloadManager is unavailable" }
    private val checkMutex = Mutex()
    private val downloadMutex = Mutex()
    private var pollJob: Job? = null
    private var foregroundCheckJob: Job? = null
    private val mutableState = MutableStateFlow(NativeAppUpdateState(channel =
        if (BuildConfig.CHANNEL == "prod") NativeUpdateChannel.STABLE else NativeUpdateChannel.DEV))
    val state = mutableState.asStateFlow()
    private var initialized = false

    private fun launchTask(block: suspend () -> Unit): Job = scope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutableState.update { it.copy(phase = NativeUpdatePhase.FAILED, error = error.message ?: error.javaClass.simpleName) }
        }
    }

    @Synchronized fun initialize(app: Context) {
        if (initialized) return
        initialized = true
        context = app.applicationContext
        val channel = runCatching { NativeUpdateChannel.valueOf(prefs.getString("channel", state.value.channel.name)!!) }
            .getOrDefault(state.value.channel)
        fun newer(release: NativeAppRelease) = isNewer(release, cachedOnly = true)
        val ongoing = cached("download_release")?.takeIf { prefs.getLong("download_id", -1) >= 0 && newer(it) }
        val saved = ongoing ?: cached("latest_${channel.name}")?.takeIf(::newer)
        mutableState.value = NativeAppUpdateState(channel = ongoing?.channel ?: channel, release = saved,
            skipped = saved != null && saved.identity == prefs.getString("skip_${channel.name}", null),
            autoDownload = prefs.getBoolean("auto_download", true),
            phase = when { ongoing != null -> NativeUpdatePhase.DOWNLOADING
                saved != null -> NativeUpdatePhase.AVAILABLE; else -> NativeUpdatePhase.IDLE })
        runCatching { NativeUpdateJob.schedule(context) }
            .onFailure { android.util.Log.w("AppUpdates", "Could not schedule update check", it) }
        launchTask {
            if (prefs.getLong("download_id", -1) >= 0) resumeDownload()
            // The Activity checks on every opening; background jobs run their own check.
        }
    }

    private fun isNewer(release: NativeAppRelease, cachedOnly: Boolean = false): Boolean {
        val digest = if (release.channel == NativeUpdateChannel.DEV && release.buildNumber == BuildConfig.VERSION_CODE)
            installedDigest(cachedOnly) else ""
        return NativeUpdatePolicy.newer(release, BuildConfig.VERSION_CODE, BuildConfig.UPDATE_BUILT_AT, digest)
    }

    private fun installedDigest(cachedOnly: Boolean): String {
        val apk = File(context.applicationInfo.sourceDir)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val key = "${apk.path}:${installed.lastUpdateTime}:${apk.length()}:${apk.lastModified()}"
        val saved = prefs.getString("installed_sha256", "").orEmpty()
        if (prefs.getString("installed_apk", null) == key && saved.matches(Regex("[a-f0-9]{64}"))) return saved
        // Startup only uses a cached digest; hashing a large APK always runs on the IO scope.
        if (cachedOnly) return ""
        return NativeUpdateArtifact.sha256(apk).also {
            prefs.edit().putString("installed_apk", key).putString("installed_sha256", it).apply()
        }
    }

    private fun cached(key: String): NativeAppRelease? = runCatching {
        prefs.getString(key, null)?.let(NativeReleaseStorage::decode)
    }.getOrNull()

    fun onForeground() {
        if (foregroundCheckJob?.isActive == true) return
        foregroundCheckJob = scope.launch {
            var checkOnOpen = true
            while (isActive) {
                try {
                    if (state.value.downloading) syncDownload()
                    else {
                        checkForUpdates(force = checkOnOpen)
                        checkOnOpen = false
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (error: Exception) { android.util.Log.w("AppUpdates", "Automatic check failed", error) }
                // The due policy throttles requests; retry after connectivity returns without reopening the app.
                delay(60_000)
            }
        }
    }
    fun onBackground() { foregroundCheckJob?.cancel(); foregroundCheckJob = null }
    fun checkNow() { launchTask { checkForUpdates(true) } }
    suspend fun checkForUpdates(force: Boolean) = checkMutex.withLock {
        val before = state.value
        if (before.downloading || before.phase == NativeUpdatePhase.READY) return@withLock
        val channel = before.channel
        val lastKey = "checked_${channel.name}"
        if (!force && !NativeUpdatePolicy.due(System.currentTimeMillis(), prefs.getLong(lastKey, 0))) return@withLock
        mutableState.update { it.copy(phase = NativeUpdatePhase.CHECKING, error = null) }
        try {
            val found = NativeUpdateRepository().latestCompatible(channel)
            currentCoroutineContext().ensureActive()
            if (state.value.channel != channel) return@withLock
            val release = found?.takeIf { isNewer(it) }
            val skipped = release != null && release.identity == prefs.getString("skip_${channel.name}", null)
            val accepted = downloadMutex.withLock {
                if (!state.value.isCurrentCheck(channel)) false else {
                    prefs.edit().putLong(lastKey, System.currentTimeMillis())
                        .putString("latest_${channel.name}", release?.let(NativeReleaseStorage::encode)).apply()
                    mutableState.update { it.copy(release = release, skipped = skipped,
                        phase = if (release == null) NativeUpdatePhase.IDLE else NativeUpdatePhase.AVAILABLE, error = null) }
                    true
                }
            }
            if (!accepted) return@withLock
            if (release != null && !skipped) {
                notifyRelease(release, ready = false)
                if (state.value.autoDownload) startDownload(automatic = true)
            }
        } catch (cancelled: CancellationException) {
            mutableState.update { if (it.channel == channel) it.checkCancelled() else it }
            throw cancelled
        }
        catch (error: Exception) {
            mutableState.update { if (it.isCurrentCheck(channel)) it.copy(phase = NativeUpdatePhase.FAILED,
                error = error.message ?: error.javaClass.simpleName) else it }
        }
    }

    fun selectChannel(channel: NativeUpdateChannel) { launchTask { downloadMutex.withLock {
        if (state.value.busy || channel == state.value.channel) return@withLock
        cancelDownloadLocked()
        prefs.edit().putString("channel", channel.name).apply()
        mutableState.update { NativeAppUpdateState(channel = channel, autoDownload = it.autoDownload) }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        checkNow()
    } } }

    fun setAutoDownload(value: Boolean) {
        prefs.edit().putBoolean("auto_download", value).apply()
        mutableState.update { it.copy(autoDownload = value) }
        if (value && state.value.release != null && !state.value.skipped && !state.value.busy && state.value.phase != NativeUpdatePhase.READY)
            launchTask { startDownload(automatic = true) }
    }

    fun skipVersion() { val identity = state.value.release?.identity; launchTask { skipVersionNow(identity) } }
    suspend fun skipVersionNow(identity: String?) = downloadMutex.withLock {
        val release = state.value.release ?: return@withLock
        if (identity != release.identity) return@withLock
        cancelDownloadLocked()
        check(prefs.edit().putString("skip_${release.channel.name}", release.identity).commit())
        mutableState.update { it.copy(skipped = true, phase = NativeUpdatePhase.AVAILABLE, error = null) }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun download() { launchTask { startDownload(automatic = false) } }
    private suspend fun startDownload(automatic: Boolean, expectedIdentity: String? = state.value.release?.identity) = downloadMutex.withLock {
        val current = state.value
        val release = current.release ?: return@withLock
        if (!current.canStartDownload(expectedIdentity, automatic)) return@withLock
        try {
            require(NativeUpdatePolicy.valid(release))
            val file = destination(release)
            file.parentFile?.mkdirs()
            if (file.exists()) check(file.delete()) { "Could not replace old APK" }
            val request = DownloadManager.Request(Uri.parse(release.url))
                .setTitle(context.getString(R.string.native_update_download_title, release.version))
                .setDescription(context.getString(R.string.native_update_background))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverRoaming(false).setAllowedOverMetered(!automatic)
                .apply { if (automatic) setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI) }
                .setDestinationUri(Uri.fromFile(file))
            val id = downloads.enqueue(request)
            if (!automatic) prefs.edit().remove("skip_${release.channel.name}").apply()
            // Commit before observing progress so a background completion can recover its metadata.
            if (!prefs.edit().putLong("download_id", id).putString("download_release", NativeReleaseStorage.encode(release)).commit()) {
                downloads.remove(id)
                error("Could not save update download")
            }
            mutableState.update { it.copy(phase = NativeUpdatePhase.DOWNLOADING, skipped = false,
                error = null, downloadedBytes = 0, totalBytes = release.size, waiting = automatic) }
            poll()
        } catch (error: Exception) {
            mutableState.update { it.copy(phase = NativeUpdatePhase.FAILED, error = error.message ?: error.javaClass.simpleName) }
        }
    }

    private suspend fun resumeDownload() {
        downloadMutex.withLock {
            // Reload under the same lock as skip/cancel: never restore an already cancelled task.
            if (prefs.getLong("download_id", -1) < 0) return@withLock
            val release = cached("download_release")
            if (release == null || !isNewer(release)) {
                cancelDownloadLocked()
                return@withLock
            }
            if (release.identity == prefs.getString("skip_${release.channel.name}", null)) {
                cancelDownloadLocked()
                mutableState.update { it.copy(skipped = true) }
                return@withLock
            }
            mutableState.update { it.copy(channel = release.channel, release = release, phase = NativeUpdatePhase.DOWNLOADING,
                totalBytes = release.size, skipped = false) }
        }
        syncDownload()
        if (state.value.downloading) poll()
    }

    private fun poll() {
        if (pollJob?.isActive == true) return
        pollJob = launchTask {
            while (currentCoroutineContext().isActive && state.value.downloading) { syncDownload(); delay(1000) }
        }
    }

    suspend fun syncDownload() = downloadMutex.withLock {
        val id = prefs.getLong("download_id", -1)
        if (id < 0) return@withLock
        val release = cached("download_release")
        if (release == null) {
            cancelDownloadLocked()
            mutableState.update { it.copy(phase = NativeUpdatePhase.FAILED, error = context.getString(R.string.native_update_invalid_apk)) }
            return@withLock
        }
        if (!isNewer(release)) {
            cancelDownloadLocked()
            return@withLock
        }
        try {
            downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                check(cursor != null && cursor.moveToFirst()) { context.getString(R.string.native_update_download_removed) }
                fun number(key: String) = cursor.getLong(cursor.getColumnIndexOrThrow(key))
                when (number(DownloadManager.COLUMN_STATUS).toInt()) {
                    DownloadManager.STATUS_FAILED -> error(context.getString(R.string.native_update_download_failed,
                        number(DownloadManager.COLUMN_REASON)))
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        mutableState.update { it.copy(phase = NativeUpdatePhase.VERIFYING, error = null) }
                        verify(release)
                        mutableState.update { it.copy(phase = NativeUpdatePhase.READY, downloadedBytes = release.size,
                            totalBytes = release.size, waiting = false, error = null) }
                        notifyRelease(release, ready = true)
                    }
                    else -> mutableState.update { it.copy(phase = NativeUpdatePhase.DOWNLOADING,
                        downloadedBytes = number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR).coerceAtLeast(0),
                        totalBytes = release.size, waiting = number(DownloadManager.COLUMN_STATUS).toInt() != DownloadManager.STATUS_RUNNING) }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            runCatching { downloads.remove(id) }
            prefs.edit().remove("download_id").remove("download_release").apply()
            mutableState.update { it.copy(phase = NativeUpdatePhase.FAILED, error = error.message ?: error.javaClass.simpleName) }
        }
    }

    fun cancelDownload() { launchTask { downloadMutex.withLock { cancelDownloadLocked() } } }
    private fun cancelDownloadLocked() {
        pollJob?.cancel(); pollJob = null
        val id = prefs.getLong("download_id", -1)
        if (id >= 0) runCatching { downloads.remove(id) }
            .onFailure { android.util.Log.w("AppUpdates", "Could not remove Android download", it) }
        prefs.edit().remove("download_id").remove("download_release").apply()
        mutableState.update { it.copy(phase = if (it.release == null) NativeUpdatePhase.IDLE else NativeUpdatePhase.AVAILABLE,
            downloadedBytes = 0, waiting = false) }
    }

    private fun destination(release: NativeAppRelease): File = File(
        requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), "updates/${release.assetId}.apk")

    private fun verify(release: NativeAppRelease): File {
        val file = destination(release)
        check(NativeUpdateArtifact.matches(file, release)) { context.getString(R.string.native_update_invalid_apk) }
        val verified = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(android.os.Build.VERSION.SDK_INT).build().verify()
        check(verified.isVerified) { context.getString(R.string.native_update_invalid_apk) }
        val pm = context.packageManager
        val apk = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        check(apk != null && apk.packageName == context.packageName && apk.longVersionCode == release.buildNumber.toLong() &&
            apk.longVersionCode >= BuildConfig.VERSION_CODE && (apk.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= android.os.Build.VERSION.SDK_INT) {
            context.getString(R.string.native_update_invalid_apk)
        }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet().orEmpty()
        val incoming = verified.signerCertificates.map { it.encoded.toList() }.toSet()
        check(installed.isNotEmpty() && installed == incoming) { context.getString(R.string.native_update_signer_mismatch) }
        return file
    }

    suspend fun installationFile(): File = downloadMutex.withLock {
        check(state.value.phase == NativeUpdatePhase.READY)
        try { verify(requireNotNull(state.value.release)) }
        catch (error: Exception) {
            cancelDownloadLocked()
            mutableState.update { it.copy(phase = NativeUpdatePhase.FAILED, error = error.message ?: error.javaClass.simpleName) }
            throw error
        }
    }

    private fun notifyRelease(release: NativeAppRelease, ready: Boolean) {
        runCatching { notifyReleaseIfAllowed(release, ready) }
            .onFailure { android.util.Log.w("AppUpdates", "Update notification unavailable", it) }
    }

    private fun notifyReleaseIfAllowed(release: NativeAppRelease, ready: Boolean) {
        if (state.value.skipped || state.value.release?.identity != release.identity) return
        val key = "${if (ready) "ready_notified" else "notified"}_${release.channel.name}"
        if (!NativeUpdatePolicy.notify(release.identity, prefs.getString("skip_${release.channel.name}", null), prefs.getString(key, null))) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, context.getString(R.string.native_about_updates), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 4702, Intent(context, MainActivity::class.java).putExtra(OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val skip = PendingIntent.getBroadcast(context, 4703, Intent(context, NativeUpdateReceiver::class.java).setAction(SKIP_UPDATE).putExtra("identity", release.identity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL).setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(context.getString(if (ready) R.string.native_update_ready else R.string.native_about_update_available, release.version))
            .setContentText(context.getString(R.string.native_update_notification_hint)).setContentIntent(open).setAutoCancel(true)
            .setOnlyAlertOnce(true).addAction(0, context.getString(R.string.native_update_skip), skip).build()
        try { manager.notify(NOTIFICATION_ID, notification); prefs.edit().putString(key, release.identity).apply() }
        catch (_: SecurityException) { /* The main-screen notice works without notification permission. */ }
    }
}

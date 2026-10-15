package com.hiddify.hiddify.nativerouting

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class NativeInstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
)

/** Lists enabled Internet-capable packages without touching routing preferences or the database. */
class NativeInstalledApps(private val context: Context) {
    fun load(): List<NativeInstalledApp> = snapshots.get(
        context.packageName to context.resources.configuration.locales.toLanguageTags(),
    ) { loadUncached() }

    private fun loadUncached(): List<NativeInstalledApp> {
        val pm = context.packageManager
        val permissionFlag = PackageManager.GET_PERMISSIONS
        val packages =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(permissionFlag.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(permissionFlag)
            }

        return packages
            .asSequence()
            .filter { pkg ->
                val info = pkg.applicationInfo
                pkg.packageName != context.packageName &&
                    info?.enabled == true &&
                    (pkg.packageName == "android" ||
                        pkg.requestedPermissions?.contains(Manifest.permission.INTERNET) == true)
            }
            .map { pkg ->
                val info = requireNotNull(pkg.applicationInfo)
                NativeInstalledApp(
                    packageName = pkg.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrElse { pkg.packageName },
                    system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    companion object {
        private val snapshots = NativeTimedSnapshotCache<Pair<String, String>, List<NativeInstalledApp>>(
            lifetimeMs = 30_000,
            clockMs = android.os.SystemClock::elapsedRealtime,
        )

        fun invalidate() = snapshots.invalidate()

        /** The receiver only invalidates; package-manager work stays on repository IO callers. */
        fun observePackageChanges(context: Context) {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: android.content.Intent?) = invalidate()
            }
            try {
                androidx.core.content.ContextCompat.registerReceiver(
                    context.applicationContext, receiver, filter,
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
                )
            } catch (error: RuntimeException) {
                // The short TTL remains a fallback if this device rejects registration.
                android.util.Log.w("A/InstalledApps", "Unable to observe package changes", error)
            }
        }
    }
}

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
    fun load(): List<NativeInstalledApp> {
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

}

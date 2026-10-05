package com.hiddify.hiddify.privacy

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.SecureRandom
import java.security.Signature
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Random package + manifest rewriting + new APK signature, as in Magisk AppMigration.
 * Uses Android's installer; root is not needed. Never removes the original application. */
object PackageIdentity {
    private const val PREFS = "package_identity"
    private const val LIMIT = 256L * 1024 * 1024
    private fun preferences(activity: Activity) = activity.getSharedPreferences(PREFS, 0)
    private fun identity(activity: Activity) = SoftwareSigningIdentity.loadOrCreate(activity.applicationContext)

    private fun matchesCertificate(activity: Activity, pkg: String): Boolean = runCatching {
        val expected = identity(activity).certificate.encoded
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= 28)
            activity.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners
        else activity.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures!!
        signatures.any { it.toByteArray().contentEquals(expected) }
    }.getOrDefault(false)

    fun status(activity: Activity): Map<String, Any> {
        val pkg = preferences(activity).getString("target", "").orEmpty()
        val original = activity.getSharedPreferences("FlutterSharedPreferences", 0).getString("identity_original", "").orEmpty()
        return mapOf("package" to activity.packageName, "target" to pkg,
            "installed" to (pkg.isNotEmpty() && matchesCertificate(activity, pkg)), "original" to original)
    }

    fun generatePackageName(): String = "app." + (1..2).joinToString(".") {
        buildString { repeat(12) { append(('a'.code + SecureRandom().nextInt(26)).toChar()) } }
    }

    fun prepare(activity: Activity, label: String, databaseSnapshot: String, style: String = "meet", requestedPackage: String = ""): String {
        require(label.isNotBlank() && label.length <= 40) { "Application name must contain 1–40 characters" }
        require(activity.applicationInfo.splitSourceDirs.isNullOrEmpty()) { "Use the standalone arm64 APK to rename the package; split APK installations are unsupported" }
        val snapshot = File(databaseSnapshot)
        require(snapshot.isFile && snapshot.canonicalPath.startsWith(activity.cacheDir.canonicalPath + File.separator)) { "Invalid database snapshot" }
        val icon = when (style) {
            "meet" -> com.hiddify.hiddify.R.drawable.ic_privacy_meet
            "notes" -> com.hiddify.hiddify.R.drawable.ic_privacy_notes
            else -> error("Unknown private appearance")
        }
        val identity = identity(activity)
        val prefs = preferences(activity)
        val pkg = requestedPackage.ifBlank { prefs.getString("target", null) ?: generatePackageName() }
        require(pkg.matches(Regex("app\\.[a-z]{12}\\.[a-z]{12}")) && pkg != activity.packageName) { "Invalid generated package" }

        val folder = File(activity.cacheDir, "privacy").apply { mkdirs() }
        val unsigned = File(folder, "unsigned.apk")
        val signed = File(folder, "application.apk")
        val oldPackage = activity.packageName
        val oldLabel = activity.applicationInfo.loadLabel(activity.packageManager).toString()
        unsigned.delete()
        signed.delete()
        // Keep original compression; mmap-able libraries are aligned to 16 KiB.
        ZipFile(activity.applicationInfo.sourceDir).use { source ->
            CountingOutput(unsigned.outputStream()).use { counted ->
                ZipOutputStream(counted).use { output ->
                    source.entries().asSequence().forEach { entry ->
                        val upperName = entry.name.uppercase(java.util.Locale.ROOT)
                        val signatureEntry = upperName.startsWith("META-INF/") &&
                            (upperName == "META-INF/MANIFEST.MF" || upperName.endsWith(".SF") ||
                             upperName.endsWith(".RSA") || upperName.endsWith(".DSA") || upperName.endsWith(".EC"))
                        if (entry.isDirectory || signatureEntry) return@forEach
                        val patch = entry.name == "resources.arsc" || entry.name == "AndroidManifest.xml" || entry.name.startsWith("res/") && entry.name.endsWith(".xml")
                        val bytes = if (patch) source.getInputStream(entry).use { it.readBytes() } else null
                        var modified = if (entry.name == "resources.arsc" && bytes != null)
                            BinaryXml.renameResourcePackage(bytes, oldPackage, pkg)
                        else if (bytes != null && bytes.size >= 8 && bytes[0] == 3.toByte() && bytes[1] == 0.toByte())
                            BinaryXml.rewrite(bytes) { value ->
                                when {
                                    value == oldPackage || value.startsWith("$oldPackage.") -> pkg + value.removePrefix(oldPackage)
                                    entry.name == "AndroidManifest.xml" && value == oldLabel -> label
                                    else -> value
                                }
                            } else bytes
                        if (entry.name == "AndroidManifest.xml" && modified != null) modified = BinaryXml.setApplicationIcon(modified, icon)
                        val next = ZipEntry(entry.name)
                        next.time = 946684800000L
                        next.method = entry.method
                        if (next.method == ZipEntry.STORED) {
                            next.size = modified?.size?.toLong() ?: entry.size
                            next.crc = modified?.let { CRC32().apply { update(it) }.value } ?: entry.crc
                            val alignment = if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) 16384 else 4
                            val base = counted.count + 30 + next.name.toByteArray(Charsets.UTF_8).size
                            var padding = ((alignment - base % alignment) % alignment).toInt()
                            if (padding in 1..3) padding += alignment
                            if (padding > 0) next.extra = ByteArray(padding).apply {
                                this[0] = 0xff.toByte(); this[1] = 0xff.toByte()
                                this[2] = ((padding - 4) and 0xff).toByte(); this[3] = ((padding - 4) shr 8).toByte()
                            }
                        }
                        output.putNextEntry(next)
                        if (modified != null) output.write(modified) else source.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
            }
        }
        try {
            val signer = ApkSigner.SignerConfig.Builder("application", identity.privateKey, listOf(identity.certificate)).build()
            try {
                ApkSigner.Builder(listOf(signer)).setInputApk(unsigned).setOutputApk(signed)
                    .setMinSdkVersion(23)
                    .setAlignmentPreserved(true)
                    .setLibraryPageAlignmentBytes(16384)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .build().sign()
            } catch (error: Exception) {
                signed.delete()
                throw IllegalStateException("APK signing failed: ${error.causeChain()}", error)
            }
            val verification = ApkVerifier.Builder(signed).setMinCheckedPlatformVersion(23).build().verify()
            check(verification.isVerified) {
                val errors = verification.errors.joinToString("; ") { it.toString() }
                "Repacked APK signature verification failed${if (errors.isBlank()) "" else ": $errors"}"
            }
            check(verification.signerCertificates.any { it.encoded.contentEquals(identity.certificate.encoded) }) {
                "Repacked APK was signed by an unexpected certificate"
            }
            check(activity.packageManager.getPackageArchiveInfo(signed.path, 0)?.packageName == pkg) { "Repacked package verification failed" }
            val archive = File(folder, "migration.zip")
            archive.delete()
            createMigration(activity, archive, snapshot)
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(identity.privateKey)
                archive.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        update(buffer, 0, read)
                    }
                }
            }.sign()
            check(prefs.edit().putString("target", pkg).putString("migration_signature", Base64.encodeToString(signature, Base64.NO_WRAP)).commit())
            return pkg
        } finally {
            unsigned.delete()
            snapshot.delete()
        }
    }

    fun install(activity: Activity) {
        val apk = File(activity.cacheDir, "privacy/application.apk")
        check(apk.isFile) { "Create the private copy first" }
        val identity = identity(activity)
        val verification = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(23).build().verify()
        check(verification.isVerified && verification.signerCertificates.any { it.encoded.contentEquals(identity.certificate.encoded) }) {
            "Private APK signature is invalid; create the private copy again"
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.privacy.files", apk)
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun launch(activity: Activity) {
        val prefs = preferences(activity)
        val pkg = prefs.getString("target", "").orEmpty()
        check(matchesCertificate(activity, pkg)) { "Install the verified private copy first" }
        val archive = File(activity.cacheDir, "privacy/migration.zip")
        check(archive.isFile) { "Migration archive is missing; create the copy again" }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.privacy.files", archive)
        val launch = activity.packageManager.getLaunchIntentForPackage(pkg) ?: error("Private copy cannot be opened")
        activity.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch.putExtra("identity_migration", uri).putExtra("identity_signature", prefs.getString("migration_signature", ""))
            .putExtra("identity_original", activity.packageName).putExtra("identity_data_dir", activity.applicationInfo.dataDir)
            .putExtra("identity_external_dir", activity.getExternalFilesDir(null)?.path.orEmpty())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(launch)
    }

    private fun createMigration(activity: Activity, archive: File, snapshot: File) {
        ZipOutputStream(archive.outputStream()).use { output ->
            val metadata = org.json.JSONObject().put("original", activity.packageName)
                .put("dataDir", activity.applicationInfo.dataDir).put("externalDir", activity.getExternalFilesDir(null)?.path.orEmpty())
            output.putNextEntry(ZipEntry("identity_metadata.json")); output.write(metadata.toString().toByteArray()); output.closeEntry()
            fun add(root: File, prefix: String) {
                if (!root.exists()) return
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    if (relative.startsWith("root-core/") || relative == "root-launch.json" || relative == "db.sqlite" || relative.startsWith("db.sqlite-") || relative.endsWith(".log") ||
                        relative.endsWith(".p12") || relative.contains("local_control")) return@forEach
                    output.putNextEntry(ZipEntry("$prefix/$relative")); file.inputStream().use { it.copyTo(output) }; output.closeEntry()
                }
            }
            val data = File(activity.applicationInfo.dataDir)
            add(File(data, "app_flutter"), "app_flutter")
            add(File(data, "files"), "files")
            // Core caches/databases are recreated; only profile sources are migrated from external storage.
            activity.getExternalFilesDir(null)?.let { add(File(it, "configs"), "external/configs") }
            activity.getSharedPreferences("FlutterSharedPreferences", 0).edit().commit()
            val prefs = File(data, "shared_prefs/FlutterSharedPreferences.xml")
            if (prefs.isFile) {
                output.putNextEntry(ZipEntry("shared_prefs/FlutterSharedPreferences.xml"))
                prefs.inputStream().use { it.copyTo(output) }; output.closeEntry()
            }
            output.putNextEntry(ZipEntry("app_flutter/db.sqlite")); snapshot.inputStream().use { it.copyTo(output) }; output.closeEntry()
        }
        check(archive.length() <= LIMIT) { "Migration archive is too large" }
    }

    /** Called before Flutter creates preferences/database connections. A migration is accepted
     * only when signed by this installed APK's own certificate, and only on a fresh copy. */
    fun importMigration(activity: Activity) {
        val uri = if (Build.VERSION.SDK_INT >= 33) activity.intent.getParcelableExtra("identity_migration", Uri::class.java)
            else @Suppress("DEPRECATION") activity.intent.getParcelableExtra<Uri>("identity_migration")
        if (uri == null) return
        val ready = File(activity.filesDir, "identity_migrated")
        if (ready.exists()) return
        val data = File(activity.applicationInfo.dataDir)
        check(!File(data, "app_flutter/db.sqlite").exists()) { "Migration cannot overwrite an existing profile database" }
        val temporary = File(activity.cacheDir, "identity_import.zip")
        val staging = File(activity.cacheDir, "identity_staging")
        val copied = mutableListOf<File>()
        try {
            activity.contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { out ->
                val buffer=ByteArray(65536); var total=0L
                while(true) { val n=input.read(buffer); if(n<0) break; total+=n; check(total<=LIMIT); out.write(buffer,0,n) }
            } }
            val signatureValue = activity.intent.getStringExtra("identity_signature").orEmpty()
            check(signatureValue.isNotBlank()) { "Missing migration signature" }
            val signatureBytes = try {
                Base64.decode(signatureValue, Base64.DEFAULT)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("Invalid migration signature encoding", error)
            }
            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= 28)
                activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners
            else activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES).signatures!!
            val certificateFactory = java.security.cert.CertificateFactory.getInstance("X.509")
            val signatureValid = signatures.any { packageSignature ->
                runCatching {
                    val cert = certificateFactory.generateCertificate(packageSignature.toByteArray().inputStream())
                    Signature.getInstance("SHA256withRSA").apply {
                        initVerify(cert)
                        temporary.inputStream().use { input ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                update(buffer, 0, read)
                            }
                        }
                    }.verify(signatureBytes)
                }.getOrDefault(false)
            }
            check(signatureValid) { "Invalid migration signature" }
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Unable to stage migration" }
            var total = 0L
            var metadata: org.json.JSONObject? = null
            ZipFile(temporary).use { zip ->
                val meta = zip.getEntry("identity_metadata.json") ?: error("Missing migration metadata")
                check(meta.size in 1..4096)
                metadata = org.json.JSONObject(zip.getInputStream(meta).use { it.reader().readText() })
                zip.entries().asSequence().forEach { entry ->
                    if (entry.name == "identity_metadata.json") return@forEach
                    check(!entry.isDirectory && !entry.name.startsWith("/") && entry.name.split('/').none { it == ".." })
                    val external = entry.name.startsWith("external/configs/")
                    check(external || entry.name.startsWith("app_flutter/") || entry.name.startsWith("files/") || entry.name == "shared_prefs/FlutterSharedPreferences.xml")
                    val root = staging
                    val dest = File(root, entry.name)
                    check(dest.canonicalPath.startsWith(root.canonicalPath+File.separator))
                    dest.parentFile!!.mkdirs()
                    zip.getInputStream(entry).use { input -> dest.outputStream().use { out ->
                        val b=ByteArray(65536); while(true) { val n=input.read(b); if(n<0) break; total+=n; check(total<=LIMIT); out.write(b,0,n) }
                    } }
                }
            }
            check(File(staging, "app_flutter/db.sqlite").isFile) { "Missing profile database" }
            staging.walkTopDown().filter { it.isFile }.forEach { source ->
                val relative = source.relativeTo(staging).invariantSeparatorsPath
                val external = relative.startsWith("external/")
                val root = if (external) activity.getExternalFilesDir(null) ?: error("External storage unavailable") else data
                val target = File(root, if (external) relative.removePrefix("external/") else relative)
                check(!target.exists()) { "Migration would overwrite existing data" }
                target.parentFile!!.mkdirs(); copied.add(target); source.copyTo(target)
            }
            val prefFile = File(data, "shared_prefs/FlutterSharedPreferences.xml")
            if (prefFile.isFile) {
                var text = prefFile.readText()
                val oldData = metadata!!.getString("dataDir")
                val oldExternal = metadata!!.getString("externalDir")
                if(oldData.isNotEmpty()) text=text.replace(oldData, data.path)
                if(oldExternal.isNotEmpty()) text=text.replace(oldExternal, activity.getExternalFilesDir(null)!!.path)
                prefFile.writeText(text)
            }
            // Keep both reservations open until distinct ports have been persisted. The
            // original application may still own its foreground control listener.
            java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { front ->
                java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { back ->
                    check(activity.getSharedPreferences("FlutterSharedPreferences", 0).edit()
                        .remove("privacy_root_route_table").remove("local_control_token").remove("grpc_flutter_public_key").remove("flutter.grpc_flutter_public_key")
                        .putInt("local_control_front_port", front.localPort)
                        .putInt("local_control_back_port", back.localPort)
                        .putInt(com.hiddify.hiddify.constant.SettingsKey.GRPC_PORT, back.localPort)
                        .putBoolean("flutter.connection_desired", false).putBoolean("flutter.started_by_user", false)
                        .putString("identity_original", metadata!!.getString("original")).commit()) {
                        "Unable to save migrated preferences"
                    }
                }
            }
            ready.writeText("1")
        } catch (error: Exception) {
            copied.forEach { it.delete() }
            ready.delete()
            throw error
        } finally {
            staging.deleteRecursively()
            temporary.delete()
            activity.intent.removeExtra("identity_migration")
        }
    }

    private fun Throwable.causeChain(): String = generateSequence(this) { it.cause }
        .map { cause -> cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName }
        .distinct()
        .joinToString(" -> ")

    private class CountingOutput(out: java.io.OutputStream) : java.io.FilterOutputStream(out) {
        var count=0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b,off,len); count+=len }
    }
}

package com.hiddify.hiddify.nativeprofile

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import com.hiddify.hiddify.Settings
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

data class NativeProfile(
    val id: String,
    val type: String,
    val active: Boolean,
    val name: String,
    val url: String?,
    val lastUpdate: String,
    val updateIntervalSeconds: Long?,
    val upload: Long?,
    val download: Long?,
    val total: Long?,
    val expire: String?,
    val webPageUrl: String?,
    val supportUrl: String?,
    val populatedHeaders: String?,
    val userOverride: String?,
) {
    val isRemote: Boolean get() = type == "remote"
    val consumed: Long? get() {
        val sent = upload ?: return null
        val received = download ?: return null
        // Do not wrap large subscription counters into a negative amount.
        return if (sent >= 0 && received >= 0 && sent > Long.MAX_VALUE - received) Long.MAX_VALUE else sent + received
    }
}

data class NativeProfileEditor(
    val profile: NativeProfile,
    val content: String,
    val name: String,
    val disableAutoUpdate: Boolean,
    val updateIntervalHours: Int?,
)

/**
 * Kotlin owner for the legacy Drift profile database.
 *
 * The table/column layout intentionally matches Drift schema v6, so Flutter and Kotlin can read
 * the same database during the migration. Once the Flutter compatibility layer is removed this
 * repository can be moved to Room without a data migration.
 */
class NativeProfileRepository(private val context: Context) {

    companion object {
        private const val MAX_CONFIG_BYTES = NativeProfileTransfer.MAX_CONFIG_BYTES
        private const val MAX_NESTED_SUBSCRIPTIONS = 128
        private const val INFINITE_TRAFFIC = 1_099_511_627_776_001L
        private const val INFINITE_EXPIRE_SECONDS = 92_233_720_368L

        private val importSchemes =
            setOf("hiddify", "v2ray", "v2rayn", "v2rayng", "clash", "clashmeta", "sing-box")
        private val allowedHeaders =
            setOf(
                "profile-title",
                "content-disposition",
                "subscription-userinfo",
                "profile-update-interval",
                "support-url",
                "profile-web-page-url",
                "enable-warp",
                "enable-psiphon",
                "enable-fragment",
            )
    }

    private val databaseFile: File
        get() {
            // path_provider's getApplicationDocumentsDirectory() maps to Flutter's
            // PathUtils.getDataDirectory(), i.e. Context.getDir("flutter", MODE_PRIVATE).
            // Keep using that exact location while Kotlin and Dart share Drift schema v6.
            val flutterDatabase = File(context.getDir("flutter", Context.MODE_PRIVATE), "db.sqlite")
            val earlyNativeDatabase = File(context.filesDir, "db.sqlite")
            return when {
                flutterDatabase.exists() -> flutterDatabase
                earlyNativeDatabase.exists() -> earlyNativeDatabase
                else -> flutterDatabase
            }
        }
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    private val nestedHttp =
        http.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private fun openDatabase(): SQLiteDatabase {
        databaseFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        ensureSchema(db)
        return db
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS profile_entries (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL DEFAULT 'remote',
                active INTEGER NOT NULL DEFAULT 0 CHECK (active IN (0, 1)),
                name TEXT NOT NULL DEFAULT 'Profile',
                url TEXT,
                last_update TEXT NOT NULL DEFAULT '',
                update_interval INTEGER,
                upload INTEGER,
                download INTEGER,
                total INTEGER,
                expire TEXT,
                web_page_url TEXT,
                support_url TEXT,
                populated_headers TEXT,
                user_override TEXT
            )
            """.trimIndent(),
        )

        // Drift schema v6 contains both tables. Create app_proxy_entries here too before bumping
        // user_version so a clean native install can still be opened by the Flutter fallback.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_proxy_entries (
                mode TEXT NOT NULL,
                pkg_name TEXT NOT NULL,
                flags INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (mode, pkg_name)
            )
            """.trimIndent(),
        )

        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(profile_entries)", null).use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        val additions =
            mapOf(
                "type" to "TEXT NOT NULL DEFAULT 'remote'",
                "active" to "INTEGER NOT NULL DEFAULT 0",
                "name" to "TEXT NOT NULL DEFAULT 'Profile'",
                "url" to "TEXT",
                "last_update" to "TEXT NOT NULL DEFAULT ''",
                "update_interval" to "INTEGER",
                "upload" to "INTEGER",
                "download" to "INTEGER",
                "total" to "INTEGER",
                "expire" to "TEXT",
                "web_page_url" to "TEXT",
                "support_url" to "TEXT",
                "populated_headers" to "TEXT",
                "user_override" to "TEXT",
            )
        for ((name, declaration) in additions) {
            if (name !in columns) db.execSQL("ALTER TABLE profile_entries ADD COLUMN $name $declaration")
        }
        if (db.version < 6) db.version = 6
    }

    fun listProfiles(): List<NativeProfile> =
        openDatabase().use { db ->
            db.rawQuery(
                """
                SELECT id, type, active, name, url, last_update, update_interval, upload, download,
                       total, expire, web_page_url, support_url, populated_headers, user_override
                FROM profile_entries
                ORDER BY active DESC, last_update DESC, name COLLATE NOCASE ASC
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toProfile())
                }
            }
        }

    fun activeProfile(): NativeProfile? =
        openDatabase().use { db ->
            db.rawQuery(
                """
                SELECT id, type, active, name, url, last_update, update_interval, upload, download,
                       total, expire, web_page_url, support_url, populated_headers, user_override
                FROM profile_entries WHERE active = 1 LIMIT 1
                """.trimIndent(),
                null,
            ).use { cursor -> if (cursor.moveToFirst()) cursor.toProfile() else null }
        }

    fun synchronizeActiveProfile(): NativeProfile? {
        val profile = activeProfile()
        syncSettings(profile)
        return profile
    }

    fun setActive(id: String): NativeProfile {
        val profile =
            openDatabase().use { db ->
                db.beginTransaction()
                try {
                    db.execSQL("UPDATE profile_entries SET active = 0")
                    val changed =
                        db.update(
                            "profile_entries",
                            ContentValues().apply { put("active", 1) },
                            "id = ?",
                            arrayOf(id),
                        )
                    check(changed == 1) { "Profile not found" }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                getById(db, id) ?: error("Profile not found after activation")
            }
        syncSettings(profile)
        return profile
    }

    fun delete(id: String) {
        var nextActive: NativeProfile? = null
        val config = profileFile(id)
        val backup = File(config.parentFile, "${config.name}.delete-${UUID.randomUUID()}")

        if (config.exists()) config.copyTo(backup, overwrite = true)
        try {
            openDatabase().use { db ->
                db.beginTransaction()
                try {
                    val current = getById(db, id) ?: error("Profile not found")
                    val deleted = db.delete("profile_entries", "id = ?", arrayOf(id))
                    check(deleted == 1) { "Unable to delete profile" }

                    if (current.active) {
                        db.execSQL("UPDATE profile_entries SET active = 0")
                        db.rawQuery(
                            """
                            SELECT id, type, active, name, url, last_update, update_interval, upload,
                                   download, total, expire, web_page_url, support_url,
                                   populated_headers, user_override
                            FROM profile_entries
                            ORDER BY last_update DESC, id ASC LIMIT 1
                            """.trimIndent(),
                            null,
                        ).use { cursor ->
                            if (cursor.moveToFirst()) {
                                val selected = cursor.toProfile().copy(active = true)
                                db.update(
                                    "profile_entries",
                                    ContentValues().apply { put("active", 1) },
                                    "id = ?",
                                    arrayOf(selected.id),
                                )
                                nextActive = selected
                            }
                        }
                    } else {
                        nextActive = activeProfileFromDb(db)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            if (config.exists() && !config.delete()) error("Unable to delete profile file")
            if (backup.exists()) backup.delete()
            syncSettings(nextActive)
        } catch (error: Exception) {
            if (backup.exists()) {
                config.parentFile?.mkdirs()
                backup.copyTo(config, overwrite = true)
            }
            throw error
        }
    }

    fun refreshRemote(id: String): NativeProfile {
        val profile =
            openDatabase().use { db -> getById(db, id) ?: error("Profile not found") }
        check(profile.isRemote && !profile.url.isNullOrBlank()) { "Profile is not a remote subscription" }
        return importRemote(profile.url, existingId = profile.id, existingOverride = profile.userOverride)
    }

    fun loadEditor(id: String): NativeProfileEditor {
        val profile = openDatabase().use { db -> getById(db, id) ?: error("Profile not found") }
        val file = profileFile(id)
        check(file.isFile) { "Profile configuration file not found" }
        val content = file.inputStream().use(NativeProfileTransfer::readText)
        return NativeProfileEditor(
            profile = profile,
            content = content,
            name = userOverrideName(profile.userOverride) ?: profile.name,
            disableAutoUpdate = userOverrideAutoUpdateDisabled(profile.userOverride),
            updateIntervalHours =
                userOverrideIntervalHours(profile.userOverride)?.toInt()
                    ?: profile.updateIntervalSeconds?.div(3600L)?.toInt()?.takeIf { it > 0 },
        )
    }

    fun saveEditedProfile(
        id: String,
        content: String,
        name: String,
        disableAutoUpdate: Boolean,
        updateIntervalHours: Int?,
    ): NativeProfile {
        val existing = openDatabase().use { db -> getById(db, id) ?: error("Profile not found") }
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Profile name cannot be empty" }
        require(content.isNotBlank()) { "Configuration cannot be empty" }
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_CONFIG_BYTES) {
            "Configuration exceeds 8 MiB"
        }
        if (content.trimStart().startsWith("{")) {
            runCatching { JSONObject(content) }
                .getOrElse { throw IllegalArgumentException("Invalid JSON configuration", it) }
        }

        val oldContent = runCatching { profileFile(id).readText() }.getOrDefault("")
        val storedHeaders = parseStoredHeaders(existing.populatedHeaders).toMutableMap()
        mergeHeaders(emptyMap(), oldContent).keys.forEach(storedHeaders::remove)
        storedHeaders.putAll(mergeHeaders(emptyMap(), content))

        val userOverride =
            editUserOverride(
                previous = existing.userOverride,
                name = cleanName,
                updateIntervalHours = updateIntervalHours,
                disableAutoUpdate = disableAutoUpdate,
            )

        val subscription = parseSubscriptionInfo(storedHeaders["subscription-userinfo"])
        val intervalSeconds =
            if (!existing.isRemote || disableAutoUpdate) {
                null
            } else {
                updateIntervalHours?.takeIf { it > 0 }?.toLong()?.times(3600L)
                    ?: storedHeaders["profile-update-interval"]?.trim()?.toLongOrNull()?.times(3600L)
            }

        val updated =
            existing.copy(
                name = cleanName,
                lastUpdate = nowIso(),
                updateIntervalSeconds = intervalSeconds,
                upload = if (existing.isRemote) subscription?.upload else null,
                download = if (existing.isRemote) subscription?.download else null,
                total = if (existing.isRemote) subscription?.total else null,
                expire = if (existing.isRemote) subscription?.expire else null,
                webPageUrl = storedHeaders["profile-web-page-url"]?.takeIf(::isHttpUrl),
                supportUrl = storedHeaders["support-url"]?.takeIf(::isHttpUrl),
                populatedHeaders = JSONObject(storedHeaders).toString(),
                userOverride = userOverride,
            )
        commit(updated, content, isNew = false)
        return updated
    }

    fun dueRemoteProfileIds(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): List<String> =
        listProfiles()
            .asSequence()
            .filter { it.isRemote && !userOverrideAutoUpdateDisabled(it.userOverride) }
            .filter { profile ->
                val interval = profile.updateIntervalSeconds ?: return@filter false
                val last =
                    runCatching { java.time.LocalDateTime.parse(profile.lastUpdate) }
                        .getOrNull() ?: return@filter true
                !last.plusSeconds(interval).isAfter(now)
            }
            .map { it.id }
            .toList()

    fun importInput(
        rawInput: String,
        name: String? = null,
        updateIntervalHours: Int? = null,
        disableAutoUpdate: Boolean = false,
    ): NativeProfile {
        val input = rawInput.trim()
        require(input.isNotEmpty()) { "Profile is empty" }

        val link = parseRemoteLink(input)
        return if (link != null) {
            importRemote(
                url = link.first,
                requestedName = name?.takeIf { it.isNotBlank() } ?: link.second,
                updateIntervalHours = updateIntervalHours,
                disableAutoUpdate = disableAutoUpdate,
            )
        } else {
            importLocal(input, name)
        }
    }

    fun importRemote(
        url: String,
        requestedName: String? = null,
        updateIntervalHours: Int? = null,
        disableAutoUpdate: Boolean = false,
        existingId: String? = null,
        existingOverride: String? = null,
    ): NativeProfile {
        require(isHttpUrl(url)) { "Only HTTP and HTTPS subscription URLs are supported natively" }

        val existing =
            openDatabase().use { db ->
                existingId?.let { getById(db, it) } ?: getByUrl(db, url)
            }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val active = existing?.active ?: true
        val response = download(url, nested = false)
        val expanded = expandNestedSubscriptions(response.body)
        val mergedHeaders = mergeHeaders(response.headers, expanded)
        val override =
            buildUserOverride(
                previous = existingOverride ?: existing?.userOverride,
                name = requestedName,
                updateIntervalHours = updateIntervalHours,
                disableAutoUpdate = disableAutoUpdate,
            )
        val resolvedName =
            userOverrideName(override)
                ?: parseProfileTitle(mergedHeaders["profile-title"])
                ?: contentDispositionName(mergedHeaders["content-disposition"])
                ?: remoteName(url)
                ?: "Remote Profile"
        val subscription = parseSubscriptionInfo(mergedHeaders["subscription-userinfo"])
        val intervalSeconds =
            if (userOverrideAutoUpdateDisabled(override)) {
                null
            } else {
                userOverrideIntervalHours(override)?.times(3600L)
                    ?: mergedHeaders["profile-update-interval"]?.trim()?.toLongOrNull()?.times(3600L)
            }

        val profile =
            NativeProfile(
                id = id,
                type = "remote",
                active = active,
                name = resolvedName,
                url = url.trim(),
                lastUpdate = nowIso(),
                updateIntervalSeconds = intervalSeconds,
                upload = subscription?.upload,
                download = subscription?.download,
                total = subscription?.total,
                expire = subscription?.expire,
                webPageUrl = mergedHeaders["profile-web-page-url"]?.takeIf(::isHttpUrl),
                supportUrl = mergedHeaders["support-url"]?.takeIf(::isHttpUrl),
                populatedHeaders = JSONObject(mergedHeaders).toString(),
                userOverride = override,
            )
        commit(profile, expanded, isNew = existing == null)
        return profile
    }

    fun importLocal(content: String, requestedName: String? = null): NativeProfile {
        val decoded = safeDecodeBase64(content)
        require(decoded.toByteArray(Charsets.UTF_8).size <= MAX_CONFIG_BYTES) { "Configuration exceeds 8 MiB" }

        val id = UUID.randomUUID().toString()
        val headers = mergeHeaders(emptyMap(), decoded)
        val profile =
            NativeProfile(
                id = id,
                type = "local",
                active = true,
                name = requestedName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: parseProfileTitle(headers["profile-title"])
                    ?: protocolName(decoded),
                url = null,
                lastUpdate = nowIso(),
                updateIntervalSeconds = null,
                upload = null,
                download = null,
                total = null,
                expire = null,
                webPageUrl = null,
                supportUrl = null,
                populatedHeaders = JSONObject(headers).toString(),
                userOverride =
                    requestedName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        JSONObject()
                            .put("version", 1)
                            .put("name", it)
                            .put("isAutoUpdateDisable", false)
                            .toString()
                    },
            )
        commit(profile, decoded, isNew = true)
        return profile
    }

    private fun commit(profile: NativeProfile, content: String, isNew: Boolean) {
        val destination = profileFile(profile.id)
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, "${destination.name}.tmp-${UUID.randomUUID()}")
        val backup = File(destination.parentFile, "${destination.name}.backup-${UUID.randomUUID()}")
        temp.writeText(content)
        check(temp.length() <= MAX_CONFIG_BYTES) { "Configuration exceeds 8 MiB" }

        val hadDestination = destination.exists()
        if (hadDestination) destination.copyTo(backup, overwrite = true)

        try {
            moveReplacing(temp, destination)
            openDatabase().use { db ->
                db.beginTransaction()
                try {
                    if (isNew && profile.active) db.execSQL("UPDATE profile_entries SET active = 0")
                    val result =
                        db.insertWithOnConflict(
                            "profile_entries",
                            null,
                            profile.toValues(),
                            SQLiteDatabase.CONFLICT_REPLACE,
                        )
                    check(result != -1L) { "Unable to save profile" }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            if (backup.exists()) backup.delete()
            if (profile.active) syncSettings(profile)
        } catch (error: Exception) {
            if (temp.exists()) temp.delete()
            if (hadDestination && backup.exists()) {
                moveReplacing(backup, destination)
            } else if (!hadDestination && destination.exists()) {
                destination.delete()
            }
            throw error
        } finally {
            if (temp.exists()) temp.delete()
            if (backup.exists()) backup.delete()
        }
    }

    private fun download(url: String, nested: Boolean): DownloadResult {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "VetrOFF-Android")
                .header("Accept", "*/*")
                .get()
                .build()
        val client = if (nested) nestedHttp else http
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Subscription HTTP ${response.code}" }
            val body = readLimited(response)
            val headers =
                buildMap {
                    for (name in response.headers.names()) {
                        val key = name.lowercase()
                        if (key in allowedHeaders) {
                            response.header(name)?.takeIf { it.isNotBlank() }?.let { put(key, it) }
                        }
                    }
                }
            DownloadResult(body, headers)
        }
    }

    private fun readLimited(response: Response): String {
        val body = response.body ?: error("Subscription response is empty")
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_CONFIG_BYTES) { "Subscription exceeds 8 MiB" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private fun expandNestedSubscriptions(content: String): String {
        val lines = content.split("\n")
        val nestedCount = lines.count { isHttpUrl(it.trim()) }
        require(nestedCount <= MAX_NESTED_SUBSCRIPTIONS) { "Too many nested subscriptions" }
        var totalBytes = content.toByteArray(Charsets.UTF_8).size
        if (nestedCount == 0) return content

        val expanded =
            lines.map { original ->
                val line = original.trim()
                if (!isHttpUrl(line)) return@map original
                validateNestedPublicUrl(line)
                val nested = download(line, nested = true).body.trim()
                totalBytes += nested.toByteArray(Charsets.UTF_8).size
                require(totalBytes <= MAX_CONFIG_BYTES) { "Nested subscriptions exceed 8 MiB" }
                nested
            }
        return expanded.joinToString("\n")
    }

    private fun validateNestedPublicUrl(url: String) {
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotBlank() && host != "localhost" && !host.endsWith(".localhost")) {
            "Nested subscription target is not public"
        }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.none(::isBlockedAddress)) {
            "Nested subscription target is not public"
        }
    }

    private fun isBlockedAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
        ) return true
        val b = address.address.map { it.toInt() and 0xff }
        if (b.size == 4) {
            val a = b[0]
            val second = b[1]
            val third = b[2]
            if (a == 0 || a == 10 || a == 127 || a >= 224) return true
            if (a == 100 && second in 64..127) return true
            if (a == 169 && second == 254) return true
            if (a == 172 && second in 16..31) return true
            if (a == 192 && second == 168) return true
            if (a == 198 && second in 18..19) return true
            if (a == 192 && second == 0 && third in listOf(0, 2)) return true
            if (a == 198 && second == 51 && third == 100) return true
            if (a == 203 && second == 0 && third == 113) return true
        } else if (b.size == 16) {
            if ((b[0] and 0xfe) == 0xfc) return true
            if (b[0] == 0xfe && (b[1] and 0xc0) == 0x80) return true
            if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) return true
        }
        return false
    }

    private fun mergeHeaders(remote: Map<String, String>, content: String): Map<String, String> {
        val merged = remote.toMutableMap()
        val decoded = safeDecodeBase64(content)
        decoded.lineSequence().take(10).forEach { line ->
            if (!line.startsWith("#") && !line.startsWith("//")) return@forEach
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val key =
                line.substring(0, colon)
                    .removePrefix("#")
                    .removePrefix("//")
                    .trim()
                    .lowercase()
            if (key !in allowedHeaders || key in merged) return@forEach
            line.substring(colon + 1).trim().takeIf { it.isNotEmpty() }?.let { merged[key] = it }
        }
        return merged
    }

    private fun parseProfileTitle(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith("base64:", ignoreCase = true)) return raw
        return runCatching {
            String(Base64.decode(raw.substringAfter(':'), Base64.DEFAULT), Charsets.UTF_8).trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun contentDispositionName(value: String?): String? {
        val raw = value ?: return null
        return Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun remoteName(url: String): String? {
        val uri = Uri.parse(url)
        uri.fragment?.takeIf { it.isNotBlank() }?.let { return Uri.decode(it) }
        return uri.lastPathSegment
            ?.replace(Regex("""\.(json|yaml|yml|txt).*$""", RegexOption.IGNORE_CASE), "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun protocolName(content: String): String {
        if (content.contains("[Interface]")) return "WireGuard"
        for (line in content.lineSequence()) {
            val value = line.trim()
            val index = value.indexOf("://")
            if (index <= 0) continue
            val scheme = value.substring(0, index).lowercase()
            val fragment =
                value.substringAfter('#', "")
                    .substringBefore(" -> ")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }
            return fragment
                ?: when (scheme) {
                    "ss", "ssconf" -> "Shadowsocks"
                    "vmess" -> "VMess"
                    "vless" -> "VLESS"
                    "trojan" -> "Trojan"
                    "tuic" -> "TUIC"
                    "hy2", "hysteria2" -> "Hysteria 2"
                    "hy", "hysteria" -> "Hysteria"
                    "ssh" -> "SSH"
                    "wg" -> "WireGuard"
                    "awg" -> "AmneziaWG"
                    "shadowtls" -> "ShadowTLS"
                    "mieru" -> "Mieru"
                    "warp" -> "WARP"
                    else -> scheme.replaceFirstChar { it.uppercase() }
                }
        }
        return "Local Profile"
    }

    private fun parseSubscriptionInfo(value: String?): Subscription? {
        val raw = value ?: return null
        val values =
            raw.split(';')
                .mapNotNull { part ->
                    val pieces = part.split('=', limit = 2)
                    if (pieces.size != 2) null
                    else pieces[0].trim().lowercase() to pieces[1].trim().toLongOrNull()
                }
                .toMap()
        val upload = values["upload"] ?: return null
        val download = values["download"] ?: return null
        val total = values["total"].let { if (it == null || it == 0L) INFINITE_TRAFFIC else it }
        var expireSeconds = values["expire"]
        if (expireSeconds == null || expireSeconds == 0L || expireSeconds > 8_640_000_000_000L) {
            expireSeconds = INFINITE_EXPIRE_SECONDS
        }
        return Subscription(
            upload = upload,
            download = download,
            total = total,
            expire = epochSecondsToLocalIso(expireSeconds),
        )
    }

    private fun parseRemoteLink(input: String): Pair<String, String?>? {
        if (isHttpUrl(input)) return input to null
        val uri = Uri.parse(input)
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme !in importSchemes || !uri.host.equals("import", ignoreCase = true)) return null

        val queryUrl = uri.getQueryParameter("url")
        val queryName = uri.getQueryParameter("name")
        if (!queryUrl.isNullOrBlank() && isHttpUrl(queryUrl)) return queryUrl.trim() to queryName

        if (scheme == "hiddify") {
            val path = uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() } ?: return null
            val decoded = Uri.decode(path)
            if (isHttpUrl(decoded)) return decoded to uri.fragment
        }
        return null
    }

    private fun isHttpUrl(value: String): Boolean =
        runCatching {
            val uri = java.net.URI(value.trim())
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                !uri.host.isNullOrBlank()
        }.getOrDefault(false)

    private fun safeDecodeBase64(value: String): String {
        val compact = value.trim()
        if (compact.isEmpty()) return compact
        return runCatching {
            val decoded = Base64.decode(compact, Base64.DEFAULT)
            val text = String(decoded, Charsets.UTF_8)
            if (text.any { it == '\uFFFD' }) value else text
        }.getOrDefault(value)
    }

    private fun editUserOverride(
        previous: String?,
        name: String,
        updateIntervalHours: Int?,
        disableAutoUpdate: Boolean,
    ): String {
        val json =
            runCatching { previous?.let(::JSONObject) }.getOrNull()
                ?: JSONObject().put("version", 1)
        json.put("version", 1)
        json.put("name", name)
        json.put("isAutoUpdateDisable", disableAutoUpdate)
        if (updateIntervalHours != null && updateIntervalHours > 0) {
            json.put("updateInterval", updateIntervalHours)
        } else {
            json.remove("updateInterval")
        }
        return json.toString()
    }

    private fun parseStoredHeaders(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(value)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = json.opt(key)
                    if (item != null && item != JSONObject.NULL) put(key, item.toString())
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun buildUserOverride(
        previous: String?,
        name: String?,
        updateIntervalHours: Int?,
        disableAutoUpdate: Boolean,
    ): String? {
        val json =
            runCatching { previous?.let(::JSONObject) }.getOrNull()
                ?: JSONObject().put("version", 1)
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanName != null) json.put("name", cleanName)
        if (updateIntervalHours != null) {
            if (updateIntervalHours > 0) json.put("updateInterval", updateIntervalHours)
            else json.remove("updateInterval")
        }
        if (disableAutoUpdate || updateIntervalHours != null) {
            json.put("isAutoUpdateDisable", disableAutoUpdate)
        }
        return if (json.length() <= 1 && cleanName == null) previous else json.toString()
    }

    private fun userOverrideName(value: String?): String? =
        runCatching { value?.let(::JSONObject)?.optString("name") }
            .getOrNull()
            ?.takeIf { !it.isNullOrBlank() }

    private fun userOverrideAutoUpdateDisabled(value: String?): Boolean =
        runCatching { value?.let(::JSONObject)?.optBoolean("isAutoUpdateDisable", false) }
            .getOrNull() == true

    private fun userOverrideIntervalHours(value: String?): Long? =
        runCatching {
            value?.let(::JSONObject)
                ?.takeIf { it.has("updateInterval") }
                ?.optLong("updateInterval")
                ?.takeIf { it > 0 }
        }.getOrNull()

    private fun getById(db: SQLiteDatabase, id: String): NativeProfile? =
        db.rawQuery(
            """
            SELECT id, type, active, name, url, last_update, update_interval, upload, download,
                   total, expire, web_page_url, support_url, populated_headers, user_override
            FROM profile_entries WHERE id = ? LIMIT 1
            """.trimIndent(),
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toProfile() else null }

    private fun getByUrl(db: SQLiteDatabase, url: String): NativeProfile? =
        db.rawQuery(
            """
            SELECT id, type, active, name, url, last_update, update_interval, upload, download,
                   total, expire, web_page_url, support_url, populated_headers, user_override
            FROM profile_entries WHERE url = ? LIMIT 1
            """.trimIndent(),
            arrayOf(url),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toProfile() else null }

    private fun activeProfileFromDb(db: SQLiteDatabase): NativeProfile? =
        db.rawQuery(
            """
            SELECT id, type, active, name, url, last_update, update_interval, upload, download,
                   total, expire, web_page_url, support_url, populated_headers, user_override
            FROM profile_entries WHERE active = 1 LIMIT 1
            """.trimIndent(),
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toProfile() else null }

    private fun profileFile(id: String): File = File(configDirectory(), "$id.json")

    private fun configDirectory(): File {
        val configured = Settings.workingDir
        val working =
            if (configured.isNotBlank() && configured != "./") {
                File(configured)
            } else {
                context.getExternalFilesDir(null) ?: context.filesDir
            }
        if (Settings.workingDir.isBlank() || Settings.workingDir == "./") Settings.workingDir = working.path
        return File(working, "configs").also { it.mkdirs() }
    }

    private fun syncSettings(profile: NativeProfile?) {
        if (profile == null) {
            Settings.activeConfigPath = ""
            Settings.activeProfileName = ""
            return
        }
        Settings.activeConfigPath = profileFile(profile.id).path
        Settings.activeProfileName = profile.name
    }

    private fun NativeProfile.toValues() =
        ContentValues().apply {
            put("id", id)
            put("type", type)
            put("active", if (active) 1 else 0)
            put("name", name)
            if (url == null) putNull("url") else put("url", url)
            put("last_update", lastUpdate)
            if (updateIntervalSeconds == null) putNull("update_interval") else put("update_interval", updateIntervalSeconds)
            if (upload == null) putNull("upload") else put("upload", upload)
            if (download == null) putNull("download") else put("download", download)
            if (total == null) putNull("total") else put("total", total)
            if (expire == null) putNull("expire") else put("expire", expire)
            if (webPageUrl == null) putNull("web_page_url") else put("web_page_url", webPageUrl)
            if (supportUrl == null) putNull("support_url") else put("support_url", supportUrl)
            if (populatedHeaders == null) putNull("populated_headers") else put("populated_headers", populatedHeaders)
            if (userOverride == null) putNull("user_override") else put("user_override", userOverride)
        }

    private fun Cursor.toProfile() =
        NativeProfile(
            id = getString(getColumnIndexOrThrow("id")),
            type = getString(getColumnIndexOrThrow("type")),
            active = getInt(getColumnIndexOrThrow("active")) != 0,
            name = getString(getColumnIndexOrThrow("name")),
            url = stringOrNull("url"),
            lastUpdate = getString(getColumnIndexOrThrow("last_update")),
            updateIntervalSeconds = longOrNull("update_interval"),
            upload = longOrNull("upload"),
            download = longOrNull("download"),
            total = longOrNull("total"),
            expire = stringOrNull("expire"),
            webPageUrl = stringOrNull("web_page_url"),
            supportUrl = stringOrNull("support_url"),
            populatedHeaders = stringOrNull("populated_headers"),
            userOverride = stringOrNull("user_override"),
        )

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun moveReplacing(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun nowIso(): String =
        java.time.LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun epochSecondsToLocalIso(seconds: Long): String =
        Instant.ofEpochSecond(seconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private data class DownloadResult(val body: String, val headers: Map<String, String>)

    private data class Subscription(
        val upload: Long,
        val download: Long,
        val total: Long,
        val expire: String,
    )
}

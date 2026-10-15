package com.hiddify.hiddify.nativeupdate

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class NativeUpdatePolicyTest {
    private val sha = "a".repeat(64)
    private fun release(tag: String = "v1.2.0", pre: Boolean = false, draft: Boolean = false, id: Int = 1,
        name: String = "VetrOFF Client 1.2.0", asset: String = "vetroff-Android-arm64-v8a.apk") = """
        {"tag_name":"$tag","name":"$name","draft":$draft,"prerelease":$pre,
        "html_url":"https://github.com/SawaMEN/hiddify-app/releases/tag/$tag","assets":[{
        "id":$id,"name":"$asset","browser_download_url":"https://github.com/SawaMEN/hiddify-app/releases/download/$tag/$asset",
        "size":4,"digest":"sha256:$sha","updated_at":"2026-10-15T10:00:00Z"}]}"""
    private fun candidate() = NativeUpdatePolicy.releases("[${release()}]", NativeUpdateChannel.STABLE).single()

    @Test fun stableExcludesDraftsDevPrereleasesAndWrongArchitectures() {
        val json = "[${release()},${release("dev-latest", true, id = 2)},${release("v1.3.0", true)},${release("v1.4.0", draft = true)},${release("v1.5.0", asset = "app-x86.apk")}]"
        assertEquals(listOf("v1.2.0"), NativeUpdatePolicy.releases(json, NativeUpdateChannel.STABLE).map { it.tag })
        assertEquals(listOf("dev-latest"), NativeUpdatePolicy.releases(json, NativeUpdateChannel.DEV).map { it.tag })
    }
    @Test fun newestCompatibleReleaseWinsEvenIfGithubReturnsOlderFirst() {
        val json = "[${release()},${release("v1.3.0", id = 3)},${release("v1.1.0", id = 4)}]"
        assertEquals(listOf(10300,10200,10100), NativeUpdatePolicy.releases(json, NativeUpdateChannel.STABLE).map { it.buildNumber })
    }
    @Test fun untrustedUrlsMissingDigestAndOversizedApksAreRejected() {
        val r = candidate()
        for (bad in listOf(r.copy(url = r.url.replace("github.com", "evil.example")), r.copy(sha256 = ""),
            r.copy(size = NativeUpdatePolicy.MAX_APK_BYTES + 1), r.copy(assetName = "../../app.apk"),
            r.copy(tag = "../other"))) assertFalse(NativeUpdatePolicy.valid(bad))
        assertTrue(NativeUpdatePolicy.releases("[${release().replace("sha256:$sha", "sha256:bad")}]", NativeUpdateChannel.STABLE).isEmpty())
    }
    @Test fun stableBuildCodesInTagsArePreservedAndValidated() {
        val r = NativeUpdatePolicy.releases("[${release("v1.2.0+40201.prod")}]", NativeUpdateChannel.STABLE).single()
        assertEquals(40201, r.buildNumber)
        assertTrue(NativeUpdatePolicy.newer(r, 40200, 0, ""))
        assertFalse(NativeUpdatePolicy.newer(r, 40201, 0, ""))
        assertFalse(NativeUpdatePolicy.valid(r.copy(buildNumber = 10200)))
        assertFalse(NativeUpdatePolicy.valid(r.copy(version = "1.3.0")))
    }
    @Test fun savedReleaseHasAStableExplicitSchemaAcrossAppBuilds() {
        val r = candidate()
        val encoded = NativeReleaseStorage.encode(r)
        assertEquals(r, NativeReleaseStorage.decode(encoded))
        val json = com.google.gson.JsonParser.parseString(encoded).asJsonObject
        assertEquals("STABLE", json.get("channel").asString)
        assertEquals(r.identity, NativeReleaseStorage.decode(encoded).identity)
        // This is the schema written by the previous reflection serializer as well.
        json.addProperty("commit", "c".repeat(40))
        assertEquals(r, NativeReleaseStorage.decode(json.toString()))
        json.addProperty("url", "https://evil.example/app.apk")
        assertThrows(IllegalArgumentException::class.java) { NativeReleaseStorage.decode(json.toString()) }
    }
    @Test fun cancelledChecksReleaseTheUiWithoutResettingDownloadOrSkipState() {
        val idle = NativeAppUpdateState(phase = NativeUpdatePhase.CHECKING).checkCancelled()
        assertEquals(NativeUpdatePhase.IDLE, idle.phase)
        assertFalse(idle.busy)
        val skipped = NativeAppUpdateState(phase = NativeUpdatePhase.CHECKING, release = candidate(), skipped = true).checkCancelled()
        assertEquals(NativeUpdatePhase.AVAILABLE, skipped.phase)
        assertTrue(skipped.skipped)
        assertFalse(skipped.showBanner)
        for (phase in NativeUpdatePhase.entries.filter { it != NativeUpdatePhase.CHECKING }) {
            val state = NativeAppUpdateState(phase = phase, release = candidate(), downloadedBytes = 2, totalBytes = 4)
            assertEquals(state, state.checkCancelled())
        }
    }
    @Test fun staleChecksAndDownloadRequestsCannotOverrideActiveWorkOrAChangedChannel() {
        val r = candidate()
        val available = NativeAppUpdateState(release = r, phase = NativeUpdatePhase.AVAILABLE)
        assertTrue(available.canStartDownload(r.identity, false))
        assertTrue(available.canStartDownload(r.identity, true))
        assertFalse(available.canStartDownload("stale", false))
        assertFalse(available.copy(channel = NativeUpdateChannel.DEV).canStartDownload(r.identity, false))
        assertFalse(available.copy(autoDownload = false).canStartDownload(r.identity, true))
        assertFalse(available.copy(skipped = true).canStartDownload(r.identity, true))
        assertTrue(available.copy(skipped = true).canStartDownload(r.identity, false))
        for (phase in listOf(NativeUpdatePhase.DOWNLOADING, NativeUpdatePhase.VERIFYING, NativeUpdatePhase.READY, NativeUpdatePhase.CHECKING))
            assertFalse(available.copy(phase = phase).canStartDownload(r.identity, false))
        val checking = available.copy(phase = NativeUpdatePhase.CHECKING)
        assertTrue(checking.isCurrentCheck(NativeUpdateChannel.STABLE))
        assertFalse(checking.isCurrentCheck(NativeUpdateChannel.DEV))
        assertFalse(available.isCurrentCheck(NativeUpdateChannel.STABLE))
        assertFalse(checking.copy(phase = NativeUpdatePhase.DOWNLOADING).isCurrentCheck(NativeUpdateChannel.STABLE))
    }
    @Test fun rollingDevUsesTheActualApkWithoutExtraMetadataFiles() {
        val r = NativeUpdatePolicy.releases("[${release("dev-latest", true)}]", NativeUpdateChannel.DEV).single()
        val installed = "c".repeat(64)
        assertTrue(NativeUpdatePolicy.newer(r, r.buildNumber, r.publishedAt - 1000, installed))
        assertFalse(NativeUpdatePolicy.newer(r, r.buildNumber, r.publishedAt - 1000, r.sha256))
        assertFalse(NativeUpdatePolicy.newer(r, r.buildNumber, r.publishedAt + 1000, installed))
        assertFalse(NativeUpdatePolicy.newer(r, r.buildNumber + 1, 0, installed))
        assertFalse(NativeUpdatePolicy.newer(r, r.buildNumber, 0, ""))
        assertNotEquals(r.identity, r.copy(assetId = 2).identity)
        assertNotEquals(r.identity, r.copy(sha256 = "d".repeat(64)).identity)
        // Moving the rolling tag before uploading the next APK must not offer the installed APK again.
        assertFalse(NativeUpdatePolicy.newer(r.copy(assetId = 9), r.buildNumber, 0, r.sha256))
    }
    @Test fun skipAndNotificationHistoryApplyToOneSpecificBuild() {
        val r = candidate()
        assertTrue(NativeUpdatePolicy.notify(r.identity, null, null))
        assertFalse(NativeUpdatePolicy.notify(r.identity, r.identity, null))
        assertFalse(NativeUpdatePolicy.notify(r.identity, null, r.identity))
        assertTrue(NativeUpdatePolicy.notify(r.copy(assetId = 2).identity, r.identity, r.identity))
    }
    @Test fun checkIntervalDoesNotNagOnEveryAppLaunchAndHandlesClockChanges() {
        assertFalse(NativeUpdatePolicy.due(1001,1000))
        assertTrue(NativeUpdatePolicy.due(1000 + NativeUpdatePolicy.CHECK_INTERVAL_MS,1000))
        assertTrue(NativeUpdatePolicy.due(999,1000))
        assertTrue(NativeUpdatePolicy.due(1000,0))
    }
    @Test fun corruptedTruncatedAndRemovedDownloadsCannotBeInstalled() {
        val file = Files.createTempFile("update", ".apk").toFile()
        try {
            val bytes = byteArrayOf(1,2,3,4)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val r = candidate().copy(sha256 = digest)
            file.writeBytes(bytes)
            assertTrue(NativeUpdateArtifact.matches(file,r))
            file.writeBytes(byteArrayOf(1,2,3,5))
            assertFalse(NativeUpdateArtifact.matches(file,r))
            file.writeBytes(byteArrayOf(1,2))
            assertFalse(NativeUpdateArtifact.matches(file,r))
            file.delete()
            assertFalse(NativeUpdateArtifact.matches(file,r))
        } finally { file.delete() }
    }
}

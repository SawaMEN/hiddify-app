package com.hiddify.hiddify.nativeupdate

import com.hiddify.hiddify.nativecore.NativeUpdateRepository
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class NativeUpdateRepositoryTest {
    private fun feed(dev: Boolean): String {
        val tag = if (dev) "dev-latest" else "v1.2.0"
        val apk = if (dev) "vetroff-arm64-dev.apk" else "vetroff-arm64.apk"
        return """[{"tag_name":"$tag","name":"VetrOFF Client 1.2.0","draft":false,"prerelease":$dev,
            "html_url":"https://github.com/SawaMEN/hiddify-app/releases/tag/$tag","assets":[{
            "id":12,"name":"$apk","browser_download_url":"https://github.com/SawaMEN/hiddify-app/releases/download/$tag/$apk",
            "size":1234,"digest":"sha256:${"a".repeat(64)}","updated_at":"2026-10-15T10:00:00Z"}]}]"""
    }

    @Test fun stableAndDevResolveUsingOnlyTheGithubReleaseList() {
        for (channel in NativeUpdateChannel.entries) {
            val requests = mutableListOf<String>()
            val repository = NativeUpdateRepository { url -> requests += url; feed(channel == NativeUpdateChannel.DEV) }
            val release = requireNotNull(repository.latestCompatible(channel))
            assertEquals(channel, release.channel)
            assertEquals("a".repeat(64), release.sha256)
            assertEquals(10200, release.buildNumber)
            assertEquals(listOf("https://api.github.com/repos/SawaMEN/hiddify-app/releases?per_page=100"), requests)
        }
    }

    @Test fun missingGithubDigestDoesNotFallBackToAnUnverifiedDownload() {
        val requests = mutableListOf<String>()
        val repository = NativeUpdateRepository { url ->
            requests += url
            feed(false).replace("sha256:${"a".repeat(64)}", "")
        }
        assertNull(repository.latestCompatible(NativeUpdateChannel.STABLE))
        assertEquals(1, requests.size)
    }

    @Test fun networkFailureCanBeReportedAndRetriedRatherThanShowingNoUpdates() {
        val repository = NativeUpdateRepository { throw IOException("offline") }
        assertThrows(IOException::class.java) { repository.latestCompatible(NativeUpdateChannel.DEV) }
    }
}

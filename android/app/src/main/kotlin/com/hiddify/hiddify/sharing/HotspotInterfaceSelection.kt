package com.hiddify.hiddify.sharing

/** Starting an AP can bring up cellular data too. Never mistake rmnet/ccmni for the Wi-Fi gateway. */
object HotspotInterfaceSelection {
    private val wireless = Regex("(?:wlan|swlan|ap|softap|wifi|wl|br[_0-9]|bridge|ra[0-9]).*")

    fun find(before: Set<String>, after: Map<String, Pair<String, String>>): Pair<String, String>? =
        after.entries.filter { it.key !in before && wireless.matches(it.value.first) }
            .map { it.value }.distinct().singleOrNull()
}

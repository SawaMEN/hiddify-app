package com.hiddify.hiddify.privacy

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument

enum class NativeCommunitySource(val host: String) {
    VPN("iplist.my-handbook.ru"), DIRECT("ru-iplist.my-handbook.ru");
    val catalogueUrl get() = "https://$host/?format=json&data=domains"
}

data class NativeCommunityService(val id: String, val domains: List<String>)
data class NativeCommunitySelection(val enabled: Boolean, val sites: String)

/** Provider JSON keys are the site= identifiers, not the individual matching domains. */
object NativeCommunityLists {
    fun parse(text: String): List<NativeCommunityService> {
        val root = NativeJsonDocument.parse(text)
        require(root.isJsonObject && root.asJsonObject.size() in 1..10000) { "Invalid community catalogue" }
        return root.asJsonObject.entrySet().map { (id, value) ->
            require(validId(id) && value.isJsonArray) { "Invalid community service" }
            val domains = value.asJsonArray.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Invalid service domain" }
                it.asString.also { domain -> require(domain.length in 1..253 && domain.none(Char::isISOControl)) }
            }
            NativeCommunityService(id, domains.take(3))
        }.sortedBy { it.id.lowercase() }
    }

    fun selected(sites: String): Set<String> = sites.split(Regex("[,;\\s]+"))
        .filter { it.isNotBlank() }.map { it.lowercase() }.toSet()

    fun selection(all: Boolean, selected: Set<String>): NativeCommunitySelection {
        require(selected.all(::validId)) { "Invalid community selection" }
        // Existing core semantics: enabled + empty sites means all; disabled means none.
        return NativeCommunitySelection(all || selected.isNotEmpty(),
            if (all) "" else selected.sorted().joinToString(","))
    }

    fun toggle(all: Boolean, selected: Set<String>, catalogue: Set<String>, id: String, checked: Boolean): Set<String> {
        val result = (if (all) catalogue + selected else selected).toMutableSet()
        if (checked) result.add(id) else result.remove(id)
        return result
    }

    private fun validId(id: String) = id.length in 1..253 &&
        id.none { it.isWhitespace() || it.isISOControl() || it in ",;/\\?#&=" }
}

package com.hiddify.hiddify.nativeprofile

/** Retry only the small local built-in schema. Never rewrite subscriptions or general configs. */
internal object NativeWarpEndpoints {
    fun candidates(raw: String, name: String): List<String> = runCatching {
        if (NativeWarpProfiles.profiles.none { name == it.titleEn || name == it.titleRu }) return emptyList()
        val root = NativeJsonDocument.parse(raw).asJsonObject
        if (root.keySet() != setOf("endpoints")) return emptyList()
        val endpoints = root.getAsJsonArray("endpoints")
        if (endpoints.size() != 1) return emptyList()
        val node = endpoints[0].asJsonObject
        val type = node.get("type").asString
        if (type !in setOf("wireguard", "awg") || node.get("mtu").asInt != 1280 ||
            node.get("tag").asString != if (type == "awg") "WARP + AWG" else "WARP") return emptyList()
        val peers = node.getAsJsonArray("peers")
        if (peers.size() != 1) return emptyList()
        val peer = peers[0].asJsonObject
        val host = peer.get("address").asString
        if (!Regex("162\\.159\\.(192|195)\\.[0-9]{1,3}").matches(host)) return emptyList()
        val original = host to peer.get("port").asInt
        // Consumer WireGuard ingress only. MASQUE ingress cannot accept these credentials.
        val addresses = listOf(original, host to 2408, host to 4500,
            "162.159.192.1" to 500, "162.159.195.1" to 1701).distinct()
        addresses.map { (address, port) ->
            val copy = root.deepCopy()
            val target = copy.getAsJsonArray("endpoints")[0].asJsonObject.getAsJsonArray("peers")[0].asJsonObject
            target.addProperty("address", address); target.addProperty("port", port)
            copy.toString()
        }
    }.getOrDefault(emptyList())
}

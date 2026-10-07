package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.hiddify.sharing.AutomaticHotspot
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.utils.GrpcClientProvider
import org.json.JSONObject

data class NativeWifiSharingDetails(
    val ssid: String = "",
    val wifiPassword: String = "",
    val host: String = "",
    val port: Int = 12334,
    val username: String = "hiddify",
    val password: String = "",
)

class NativeWifiSharingRepository {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    fun load(): NativeWifiSharingDetails {
        val hotspot = AutomaticHotspot.snapshot()
        val root =
            runCatching {
                Settings.configOptions.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(::JSONObject)
                    ?: JSONObject()
            }.getOrElse { JSONObject() }

        val port =
            root.optInt("mixed-port", 12334)
                .takeIf { it in 1..65535 }
                ?: 12334
        val password = root.optString("lan-sharing-password").trim()
        val host = hotspot["ip"] as? String ?: runCatching {
                client().GetLANIP().executeBlocking(Empty()).ip.trim()
            }.getOrDefault("")

        return NativeWifiSharingDetails(
            ssid = hotspot["ssid"] as? String ?: "",
            wifiPassword = hotspot["password"] as? String ?: "",
            host = host,
            port = port,
            password = password,
        )
    }
}

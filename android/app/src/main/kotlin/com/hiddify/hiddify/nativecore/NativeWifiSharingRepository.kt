package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.utils.GrpcClientProvider
import org.json.JSONObject

data class NativeWifiSharingDetails(
    val host: String = "",
    val port: Int = 12334,
    val username: String = "hiddify",
    val password: String = "",
)

class NativeWifiSharingRepository {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    fun load(): NativeWifiSharingDetails {
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
        val host =
            runCatching {
                client().GetLANIP().executeBlocking(Empty()).ip.trim()
            }.getOrDefault("")

        return NativeWifiSharingDetails(
            host = host,
            port = port,
            password = password,
        )
    }
}

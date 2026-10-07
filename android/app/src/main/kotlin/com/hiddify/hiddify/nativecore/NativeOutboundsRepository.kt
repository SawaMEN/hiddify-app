package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcommon.ResponseCode
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.SelectOutboundRequest
import com.hiddify.core.api.v2.hcore.UrlTestRequest
import com.hiddify.hiddify.utils.GrpcClientProvider

data class NativeOutboundIpInfo(
    val ip: String = "", val countryCode: String = "", val region: String = "", val city: String = "",
    val asn: Int = 0, val organization: String = "", val latitude: Double = 0.0, val longitude: Double = 0.0,
    val postalCode: String = "",
)

data class NativeOutbound(
    val tag: String,
    val name: String,
    val type: String,
    val selected: Boolean,
    val visible: Boolean,
    val delayMs: Int,
    val host: String,
    val port: Int,
    val upload: Long,
    val download: Long,
    val selectedChild: String?,
    val isGroup: Boolean = false,
    val selectedChildTag: String? = null,
    val testTimestampMs: Long = 0,
    val secure: Boolean = false,
    val ipInfo: NativeOutboundIpInfo? = null,
)

data class NativeOutboundGroup(
    val tag: String,
    val type: String,
    val selectedTag: String,
    val selectable: Boolean,
    val items: List<NativeOutbound>,
)

class NativeOutboundsRepository {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    fun load(): List<NativeOutboundGroup> {
        val call = client().OutboundsInfo()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        try {
            val (requestSink, responseSource) = call.executeBlocking()
            requestSink.use { sink ->
                sink.write(Empty())
            }
            return responseSource.use { source ->
                val response = source.read() ?: return@use emptyList()
                response.items.map { group ->
                    NativeOutboundGroup(
                        tag = group.tag,
                        type = group.type,
                        selectedTag = group.selected,
                        selectable = group.selectable,
                        items =
                            group.items
                                .filter { outbound -> outbound.is_visible }
                                .map { outbound ->
                                    NativeOutbound(
                                        tag = outbound.tag,
                                        name = outbound.tag_display.ifBlank { outbound.tag },
                                        type = outbound.type,
                                        selected = outbound.is_selected,
                                        visible = outbound.is_visible,
                                        delayMs = outbound.url_test_delay,
                                        host = outbound.host,
                                        port = outbound.port.toInt(),
                                        upload = outbound.upload,
                                        download = outbound.download,
                                        selectedChild = outbound.group_selected_tag_display,
                                        isGroup = outbound.is_group,
                                        selectedChildTag = outbound.group_selected_tag,
                                        testTimestampMs = outbound.url_test_time?.toEpochMilli() ?: 0,
                                        secure = outbound.is_secure,
                                        ipInfo = outbound.ipinfo?.let { info ->
                                            NativeOutboundIpInfo(info.ip, info.country_code, info.region, info.city,
                                                info.asn, info.org, info.latitude, info.longitude, info.postal_code)
                                        },
                                    )
                                },
                    )
                }
            }
        } finally {
            call.cancel()
        }
    }

    fun select(groupTag: String, outboundTag: String) {
        val response =
            client()
                .SelectOutbound()
                .executeBlocking(
                    SelectOutboundRequest(
                        group_tag = groupTag,
                        outbound_tag = outboundTag,
                    ),
                )
        check(response.code == ResponseCode.OK) {
            response.message.ifBlank { "Unable to select outbound" }
        }
    }

    /** Cancellable selection for the foreground automatic controller. */
    suspend fun selectForeground(groupTag: String, outboundTag: String) {
        val call = client().SelectOutbound()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val response = try { call.execute(SelectOutboundRequest(group_tag = groupTag, outbound_tag = outboundTag)) }
            finally { call.cancel() }
        check(response.code == ResponseCode.OK) { response.message.ifBlank { "Unable to select outbound" } }
    }

    fun test(tag: String) {
        val response =
            client()
                .UrlTest()
                .executeBlocking(UrlTestRequest(tag = tag))
        check(response.code == ResponseCode.OK) {
            response.message.ifBlank { "Unable to start URL test" }
        }
    }

    fun testActive() {
        val response =
            client()
                .UrlTestActive()
                .executeBlocking(Empty())
        check(response.code == ResponseCode.OK) {
            response.message.ifBlank { "Unable to start URL test" }
        }
    }
}

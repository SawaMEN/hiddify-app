package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcommon.ResponseCode
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.SelectOutboundRequest
import com.hiddify.core.api.v2.hcore.UrlTestRequest
import com.hiddify.hiddify.utils.GrpcClientProvider

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
        val (requestSink, responseSource) = client().OutboundsInfo().executeBlocking()
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
                                )
                            },
                )
            }
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

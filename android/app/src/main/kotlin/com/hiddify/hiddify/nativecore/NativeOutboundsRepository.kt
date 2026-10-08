package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcommon.ResponseCode
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.SelectOutboundRequest
import com.hiddify.core.api.v2.hcore.UrlTestRequest
import com.hiddify.hiddify.utils.GrpcClientProvider
import com.hiddify.core.api.v2.hcore.OutboundGroup as CoreOutboundGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException

class NativeOutboundsRepository {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    fun load(): List<NativeOutboundGroup> = loadGroups(main = false)

    // Same RPC and first group/item as Dart's ActiveProxyNotifier.
    fun loadActive(): NativeOutbound? = loadGroups(main = true).firstOrNull()?.items?.firstOrNull()

    private fun loadGroups(main: Boolean): List<NativeOutboundGroup> {
        val call = if (main) client().MainOutboundsInfo() else client().OutboundsInfo()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        try {
            val (requestSink, responseSource) = call.executeBlocking()
            requestSink.use { sink ->
                sink.write(Empty())
            }
            return responseSource.use { source ->
                val response = source.read() ?: return@use emptyList()
                response.items.map { it.toNative(visibleOnly = !main) }
            }
        } finally {
            call.cancel()
        }
    }

    /** The Dart overview consumes the first group, including its complete item list. */
    fun watchPrimary(): Flow<NativeOutboundGroup?> = callbackFlow {
        val call = client().OutboundsInfo()
        val reader = launch(Dispatchers.IO) {
            try {
                val (requestSink, responseSource) = call.executeBlocking()
                requestSink.use { it.write(Empty()) }
                responseSource.use { source ->
                    while (isActive) {
                        val response = source.read() ?: throw IOException("Proxy updates stream closed")
                        send(response.items.firstOrNull()?.toNative(visibleOnly = false))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                close(error)
            }
        }
        // Cancelling the sheet/lifecycle must unblock the blocking Wire read before joining it.
        awaitClose { call.cancel(); reader.cancel() }
    }.buffer(Channel.CONFLATED)

    private fun CoreOutboundGroup.toNative(visibleOnly: Boolean): NativeOutboundGroup {
        val group = this
        return NativeOutboundGroup(
            tag = group.tag,
            type = group.type,
            selectedTag = group.selected,
            selectable = group.selectable,
            items =
                group.items
                    .filter { outbound -> !visibleOnly || outbound.is_visible }
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

    fun select(groupTag: String, outboundTag: String) {
        val call = client().SelectOutbound()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val response = try { call.executeBlocking(SelectOutboundRequest(group_tag = groupTag, outbound_tag = outboundTag)) }
            finally { call.cancel() }
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
        val call = client().UrlTest()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val response = try { call.executeBlocking(UrlTestRequest(tag = tag)) }
            finally { call.cancel() }
        check(response.code == ResponseCode.OK) {
            response.message.ifBlank { "Unable to start URL test" }
        }
    }

}

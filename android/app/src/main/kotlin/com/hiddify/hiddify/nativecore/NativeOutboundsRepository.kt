package com.hiddify.hiddify.nativecore

import com.hiddify.core.api.v2.hcommon.Empty
import com.hiddify.core.api.v2.hcommon.ResponseCode
import com.hiddify.core.api.v2.hcore.CoreClient
import com.hiddify.core.api.v2.hcore.SelectOutboundRequest
import com.hiddify.core.api.v2.hcore.UrlTestRequest
import com.hiddify.hiddify.utils.GrpcClientProvider
import com.hiddify.core.api.v2.hcore.OutboundGroup as CoreOutboundGroup
import com.hiddify.core.api.v2.hcore.OutboundInfo as CoreOutboundInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import java.io.IOException

class NativeOutboundsRepository {
    private fun client(): CoreClient =
        GrpcClientProvider.grpcClient.create(CoreClient::class)

    suspend fun load(): List<NativeOutboundGroup> {
        val call = client().OutboundsInfo()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val response = withNativeResponseChannel(Empty(), call::executeIn, call::cancel) { it.nativeFirstOrNull() }
        return response?.items?.map { it.toNative(visibleOnly = true) }.orEmpty()
    }

    // Same RPC and first group/item as Dart's ActiveProxyNotifier; convert only that item.
    suspend fun loadActive(): NativeOutbound? {
        val call = client().MainOutboundsInfo()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val response = withNativeResponseChannel(Empty(), call::executeIn, call::cancel) { it.nativeFirstOrNull() }
        return response?.items?.firstOrNull()?.items?.firstOrNull()?.toNative()
    }

    /** A scoped, cancellable stream shared by the sheet and automatic ranking. */
    fun watchPrimary(): Flow<NativeOutboundGroup?> = flow {
        val call = client().OutboundsInfo()
        withNativeResponseChannel(Empty(), call::executeIn, call::cancel) { responses ->
            for (response in responses) emit(response.items.firstOrNull()?.toNative(visibleOnly = false))
            throw IOException("Proxy updates stream closed")
        }
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
                    .map { it.toNative() },
        )
    }

    private fun CoreOutboundInfo.toNative(): NativeOutbound = NativeOutbound(
        tag = tag,
        name = tag_display.ifBlank { tag },
        type = type,
        selected = is_selected,
        visible = is_visible,
        delayMs = url_test_delay,
        host = host,
        port = port.toInt(),
        upload = upload,
        download = download,
        selectedChild = group_selected_tag_display,
        isGroup = is_group,
        selectedChildTag = group_selected_tag,
        testTimestampMs = url_test_time?.toEpochMilli() ?: 0,
        secure = is_secure,
        detour = detour,
        ipInfo = ipinfo?.let { info ->
            NativeOutboundIpInfo(info.ip, info.country_code, info.region, info.city,
                info.asn, info.org, info.latitude, info.longitude, info.postal_code)
        },
    )

    suspend fun select(groupTag: String, outboundTag: String) = selectForeground(groupTag, outboundTag)

    /** Cancellable selection for the foreground automatic controller. */
    suspend fun selectForeground(groupTag: String, outboundTag: String) {
        val call = client().SelectOutbound()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val response = try { call.execute(SelectOutboundRequest(group_tag = groupTag, outbound_tag = outboundTag)) }
            finally { call.cancel() }
        check(response.code == ResponseCode.OK) { response.message.ifBlank { "Unable to select outbound" } }
    }

    suspend fun test(tag: String) {
        val call = client().UrlTest()
        call.timeout.timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val response = try { call.execute(UrlTestRequest(tag = tag)) }
            finally { call.cancel() }
        check(response.code == ResponseCode.OK) {
            response.message.ifBlank { "Unable to start URL test" }
        }
    }

}

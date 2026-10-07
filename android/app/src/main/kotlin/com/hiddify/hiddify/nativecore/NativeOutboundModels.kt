package com.hiddify.hiddify.nativecore

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

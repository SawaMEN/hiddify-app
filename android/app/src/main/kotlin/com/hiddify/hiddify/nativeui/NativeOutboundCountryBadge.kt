package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import java.util.Locale

// Keyword precedence, colors and sizes follow IPCountryFlag / OrganisationFlag.
private data class ProviderArtwork(val keyword: String, val drawable: Int, val color: Color)
private val providers = listOf(
    ProviderArtwork("cloudflare", R.drawable.native_provider_cloudflare, Color(0xFFF38020)),
    ProviderArtwork("hetzner", R.drawable.native_provider_hetzner, Color(0xFFD50C2D)),
    ProviderArtwork("ovh", R.drawable.native_provider_ovh, Color(0xFF123F6D)),
    ProviderArtwork("azure", R.drawable.native_provider_cloud, Color(0xFF0078D4)),
    ProviderArtwork("amazon", R.drawable.native_provider_cloud, Color(0xFFFF9900)),
    ProviderArtwork("oracle", R.drawable.native_provider_storage, Color(0xFFF80000)),
    ProviderArtwork("fastly", R.drawable.native_provider_fastly, Color(0xFFFF282D)),
    ProviderArtwork("digitalocean", R.drawable.native_provider_digitalocean, Color(0xFF0080FF)),
    ProviderArtwork("alibaba", R.drawable.native_provider_alibabacloud, Color(0xFFFF6A00)),
    ProviderArtwork("google", R.drawable.native_provider_googlecloud, Color(0xFF4285F4)),
    ProviderArtwork("starlink", R.drawable.native_provider_satellite, Color(0xFF000000)),
)

@Composable
internal fun NativeOutboundCountryBadge(countryCode: String, size: Dp = 40.dp, organization: String = "") {
    val locale = LocalConfiguration.current.locales[0]
    val code = countryCode.lowercase(Locale.ROOT)
    val label = if (code.isBlank()) stringResource(R.string.native_outbound_unknown_country)
        else Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(locale).ifBlank { code }
    Box(Modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (code.isBlank()) {
            Icon(painterResource(R.drawable.native_question_circle), null, Modifier.size(size))
        } else {
            val key = if (code == "ir") "ir-shir" else code
            Image(painterResource(NativeFlagDrawables[key] ?: R.drawable.native_flag_xx), null,
                Modifier.size((size - 8.dp).coerceAtLeast(1.dp)).clip(RoundedCornerShape(8.dp)))
            val provider = providers.firstOrNull { organization.lowercase(Locale.ROOT).contains(it.keyword) }
            if (provider != null) {
                val badgeSize = size / 2.5f
                val providerLabel = stringResource(R.string.native_outbound_organization) + " " + organization
                Box(Modifier.align(Alignment.BottomEnd).size(badgeSize).background(provider.color, CircleShape)
                    .semantics { contentDescription = providerLabel }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(provider.drawable), null,
                        Modifier.size((badgeSize - 6.dp).coerceAtLeast(1.dp)), tint = Color.White)
                }
            }
        }
    }
}

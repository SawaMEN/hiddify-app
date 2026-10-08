package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

/** Every option is edited by its scoped repository; this page holds no stale configuration draft. */
@Composable
fun NativeCoreOptionsScreen(
    onBack: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenTls: () -> Unit,
    onOpenGeneralOptions: () -> Unit,
    onOpenTunnel: () -> Unit,
    onOpenInbound: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_core_options_title), onBack)
        listOf(
            Triple(R.string.native_general_options_title, R.drawable.native_route, onOpenGeneralOptions),
            Triple(R.string.native_core_dns, R.drawable.native_dns, onOpenDns),
            Triple(R.string.native_inbound_title, R.drawable.native_route, onOpenInbound),
            Triple(R.string.native_tunnel_title, R.drawable.native_route, onOpenTunnel),
            Triple(R.string.native_core_tls, R.drawable.native_shield, onOpenTls),
        ).forEach { (title, icon, open) ->
            NativeSurface(Modifier.fillMaxWidth()) { NativeSettingsLink(title, icon, open) }
        }
    }
}

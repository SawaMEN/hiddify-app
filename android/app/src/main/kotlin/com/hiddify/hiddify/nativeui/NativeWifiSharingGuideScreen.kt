package com.hiddify.hiddify.nativeui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeWifiSharingDetails
import kotlinx.coroutines.launch

private val WifiGuideSources = listOf(
    "https://support.google.com/android/answer/9059108",
    "https://support.microsoft.com/en-us/windows/experience/connectivity-networking/use-a-proxy-server-in-windows",
    "https://help.gnome.org/users/gnome-help/stable/net-proxy.html.en",
    "https://support.apple.com/guide/mac-help/mchlp2591/mac",
    "https://support.apple.com/guide/iphone/iphw5gjwl8k2/ios",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeWifiSharingGuideScreen(rootMode: Boolean, details: NativeWifiSharingDetails, busy: Boolean,
    onBack: () -> Unit, onRefresh: () -> Unit) {
    val ru = LocalConfiguration.current.locales[0].language == "ru"
    val context = LocalContext.current
    val pager = rememberPagerState { 5 }
    val scope = rememberCoroutineScope()
    fun t(russian: String, english: String) = if (ru) russian else english
    val host = details.host.ifBlank { t("IP раздатчика", "Host IP") }
    val port = details.port.takeIf { it > 0 } ?: 12334
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(t("Подключение к Wi-Fi телефона", "Connect to phone Wi-Fi"), onBack)
        ScrollableTabRow(selectedTabIndex = pager.currentPage, containerColor = androidx.compose.ui.graphics.Color.Transparent,
            edgePadding = 16.dp) {
            listOf("Android", "Windows", "Linux", "macOS", "iOS / iPadOS").forEachIndexed { i, label ->
                Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(label) })
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { platform ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(t("Этот телефон с приложением раздаёт Wi-Fi через VPN. Выберите ОС устройства, которое получает Wi-Fi от телефона. Все шаги и схемы ниже относятся к получающему устройству.",
                    "This phone runs the app and shares Wi-Fi through VPN. Choose the OS of the device receiving Wi-Fi from this phone. All steps and diagrams below apply to the receiving device."))
                Spacer(Modifier.height(16.dp))
                if (details.ssid.isNotBlank() && details.wifiPassword.isNotBlank()) NativeCard(Modifier.fillMaxWidth()) {
                    SelectionContainer { Text("${t("Подключитесь к сети", "Join network")}: ${details.ssid}\n" +
                        "${t("Пароль Wi-Fi", "Wi-Fi password")}: ${details.wifiPassword}", Modifier.padding(16.dp)) }
                }
                if (!rootMode) NativeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t("Данные для подключения", "Connection details"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val address = details.host.ifBlank { t("шлюз Wi-Fi клиента при прямой раздаче", "client Wi-Fi gateway for a direct hotspot") }
                        val password = details.password.ifBlank { t("не установлен — включите раздачу в основных настройках", "not set — enable sharing in general settings") }
                        SelectionContainer { Text(if (ru) "IP: $address\nПорт: $port\nПользователь: hiddify\nПароль прокси: $password"
                            else "IP: $address\nPort: $port\nUsername: hiddify\nProxy password: $password") }
                        NativeTextButton(onClick = onRefresh, enabled = !busy) {
                            Icon(painterResource(R.drawable.native_refresh), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t("Обновить IP раздатчика", "Refresh host IP"))
                        }
                        Text(t("Точка доступа создаётся приложением автоматически. Пароль Wi-Fi показан выше; пароль прокси берите отсюда.",
                            "The app creates the hotspot automatically. The Wi-Fi password is above; use the proxy password shown here."))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(t("Ниже — нарисованные схемы экранов, а не снимки вашей системы. Названия пунктов могут отличаться. Подставляйте свои IP и порт.",
                    "Below are drawn screen diagrams, not screenshots of your system. Menu names may differ. Use your own IP and port."))
                if (rootMode) Text(t("На телефоне включён root-режим: подключайтесь без ручного прокси.",
                    "The phone uses root mode: connect without a manual proxy."))
                val sections = remember(platform, ru, host, port, rootMode, details.ssid) {
                    nativeWifiGuide(platform, ru, host, port, rootMode, details.ssid.ifBlank { "VPN-WiFi" })
                }
                sections.forEachIndexed { i, section -> GuideSection(section, i + 1, ru) }
                NativeTextButton(onClick = { runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WifiGuideSources[platform])))
                } }) {
                    Icon(painterResource(R.drawable.privacy_open), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Официальная справка по настройкам ОС", "Official OS settings help"))
                }
            }
        }
    }
}

@Composable
private fun GuideSection(section: NativeWifiGuideSection, number: Int, ru: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text("$number. ${section.title}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        section.steps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center) { Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(10.dp))
                SelectionContainer(Modifier.weight(1f)) { Text(step) }
            }
        }
        section.screenTitle?.let { SettingsSketch(it, section.fields, ru) }
    }
}

@Composable
private fun SettingsSketch(title: String, fields: Map<String, String>, ru: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().semantics {
        contentDescription = if (ru) "Схематичный экран настроек" else "Illustrated settings screen"
    }, shape = RoundedCornerShape(18.dp), color = colors.surface, border = BorderStroke(2.dp, colors.outline)) {
        Column {
            Row(Modifier.fillMaxWidth().background(colors.secondaryContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.native_settings), null, tint = colors.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(title, Modifier.weight(1f), color = colors.onSecondaryContainer, fontWeight = FontWeight.Bold)
            }
            fields.forEach { (label, value) ->
                Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Surface(Modifier.fillMaxWidth(), color = colors.primaryContainer,
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.primary)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(value, Modifier.weight(1f), color = colors.onPrimaryContainer)
                            if (value == if (ru) "Включено" else "On") {
                                Spacer(Modifier.width(8.dp))
                                Icon(painterResource(R.drawable.native_toggle_on), null, Modifier.size(32.dp), tint = colors.primary)
                            }
                        }
                    }
                }
            }
            Text(if (ru) "Схема • подставьте свои данные" else "Illustration • use your own details",
                Modifier.padding(14.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

package com.hiddify.hiddify.nativeui

import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.hiddify.hiddify.R
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.nativeprofile.NativeFreeProfile
import com.hiddify.hiddify.nativeprofile.NativeFreeProfilesRepository
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@Composable
internal fun NativeFreeProfilesPane(onImport: (NativeFreeProfile, String) -> Unit) {
    val context = LocalContext.current
    val russian = context.resources.configuration.locales[0].language == "ru"
    val region = remember { runCatching { JSONObject(Settings.configOptions.ifBlank { "{}" }).optString("region", "other") }.getOrDefault("other") }
    val repository = remember { NativeFreeProfilesRepository() }
    var profiles by remember { mutableStateOf<List<NativeFreeProfile>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var consentUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(retry) {
        profiles = null; failed = false
        try { profiles = repository.load() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    val available = profiles?.filter { it.matches(region) }
    Box(Modifier.fillMaxWidth().height(180.dp)) {
        when {
            failed -> Column(Modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.native_free_load_failed))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.native_profiles_retry)) }
            }
            available == null -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            available.isEmpty() -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(stringResource(if (profiles?.isEmpty() == true) R.string.native_free_empty else R.string.native_free_region_empty), style = MaterialTheme.typography.bodySmall)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(available) { profile ->
                    Surface(onClick = { consentUrl = profile.url }, shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(profile.title(russian), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Column {
                                    profile.neededFeatures.orEmpty().filter { it in setOf("warp_over_proxies", "psiphon_over_proxies", "fragment") }.forEach { feature ->
                                        Text(when (feature) { "warp_over_proxies" -> "WARP"; "psiphon_over_proxies" -> "Psiphon"; else -> "Fragment" },
                                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(profile.tags(russian).joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    profiles?.firstOrNull { it.url == consentUrl }?.let { profile ->
        val color = MaterialTheme.colorScheme.onSurface.toArgb()
        val linkColor = MaterialTheme.colorScheme.primary.toArgb()
        AlertDialog(onDismissRequest = { consentUrl = null }, title = { Text(profile.title(russian)) }, text = {
            Box(Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                AndroidView(factory = { TextView(it).apply {
                    textSize = 14f
                    typeface = ResourcesCompat.getFont(it, R.font.manrope)
                    movementMethod = LinkMovementMethod.getInstance()
                } }, update = {
                    it.setTextColor(color); it.setLinkTextColor(linkColor)
                    it.text = Html.fromHtml(consentHtml(profile.consent(russian)), Html.FROM_HTML_MODE_COMPACT)
                })
            }
        }, confirmButton = { TextButton(onClick = { consentUrl = null; onImport(profile, profile.title(russian)) }) {
            Text(stringResource(R.string.native_free_continue))
        } }, dismissButton = { TextButton(onClick = { consentUrl = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

/** Render the consent's text formatting/links without a WebView or remote image requests. */
private fun consentHtml(markdown: String): String {
    var text = Html.escapeHtml(markdown)
    text = Regex("\\[([^]\\n]+)]\\((https?://[^\\s)]+)\\)").replace(text) { match ->
        val url = match.groupValues[2].replace("\"", "&quot;").replace("'", "&#39;")
        "<a href=\"$url\">${match.groupValues[1]}</a>"
    }
    text = Regex("\\*\\*([^*]+)\\*\\*").replace(text, "<b>$1</b>")
    text = Regex("`([^`]+)`").replace(text, "<tt>$1</tt>")
    text = Regex("(?m)^#{1,6} (.+)$").replace(text, "<b>$1</b>")
    text = Regex("(?m)^[-*] (.+)$").replace(text, "• $1")
    return text.replace("\n", "<br>")
}

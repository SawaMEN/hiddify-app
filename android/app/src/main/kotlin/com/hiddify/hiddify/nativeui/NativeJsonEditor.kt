package com.hiddify.hiddify.nativeui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument as Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class JsonRow(val path: String, val label: String, val value: JsonElement, val depth: Int)
private data class JsonTreeSnapshot(val source: JsonElement?, val rows: List<JsonRow>)
private data class JsonEdit(val path: String, val action: String, val template: JsonObject? = null)

/** Native counterpart of Dart JsonEditor, with lazy tree rows and immediate edit publication. */
@Composable
internal fun NativeJsonEditor(
    content: String,
    enabled: Boolean,
    onChanged: (String) -> Unit,
    onValidationChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val editorFocus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<JsonElement?>(null) }
    var textMode by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf("", "/outbounds", "/endpoints")) }
    var query by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<String>()) }
    var matchIndex by remember { mutableIntStateOf(0) }
    var parsing by remember { mutableStateOf(true) }
    var mutationBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuPath by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<JsonEdit?>(null) }
    var modeOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var templates by remember { mutableStateOf<JsonObject?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        templates = withContext(Dispatchers.IO) {
            context.assets.open("profile_editor_templates.json").bufferedReader().use { Json.parse(it.readText()).asJsonObject }
        }
    }
    LaunchedEffect(content) {
        parsing = true
        onValidationChanged(false)
        if (textMode) delay(350)
        try {
            root = withContext(Dispatchers.Default) { Json.parse(content) }
            error = null
            onValidationChanged(true)
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { error = context.getString(R.string.native_json_invalid); onValidationChanged(false) }
        finally { parsing = false }
    }
    LaunchedEffect(root, query) {
        delay(350)
        val value = root
        matches = if (value != null) withContext(Dispatchers.Default) { Json.search(value, query) } else emptyList()
        matchIndex = 0
    }
    val snapshot by produceState(initialValue = JsonTreeSnapshot(null, emptyList()), root, expanded) {
        val document = root
        val openPaths = expanded
        val visible = withContext(Dispatchers.Default) {
            buildList<JsonRow> {
                fun visit(value: JsonElement, path: String, label: String, depth: Int) {
                    add(JsonRow(path, label, value, depth))
                    if (path !in openPaths) return
                    if (value.isJsonObject) value.asJsonObject.entrySet().forEach { (key, child) -> visit(child, Json.child(path, key), key, depth + 1) }
                    else if (value.isJsonArray) value.asJsonArray.forEachIndexed { index, child -> visit(child, Json.child(path, index.toString()), index.toString(), depth + 1) }
                }
                document?.let { visit(it, "", "config", 0) }
            }
        }
        value = JsonTreeSnapshot(document, visible)
    }
    val rows = snapshot.rows
    val active = enabled && !mutationBusy && !parsing && (textMode || snapshot.source === root)
    val focused = matches.getOrNull(matchIndex)
    LaunchedEffect(focused) {
        if (focused != null) {
            var ancestor = Json.parent(focused)
            val parents = mutableSetOf("")
            while (ancestor.isNotEmpty()) { parents += ancestor; ancestor = Json.parent(ancestor) }
            expanded = expanded + parents
        }
    }
    LaunchedEffect(focused, rows) {
        val index = rows.indexOfFirst { it.path == focused }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    fun mutate(operation: (JsonElement) -> JsonElement) {
        val value = root ?: return
        if (!active) return
        mutationBusy = true
        onValidationChanged(false)
        scope.launch {
            try {
                val changed = withContext(Dispatchers.Default) {
                    val document = operation(value)
                    val text = Json.format(document)
                    Json.parse(text) // Revalidate size/depth after additions, too.
                    document to text
                }
                root = changed.first
                onChanged(changed.second)
                menuPath = null
                edit = null
                error = null
                onValidationChanged(true)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { error = failure.message ?: context.getString(R.string.native_json_invalid); onValidationChanged(true) }
            finally { mutationBusy = false }
        }
    }
    fun schemaPath(path: String): String {
        val names = mutableListOf("config")
        var owner = root ?: return "config"
        if (path.isNotEmpty()) for (segment in path.substring(1).split('/')) {
            val key = Json.key("/$segment")
            if (owner.isJsonObject) names += key
            owner = when {
                owner.isJsonObject -> owner.asJsonObject.get(key) ?: return ""
                owner.isJsonArray -> {
                    val index = key.toIntOrNull() ?: return ""
                    if (index !in 0 until owner.asJsonArray.size()) return ""
                    owner.asJsonArray[index]
                }
                else -> return ""
            }
        }
        return names.joinToString(".")
    }
    fun copy() {
        scope.launch {
            val value = root
            val text = if (textMode || value == null) content else withContext(Dispatchers.Default) { Json.format(value) }
            if (text.toByteArray(Charsets.UTF_8).size > com.hiddify.hiddify.nativeprofile.NativeProfileTransfer.MAX_CLIPBOARD_BYTES) {
                Toast.makeText(context, R.string.native_json_clipboard_large, Toast.LENGTH_SHORT).show()
            } else {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Config", text))
                Toast.makeText(context, R.string.native_json_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier.border(if (error == null) 1.dp else 2.dp, if (error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Config Editor: ", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
            Box {
                TextButton(enabled = active && error == null, onClick = { modeOpen = true }) {
                    Text(if (textMode) "text ▾" else "tree ▾", color = MaterialTheme.colorScheme.onPrimary)
                }
                DropdownMenu(modeOpen, { modeOpen = false }) {
                    DropdownMenuItem(text = { Text("Tree") }, onClick = { editorFocus.clearFocus(); textMode = false; modeOpen = false })
                    DropdownMenuItem(text = { Text("Text") }, onClick = { editorFocus.clearFocus(); textMode = true; modeOpen = false })
                }
            }
            if (!textMode) {
                BasicTextField(query, { query = it }, Modifier.width(120.dp).padding(8.dp), singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(stringResource(R.string.native_json_search), color = MaterialTheme.colorScheme.onPrimary); inner() } })
                if (query.isNotBlank()) Text("${if (matches.isEmpty()) 0 else matchIndex + 1}/${matches.size}", color = MaterialTheme.colorScheme.onPrimary)
                TextButton(enabled = matches.isNotEmpty(), onClick = { matchIndex = (matchIndex - 1 + matches.size) % matches.size }) {
                    Text("↑", color = MaterialTheme.colorScheme.onPrimary)
                }
                TextButton(enabled = matches.isNotEmpty(), onClick = { matchIndex = (matchIndex + 1) % matches.size }) {
                    Text("↓", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            TextButton(onClick = ::copy) { Text(stringResource(R.string.native_json_copy), color = MaterialTheme.colorScheme.onPrimary) }
            Box {
                IconButton(onClick = { toolsOpen = true }, enabled = active) {
                    Icon(painterResource(R.drawable.native_more), stringResource(R.string.native_profile_actions), tint = MaterialTheme.colorScheme.onPrimary)
                }
                DropdownMenu(toolsOpen, { toolsOpen = false }) {
                    if (textMode) DropdownMenuItem(text = { Text(stringResource(R.string.native_json_format)) }, enabled = error == null,
                        onClick = { toolsOpen = false; mutate { it } })
                    else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_json_expand)) }, onClick = {
                            toolsOpen = false
                            fun collect(value: JsonElement, path: String, result: MutableSet<String>) {
                                if (value.isJsonObject) { result += path; value.asJsonObject.entrySet().forEach { (key, child) -> collect(child, Json.child(path, key), result) } }
                                else if (value.isJsonArray) { result += path; value.asJsonArray.forEachIndexed { index, child -> collect(child, Json.child(path, index.toString()), result) } }
                            }
                            val document = root
                            scope.launch {
                                expanded = withContext(Dispatchers.Default) {
                                    val all = mutableSetOf<String>(); document?.let { collect(it, "", all) }; all
                                }
                            }
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.native_json_collapse)) }, onClick = { toolsOpen = false; expanded = emptySet() })
                    }
                }
            }
        }
        error?.let { Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (parsing || mutationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (textMode) BasicTextField(content, { onValidationChanged(false); onChanged(it) },
            Modifier.weight(1f).fillMaxWidth().padding(start = 5.dp, top = 8.dp, bottom = 8.dp), enabled = enabled && !mutationBusy,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp))
        else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxWidth
            val width = (rows.maxOfOrNull {
                val valueLength = if (it.value.isJsonPrimitive) it.value.asString.length.coerceAtMost(1024) else 30
                (it.depth * 18 + it.label.length.coerceAtMost(400) * 9 + maxOf(450, valueLength * 9) + 80).dp
            } ?: viewport).coerceAtLeast(viewport)
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(Modifier.width(width).fillMaxHeight().clickable { editorFocus.clearFocus() }, state = listState) {
                    itemsIndexed(rows, key = { _, row -> row.path }) { _, row ->
                        val container = row.value.isJsonObject || row.value.isJsonArray
                        Row(Modifier.fillMaxWidth().heightIn(min = 30.dp).background(when {
                            row.path == focused -> MaterialTheme.colorScheme.primaryContainer
                            row.path in matches -> MaterialTheme.colorScheme.secondaryContainer
                            else -> Color.Transparent
                        }).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { menuPath = row.path }, enabled = active, modifier = Modifier.size(30.dp)) {
                                    Icon(painterResource(R.drawable.native_more), stringResource(R.string.native_profile_actions), Modifier.size(16.dp))
                                }
                                DropdownMenu(menuPath == row.path, { menuPath = null }) {
                                    if (container) listOf("object", "array", "string", "boolean", "number", "null").forEach { type ->
                                        DropdownMenuItem(text = { Text("+ $type") }, onClick = { menuPath = null; error = null; edit = JsonEdit(row.path, type) })
                                    }
                                    if (!container) DropdownMenuItem(text = { Text(stringResource(R.string.native_json_edit_value)) }, onClick = { menuPath = null; error = null; edit = JsonEdit(row.path, "value") })
                                    if (row.value.isJsonArray && schemaPath(row.path) in listOf("config.outbounds", "config.endpoints")) {
                                        templates?.getAsJsonObject("protocolSchemaValues")?.entrySet()?.forEach { (name, value) ->
                                            DropdownMenuItem(text = { Text("+ $name") }, onClick = { menuPath = null; mutate { Json.add(it, row.path, "", value) } })
                                        }
                                    }
                                    if (row.value.isJsonObject) templates?.getAsJsonObject("exampleSchemaValues")?.getAsJsonObject(schemaPath(row.path))?.entrySet()?.forEach { (name, value) ->
                                        DropdownMenuItem(text = { Text("+ $name") }, onClick = { menuPath = null; error = null; edit = JsonEdit(row.path, "merge", value.asJsonObject) })
                                    }
                                    if (row.path.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.native_profile_delete)) },
                                        onClick = { menuPath = null; mutate { Json.remove(it, row.path) } })
                                }
                            }
                            Spacer(Modifier.width((18 * (row.depth + 1)).dp))
                            Text(if (container) { if (row.path in expanded) "▾" else "▸" } else " ",
                                Modifier.width(18.dp).clickable(enabled = container) {
                                    expanded = if (row.path in expanded) expanded - row.path else expanded + row.path
                                }, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            InlineJsonText(row.label, active && row.path.isNotEmpty() && root?.let { Json.at(it, Json.parent(row.path)).isJsonObject } == true,
                                maxWidth = 100, onEditingChanged = { onValidationChanged(!it) }, onCommit = { name -> if (name != row.label) mutate { Json.rename(it, row.path, name) } })
                            Text(if (container) " " else ": ", fontSize = 16.sp)
                            val summary = when {
                                row.value.isJsonObject -> if (row.path in expanded) "" else {
                                    val item = row.value.asJsonObject
                                    val type = item.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                                    val tag = item.get("tag")?.takeIf { it.isJsonPrimitive }?.asString
                                    "{$type [${tag ?: item.toString().take(20) + "..."}]}"
                                }
                                row.value.isJsonArray -> "{${row.value.asJsonArray.size()}}"
                                else -> if (row.value.isJsonPrimitive && row.value.asJsonPrimitive.isString) row.value.asString else row.value.toString()
                            }
                            val options = if (!container) templates?.getAsJsonObject("possibleValues")?.getAsJsonArray(schemaPath(row.path))?.map { it.asString }.orEmpty() else emptyList()
                            when {
                                row.value.isJsonPrimitive && row.value.asJsonPrimitive.isBoolean -> {
                                    Checkbox(row.value.asBoolean, { selected -> mutate { Json.replace(it, row.path, JsonPrimitive(selected)) } },
                                        enabled = active, modifier = Modifier.size(24.dp).scale(.75f))
                                    Text(summary, fontSize = 16.sp)
                                }
                                options.isNotEmpty() -> JsonValueChoices(summary, options, active) { selected -> mutate { Json.replace(it, row.path, JsonPrimitive(selected)) } }
                                row.value.isJsonPrimitive -> InlineJsonText(summary, active,
                                    maxWidth = if (row.value.asJsonPrimitive.isNumber) 80 else 400, onEditingChanged = { onValidationChanged(!it) }, onCommit = { text ->
                                        if (text != summary) {
                                            val replacement = runCatching {
                                                if (row.value.asJsonPrimitive.isString) JsonPrimitive(text) else Json.parse(text).also {
                                                    require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
                                                }
                                            }.getOrNull()
                                            if (replacement == null) error = context.getString(R.string.native_json_invalid)
                                            else mutate { Json.replace(it, row.path, replacement) }
                                        }
                                    })
                                else -> Text(summary, Modifier.clickable(enabled = active && !container) { edit = JsonEdit(row.path, "value") }, fontSize = 16.sp)
                            }

                        }
                    }
                }
            }
        }
    }
    edit?.let { request ->
        val document = root ?: return@let
        val value = runCatching { Json.at(document, request.path) }.getOrNull() ?: return@let
        JsonEditDialog(request, value, templates?.getAsJsonObject("possibleValues")?.getAsJsonArray(schemaPath(request.path))?.map { it.asString }.orEmpty(),
            operationError = error, enabled = active, onDismiss = { edit = null; error = null }, onApply = { name, replacement ->
                mutate { when (request.action) {
                    "rename" -> Json.rename(it, request.path, name)
                    "value" -> Json.replace(it, request.path, replacement)
                    "merge" -> Json.merge(it, request.path, request.template!!)
                    else -> Json.add(it, request.path, name, replacement)
                } }
            })
    }
}

@Composable
private fun JsonEditDialog(request: JsonEdit, value: JsonElement, choices: List<String>, operationError: String?, enabled: Boolean, onDismiss: () -> Unit, onApply: (String, JsonElement) -> Unit) {
    var name by remember(request) { mutableStateOf(if (request.action == "rename") Json.key(request.path) else {
        var candidate = "new_key_added"; var suffix = 1
        while (value.isJsonObject && value.asJsonObject.has(candidate)) candidate = "new_key_added_${suffix++}"
        candidate
    }) }
    val initial = if (request.action == "value") value else Json.empty(request.action.takeIf { it in listOf("object", "array", "string", "boolean", "number", "null") } ?: "null")
    var text by remember(request) { mutableStateOf(if (initial.isJsonPrimitive && initial.asJsonPrimitive.isString) initial.asString else initial.toString()) }
    var error by remember(request) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (request.action == "rename") R.string.native_json_rename else R.string.native_json_edit_value)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (error) Text(stringResource(R.string.native_json_invalid), color = MaterialTheme.colorScheme.error)
            if (request.action == "rename" || (value.isJsonObject && request.action !in listOf("value", "merge"))) NativeTextField(name, { name = it }, label = { Text(stringResource(R.string.native_profile_name)) }, singleLine = true)
            if (request.action == "merge") Text(stringResource(R.string.native_json_merge_confirm))
            else if (request.action != "rename") {
                if (initial.isJsonPrimitive && initial.asJsonPrimitive.isBoolean) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Boolean", Modifier.weight(1f)); Switch(text == "true", { text = it.toString() })
                } else NativeTextField(text, { text = it; error = false }, label = { Text(stringResource(R.string.native_json_value)) }, isError = error, maxLines = 5)
                if (choices.isNotEmpty()) Column(Modifier.heightIn(max = 160.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    choices.forEach { choice -> TextButton(onClick = { text = choice }) { Text(choice.ifEmpty { "\"\"" }) } }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = enabled, onClick = {
        try {
            val replacement = when {
                request.action in listOf("rename", "merge") -> initial
                initial.isJsonPrimitive && initial.asJsonPrimitive.isString -> JsonPrimitive(text)
                else -> Json.parse(text).also { parsed ->
                    if (initial.isJsonPrimitive && initial.asJsonPrimitive.isNumber) require(parsed.isJsonPrimitive && parsed.asJsonPrimitive.isNumber)
                }
            }
            onApply(name, replacement)
        } catch (_: Exception) { error = true }
    }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}


@Composable
private fun InlineJsonText(value: String, enabled: Boolean, maxWidth: Int, onEditingChanged: (Boolean) -> Unit, onCommit: (String) -> Unit) {
    var editing by remember(value) { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    var focusedOnce by remember(value) { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    if (editing) {
        BasicTextField(draft, { draft = it }, Modifier.widthIn(min = 20.dp, max = maxWidth.dp)
            .border(.3.dp, MaterialTheme.colorScheme.outline).padding(3.dp).focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused) focusedOnce = true
                else if (focusedOnce) { editing = false; focusedOnce = false; onEditingChanged(false); onCommit(draft) }
            }, enabled = enabled, singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        LaunchedEffect(Unit) { requester.requestFocus() }
    } else Text(value.ifEmpty { " " }, (if (value.isEmpty()) Modifier.width(maxWidth.dp) else Modifier)
        .clickable(enabled = enabled) { draft = value; onEditingChanged(true); editing = true }, maxLines = 1, overflow = TextOverflow.Clip, fontSize = 16.sp)
}

@Composable
private fun JsonValueChoices(value: String, choices: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled, contentPadding = PaddingValues(horizontal = 3.dp), modifier = Modifier.heightIn(min = 30.dp)) {
            Text(value.ifEmpty { "\"\"" } + " ↓", fontSize = 16.sp)
        }
        DropdownMenu(open, { open = false }) {
            (choices + value).distinct().forEach { choice -> DropdownMenuItem(text = { Text(choice.ifEmpty { "\"\"" }) },
                onClick = { open = false; onSelect(choice) }) }
        }
    }
}

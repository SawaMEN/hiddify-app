package com.hiddify.hiddify.nativeprofile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Keep potentially 8 MiB drafts across Activity recreation without putting them in a Bundle. */
class NativeProfileEditorSession : ViewModel() {
    var identity by mutableStateOf<String?>(null)
        private set
    var name by mutableStateOf("")
    var disableAutoUpdate by mutableStateOf(false)
    var intervalHours by mutableStateOf(0)
    var content by mutableStateOf("")
    private var original: NativeProfileEditor? = null

    fun load(editor: NativeProfileEditor) {
        val key = "${editor.profile.id}:${editor.profile.lastUpdate}"
        if (key == identity) return
        identity = key
        original = editor
        name = editor.name
        disableAutoUpdate = editor.disableAutoUpdate
        intervalHours = editor.updateIntervalHours ?: 0
        content = editor.content
    }

    fun reset() { identity = null; original = null }

    val changed: Boolean get() = original?.let {
        name != it.name || disableAutoUpdate != it.disableAutoUpdate ||
            intervalHours != (it.updateIntervalHours ?: 0) || content != it.content
    } ?: false
}

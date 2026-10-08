package com.hiddify.hiddify.nativecore

/** Retain the document across picker-related Activity recreation without a large Bundle. */
class NativeSettingsExportSession : androidx.lifecycle.ViewModel() {
    var payload: String? = null
        internal set

    override fun onCleared() { payload = null }
}

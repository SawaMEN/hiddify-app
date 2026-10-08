package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.nativeconnection.NativeConnectionOptions

/** Shared traffic policy for native UI and core configuration. */
object NetworkPrivacySettings {
    fun loadProxyPrivacy(context: Context): NativeProxyPrivacy = NativeProxyPrivacy.fromPreferences(
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all,
    )

    fun saveProxyPrivacy(context: Context, options: NativeProxyPrivacy) {
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
        options.preferenceValues().forEach { (key, value) -> editor.putBoolean(key, value) }
        check(editor.commit()) { "Could not save proxy privacy settings" }
    }

    fun loadConnection(context: Context): NativeConnectionOptions =
        NativeConnectionOptions.fromPreferences(
            context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all,
        )

    fun saveConnection(context: Context, options: NativeConnectionOptions) {
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
        options.preferences().forEach { (key, value) -> editor.putBoolean(key, value) }
        check(editor.commit()) { "Could not save connection policy" }
    }

    fun loadFilters(context: Context): NativeTrafficFilters = NativeTrafficFilters.fromPreferences(
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all,
    )

    /** One disk transaction prevents a partially enabled group of filters. Call on IO. */
    fun saveFilters(context: Context, filters: NativeTrafficFilters) {
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
        filters.preferenceValues().forEach { (key, value) -> editor.putBoolean(key, value) }
        check(editor.commit()) { "Could not save traffic filters" }
    }

    fun policy(context: Context): Map<String, Boolean> = loadConnection(context).corePolicy() +
        loadFilters(context).let { filters ->
            NativeTrafficFilter.entries.associate { it.key to (it in filters.enabled) }
        }
}

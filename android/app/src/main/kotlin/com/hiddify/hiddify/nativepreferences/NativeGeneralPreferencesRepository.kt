package com.hiddify.hiddify.nativepreferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class NativeLanguage(val value: String?) {
    SYSTEM(null), ENGLISH("en"), RUSSIAN("ru");
}

data class NativeGeneralPreferences(
    val language: NativeLanguage = NativeLanguage.SYSTEM,
    val hapticFeedback: Boolean = true,
)

class NativeGeneralPreferencesRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeGeneralPreferences {
        val values = preferences.all
        return NativeGeneralPreferences(
            language = NativeLanguage.entries.firstOrNull { it.value == values["flutter.locale"] } ?: NativeLanguage.SYSTEM,
            hapticFeedback = (values["flutter.haptic_feedback"] as? Boolean) ?: true,
        )
    }

    fun saveLanguage(language: NativeLanguage): NativeGeneralPreferences {
        val editor = preferences.edit()
        if (language.value == null) editor.remove("flutter.locale") else editor.putString("flutter.locale", language.value)
        check(editor.commit()) { "Could not save language" }
        return load()
    }

    fun saveHapticFeedback(enabled: Boolean): NativeGeneralPreferences {
        check(preferences.edit().putBoolean("flutter.haptic_feedback", enabled).commit()) { "Could not save haptic preference" }
        return load()
    }

    companion object {
        /** Scope resources to this Activity; leave the running VPN service and process locale intact. */
        fun localizedContext(base: Context, language: NativeLanguage): Context {
            val tag = language.value ?: return base
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}

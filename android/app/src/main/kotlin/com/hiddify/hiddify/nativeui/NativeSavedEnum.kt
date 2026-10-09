package com.hiddify.hiddify.nativeui

/**
 * A restored Compose navigation value can come from an older app version.
 * Never call valueOf() directly on persisted UI state: an obsolete enum name
 * would throw during composition and terminate the Activity.
 */
internal inline fun <reified T : Enum<T>> restoredEnumOrDefault(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

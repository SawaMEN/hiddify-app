package com.hiddify.hiddify.nativeui

import androidx.compose.runtime.saveable.Saver

// Large drafts remain usable in memory. After recreation, oversized drafts fall back
// to the screen's initializer (including persisted settings) instead of crashing onStop.
internal val NativeStringDraftSaver = Saver<String, String>(
    save = { it.takeIf(NativeSavedDraftBudget::fits) },
    restore = { it.takeIf(NativeSavedDraftBudget::fits) },
)

internal val NativeStringListDraftSaver = Saver<List<String>, ArrayList<String>>(
    save = { it.takeIf(NativeSavedDraftBudget::fits)?.let(::ArrayList) },
    restore = { it.takeIf(NativeSavedDraftBudget::fits) },
)

// Preserve the difference between automatic choices (null) and a manual empty list.
internal val NativeNullableStringListDraftSaver = Saver<List<String>?, ArrayList<String>>(
    save = { values ->
        if (values == null) arrayListOf()
        else (listOf("") + values).takeIf(NativeSavedDraftBudget::fits)?.let(::ArrayList)
    },
    restore = { encoded -> encoded.takeIf(NativeSavedDraftBudget::fits)?.takeIf { it.isNotEmpty() }?.drop(1) },
)

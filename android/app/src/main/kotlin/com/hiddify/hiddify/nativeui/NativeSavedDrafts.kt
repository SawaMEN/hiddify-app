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

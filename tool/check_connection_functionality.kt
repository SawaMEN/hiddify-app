package com.hiddify.hiddify

import com.hiddify.hiddify.constant.Status
import com.hiddify.hiddify.nativeconnection.NativeStartupCancellation
import com.hiddify.hiddify.nativeprofile.NativeProfileFileRemoval
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

// Run with kotlinc and kotlinx-coroutines-core-jvm on the compile/runtime classpath:
// production inputs: constant/Status.kt, ServiceStartTracker.kt,
// nativeconnection/NativeStartupCancellation.kt, nativeprofile/NativeProfileFileRemoval.kt.
// No Android device, Android stubs, native AAR or APK is required.
fun main() = runBlocking {
    val tracker = ServiceStartTracker()
    tracker.markIssued()
    repeat(4) { check(tracker.onStatus(Status.Stopped) == null) }
    check(tracker.onStatus(Status.Starting) == null)
    check(tracker.onStatus(Status.Stopped) == false)
    tracker.reset()
    tracker.markIssued()
    check(tracker.onStatus(Status.Started) == true)
    tracker.reset()
    check(tracker.onStatus(Status.Started) == null)

    // A stop issued before Go installs startCancel must be retried after Start enters.
    val starting = AtomicBoolean(true)
    val enteredNative = CompletableDeferred<Unit>()
    val cancellationDelivered = CompletableDeferred<Unit>()
    var stopCalls = 0
    val cancellation = launch {
        NativeStartupCancellation.cancelWhileStarting({ starting.get() }) {
            stopCalls++
            if (enteredNative.isCompleted) cancellationDelivered.complete(Unit)
        }
    }
    yield()
    check(stopCalls == 1 && !cancellationDelivered.isCompleted)
    enteredNative.complete(Unit)
    withTimeout(2000) { cancellationDelivered.await() }
    starting.set(false)
    withTimeout(2000) { cancellation.join() }
    check(stopCalls >= 2)
    NativeStartupCancellation.cancelWhileStarting({ false }) { error("Must not stop a later core") }

    val directory = Files.createTempDirectory("profile-delete-regression").toFile()
    try {
        val config = directory.resolve("active.json").apply { writeText("original config") }
        val transactionError = IllegalStateException("database commit failed")
        val failure = runCatching {
            NativeProfileFileRemoval.remove(config) {
                check(!config.exists())
                throw transactionError
            }
        }.exceptionOrNull()
        check(failure === transactionError)
        check(config.readText() == "original config")
        check(directory.listFiles()!!.size == 1)
        val result = NativeProfileFileRemoval.remove(config) { check(!config.exists()); "committed" }
        check(result == "committed" && !config.exists())
        check(directory.listFiles()!!.isEmpty())
        check(NativeProfileFileRemoval.remove(config) { "missing config also deletable" }.isNotEmpty())
        val invalid = directory.resolve("folder.json").apply { mkdir() }
        var committed = false
        check(runCatching { NativeProfileFileRemoval.remove(invalid) { committed = true } }.isFailure)
        check(!committed && invalid.isDirectory)
    } finally { directory.deleteRecursively() }
    println("Connection startup/cancellation and transactional profile deletion regression checks passed.")
}

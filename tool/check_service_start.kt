package com.hiddify.hiddify

import com.hiddify.hiddify.constant.Status

// Run without Android or a native AAR:
// kotlinc android/app/src/main/kotlin/com/hiddify/hiddify/constant/Status.kt \
//   android/app/src/main/kotlin/com/hiddify/hiddify/ServiceStartTracker.kt \
//   tool/check_service_start.kt -include-runtime -d /tmp/service-start-tests.jar
// java -jar /tmp/service-start-tests.jar
fun main() {
    val tracker = ServiceStartTracker()
    check(tracker.onStatus(Status.Started) == null) { "Ignore snapshots before a start is issued" }
    tracker.markIssued()
    repeat(3) {
        check(tracker.onStatus(Status.Stopped) == null) { "Binding Stopped snapshots must not fail startup" }
    }
    check(tracker.onStatus(Status.Starting) == null)
    check(tracker.onStatus(Status.Started) == true) { "Complete after service startup" }

    tracker.reset()
    check(!tracker.issued)
    check(tracker.onStatus(Status.Started) == null) { "Cancelled requests cannot complete later" }
    tracker.markIssued()
    check(tracker.onStatus(Status.Stopped) == null) { "A retry must forget previous Starting" }
    check(tracker.onStatus(Status.Starting) == null)
    check(tracker.onStatus(Status.Stopping) == null)
    check(tracker.onStatus(Status.Stopped) == false) { "An actual stop after Starting must fail" }

    tracker.reset()
    tracker.markIssued()
    check(tracker.onStatus(Status.Started) == true) { "Binding an already-started service must succeed" }
    println("Android service start regression checks passed.")
}

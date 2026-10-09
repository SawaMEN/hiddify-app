package com.hiddify.hiddify

import com.hiddify.hiddify.constant.Status
import org.junit.Test

class ServiceStartTrackerTest {
    @Test fun bindingSnapshotsRetriesAndCancellationPreserveStartState() {
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
    }
}

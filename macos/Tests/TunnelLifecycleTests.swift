import XCTest
@testable import VetrOFF

final class TunnelLifecycleTests: XCTestCase {
    func testNormalStartAndStop() throws {
        let state = TunnelLifecycle()
        XCTAssertEqual(state.snapshot, .idle)
        try state.beginStart()
        XCTAssertEqual(state.snapshot, .starting)
        try state.checkStarting()
        try state.didStart()
        XCTAssertEqual(state.snapshot, .running)
        XCTAssertTrue(state.requestStop())
        XCTAssertTrue(state.isStopping)
        state.finish()
        XCTAssertEqual(state.snapshot, .idle)
    }

    func testCancellationDuringStartupNeverBecomesConnected() throws {
        let state = TunnelLifecycle()
        try state.beginStart()
        XCTAssertTrue(state.requestStop())
        XCTAssertThrowsError(try state.didStart()) {
            XCTAssertEqual($0 as? TunnelLifecycleError, .cancelled)
        }
        XCTAssertThrowsError(try state.checkStarting())
        state.finish()
        try state.beginStart() // A retry is permitted after cleanup.
    }

    func testSecondStartDoesNotReplaceActiveRun() throws {
        let state = TunnelLifecycle()
        try state.beginStart()
        XCTAssertThrowsError(try state.beginStart()) {
            XCTAssertEqual($0 as? TunnelLifecycleError, .alreadyStarted)
        }
        XCTAssertFalse(state.isStopping)
    }

    func testRepeatedStopRequestIsIdempotent() throws {
        let state = TunnelLifecycle()
        XCTAssertFalse(state.requestStop())
        try state.beginStart()
        XCTAssertTrue(state.requestStop())
        XCTAssertFalse(state.requestStop())
        state.finish()
        XCTAssertFalse(state.requestStop())
    }

    func testConcurrentStopAndStartCannotBothWin() throws {
        let state = TunnelLifecycle()
        try state.beginStart()
        let group = DispatchGroup()
        let queue = DispatchQueue(label: "vetroff.lifecycle.tests", attributes: .concurrent)
        for _ in 0..<100 {
            group.enter()
            queue.async {
                _ = state.requestStop()
                group.leave()
            }
        }
        group.wait()
        XCTAssertThrowsError(try state.didStart())
        XCTAssertEqual(state.snapshot, .stopping)
    }
}

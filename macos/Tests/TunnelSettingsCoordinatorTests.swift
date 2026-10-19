import XCTest
import NetworkExtension
@testable import VetrOFF

/// Controls OS-style deferred completion without a real Network Extension.
private final class DeferredNetworkSettings: @unchecked Sendable {
    private let lock = NSLock()
    private var pending: [(isApply: Bool, completion: (Error?) -> Void)] = []
    private var history: [Bool] = []

    func set(
        _ settings: NEPacketTunnelNetworkSettings?,
        completion: @escaping (Error?) -> Void
    ) {
        lock.lock()
        history.append(settings != nil)
        pending.append((isApply: settings != nil, completion: completion))
        lock.unlock()
    }

    var operations: [Bool] {
        lock.lock()
        defer { lock.unlock() }
        return history
    }

    func completeNext(error: Error? = nil) {
        lock.lock()
        let item = pending.removeFirst()
        lock.unlock()
        item.completion(error)
    }
}

final class TunnelSettingsCoordinatorTests: XCTestCase {
    private func makeSettings() -> NEPacketTunnelNetworkSettings {
        NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
    }

    func testLateSettingsApplyCannotFollowRouteCleanup() {
        let mock = DeferredNetworkSettings()
        let coordinator = TunnelSettingsCoordinator { settings, callback in
            mock.set(settings, completion: callback)
        }
        var applied = false
        var cleared = false
        coordinator.apply(makeSettings()) { _ in applied = true }
        XCTAssertEqual(mock.operations, [true])

        coordinator.clear { error in
            XCTAssertNil(error)
            cleared = true
        }
        XCTAssertEqual(mock.operations, [true],
                       "Do not start clear before the asynchronous apply finishes.")
        mock.completeNext()
        XCTAssertTrue(applied)
        XCTAssertEqual(mock.operations, [true, false],
                       "The late apply must be followed by exactly one nil-settings cleanup.")
        XCTAssertFalse(cleared)
        mock.completeNext()
        XCTAssertTrue(cleared)
    }

    func testImmediateAndRepeatedClearIsIdempotent() {
        let mock = DeferredNetworkSettings()
        let coordinator = TunnelSettingsCoordinator { settings, callback in
            mock.set(settings, completion: callback)
        }
        var completed = 0
        coordinator.clear { _ in completed += 1 }
        coordinator.clear { _ in completed += 1 }
        XCTAssertEqual(mock.operations, [false])
        mock.completeNext()
        XCTAssertEqual(completed, 2)

        coordinator.clear { _ in completed += 1 }
        XCTAssertEqual(completed, 3)
        XCTAssertEqual(mock.operations, [false])
    }

    func testApplyIsRefusedOnceTeardownBegins() {
        let mock = DeferredNetworkSettings()
        let coordinator = TunnelSettingsCoordinator { settings, callback in
            mock.set(settings, completion: callback)
        }
        coordinator.clear { _ in }
        var refusal: Error?
        coordinator.apply(makeSettings()) { error in refusal = error }
        XCTAssertNotNil(refusal)
        XCTAssertEqual(mock.operations, [false])
        mock.completeNext()
    }

    func testConcurrentApplyIsRejected() {
        let mock = DeferredNetworkSettings()
        let coordinator = TunnelSettingsCoordinator { settings, callback in
            mock.set(settings, completion: callback)
        }
        coordinator.apply(makeSettings()) { _ in }
        var rejection: Error?
        coordinator.apply(makeSettings()) { error in rejection = error }
        XCTAssertNotNil(rejection)
        XCTAssertEqual(mock.operations, [true])
        mock.completeNext()
        coordinator.clear { _ in }
        XCTAssertEqual(mock.operations, [true, false])
        mock.completeNext()
    }

    func testCleanupFailurePropagatesAndDoesNotApplyNewSettings() {
        let mock = DeferredNetworkSettings()
        let coordinator = TunnelSettingsCoordinator { settings, callback in
            mock.set(settings, completion: callback)
        }
        var clearError: Error?
        coordinator.clear { clearError = $0 }
        mock.completeNext(error: NSError(domain: "test", code: 88))
        XCTAssertNotNil(clearError)
        coordinator.apply(makeSettings()) { XCTAssertNotNil($0) }
        XCTAssertEqual(mock.operations, [false])
    }
}

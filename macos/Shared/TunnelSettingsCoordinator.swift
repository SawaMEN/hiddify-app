import Foundation
@preconcurrency import NetworkExtension

/// Serializes macOS network-settings application against tunnel teardown.
///
/// In particular, an OpenTun() timeout must never allow a delayed
/// setTunnelNetworkSettings(settings) callback to race *after* the final
/// setTunnelNetworkSettings(nil) cleanup and reinstall VPN routes.
final class TunnelSettingsCoordinator: @unchecked Sendable {
    typealias Setter = (
        NEPacketTunnelNetworkSettings?,
        @escaping (Error?) -> Void
    ) -> Void

    private let setter: Setter
    private let lock = NSLock()
    private var applying = false
    private var clearRequested = false
    private var clearing = false
    private var clearResult: Error?
    private var clearFinished = false
    private var clearCallbacks: [(Error?) -> Void] = []

    init(setter: @escaping Setter) {
        self.setter = setter
    }

    func apply(
        _ settings: NEPacketTunnelNetworkSettings,
        completion: @escaping (Error?) -> Void
    ) {
        lock.lock()
        let accepted = !applying && !clearRequested
        if accepted { applying = true }
        lock.unlock()

        guard accepted else {
            completion(NSError(
                domain: "app.vetroff.client.macos.NetworkSettings",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey:
                    "Network settings are already applying or tunnel teardown has started."]
            ))
            return
        }

        setter(settings) { [self] error in
            lock.lock()
            applying = false
            let mustClear = clearRequested && !clearing
            if mustClear { clearing = true }
            lock.unlock()

            // Wake Go's OpenTun waiter regardless of whether it timed out.
            completion(error)
            // If cancellation/timeout happened during the OS operation,
            // clear routes only AFTER the late apply callback returns.
            if mustClear { performClear() }
        }
    }

    func clear(completion: @escaping (Error?) -> Void) {
        lock.lock()
        if clearFinished {
            let previousError = clearResult
            lock.unlock()
            completion(previousError)
            return
        }

        clearCallbacks.append(completion)
        clearRequested = true
        let startClear = !applying && !clearing
        if startClear { clearing = true }
        lock.unlock()
        if startClear { performClear() }
    }

    private func performClear() {
        setter(nil) { [self] error in
            lock.lock()
            clearResult = error
            clearFinished = true
            let callbacks = clearCallbacks
            clearCallbacks.removeAll()
            lock.unlock()
            for callback in callbacks {
                callback(error)
            }
        }
    }
}

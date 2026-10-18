import Foundation

enum TunnelLifecycleError: LocalizedError, Equatable {
    case alreadyStarted
    case cancelled

    var errorDescription: String? {
        switch self {
        case .alreadyStarted: "Попытка повторного запуска VPN."
        case .cancelled: "Запуск VPN отменён системным запросом остановки."
        }
    }
}

enum TunnelLifecycleState: Equatable {
    case idle
    case starting
    case running
    case stopping
}

/// Thread-safe *control plane* used across PacketTunnelProvider callbacks,
/// Go's synchronous OpenTun callback and the serialized core worker.
final class TunnelLifecycle: @unchecked Sendable {
    private let lock = NSLock()
    private var state: TunnelLifecycleState = .idle

    func beginStart() throws {
        lock.lock()
        defer { lock.unlock() }
        guard state == .idle else { throw TunnelLifecycleError.alreadyStarted }
        state = .starting
    }

    func checkStarting() throws {
        lock.lock()
        defer { lock.unlock() }
        guard state == .starting else { throw TunnelLifecycleError.cancelled }
    }

    /// A stop request wins even when the Go start call is currently blocking.
    /// The worker will observe cancellation before it reports success.
    @discardableResult
    func requestStop() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        switch state {
        case .idle, .stopping:
            return false
        case .starting, .running:
            state = .stopping
            return true
        }
    }

    func didStart() throws {
        lock.lock()
        defer { lock.unlock() }
        guard state == .starting else { throw TunnelLifecycleError.cancelled }
        state = .running
    }

    func finish() {
        lock.lock()
        state = .idle
        lock.unlock()
    }

    var isStopping: Bool {
        lock.lock()
        defer { lock.unlock() }
        return state == .stopping || state == .idle
    }

    var snapshot: TunnelLifecycleState {
        lock.lock()
        defer { lock.unlock() }
        return state
    }
}

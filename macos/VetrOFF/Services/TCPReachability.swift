import Foundation
@preconcurrency import Network

enum TCPReachabilityError: LocalizedError {
    case unsupported
    case timedOut
    case failed

    var errorDescription: String? {
        switch self {
        case .unsupported: "Этот формат сервера не поддерживает простой TCP-тест. Используйте проверку протокола после настройки VPN."
        case .timedOut: "TCP-соединение не установлено за 5 секунд."
        case .failed: "Удалённый TCP-порт недоступен."
        }
    }
}

struct TCPTarget: Sendable, Equatable {
    let host: String
    let port: UInt16
}

/// Connection timing to the selected *TCP port*, not VPN speed or a
/// protocol-level latency check. UDP transports (TUIC/Hysteria2/QUIC) are
/// intentionally excluded to avoid misleading the user.
enum TCPReachability {
    static func target(for server: ProfileServer) -> TCPTarget? {
        let tcpSchemes: Set<String> = [
            "vless", "trojan", "ss", "ssh", "socks", "socks5",
            "naive", "naive+https", "anytls"
        ]
        guard tcpSchemes.contains(server.scheme),
              let uri = URLComponents(string: server.rawURI),
              let host = uri.host, !host.isEmpty,
              !host.contains(" ") && !host.contains("/"),
              let number = uri.port, (1...65535).contains(number)
        else { return nil }
        return TCPTarget(host: host, port: UInt16(number))
    }

    static func measure(_ server: ProfileServer) async throws -> Int {
        guard let target = target(for: server),
              let port = NWEndpoint.Port(rawValue: target.port)
        else { throw TCPReachabilityError.unsupported }

        return try await Task.detached(priority: .utility) {
            let connection = NWConnection(
                host: NWEndpoint.Host(target.host), port: port, using: .tcp
            )
            let result = TCPProbeResult()
            let queue = DispatchQueue(label: "app.vetroff.client.macos.tcp-probe", qos: .utility)
            let started = DispatchTime.now().uptimeNanoseconds
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    let millis = Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000)
                    result.complete(.success(millis))
                case .failed:
                    result.complete(.failure(TCPReachabilityError.failed))
                default:
                    break
                }
            }
            connection.start(queue: queue)
            // The probe runs on a detached utility thread, never the UI
            // executor. Timeout is bounded even if Network.framework stalls.
            let received = result.wait(timeout: .now() + .seconds(5))
            connection.cancel()
            guard received else { throw TCPReachabilityError.timedOut }
            return try result.value()
        }.value
    }
}

private final class TCPProbeResult: @unchecked Sendable {
    private let semaphore = DispatchSemaphore(value: 0)
    private let lock = NSLock()
    private var stored: Result<Int, Error>?

    func complete(_ result: Result<Int, Error>) {
        lock.lock()
        let shouldSignal = stored == nil
        if shouldSignal { stored = result }
        lock.unlock()
        if shouldSignal { semaphore.signal() }
    }

    func wait(timeout: DispatchTime) -> Bool {
        semaphore.wait(timeout: timeout) == .success
    }

    func value() throws -> Int {
        lock.lock()
        let current = stored
        lock.unlock()
        guard let current else { throw TCPReachabilityError.timedOut }
        return try current.get()
    }
}

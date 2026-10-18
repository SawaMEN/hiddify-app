import Foundation

/// Fixed, credentials-free diagnostics exchanged over the OS-provided
/// NETunnelProviderSession channel. No Go gRPC server or local listener.
enum TunnelDiagnosticsProtocol {
    static let statusRequest = Data("VETROFF_STATUS_V1".utf8)

    static func accepts(_ data: Data) -> Bool {
        data == statusRequest
    }
}

struct TunnelDiagnosticReply: Codable, Sendable, Equatable {
    let version: Int
    let coreLinked: Bool
    let lifecycleState: String
    let trafficVerified: Bool

    init(coreLinked: Bool, lifecycleState: String) {
        self.version = 1
        self.coreLinked = coreLinked
        self.lifecycleState = lifecycleState
        // A connected core does NOT prove that packets were forwarded.
        self.trafficVerified = false
    }
}

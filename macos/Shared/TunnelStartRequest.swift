import Foundation

enum TunnelStartRequestError: LocalizedError, Equatable {
    case missingProfileID
    case invalidProfileID

    var errorDescription: String? {
        switch self {
        case .missingProfileID: "VPN-профиль не выбран."
        case .invalidProfileID: "Недопустимый идентификатор VPN-профиля."
        }
    }
}

/// Only opaque IDs may cross NETunnelProviderSession's options boundary.
/// Subscription URLs, credentials and JSON must stay in shared Keychain.
struct TunnelStartRequest: Equatable, Sendable {
    static let profileIDKey = "profileID"
    let profileID: UUID

    init(profileID: UUID) {
        self.profileID = profileID
    }

    init(options: [String: NSObject]?) throws {
        guard let value = options?[Self.profileIDKey] as? String else {
            throw TunnelStartRequestError.missingProfileID
        }
        guard let id = UUID(uuidString: value) else {
            throw TunnelStartRequestError.invalidProfileID
        }
        self.profileID = id
    }

    var options: [String: NSObject] {
        [Self.profileIDKey: profileID.uuidString as NSString]
    }
}

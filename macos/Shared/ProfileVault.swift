import Foundation
import Security

/// Provisioned builds share the signed group. A preview host can manage
/// profiles in its own login Keychain; extensions never use that fallback.
enum ProfileVaultError: LocalizedError {
    case missingAccessGroup
    case keychain(OSStatus)
    case missingItem
    case invalidUTF8

    var errorDescription: String? {
        switch self {
        case .missingAccessGroup:
            "В подписи приложения отсутствует общая группа Keychain."
        case .keychain(let status):
            "Ошибка доступа к Keychain (код \(status))."
        case .missingItem:
            "Профиль не найден в Keychain."
        case .invalidUTF8:
            "Профиль содержит некорректный UTF-8."
        }
    }
}

enum ProfileVault {
    private static let service = "app.vetroff.client.macos.profiles"
    private static let cacheService = service + ".subscription-cache"
    private static let activeService = service + ".active-source"
    private static let metadataService = service + ".subscription-metadata"

    private static var signedGroup: String? {
        guard let task = SecTaskCreateFromSelf(kCFAllocatorDefault),
              let groups = SecTaskCopyValueForEntitlement(
                task, "keychain-access-groups" as CFString, nil
              ) as? [String] else { return nil }
        return groups.first { $0.hasSuffix(".app.vetroff.client.macos.shared") }
    }

    private static func query(
        _ id: UUID, bundle: Bundle, service serviceName: String = service,
        local: Bool = false
    ) throws -> [String: Any] {
        let scope = try ProfileVaultScope.resolve(
            bundleID: bundle.bundleIdentifier, sharedGroup: local ? nil : signedGroup
        )
        var request: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName + (scope == .application ? ".local" : ""),
            kSecAttrAccount as String: id.uuidString
        ]
        if case .shared(let group) = scope {
            request[kSecAttrAccessGroup as String] = group
            request[kSecUseDataProtectionKeychain as String] = true
        }
        return request
    }

    static func save(id: UUID, source: String, bundle: Bundle = .main) throws {
        let bytes = Data(source.utf8)
        var request = try query(id, bundle: bundle)
        request[kSecValueData as String] = bytes
        let status = SecItemAdd(request as CFDictionary, nil)
        guard status == errSecSuccess else { throw ProfileVaultError.keychain(status) }
    }

    static func source(id: UUID, bundle: Bundle = .main) throws -> String {
        try get(id: id, slot: service, bundle: bundle)
    }

    // The original subscription URL and its fetched contents are secrets.
    // Both live in the shared signed Keychain, not UserDefaults or files.
    private static func put(id: UUID, content: String, slot: String, bundle: Bundle) throws {
        let bytes = Data(content.utf8)
        guard bytes.count <= 8 * 1024 * 1024 else { throw ProfileVaultError.invalidUTF8 }
        let key = try query(id, bundle: bundle, service: slot)
        let status = SecItemUpdate(key as CFDictionary, [
            kSecValueData as String: bytes
        ] as CFDictionary)
        if status == errSecItemNotFound {
            var request = key
            request[kSecValueData as String] = bytes
            let added = SecItemAdd(request as CFDictionary, nil)
            guard added == errSecSuccess else { throw ProfileVaultError.keychain(added) }
        } else if status != errSecSuccess {
            throw ProfileVaultError.keychain(status)
        }
    }

    private static func get(id: UUID, slot: String, bundle: Bundle) throws -> String {
        var request = try query(id, bundle: bundle, service: slot)
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &item)
        if status == errSecItemNotFound {
            // Only the host application may migrate preview credentials from
            // its private login Keychain into a provisioned shared group.
            // PacketTunnel must always fail closed without its entitlement.
            if signedGroup != nil && bundle.bundleIdentifier == ProfileVaultScope.applicationID {
                var legacy = try query(id, bundle: bundle, service: slot, local: true)
                legacy[kSecReturnData as String] = true
                legacy[kSecMatchLimit as String] = kSecMatchLimitOne
                var localItem: CFTypeRef?
                let localStatus = SecItemCopyMatching(legacy as CFDictionary, &localItem)
                if localStatus == errSecSuccess, let bytes = localItem as? Data,
                   let text = String(data: bytes, encoding: .utf8) {
                    try put(id: id, content: text, slot: slot, bundle: bundle)
                    return text
                }
                if localStatus != errSecItemNotFound { throw ProfileVaultError.keychain(localStatus) }
            }
            throw ProfileVaultError.missingItem
        }
        guard status == errSecSuccess else { throw ProfileVaultError.keychain(status) }
        guard let data = item as? Data, let text = String(data: data, encoding: .utf8)
        else { throw ProfileVaultError.invalidUTF8 }
        return text
    }

    static func saveSubscriptionCache(id: UUID, source: String, bundle: Bundle = .main) throws {
        try put(id: id, content: source, slot: cacheService, bundle: bundle)
    }

    static func subscriptionCache(id: UUID, bundle: Bundle = .main) throws -> String {
        try get(id: id, slot: cacheService, bundle: bundle)
    }

    static func saveActiveSource(id: UUID, source: String, bundle: Bundle = .main) throws {
        try put(id: id, content: source, slot: activeService, bundle: bundle)
    }

    /// Called by the extension. No subscription tokens, URLs or actual
    /// credentials cross NETunnelProviderSession IPC.
    static func connectableSource(id: UUID, bundle: Bundle = .main) throws -> String {
        do {
            return try get(id: id, slot: activeService, bundle: bundle)
        } catch ProfileVaultError.missingItem {
            let raw = try source(id: id, bundle: bundle)
            // Never mistake an unrefreshed subscription URL for proxy config.
            guard !raw.trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased().hasPrefix("https://")
            else { throw ProfileVaultError.missingItem }
            return raw
        }
    }

    static func replaceSource(id: UUID, source: String, bundle: Bundle = .main) throws {
        try put(id: id, content: source, slot: service, bundle: bundle)
    }

    static func saveMetadata(id: UUID, source: String, bundle: Bundle = .main) throws {
        try put(id: id, content: source, slot: metadataService, bundle: bundle)
    }

    static func metadata(id: UUID, bundle: Bundle = .main) throws -> String {
        try get(id: id, slot: metadataService, bundle: bundle)
    }

    static func delete(id: UUID, bundle: Bundle = .main) throws {
        var firstError: OSStatus?
        for slot in [service, cacheService, activeService, metadataService] {
            var queries = [try query(id, bundle: bundle, service: slot)]
            if signedGroup != nil && bundle.bundleIdentifier == ProfileVaultScope.applicationID {
                queries.append(try query(id, bundle: bundle, service: slot, local: true))
            }
            for request in queries {
                let status = SecItemDelete(request as CFDictionary)
                if status != errSecSuccess && status != errSecItemNotFound && firstError == nil {
                    firstError = status
                }
            }
        }
        if let firstError { throw ProfileVaultError.keychain(firstError) }
    }
}

/// Kept separate from Security calls so the extension boundary is testable.
enum ProfileVaultScope: Equatable {
    static let applicationID = "app.vetroff.client.macos"
    case application
    case shared(String)

    static func resolve(bundleID: String?, sharedGroup: String?) throws -> Self {
        if let sharedGroup { return .shared(sharedGroup) }
        guard bundleID == applicationID else { throw ProfileVaultError.missingAccessGroup }
        return .application
    }
}

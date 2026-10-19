import Foundation
import Security

/// Both the app and its Packet Tunnel extension use the same explicitly
/// provisioned Keychain access group. The group is resolved at signing time.
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

    private static func group(bundle _: Bundle) throws -> String {
        // Read the actual code-signing entitlement instead of trusting an
        // Info.plist build-variable expansion. The extension has a different
        // bundle identifier but receives the exact same shared group.
        guard let task = SecTaskCreateFromSelf(kCFAllocatorDefault),
              let value = SecTaskCopyValueForEntitlement(
                  task,
                  "keychain-access-groups" as CFString,
                  nil
              ) as? [String],
              let accessGroup = value.first(where: {
                  $0.hasSuffix(".app.vetroff.client.macos.shared")
              })
        else { throw ProfileVaultError.missingAccessGroup }
        return accessGroup
    }

    private static func query(_ id: UUID, bundle: Bundle, service serviceName: String = service) throws -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: id.uuidString,
            kSecAttrAccessGroup as String: try group(bundle: bundle)
        ]
    }

    static func save(id: UUID, source: String, bundle: Bundle = .main) throws {
        let bytes = Data(source.utf8)
        var request = try query(id, bundle: bundle)
        request[kSecValueData as String] = bytes
        let status = SecItemAdd(request as CFDictionary, nil)
        guard status == errSecSuccess else { throw ProfileVaultError.keychain(status) }
    }

    static func source(id: UUID, bundle: Bundle = .main) throws -> String {
        var request = try query(id, bundle: bundle)
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var value: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &value)
        if status == errSecItemNotFound { throw ProfileVaultError.missingItem }
        guard status == errSecSuccess else { throw ProfileVaultError.keychain(status) }
        guard let data = value as? Data,
              let text = String(data: data, encoding: .utf8)
        else { throw ProfileVaultError.invalidUTF8 }
        return text
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
        if status == errSecItemNotFound { throw ProfileVaultError.missingItem }
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

    static func delete(id: UUID, bundle: Bundle = .main) throws {
        var firstError: OSStatus?
        for slot in [service, cacheService, activeService] {
            let status = SecItemDelete(try query(id, bundle: bundle, service: slot) as CFDictionary)
            if status != errSecSuccess && status != errSecItemNotFound && firstError == nil {
                firstError = status
            }
        }
        if let firstError { throw ProfileVaultError.keychain(firstError) }
    }
}

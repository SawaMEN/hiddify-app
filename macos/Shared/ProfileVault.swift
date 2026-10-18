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

    private static func query(_ id: UUID, bundle: Bundle) throws -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
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

    static func delete(id: UUID, bundle: Bundle = .main) throws {
        let status = SecItemDelete(try query(id, bundle: bundle) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw ProfileVaultError.keychain(status)
        }
    }
}

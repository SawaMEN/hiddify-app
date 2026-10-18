import Foundation
import Observation
import Security

struct SavedProfile: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var name: String
    let format: ProfileSourceKind
    let createdAt: Date
}

enum ProfileStoreError: LocalizedError {
    case keychain(OSStatus)
    case profileMissing

    var errorDescription: String? {
        switch self {
        case let .keychain(code):
            "Не удалось получить доступ к Keychain (код \(code))."
        case .profileMissing:
            "Исходная конфигурация отсутствует в Keychain."
        }
    }
}

/// Profile metadata lives in preferences. Full subscription URLs,
/// credentials, server secrets and JSON are stored only in macOS Keychain.
@MainActor
@Observable
final class ProfileStore {
    private(set) var profiles: [SavedProfile] = []

    @ObservationIgnored private let service = "app.vetroff.client.macos.profiles"
    @ObservationIgnored private let metadataKey = "macos.profile.metadata.v1"

    init() {
        if let data = UserDefaults.standard.data(forKey: metadataKey),
           let decoded = try? JSONDecoder().decode([SavedProfile].self, from: data) {
            profiles = decoded
        }
    }

    @discardableResult
    func add(_ source: String, name: String? = nil) throws -> SavedProfile {
        let format = try ProfileSource.classify(source)
        let trimmedName = name?.trimmingCharacters(in: .whitespacesAndNewlines)
        let record = SavedProfile(
            id: UUID(),
            name: (trimmedName?.isEmpty == false ? trimmedName! : "Профиль \(profiles.count + 1)"),
            format: format,
            createdAt: Date()
        )
        let query = keychainQuery(record.id)
        var inserted = query
        inserted[kSecValueData as String] = Data(source.utf8)
        let status = SecItemAdd(inserted as CFDictionary, nil)
        guard status == errSecSuccess else { throw ProfileStoreError.keychain(status) }
        do {
            let newProfiles = profiles + [record]
            try persist(newProfiles)
            profiles = newProfiles
            return record
        } catch {
            SecItemDelete(query as CFDictionary)
            throw error
        }
    }

    func source(for id: UUID) throws -> String {
        var query = keychainQuery(id)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { throw ProfileStoreError.profileMissing }
        guard status == errSecSuccess else { throw ProfileStoreError.keychain(status) }
        guard let data = result as? Data else { throw ProfileStoreError.profileMissing }
        return try ProfileSource.decodeUTF8(data)
    }

    func delete(_ id: UUID) throws {
        // Commit metadata first; Keychain removal after commit prevents stale
        // metadata from pointing at a deleted secret on save failure.
        let remaining = profiles.filter { $0.id != id }
        try persist(remaining)
        profiles = remaining
        let status = SecItemDelete(keychainQuery(id) as CFDictionary)
        if status != errSecSuccess && status != errSecItemNotFound {
            throw ProfileStoreError.keychain(status)
        }
    }

    private func persist(_ list: [SavedProfile]) throws {
        let data = try JSONEncoder().encode(list)
        UserDefaults.standard.set(data, forKey: metadataKey)
    }

    private func keychainQuery(_ id: UUID) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id.uuidString
        ]
    }
}

import Foundation
import Observation

struct SavedProfile: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var name: String
    let format: ProfileSourceKind
    let createdAt: Date
}

/// Profile metadata lives in preferences. Full subscription URLs,
/// credentials, server secrets and JSON are stored only in macOS Keychain.
@MainActor
@Observable
final class ProfileStore {
    private(set) var profiles: [SavedProfile] = []

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
        try ProfileVault.save(id: record.id, source: source)
        do {
            let newProfiles = profiles + [record]
            try persist(newProfiles)
            profiles = newProfiles
            return record
        } catch {
            try? ProfileVault.delete(id: record.id)
            throw error
        }
    }

    func source(for id: UUID) throws -> String {
        return try ProfileSource.decodeUTF8(Data(ProfileVault.source(id: id).utf8))
    }

    func delete(_ id: UUID) throws {
        // Commit metadata first; Keychain removal after commit prevents stale
        // metadata from pointing at a deleted secret on save failure.
        let remaining = profiles.filter { $0.id != id }
        try persist(remaining)
        profiles = remaining
        try ProfileVault.delete(id: id)
    }

    private func persist(_ list: [SavedProfile]) throws {
        let data = try JSONEncoder().encode(list)
        UserDefaults.standard.set(data, forKey: metadataKey)
    }

}

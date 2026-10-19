import Foundation
import Observation

struct SavedProfile: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var name: String
    let format: ProfileSourceKind
    let createdAt: Date
    var lastRefreshedAt: Date?
    var selectedServerIndex: Int?
}

enum ProfileStoreError: LocalizedError {
    case notFound
    case notSubscription
    case refreshInProgress
    case invalidServer

    var errorDescription: String? {
        switch self {
        case .notFound: "Профиль не найден."
        case .notSubscription: "Этот профиль не является подпиской."
        case .refreshInProgress: "Подписка уже обновляется."
        case .invalidServer: "Выбранный сервер больше не доступен."
        }
    }
}

/// Metadata only in UserDefaults. Raw URLs, subscription payloads and the
/// exact currently selected outbound remain inside the shared Keychain.
@MainActor
@Observable
final class ProfileStore {
    private(set) var profiles: [SavedProfile] = []
    private(set) var serverLists: [UUID: [ProfileServer]] = [:]
    private(set) var refreshing: Set<UUID> = []

    @ObservationIgnored private let metadataKey = "macos.profile.metadata.v1"

    init() {
        if let data = UserDefaults.standard.data(forKey: metadataKey),
           let decoded = try? JSONDecoder().decode([SavedProfile].self, from: data) {
            profiles = decoded
        }
        for record in profiles {
            if let content = try? catalogSource(for: record) {
                serverLists[record.id] = ProfileSource.servers(in: content)
            }
        }
    }

    @discardableResult
    func add(_ source: String, name: String? = nil) throws -> SavedProfile {
        let format = try ProfileSource.classify(source)
        let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines)
        let servers = format == .proxyURI ? ProfileSource.servers(in: source) : []
        let record = SavedProfile(
            id: UUID(),
            name: (trimmed?.isEmpty == false ? trimmed! : "Профиль \(profiles.count + 1)"),
            format: format,
            createdAt: Date(),
            lastRefreshedAt: nil,
            selectedServerIndex: servers.isEmpty ? nil : 0
        )

        try ProfileVault.save(id: record.id, source: source)
        do {
            if let first = servers.first {
                try ProfileVault.saveActiveSource(id: record.id, source: first.rawURI)
            }
            let next = profiles + [record]
            try persist(next)
            profiles = next
            serverLists[record.id] = servers
            return record
        } catch {
            try? ProfileVault.delete(id: record.id)
            throw error
        }
    }

    func isReadyForDebugConnect(_ id: UUID) -> Bool {
        guard let profile = profiles.first(where: { $0.id == id }) else { return false }
        if profile.format == .subscriptionURL {
            return profile.lastRefreshedAt != nil &&
                (try? ProfileVault.connectableSource(id: id)) != nil
        }
        return true
    }

    func servers(for id: UUID) -> [ProfileServer] { serverLists[id] ?? [] }

    func source(for id: UUID) throws -> String {
        try ProfileSource.decodeUTF8(Data(ProfileVault.source(id: id).utf8))
    }

    func selectServer(id: UUID, index: Int) throws {
        guard let position = profiles.firstIndex(where: { $0.id == id }) else {
            throw ProfileStoreError.notFound
        }
        let servers = serverLists[id] ?? []
        guard servers.indices.contains(index) else { throw ProfileStoreError.invalidServer }
        // The extension reads exactly this source; it must never receive a
        // Swift-reassembled URI or an unauthenticated URL via VPN options.
        try ProfileVault.saveActiveSource(id: id, source: servers[index].rawURI)
        var next = profiles
        next[position].selectedServerIndex = index
        try persist(next)
        profiles = next
    }

    func refresh(_ id: UUID) async throws {
        guard let position = profiles.firstIndex(where: { $0.id == id }) else {
            throw ProfileStoreError.notFound
        }
        guard profiles[position].format == .subscriptionURL else {
            throw ProfileStoreError.notSubscription
        }
        guard refreshing.insert(id).inserted else {
            throw ProfileStoreError.refreshInProgress
        }
        defer { refreshing.remove(id) }

        let url = try ProfileVault.source(id: id)
        let content = try await SubscriptionDownloader.fetch(url)
        // Profile positions may change while network I/O suspends this actor
        // (e.g. another profile is removed). Re-resolve by stable UUID.
        guard let updatedPosition = profiles.firstIndex(where: { $0.id == id }) else {
            throw ProfileStoreError.notFound
        }
        let servers = ProfileSource.servers(in: content)
        let oldActive = try? ProfileVault.connectableSource(id: id)
        let selectedIndex = ProfileSource.preservingSelection(
            in: servers, previousURI: oldActive
        )
        let effective = selectedIndex.map { servers[$0].rawURI } ?? content

        // The prior working profile remains intact if downloading/decoding
        // fails. Only validated, bounded content is stored in Keychain.
        try ProfileVault.saveSubscriptionCache(id: id, source: content)
        try ProfileVault.saveActiveSource(id: id, source: effective)

        var next = profiles
        next[updatedPosition].lastRefreshedAt = Date()
        next[updatedPosition].selectedServerIndex = selectedIndex
        try persist(next)
        profiles = next
        serverLists[id] = servers
    }

    /// Refresh on launch only if stale; explicit user refresh is still
    /// available. Failed updates retain the last known-good Keychain cache.
    func refreshStaleSubscriptions(maxAge: TimeInterval = 12 * 60 * 60) async {
        let stale = profiles.filter {
            $0.format == .subscriptionURL &&
            ($0.lastRefreshedAt.map { Date().timeIntervalSince($0) > maxAge } ?? true)
        }
        for record in stale where !refreshing.contains(record.id) {
            try? await refresh(record.id)
        }
    }

    func delete(_ id: UUID) throws {
        guard !refreshing.contains(id) else { throw ProfileStoreError.refreshInProgress }
        let remaining = profiles.filter { $0.id != id }
        try persist(remaining)
        profiles = remaining
        serverLists.removeValue(forKey: id)
        try ProfileVault.delete(id: id)
    }

    private func catalogSource(for record: SavedProfile) throws -> String {
        if record.format == .subscriptionURL {
            return try ProfileVault.subscriptionCache(id: record.id)
        }
        return try ProfileVault.source(id: record.id)
    }

    private func persist(_ list: [SavedProfile]) throws {
        let data = try JSONEncoder().encode(list)
        UserDefaults.standard.set(data, forKey: metadataKey)
    }
}

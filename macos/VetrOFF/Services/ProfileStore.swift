import Foundation
import Observation

struct SavedProfile: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var name: String
    var format: ProfileSourceKind
    let createdAt: Date
    var lastRefreshedAt: Date?
    var selectedServerIndex: Int?
    var automaticName: Bool? = nil
    var subscription: SubscriptionMetadata? = nil
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
    private(set) var subscriptionLinks: [UUID: SubscriptionMetadata] = [:]
    private(set) var refreshErrors: [UUID: String] = [:]

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let download: @Sendable (String) async throws -> SubscriptionPayload
    @ObservationIgnored private let metadataKey = "macos.profile.metadata.v1"

    init(defaults: UserDefaults = .standard,
         download: @escaping @Sendable (String) async throws -> SubscriptionPayload = SubscriptionDownloader.fetch) {
        self.defaults = defaults
        self.download = download
        if let data = defaults.data(forKey: metadataKey),
           let decoded = try? JSONDecoder().decode([SavedProfile].self, from: data) {
            profiles = decoded
        }
        for record in profiles {
            if record.lastRefreshedAt != nil, let text = try? ProfileVault.metadata(id: record.id),
               let data = text.data(using: .utf8),
               let metadata = try? JSONDecoder().decode(SubscriptionMetadata.self, from: data) {
                subscriptionLinks[record.id] = metadata
            }
            if let content = try? catalogSource(for: record) {
                serverLists[record.id] = ProfileSource.servers(in: content)
            }
        }
    }

    @discardableResult
    func add(_ source: String, name: String? = nil) throws -> SavedProfile {
        let linkName = ProfileImport.suggestedName(source)
        let source = try ProfileImport.normalize(source)
        let format = try ProfileSource.classify(source)
        let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines)
        let servers = format == .subscriptionURL ? [] : ProfileSource.servers(in: source)
        let automaticTitle = SubscriptionMetadata.parse(content: source).title ?? linkName ?? servers.first?.displayName ?? servers.first?.title
        let selection = servers.firstIndex(where: \.selectable)
        let record = SavedProfile(
            id: UUID(),
            name: (trimmed?.isEmpty == false ? String(trimmed!.prefix(100)) : (automaticTitle ?? "Профиль \(profiles.count + 1)" )),
            format: format,
            createdAt: Date(),
            lastRefreshedAt: nil,
            selectedServerIndex: selection,
            automaticName: trimmed?.isEmpty != false,
            subscription: nil
        )

        try ProfileVault.save(id: record.id, source: source)
        do {
            if let selection {
                try ProfileVault.saveActiveSource(id: record.id, source: servers[selection].rawURI)
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

    func isReadyForConnect(_ id: UUID) -> Bool {
        guard let profile = profiles.first(where: { $0.id == id }) else { return false }
        if !servers(for: id).isEmpty && !servers(for: id).contains(where: \.selectable) { return false }
        if profile.format == .subscriptionURL {
            return profile.lastRefreshedAt != nil &&
                (try? ProfileVault.connectableSource(id: id)) != nil
        }
        // Reading the effective source also migrates a preview's selected URI
        // into the provisioned group before PacketTunnel tries to read it.
        return (try? ProfileVault.connectableSource(id: id)) != nil
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
        guard servers.indices.contains(index), servers[index].selectable else { throw ProfileStoreError.invalidServer }
        // The extension reads exactly this source; it must never receive a
        // Swift-reassembled URI or an unauthenticated URL via VPN options.
        try ProfileVault.saveActiveSource(id: id, source: servers[index].rawURI)
        var next = profiles
        next[position].selectedServerIndex = index
        try persist(next)
        profiles = next
    }

    @discardableResult
    func refresh(_ id: UUID) async throws -> Bool {
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
        let response: SubscriptionPayload
        do {
            response = try await download(url)
            refreshErrors.removeValue(forKey: id)
        } catch {
            refreshErrors[id] = (error as? LocalizedError)?.errorDescription ?? "Не удалось загрузить подписку. Проверьте подключение и повторите попытку."
            throw error
        }
        let content = response.content
        // Profile positions may change while network I/O suspends this actor
        // (e.g. another profile is removed). Re-resolve by stable UUID.
        guard let updatedPosition = profiles.firstIndex(where: { $0.id == id }) else {
            throw ProfileStoreError.notFound
        }
        let cached = try? ProfileVault.subscriptionCache(id: id)
        let oldActive = try? ProfileVault.connectableSource(id: id)
        let changed = ProfileSource.subscriptionContentChanged(previous: cached, downloaded: content)
        let needsRepair = oldActive == nil || serverLists[id]?.isEmpty != false
        var selectedIndex = profiles[updatedPosition].selectedServerIndex
        var updatedServers: [ProfileServer]? = nil

        if changed || needsRepair {
            let servers = ProfileSource.servers(in: content)
            selectedIndex = ProfileSource.preservingSelection(
                in: servers, previousURI: oldActive
            )
            let effective = selectedIndex.map { servers[$0].rawURI } ?? content

            // Failed download or validation never overwrites working credentials.
            // Byte-identical refreshes avoid needless shared-Keychain writes.
            try ProfileVault.saveSubscriptionCache(id: id, source: content)
            try ProfileVault.saveActiveSource(id: id, source: effective)
            updatedServers = servers
        }

        var next = profiles
        next[updatedPosition].lastRefreshedAt = Date()
        next[updatedPosition].selectedServerIndex = selectedIndex
        let autoName = next[updatedPosition].automaticName ?? next[updatedPosition].name.hasPrefix("Профиль ")
        if autoName, let title = response.metadata.title { next[updatedPosition].name = title }
        var summary = response.metadata
        summary.website = nil; summary.support = nil
        next[updatedPosition].subscription = summary
        next[updatedPosition].automaticName = autoName
        let metadataBytes = try JSONEncoder().encode(response.metadata)
        if let text = String(data: metadataBytes, encoding: .utf8) { try ProfileVault.saveMetadata(id: id, source: text) }
        subscriptionLinks[id] = response.metadata
        try persist(next)
        profiles = next
        if let updatedServers { serverLists[id] = updatedServers }
        return changed
    }

    /// Refresh on launch only if stale; explicit user refresh is still
    /// available. Failed updates retain the last known-good Keychain cache.
    func refreshStaleSubscriptions(
        maxAge: TimeInterval = 12 * 60 * 60,
        shouldContinue: () -> Bool = { true }
    ) async {
        let stale = profiles.filter {
            $0.format == .subscriptionURL &&
            ($0.lastRefreshedAt.map { Date().timeIntervalSince($0) > maxAge } ?? true)
        }
        for record in stale where !refreshing.contains(record.id) {
            guard shouldContinue() else { break }
            try? await refresh(record.id)
        }
    }

    func delete(_ id: UUID) throws {
        guard !refreshing.contains(id) else { throw ProfileStoreError.refreshInProgress }
        try ProfileVault.delete(id: id)
        let remaining = profiles.filter { $0.id != id }
        try persist(remaining)
        profiles = remaining
        serverLists.removeValue(forKey: id)
        subscriptionLinks.removeValue(forKey: id)
        refreshErrors.removeValue(forKey: id)
    }

    func rename(_ id: UUID, name: String) throws {
        guard let index = profiles.firstIndex(where: { $0.id == id }) else {
            throw ProfileStoreError.notFound
        }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw ProfileSourceError.empty }
        var next = profiles
        next[index].name = String(trimmed.prefix(100))
        next[index].automaticName = false
        try persist(next)
        profiles = next
    }

    func restoreAutomaticName(_ id: UUID) throws {
        guard let index = profiles.firstIndex(where: { $0.id == id }) else { throw ProfileStoreError.notFound }
        var next = profiles
        next[index].automaticName = true
        if let title = next[index].subscription?.title { next[index].name = title }
        try persist(next); profiles = next
    }

    func duplicate(_ id: UUID) throws -> SavedProfile {
        guard let record = profiles.first(where: { $0.id == id }) else { throw ProfileStoreError.notFound }
        return try add(source(for: id), name: record.name + " — копия")
    }

    /// Validate before changing any existing source; replacing a subscription
    /// clears its ready flag so the previous provider is never used by mistake.
    func replace(_ id: UUID, source: String) throws {
        guard !refreshing.contains(id) else { throw ProfileStoreError.refreshInProgress }
        guard let index = profiles.firstIndex(where: { $0.id == id }) else { throw ProfileStoreError.notFound }
        let normalized = try ProfileImport.normalize(source)
        let kind = try ProfileSource.classify(normalized)
        let servers = kind == .subscriptionURL ? [] : ProfileSource.servers(in: normalized)
        let selection = servers.firstIndex(where: \.selectable)
        let previous = try ProfileVault.source(id: id)
        let previousActive = try? ProfileVault.connectableSource(id: id)
        do {
            try ProfileVault.replaceSource(id: id, source: normalized)
            if let selection { try ProfileVault.saveActiveSource(id: id, source: servers[selection].rawURI) }
            else if kind != .subscriptionURL { try ProfileVault.saveActiveSource(id: id, source: normalized) }
            var next = profiles
            next[index].format = kind
            next[index].lastRefreshedAt = nil
            next[index].selectedServerIndex = selection
            next[index].subscription = nil
            try persist(next); profiles = next
            serverLists[id] = servers
            subscriptionLinks.removeValue(forKey: id)
            refreshErrors.removeValue(forKey: id)
        } catch {
            try? ProfileVault.replaceSource(id: id, source: previous)
            if let previousActive { try? ProfileVault.saveActiveSource(id: id, source: previousActive) }
            throw error
        }
    }

    private func catalogSource(for record: SavedProfile) throws -> String {
        if record.format == .subscriptionURL {
            guard record.lastRefreshedAt != nil else { throw ProfileStoreError.notFound }
            return try ProfileVault.subscriptionCache(id: record.id)
        }
        return try ProfileVault.source(id: record.id)
    }

    private func persist(_ list: [SavedProfile]) throws {
        let data = try JSONEncoder().encode(list)
        defaults.set(data, forKey: metadataKey)
    }
}

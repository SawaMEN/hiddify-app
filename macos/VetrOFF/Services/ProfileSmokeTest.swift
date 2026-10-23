import Foundation

/// Opt-in packaging regression. Uses a fresh UUID, tests the same store as
/// the UI and removes all test data; never opens or modifies user profiles.
@MainActor
enum ProfileSmokeTest {
    static func run(store: ProfileStore) throws {
        let subscription = try store.add("https://example.invalid/subscription", name: "Subscription regression")
        defer { try? store.delete(subscription.id) }
        guard try store.source(for: subscription.id) == "https://example.invalid/subscription",
              !store.isReadyForConnect(subscription.id) else {
            throw ProfileSourceError.invalidSubscription
        }
        let source = "vless://smoke@example.invalid:443#Smoke"
        let record = try store.add(source, name: "Packaging regression")
        defer { try? store.delete(record.id) }
        guard try store.source(for: record.id) == source,
              store.servers(for: record.id).count == 1,
              store.isReadyForConnect(record.id) else {
            throw ProfileSourceError.invalidSubscription
        }
        try store.rename(record.id, name: "Renamed regression")
        try store.selectServer(id: record.id, index: 0)
        guard store.profiles.first(where: { $0.id == record.id })?.name == "Renamed regression" else {
            throw ProfileStoreError.notFound
        }
        try store.delete(record.id)
        guard !store.profiles.contains(where: { $0.id == record.id }) else {
            throw ProfileStoreError.notFound
        }
        FileHandle.standardOutput.write(Data("VETROFF_PROFILE_STORE_READY\n".utf8))
    }
}

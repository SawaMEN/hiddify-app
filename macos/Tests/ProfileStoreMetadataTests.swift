import XCTest
@testable import VetrOFF

final class ProfileStoreMetadataTests: XCTestCase {
    @MainActor
    func testRefreshAutomaticTitleRenameAndRestore() async throws {
        let domain = "VetrOFF.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: domain))
        defer { defaults.removePersistentDomain(forName: domain) }
        let store = ProfileStore(defaults: defaults) { _ in
            SubscriptionPayload(content: "snell://secret@example.org:443?version=4#Snell",
                metadata: SubscriptionMetadata.parse(headers: ["profile-title": "Provider title", "subscription-userinfo": "download=100; total=1000"]))
        }
        let record = try store.add("https://example.org/sub")
        defer { try? store.delete(record.id) }
        try await store.refresh(record.id)
        XCTAssertEqual(store.profiles.first?.name, "Provider title")
        XCTAssertEqual(store.profiles.first?.subscription?.download, 100)
        XCTAssertTrue(store.isReadyForConnect(record.id))
        try store.rename(record.id, name: "My name")
        try await store.refresh(record.id)
        XCTAssertEqual(store.profiles.first?.name, "My name")
        try store.restoreAutomaticName(record.id)
        XCTAssertEqual(store.profiles.first?.name, "Provider title")
        try store.replace(record.id, source: "https://example.net/new-sub")
        XCTAssertFalse(store.isReadyForConnect(record.id))
        XCTAssertNil(store.profiles.first?.subscription)
        XCTAssertTrue(store.servers(for: record.id).isEmpty)
    }
    @MainActor
    func testInvalidEditLeavesSourceIntactAndUnsupportedFirstNodeIsNotSelected() throws {
        let domain = "VetrOFF.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: domain))
        defer { defaults.removePersistentDomain(forName: domain) }
        let store = ProfileStore(defaults: defaults)
        let source = "tg://test@example.org\nsnell://test@example.org:443?version=4"
        let record = try store.add(source)
        defer { try? store.delete(record.id) }
        XCTAssertEqual(store.profiles.first?.selectedServerIndex, 1)
        XCTAssertThrowsError(try store.replace(record.id, source: "not a config"))
        XCTAssertEqual(try store.source(for: record.id), source)
    }
}

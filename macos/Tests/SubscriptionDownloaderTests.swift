import XCTest
@testable import VetrOFF

final class SubscriptionDownloaderTests: XCTestCase {
    func testAcceptsPublicHTTPSHostname() throws {
        try SubscriptionDownloader.validate(URL(string: "https://subscription.example.org/api?token=hidden")!)
    }

    func testRejectsCleartextHTTPAndCredentialURL() {
        XCTAssertThrowsError(try SubscriptionDownloader.validate(URL(string: "http://example.org/")!))
        XCTAssertThrowsError(try SubscriptionDownloader.validate(URL(string: "https://user:password@example.org/")!))
    }

    func testRejectsLocalhostAndPrivateLiteralIPs() {
        for host in ["localhost", "127.0.0.1", "192.168.1.1",
                     "10.1.2.3", "[::1]", "server.local", "api.internal"] {
            XCTAssertThrowsError(
                try SubscriptionDownloader.validate(URL(string: "https://\(host)/sub")!)
            )
        }
    }

    func testRejectsURLFragments() {
        XCTAssertThrowsError(
            try SubscriptionDownloader.validate(URL(string: "https://example.com/sub#token")!)
        )
    }
}

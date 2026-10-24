import XCTest
@testable import VetrOFF

final class SubscriptionDownloaderTests: XCTestCase {
    func testAcceptsPublicHTTPSHostname() throws {
        try SubscriptionDownloader.validate(URL(string: "https://subscription.example.org/api?token=hidden")!)
    }

    func testAcceptsExplicitHTTPAndRejectsCredentialURL() throws {
        try SubscriptionDownloader.validate(URL(string: "http://example.org/")!)
        try SubscriptionDownloader.validate(URL(string: "https://8.8.8.8/sub")!)
        try SubscriptionDownloader.validate(URL(string: "https://[2606:4700:4700::1111]/sub")!)
        XCTAssertThrowsError(try SubscriptionDownloader.validate(URL(string: "https://user:password@example.org/")!))
    }

    func testRejectsLocalhostAndPrivateLiteralIPs() {
        for host in ["localhost", "127.0.0.1", "192.168.1.1",
                     "10.1.2.3", "127.1", "2130706433", "[::1]", "server.local", "api.internal"] {
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

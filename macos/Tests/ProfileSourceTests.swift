import XCTest
@testable import VetrOFF

final class ProfileSourceTests: XCTestCase {
    func testClassifiesJSON() throws {
        XCTAssertEqual(try ProfileSource.classify(#"{"outbounds":[]}"#), .json)
    }

    func testRejectsInvalidJSON() {
        XCTAssertThrowsError(try ProfileSource.classify(#"{"outbounds":[}"#)) {
            XCTAssertEqual($0 as? ProfileSourceError, .invalidJSON)
        }
    }

    func testClassifiesDirectURI() throws {
        XCTAssertEqual(try ProfileSource.classify("vless://uuid@example.com:443"), .proxyURI)
        XCTAssertEqual(try ProfileSource.classify("hy2://secret@example.org"), .proxyURI)
    }

    func testClassifiesSubscriptionURL() throws {
        XCTAssertEqual(try ProfileSource.classify("https://example.com/subscription"), .subscriptionURL)
    }

    func testRejectsUnsupportedFormat() {
        XCTAssertThrowsError(try ProfileSource.classify("definitely-not-a-profile"))
    }

    func testRejectsInvalidUTF8() {
        XCTAssertThrowsError(try ProfileSource.decodeUTF8(Data([0xC3, 0x28]))) {
            XCTAssertEqual($0 as? ProfileSourceError, .invalidUTF8)
        }
    }

    func testPreservesRawSource() throws {
        let source = "vless://UUID@example.org:443?type=ws#A%20B"
        XCTAssertEqual(try ProfileSource.decodeUTF8(Data(source.utf8)), source)
    }

    func testRejectsOversizedInput() {
        XCTAssertThrowsError(try ProfileSource.classify(String(repeating: "x", count: ProfileSource.maxBytes + 1))) {
            XCTAssertEqual($0 as? ProfileSourceError, .tooLarge)
        }
    }
    func testMultiLineLinksAreSelectableWithoutChangingRawURI() throws {
        let first = "vless://test@example.com:443#Primary"
        let second = "trojan://secret@example.net:443#Secondary"
        let input = "# Comment\n" + first + "\n" + second + "\n"
        XCTAssertEqual(try ProfileSource.classify(input), .proxyURI)
        let servers = ProfileSource.servers(in: input)
        XCTAssertEqual(servers.count, 2)
        XCTAssertEqual(servers[0].rawURI, first)
        XCTAssertEqual(servers[1].rawURI, second)
        XCTAssertEqual(servers[0].scheme, "vless")
    }

    func testUnknownServerInBatchMustNotSilentlyDisappear() {
        let input = "vless://uuid@example.com\nunknown://secret@example.com"
        XCTAssertThrowsError(try ProfileSource.classify(input))
        XCTAssertTrue(ProfileSource.servers(in: input).isEmpty)
    }

    func testBase64SubscriptionDecodesAndKeepsLinks() throws {
        let links = "vless://uuid@one.example:443\ntrojan://pass@two.example:443"
        let base64 = Data(links.utf8).base64EncodedString()
        let decoded = try ProfileSource.decodeSubscription(Data(base64.utf8))
        XCTAssertEqual(decoded, links)
        XCTAssertEqual(ProfileSource.servers(in: decoded).count, 2)
    }

    func testSubscriptionAcceptsClashYAMLAndJSON() throws {
        let yaml = "proxies:\n  - name: Node1\n    type: ss\n    server: example.org"
        XCTAssertEqual(try ProfileSource.classify(yaml), .yaml)
        XCTAssertEqual(try ProfileSource.decodeSubscription(Data(yaml.utf8)), yaml)
        let json = #"{"outbounds":[{"type":"direct","tag":"direct"}]}"#
        XCTAssertEqual(try ProfileSource.decodeSubscription(Data(json.utf8)), json)
    }

    func testRejectsSubscriptionContainingAnotherSubscriptionURL() {
        XCTAssertThrowsError(
            try ProfileSource.decodeSubscription(Data("https://private.example/sub".utf8))
        ) {
            XCTAssertEqual($0 as? ProfileSourceError, .invalidSubscription)
        }
    }

    func testServerListDoesNotExposeCredentialsInTitle() {
        let entries = ProfileSource.servers(in: "trojan://topsecret@example.net:443#Europe")
        XCTAssertEqual(entries.count, 1)
        XCTAssertFalse(entries[0].title.contains("topsecret"))
    }

    func testNoServerCountExplosion() {
        let links = Array(repeating: "vless://uuid@example.net:443", count: ProfileSource.maxServers + 1)
        XCTAssertTrue(ProfileSource.servers(in: links.joined(separator: "\n")).isEmpty)
    }

    func testSelectionSurvivesSubscriptionNodeReorder() {
        let old = "vless://old@example.com:443#Old"
        let selected = "trojan://pw@example.net:443#Selected"
        let next = "vless://new@example.org:443#New"
        let servers = ProfileSource.servers(in: [next, old, selected].joined(separator: "\n"))
        XCTAssertEqual(ProfileSource.preservingSelection(in: servers, previousURI: selected), 2)
        XCTAssertEqual(ProfileSource.preservingSelection(in: servers, previousURI: "missing"), 0)
        XCTAssertNil(ProfileSource.preservingSelection(in: [], previousURI: selected))
    }

    func testFriendlyFragmentLabelsDoNotExposeCredentials() {
        let server = ProfileSource.servers(
            in: "trojan://secret@example.net:443#Europe%20One"
        ).first!
        XCTAssertEqual(server.title, "Europe One · TROJAN")
        XCTAssertFalse(server.title.contains("secret"))
        let unsafe = ProfileSource.servers(
            in: "trojan://secret@example.net:443#token%3Dsecret"
        ).first!
        XCTAssertEqual(unsafe.title, "Сервер 1 · TROJAN")
    }

}

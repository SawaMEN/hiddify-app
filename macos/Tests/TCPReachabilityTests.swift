import XCTest
@testable import VetrOFF

final class TCPReachabilityTests: XCTestCase {
    func testExtractsEndpointWithoutCredentialMaterial() {
        let link = ProfileServer(
            index: 0, scheme: "trojan",
            rawURI: "trojan://hidden-secret@example.org:443?security=tls#Europe"
        )
        XCTAssertEqual(TCPReachability.target(for: link), TCPTarget(host: "example.org", port: 443))
    }

    func testTCPVLESSAndNaiveURLs() {
        XCTAssertEqual(
            TCPReachability.target(for: ProfileServer(
                index: 0, scheme: "vless",
                rawURI: "vless://uuid@node.example.net:8443?type=ws"
            )),
            TCPTarget(host: "node.example.net", port: 8443)
        )
        XCTAssertEqual(
            TCPReachability.target(for: ProfileServer(
                index: 0, scheme: "naive+https",
                rawURI: "naive+https://login:secret@proxy.example.net:443"
            )),
            TCPTarget(host: "proxy.example.net", port: 443)
        )
    }

    func testUDPOnlyProtocolsNeverReportTCPAsVPNLatency() {
        for value in [
            ("hysteria2", "hysteria2://secret@node.example.net:443"),
            ("tuic", "tuic://uuid:secret@node.example.net:443"),
            ("naive+quic", "naive+quic://user:pass@node.example.net:443")
        ] {
            XCTAssertNil(TCPReachability.target(for: ProfileServer(
                index: 0, scheme: value.0, rawURI: value.1
            )))
        }
    }

    func testMissingPortOrInvalidEndpointIsUnsupported() {
        let links = [
            "trojan://secret@example.org",
            "trojan://secret@example.org:0",
            "trojan://secret@example.org:65536"
        ]
        for (i, text) in links.enumerated() {
            XCTAssertNil(TCPReachability.target(for: ProfileServer(
                index: i, scheme: "trojan", rawURI: text
            )))
        }
    }
}

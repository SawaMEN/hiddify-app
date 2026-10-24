import XCTest
@testable import VetrOFF

final class ConfigurationCatalogTests: XCTestCase {
    func testAllPanelSchemesAreVisibleAndSelectable() throws {
        let schemes = ["snell", "mieru", "mierus", "vpn", "tt", "trusttunnel", "sudoku", "openflux", "pingtunnel", "olcrtc", "psiphon", "dnstt", "ssconf", "xvless", "phttps"]
        let links = schemes.map { "\($0)://example.test/payload" } + ["fptn:payload"]
        let text = links.joined(separator: "\n")
        XCTAssertEqual(try ProfileSource.classify(text), .proxyURI)
        let nodes = ProfileSource.servers(in: text)
        XCTAssertEqual(nodes.count, links.count)
        XCTAssertTrue(nodes.allSatisfy(\.selectable))
        XCTAssertEqual(nodes.map(\.rawURI), links)
    }
    func testChainIsAnIndivisibleSelection() {
        let chain = "vless://id@one.example:443 -> snell://secret@two.example:443?version=4"
        let nodes = ProfileSource.servers(in: chain)
        XCTAssertEqual(nodes.count, 1); XCTAssertEqual(nodes.first?.rawURI, chain)
        XCTAssertEqual(nodes.first?.selectable, true)
    }
    func testOlcRTCTitleNeverExposesEncryptionKey() {
        let link = "olcrtc://jitsi?datachannel@room#very-private-encryption-key$My node"
        let title = ProfileSource.servers(in: link)[0].title
        XCTAssertTrue(title.contains("My node")); XCTAssertFalse(title.contains("private"))
    }
    func testVmessTitleComesFromPS() throws {
        let object = #"{"ps":"VMess Europe","id":"secret"}"#
        let link = "vmess://" + Data(object.utf8).base64EncodedString()
        XCTAssertEqual(ProfileSource.servers(in: link)[0].title, "VMess Europe · VMESS")
    }
    func testJSONCAndArrayOfCompleteConfigurations() throws {
        let json = #"[{"remarks":"One","outbounds":[{"type":"socks","server":"one.example","server_port":1080}]},{"remarks":"Two","outbounds":[{"type":"socks","server":"two.example","server_port":1080}]}]"#
        let nodes = ProfileSource.servers(in: json)
        XCTAssertEqual(nodes.count, 2)
        XCTAssertEqual(nodes[1].displayName, "Two")
        XCTAssertFalse(nodes[1].rawURI.contains("one.example"))
        let jsonc = "// generated\n{\"outbounds\":[{\"type\":\"socks\",\"server\":\"example.org\",\"server_port\":1080}],\"url\":\"https://example.org\"}"
        XCTAssertEqual(try ProfileSource.classify(jsonc), .json)
        XCTAssertEqual(ProfileSource.servers(in: jsonc).count, 1)
    }
    func testJSONSelectionRetainsOnlyItsDependencyChain() throws {
        let json = #"{"outbounds":[{"type":"socks","tag":"selected","server":"entry.example","server_port":1080,"detour":"hop"},{"type":"socks","tag":"hop","server":"exit.example","server_port":1081},{"type":"socks","tag":"other","server":"other.example","server_port":1082}]}"#
        let source = ProfileSource.servers(in: json)[0].rawURI
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(source.utf8)) as? [String: Any])
        let nodes = try XCTUnwrap(object["outbounds"] as? [[String: Any]])
        XCTAssertEqual(nodes.count, 2)
        XCTAssertEqual(nodes[0]["detour"] as? String, "hop §hide§")
        XCTAssertEqual(nodes[1]["tag"] as? String, "hop §hide§")
        XCTAssertEqual(nodes[1]["server"] as? String, "exit.example")
        XCTAssertFalse(source.contains("other.example"))
    }
    func testClashYAMLSelectionAndWireGuardINI() throws {
        let yaml = "proxies:\n  - name: First\n    type: ss\n    server: first.example\n    port: 443\n    cipher: aes-128-gcm\n    password: test\n  - name: Second\n    type: trojan\n    server: second.example\n    port: 443\n    password: test\n"
        let servers = ProfileSource.servers(in: yaml)
        XCTAssertEqual(servers.count, 2)
        XCTAssertEqual(servers[1].displayName, "Second")
        XCTAssertFalse(servers[1].rawURI.contains("first.example"))
        let ini = "[Interface]\nPrivateKey = test\nAddress = 10.0.0.2/32\n[Peer]\nPublicKey = test\nEndpoint = example.org:443"
        XCTAssertEqual(try ProfileSource.classify(ini), .wireguard)
        XCTAssertEqual(ProfileSource.servers(in: ini).first?.rawURI, ini)
    }
    func testUnknownProtocolDoesNotDiscardWorkingNodes() throws {
        let content = "tg://secret@some.example\nvless://id@example.org:443"
        let nodes = ProfileSource.servers(in: content)
        XCTAssertEqual(nodes.count, 2)
        XCTAssertFalse(nodes[0].selectable); XCTAssertTrue(nodes[1].selectable)
        XCTAssertEqual(ProfileSource.preservingSelection(in: nodes, previousURI: nil), 1)
    }
}

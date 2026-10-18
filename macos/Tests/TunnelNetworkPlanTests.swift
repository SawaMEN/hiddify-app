import XCTest
import NetworkExtension
@testable import VetrOFF

final class TunnelNetworkPlanTests: XCTestCase {
    private func validPlan() -> TunnelNetworkPlan {
        TunnelNetworkPlan(
            ipv4: [TunnelIPv4Address(address: "172.19.0.1", mask: "255.255.255.0")],
            ipv6: [TunnelIPv6Address(address: "fd00::1", prefixLength: 64)],
            dns: ["1.1.1.1", "2606:4700:4700::1111"],
            mtu: 1500,
            fullTunnel: true
        )
    }

    func testFullTunnelGetsDefaultRoutesAndDNS() throws {
        let settings = try validPlan().makeSettings()
        XCTAssertEqual(settings.mtu?.intValue, 1500)
        XCTAssertEqual(settings.ipv4Settings?.includedRoutes?.count, 1)
        XCTAssertEqual(settings.ipv6Settings?.includedRoutes?.count, 1)
        XCTAssertEqual(settings.dnsSettings?.servers.count, 2)
        XCTAssertEqual(settings.dnsSettings?.matchDomains, [""])
    }

    func testRejectsEmptyDNSForFullTunnel() {
        let p = TunnelNetworkPlan(
            ipv4: [TunnelIPv4Address(address: "10.0.0.1", mask: "255.0.0.0")],
            ipv6: [],
            dns: [],
            mtu: 1400,
            fullTunnel: true
        )
        XCTAssertThrowsError(try p.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .missingDNS)
        }
    }

    func testRejectsInvalidIPv6Prefix() {
        let p = TunnelNetworkPlan(
            ipv4: [],
            ipv6: [TunnelIPv6Address(address: "fd00::1", prefixLength: 129)],
            dns: [],
            mtu: 1500,
            fullTunnel: false
        )
        XCTAssertThrowsError(try p.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .invalidPrefix)
        }
    }

    func testRejectsBadMTU() {
        let p = TunnelNetworkPlan(
            ipv4: [TunnelIPv4Address(address: "10.0.0.1", mask: "255.255.255.0")],
            ipv6: [],
            dns: ["1.1.1.1"],
            mtu: 1100,
            fullTunnel: true
        )
        XCTAssertThrowsError(try p.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .invalidMTU)
        }
    }

    func testRejectsInvalidIPAddress() {
        let p = TunnelNetworkPlan(
            ipv4: [TunnelIPv4Address(address: "999.99.1.1", mask: "255.255.255.0")],
            ipv6: [],
            dns: ["1.1.1.1"],
            mtu: 1500,
            fullTunnel: true
        )
        XCTAssertThrowsError(try p.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .invalidIPv4)
        }
    }
}

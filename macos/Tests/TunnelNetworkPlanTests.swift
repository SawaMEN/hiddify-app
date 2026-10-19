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
            ipv6: [TunnelIPv6Address(address: "fd00::1", prefixLength: 64)],
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
            ipv6: [TunnelIPv6Address(address: "fd00::1", prefixLength: 64)],
            dns: ["1.1.1.1"],
            mtu: 1500,
            fullTunnel: true
        )
        XCTAssertThrowsError(try p.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .invalidIPv4)
        }
    }
    func testExplicitSplitRoutesAreHonored() throws {
        var plan = validPlan()
        plan.includedIPv4Routes = [TunnelIPv4Address(address: "10.0.0.0", mask: "255.0.0.0")]
        plan.excludedIPv4Routes = [TunnelIPv4Address(address: "10.4.0.0", mask: "255.255.0.0")]
        plan.includedIPv6Routes = [TunnelIPv6Address(address: "2001:db8::", prefixLength: 32)]
        plan.excludedIPv6Routes = [TunnelIPv6Address(address: "2001:db8:42::", prefixLength: 48)]
        let settings = try plan.makeSettings()
        XCTAssertEqual(settings.ipv4Settings?.includedRoutes?.count, 2)
        XCTAssertEqual(settings.ipv4Settings?.includedRoutes?.last?.destinationAddress, "10.0.0.0")
        XCTAssertTrue(settings.ipv4Settings?.includedRoutes?.contains {
            $0.destinationAddress == "0.0.0.0" && $0.destinationSubnetMask == "0.0.0.0"
        } ?? false)
        XCTAssertEqual(settings.ipv4Settings?.excludedRoutes?.first?.destinationAddress, "10.4.0.0")
        XCTAssertEqual(settings.ipv6Settings?.includedRoutes?.count, 2)
        XCTAssertEqual(settings.ipv6Settings?.includedRoutes?.last?.destinationNetworkPrefixLength.intValue, 32)
        XCTAssertTrue(settings.ipv6Settings?.includedRoutes?.contains {
            $0.destinationNetworkPrefixLength.intValue == 0
        } ?? false)
        XCTAssertEqual(settings.ipv6Settings?.excludedRoutes?.first?.destinationNetworkPrefixLength.intValue, 48)
    }

    func testRejectsBadExcludedRoute() {
        var plan = validPlan()
        plan.excludedIPv4Routes = [TunnelIPv4Address(address: "not-an-ip", mask: "255.255.255.0")]
        XCTAssertThrowsError(try plan.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .invalidIPv4)
        }
    }

    func testFullTunnelRequiresIPv6ForLeakSafety() {
        let plan = TunnelNetworkPlan(
            ipv4: [TunnelIPv4Address(address: "172.19.0.1", mask: "255.255.255.0")],
            ipv6: [],
            dns: ["1.1.1.1"],
            mtu: 1400,
            fullTunnel: true
        )
        XCTAssertThrowsError(try plan.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .missingIPv6)
        }
    }

    func testFullTunnelRequiresIPv4ForLeakSafety() {
        let plan = TunnelNetworkPlan(
            ipv4: [],
            ipv6: [TunnelIPv6Address(address: "fd00::1", prefixLength: 64)],
            dns: ["2606:4700:4700::1111"],
            mtu: 1400,
            fullTunnel: true
        )
        XCTAssertThrowsError(try plan.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .missingIPv4)
        }
    }

    func testExplicitDefaultRoutesAreNotDuplicated() throws {
        var plan = validPlan()
        plan.includedIPv4Routes = [TunnelIPv4Address(address: "0.0.0.0", mask: "0.0.0.0")]
        plan.includedIPv6Routes = [TunnelIPv6Address(address: "::", prefixLength: 0)]
        let settings = try plan.makeSettings()
        XCTAssertEqual(settings.ipv4Settings?.includedRoutes?.count, 1)
        XCTAssertEqual(settings.ipv6Settings?.includedRoutes?.count, 1)
    }

    func testSplitTunnelCanBeIPv4Only() throws {
        let plan = TunnelNetworkPlan(
            ipv4: [TunnelIPv4Address(address: "10.0.0.1", mask: "255.255.255.0")],
            ipv6: [],
            dns: [],
            mtu: 1400,
            fullTunnel: false
        )
        let settings = try plan.makeSettings()
        XCTAssertNotNil(settings.ipv4Settings)
        XCTAssertNil(settings.ipv6Settings)
    }

    func testRejectsNonContiguousIPv4Mask() {
        var plan = validPlan()
        plan.includedIPv4Routes = [
            TunnelIPv4Address(address: "192.168.0.0", mask: "255.0.255.0")
        ]
        XCTAssertThrowsError(try plan.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .invalidMask)
        }
    }

    func testRejectsExcludedIPv4DefaultInFullTunnel() {
        var plan = validPlan()
        plan.excludedIPv4Routes = [
            TunnelIPv4Address(address: "0.0.0.0", mask: "0.0.0.0")
        ]
        XCTAssertThrowsError(try plan.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .unsafeExclusion)
        }
    }

    func testRejectsExcludedIPv6DefaultInFullTunnel() {
        var plan = validPlan()
        plan.excludedIPv6Routes = [
            TunnelIPv6Address(address: "::", prefixLength: 0)
        ]
        XCTAssertThrowsError(try plan.makeSettings()) {
            XCTAssertEqual($0 as? TunnelNetworkPlanError, .unsafeExclusion)
        }
    }

    func testValidFullSubnetMaskAllowed() throws {
        var plan = validPlan()
        plan.includedIPv4Routes = [
            TunnelIPv4Address(address: "10.4.0.0", mask: "255.255.0.0")
        ]
        XCTAssertNoThrow(try plan.makeSettings())
    }

}

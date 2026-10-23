import XCTest
@testable import VetrOFF

final class ProfileVaultScopeTests: XCTestCase {
    func testPreviewHostCanUsePrivateKeychain() throws {
        XCTAssertEqual(try ProfileVaultScope.resolve(
            bundleID: ProfileVaultScope.applicationID, sharedGroup: nil
        ), .application)
    }

    func testExtensionNeverFallsBackToPrivateKeychain() {
        for id in ["app.vetroff.client.macos.PacketTunnel", "other.app", ""] {
            XCTAssertThrowsError(try ProfileVaultScope.resolve(bundleID: id, sharedGroup: nil))
        }
    }

    func testProvisionedTargetsUseTheSameSharedGroup() throws {
        let group = "TEAM.app.vetroff.client.macos.shared"
        for id in [ProfileVaultScope.applicationID, "app.vetroff.client.macos.PacketTunnel"] {
            XCTAssertEqual(try ProfileVaultScope.resolve(bundleID: id, sharedGroup: group), .shared(group))
        }
    }
}

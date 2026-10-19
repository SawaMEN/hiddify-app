import XCTest
@testable import VetrOFF

final class MacOSReleaseCheckerTests: XCTestCase {
    func testSemanticVersionOrder() {
        let versions = ["1.10.0", "1.2.9", "2.0.0", "0.9.10"].compactMap(MacOSVersion.init)
        XCTAssertEqual(versions.sorted().map { "\($0.major).\($0.minor).\($0.patch)" },
                       ["0.9.10", "1.2.9", "1.10.0", "2.0.0"])
        XCTAssertNil(MacOSVersion("v1.2.3"))
        XCTAssertNil(MacOSVersion("1.2"))
        XCTAssertNil(MacOSVersion("1.2.-1"))
    }

    func testOnlyNewerSignedMacDMGIsAccepted() throws {
        let json = """
        [
         {"tag_name":"v9.0.0","draft":false,"prerelease":false,
          "assets":[{"name":"vetroff-Android-arm64.apk"}]},
         {"tag_name":"macos-v0.5.1-abcdef123456","draft":false,"prerelease":true,
          "assets":[{"name":"VetrOFF-macOS-arm64.dmg"}]},
         {"tag_name":"macos-v1.0.0-aabbcc112233","draft":true,"prerelease":true,
          "assets":[{"name":"VetrOFF-macOS-arm64.dmg"}]},
         {"tag_name":"macos-v0.8.0-abcdefabcdef","draft":false,"prerelease":true,
          "assets":[{"name":"VetrOFF-macOS-arm64.dmg"}]},
         {"tag_name":"macos-v0.9.0-abc123abc123","draft":false,"prerelease":true,
          "assets":[{"name":"VetrOFF-macOS-arm64.dmg"},{"name":"unwanted.zip"}]}
        ]
        """
        let releases = try JSONDecoder().decode(
            [MacOSSignedRelease].self, from: Data(json.utf8)
        )
        let result = MacOSUpdatePolicy.latestNewer(releases, current: MacOSVersion("0.5.0")!)
        XCTAssertEqual(result?.version, MacOSVersion("0.8.0"))
        XCTAssertEqual(result?.url.host, "github.com")
        XCTAssertEqual(result?.url.path,
                       "/SawaMEN/hiddify-app/releases/tag/macos-v0.8.0-abcdefabcdef")
    }

    func testNoUpdateWhenVersionIsEqualOrOlder() throws {
        let json = """
        [{"tag_name":"macos-v0.1.0-0123456789ab","draft":false,
          "prerelease":true,"assets":[{"name":"VetrOFF-macOS-arm64.dmg"}]}]
        """
        let releases = try JSONDecoder().decode(
            [MacOSSignedRelease].self, from: Data(json.utf8)
        )
        XCTAssertNil(MacOSUpdatePolicy.latestNewer(
            releases, current: MacOSVersion("0.1.0")!
        ))
    }
}

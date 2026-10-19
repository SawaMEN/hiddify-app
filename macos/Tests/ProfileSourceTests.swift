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
}

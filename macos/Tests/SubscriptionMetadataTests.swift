import XCTest
@testable import VetrOFF

final class SubscriptionMetadataTests: XCTestCase {
    func testRemoteTitleBase64AndCaseInsensitiveHeaders() {
        let title = "VetrOFF · Моя подписка"
        let result = SubscriptionMetadata.parse(headers: ["Profile-Title": "base64:" + Data(title.utf8).base64EncodedString()])
        XCTAssertEqual(result.title, title)
    }
    func testRemoteHeadersOverrideInlineMetadata() {
        let result = SubscriptionMetadata.parse(headers: ["profile-title": "Provider"],
            content: "# profile-title: Inline\n// subscription-userinfo: upload=10; download=20; total=100; expire=2000000000")
        XCTAssertEqual(result.title, "Provider")
        XCTAssertEqual(result.usedBytes, 30)
        XCTAssertEqual(result.total, 100)
        XCTAssertEqual(result.expiresAt, Date(timeIntervalSince1970: 2_000_000_000))
    }
    func testDispositionUTF8NameAndUpdateInterval() {
        let result = SubscriptionMetadata.parse(headers: ["content-disposition": "attachment; filename*=UTF-8''%D0%A2%D0%B5%D1%81%D1%82", "profile-update-interval": "6"])
        XCTAssertEqual(result.title, "Тест")
        XCTAssertEqual(result.updateHours, 6)
    }
    func testBadNumbersAndUnsafeTitlesDoNotEscapeIntoUI() {
        let result = SubscriptionMetadata.parse(headers: ["profile-title": "Title\nFake", "subscription-userinfo": "upload=-1; download=9223372036854775808; expire=999999999999999999; total=-1"])
        XCTAssertNil(result.title); XCTAssertNil(result.upload); XCTAssertNil(result.download)
        XCTAssertNil(result.total); XCTAssertNil(result.expiresAt)
    }
    func testBase64InlineMetadata() throws {
        let text = "# profile-title: base64:" + Data("Provider".utf8).base64EncodedString() + "\nsnell://secret@example.org:443?version=4"
        let decoded = try ProfileSource.decodeSubscription(Data(Data(text.utf8).base64EncodedString().utf8))
        XCTAssertEqual(SubscriptionMetadata.parse(content: decoded).title, "Provider")
    }
}

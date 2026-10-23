import XCTest
@testable import VetrOFF

final class ProfileImportTests: XCTestCase {
    func testWrappedSubscriptionRetainsEncodedToken() throws {
        let source = "https://example.com/sub?token=a%2Fb%3D"
        XCTAssertEqual(try ProfileImport.normalize("hiddify://import/" + source), source)
        XCTAssertEqual(try ProfileImport.normalize("vetroff://import/" + source), source)
    }

    func testShareLinkDisplayFragmentIsNotSentToSubscriptionServer() throws {
        let address = "https://example.com/sub?token=a%2Fb%3D"
        XCTAssertEqual(try ProfileImport.normalize(address + "#My%20subscription"), address)
        XCTAssertEqual(try ProfileImport.normalize("hiddify://import/" + address + "#Name"), address)
    }

    func testEncodedWrappedURL() throws {
        XCTAssertEqual(try ProfileImport.normalize(
            "hiddify://import/https%3A%2F%2Fexample.com%2Fsub%3Ftoken%3Dx"
        ), "https://example.com/sub?token=x")
    }

    func testBase64PasteAndRawConfig() throws {
        let links = "vless://uuid@example.com:443#One\ntrojan://pass@example.net:443#Two"
        XCTAssertEqual(try ProfileImport.normalize(Data(links.utf8).base64EncodedString()), links)
        XCTAssertEqual(try ProfileImport.normalize("  " + links + "\n"), links)
    }

    func testRejectsWrappedInsecureOrUnrelatedAction() {
        for text in ["hiddify://import/http://example.com/sub", "vetroff://delete/all",
                     "hiddify://import/https://localhost/sub"] {
            XCTAssertThrowsError(try ProfileImport.normalize(text))
        }
    }
}

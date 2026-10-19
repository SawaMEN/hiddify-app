import Foundation
import XCTest
@testable import VetrOFF

final class TunnelStartRequestTests: XCTestCase {
    func testRoundTripContainsOnlyOpaqueUUID() throws {
        let request = TunnelStartRequest(profileID: UUID())
        let decoded = try TunnelStartRequest(options: request.options)
        XCTAssertEqual(request, decoded)
        XCTAssertEqual(Array(request.options.keys), ["profileID"])
        XCTAssertEqual((request.options["profileID"] as? String)?.count, 36)
    }

    func testMissingProfileRejected() {
        XCTAssertThrowsError(try TunnelStartRequest(options: [:])) {
            XCTAssertEqual($0 as? TunnelStartRequestError, .missingProfileID)
        }
    }

    func testMalformedProfileRejected() {
        XCTAssertThrowsError(try TunnelStartRequest(options: ["profileID": "abc" as NSString])) {
            XCTAssertEqual($0 as? TunnelStartRequestError, .invalidProfileID)
        }
    }
}

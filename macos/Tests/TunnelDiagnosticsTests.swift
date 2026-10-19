import Foundation
import XCTest
@testable import VetrOFF

final class TunnelDiagnosticsTests: XCTestCase {
    func testRejectsUnknownCommands() {
        XCTAssertTrue(TunnelDiagnosticsProtocol.accepts(TunnelDiagnosticsProtocol.statusRequest))
        XCTAssertFalse(TunnelDiagnosticsProtocol.accepts(Data("start".utf8)))
        XCTAssertFalse(TunnelDiagnosticsProtocol.accepts(Data("secret=foo".utf8)))
        XCTAssertFalse(TunnelDiagnosticsProtocol.accepts(Data()))
    }

    func testReplyNeverClaimsPacketVerification() throws {
        let reply = TunnelDiagnosticReply(coreLinked: true, lifecycleState: "running")
        let bytes = try JSONEncoder().encode(reply)
        let restored = try JSONDecoder().decode(TunnelDiagnosticReply.self, from: bytes)
        XCTAssertEqual(reply, restored)
        XCTAssertEqual(restored.version, 1)
        XCTAssertFalse(restored.trafficVerified)
        let text = try XCTUnwrap(String(data: bytes, encoding: .utf8))
        XCTAssertFalse(text.contains("password"))
        XCTAssertFalse(text.contains("subscription"))
    }
}

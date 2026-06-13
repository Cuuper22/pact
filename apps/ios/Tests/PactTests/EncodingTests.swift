import XCTest
@testable import Pact

/// Verifies the client encodes actions and frames in the exact wire shape the
/// relay expects (server/src/protocol.ts).
final class EncodingTests: XCTestCase {
    private let encoder = JSONEncoder()

    private func object(_ value: some Encodable) throws -> [String: Any] {
        let data = try encoder.encode(value)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func testEncodesAskWithReason() throws {
        let obj = try object(ClientAction.ask(reason: "Need to pay"))
        XCTAssertEqual(obj["type"] as? String, "ask")
        XCTAssertEqual(obj["reason"] as? String, "Need to pay")
    }

    func testEncodesAskWithoutReasonOmitsField() throws {
        let obj = try object(ClientAction.ask(reason: nil))
        XCTAssertEqual(obj["type"] as? String, "ask")
        XCTAssertNil(obj["reason"], "reason must be omitted, not null, when absent")
    }

    func testEncodesVote() throws {
        let obj = try object(ClientAction.vote(allow: true))
        XCTAssertEqual(obj["type"] as? String, "vote")
        XCTAssertEqual(obj["allow"] as? Bool, true)
    }

    func testEncodesSetPush() throws {
        let obj = try object(ClientAction.setPush(pushToken: "abc", platform: .ios))
        XCTAssertEqual(obj["type"] as? String, "setPush")
        XCTAssertEqual(obj["pushToken"] as? String, "abc")
        XCTAssertEqual(obj["platform"] as? String, "ios")
    }

    func testEncodesClientFrameHello() throws {
        let obj = try object(ClientFrame.hello(seatId: "s1", token: "t"))
        XCTAssertEqual(obj["type"] as? String, "hello")
        XCTAssertEqual(obj["seatId"] as? String, "s1")
        XCTAssertEqual(obj["token"] as? String, "t")
    }

    func testEncodesClientFramePing() throws {
        let obj = try object(ClientFrame.ping)
        XCTAssertEqual(obj["type"] as? String, "ping")
    }

    func testEncodesClientFrameActionFlat() throws {
        // An action frame must serialize as the action itself (flat), not nested.
        let obj = try object(ClientFrame.action(.lock))
        XCTAssertEqual(obj["type"] as? String, "lock")
    }

    func testEncodesActionBody() throws {
        let obj = try object(ActionBody(seatId: "s2", token: "t", action: .vote(allow: false)))
        XCTAssertEqual(obj["seatId"] as? String, "s2")
        let action = try XCTUnwrap(obj["action"] as? [String: Any])
        XCTAssertEqual(action["type"] as? String, "vote")
        XCTAssertEqual(action["allow"] as? Bool, false)
    }
}

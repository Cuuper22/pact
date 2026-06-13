import XCTest
@testable import Pact

/// Wire-contract tests: the relay's JSON must decode into our models exactly.
/// These are the most failure-prone code (a discriminated union by string), so
/// they're pinned against literal payloads matching docs/PROTOCOL.md.
final class DecodingTests: XCTestCase {
    private let decoder = JSONDecoder()

    func testDecodesWelcomeWithNight() throws {
        let json = """
        {
          "type": "welcome",
          "pactId": "p1",
          "serverTime": 1700000000000,
          "view": {
            "screen": "night",
            "me": { "id": "s1", "name": "Yousef", "host": true },
            "members": [{ "id": "s1", "name": "Yousef", "host": true }],
            "presentMs": 65000,
            "stakes": "First break buys dessert",
            "canAsk": true,
            "cooldownMs": null,
            "notice": null
          }
        }
        """
        let frame = try decoder.decode(ServerFrame.self, from: Data(json.utf8))
        guard case let .welcome(pactId, _, view) = frame else {
            return XCTFail("expected welcome")
        }
        XCTAssertEqual(pactId, "p1")
        guard case let .night(p) = view else { return XCTFail("expected night") }
        XCTAssertEqual(p.presentMs, 65000)
        XCTAssertEqual(p.canAsk, true)
        XCTAssertNil(p.cooldownMs)
        XCTAssertEqual(p.members.count, 1)
    }

    func testDecodesAskTallyWithMixedVotes() throws {
        let json = """
        {
          "type": "state",
          "serverTime": 1,
          "view": {
            "screen": "ask",
            "me": { "id": "s2", "name": "Maya", "host": false },
            "members": [],
            "presentMs": 0,
            "stakes": "",
            "asker": "Yousef",
            "reason": "Need to pay",
            "remainMs": 42000,
            "tally": [
              { "seatId": "s1", "name": "Yousef", "vote": true },
              { "seatId": "s2", "name": "Maya", "vote": false },
              { "seatId": "s3", "name": "Sam" }
            ]
          }
        }
        """
        let frame = try decoder.decode(ServerFrame.self, from: Data(json.utf8))
        guard case let .state(_, view) = frame, case let .ask(p) = view else {
            return XCTFail("expected ask state")
        }
        XCTAssertEqual(p.asker, "Yousef")
        XCTAssertEqual(p.tally.count, 3)
        XCTAssertEqual(p.tally[0].vote, true)
        XCTAssertEqual(p.tally[1].vote, false)
        XCTAssertNil(p.tally[2].vote) // still deciding
    }

    func testDecodesBrokenRecap() throws {
        let json = """
        {
          "screen": "broken",
          "me": { "id": "s1", "name": "Yousef", "host": true },
          "recap": {
            "presentMs": 3600000,
            "asks": 4,
            "granted": 3,
            "denied": 1,
            "brokenBy": "Sam",
            "stakes": "Dessert"
          }
        }
        """
        let view = try decoder.decode(SeatView.self, from: Data(json.utf8))
        guard case let .broken(p) = view else { return XCTFail("expected broken") }
        XCTAssertEqual(p.recap.brokenBy, "Sam")
        XCTAssertEqual(p.recap.asks, 4)
        XCTAssertEqual(p.recap.presentMs, 3600000)
    }

    func testDecodesBrokenRecapNullBrokenBy() throws {
        let json = """
        { "screen": "broken",
          "me": { "id": "s1", "name": "Y", "host": true },
          "recap": { "presentMs": 0, "asks": 0, "granted": 0, "denied": 0, "brokenBy": null, "stakes": "" } }
        """
        let view = try decoder.decode(SeatView.self, from: Data(json.utf8))
        guard case let .broken(p) = view else { return XCTFail("expected broken") }
        XCTAssertNil(p.recap.brokenBy)
    }

    func testUnknownScreenFallsBackInsteadOfThrowing() throws {
        let json = #"{ "screen": "some-future-screen", "x": 1 }"#
        let view = try decoder.decode(SeatView.self, from: Data(json.utf8))
        XCTAssertEqual(view.screenName, "some-future-screen")
        if case .unknown = view {} else { XCTFail("expected unknown fallback") }
    }

    func testSeatCredentialsDecodeWsUrlKey() throws {
        let json = """
        { "pactId": "p1", "code": "TBL-K7QP", "seatId": "s1", "token": "secret",
          "wsUrl": "wss://relay/ws?pactId=p1&seatId=s1&token=secret",
          "joinLink": "pact://join?code=TBL-K7QP" }
        """
        let creds = try decoder.decode(SeatCredentials.self, from: Data(json.utf8))
        XCTAssertEqual(creds.code, "TBL-K7QP")
        XCTAssertEqual(creds.wsURL.scheme, "wss")
    }
}

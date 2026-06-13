import Foundation

/// `POST /pacts` body. Mirrors `CreatePactBody`. Push fields are optional.
public struct CreatePactBody: Encodable, Sendable {
    public let hostName: String
    /// One of 2 | 5 | 10 (relay defaults to 5 if omitted/invalid).
    public let passMinutes: Int?
    public let stakes: String?
    public let pushToken: String?
    public let platform: Platform?

    public init(
        hostName: String,
        passMinutes: Int? = nil,
        stakes: String? = nil,
        pushToken: String? = nil,
        platform: Platform? = nil
    ) {
        self.hostName = hostName
        self.passMinutes = passMinutes
        self.stakes = stakes
        self.pushToken = pushToken
        self.platform = platform
    }
}

/// `POST /pacts/:code/join` body. Mirrors `JoinPactBody`.
public struct JoinPactBody: Encodable, Sendable {
    public let name: String
    public let pushToken: String?
    public let platform: Platform?

    public init(name: String, pushToken: String? = nil, platform: Platform? = nil) {
        self.name = name
        self.pushToken = pushToken
        self.platform = platform
    }
}

/// `POST /pacts/:pactId/actions` body. Mirrors `ActionBody`. Used to act from a
/// push handler / locked phone with no live socket.
public struct ActionBody: Encodable, Sendable {
    public let seatId: String
    public let token: String
    public let action: ClientAction

    public init(seatId: String, token: String, action: ClientAction) {
        self.seatId = seatId
        self.token = token
        self.action = action
    }
}

/// `200`/`409` response to an `actions` post: whether the engine applied it and
/// your resulting view.
public struct ActionResult: Decodable, Sendable {
    public let ok: Bool
    public let view: SeatView
}

/// The relay's error body for non-2xx REST responses.
public struct RelayErrorBody: Decodable, Sendable {
    public let error: String
}

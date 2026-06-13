import Foundation

/// Frames the relay sends over the WebSocket. Mirrors `ServerFrame` in
/// server/src/protocol.ts. Decode-only on the client.
public enum ServerFrame: Decodable, Sendable {
    case welcome(pactId: String, serverTime: Double, view: SeatView)
    case state(serverTime: Double, view: SeatView)
    case pong(serverTime: Double)
    case error(code: String, message: String)

    private enum CodingKeys: String, CodingKey {
        case type, pactId, serverTime, view, code, message
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(String.self, forKey: .type)
        switch type {
        case "welcome":
            self = .welcome(
                pactId: try c.decode(String.self, forKey: .pactId),
                serverTime: try c.decode(Double.self, forKey: .serverTime),
                view: try c.decode(SeatView.self, forKey: .view)
            )
        case "state":
            self = .state(
                serverTime: try c.decode(Double.self, forKey: .serverTime),
                view: try c.decode(SeatView.self, forKey: .view)
            )
        case "pong":
            self = .pong(serverTime: try c.decode(Double.self, forKey: .serverTime))
        case "error":
            self = .error(
                code: try c.decode(String.self, forKey: .code),
                message: try c.decode(String.self, forKey: .message)
            )
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type, in: c,
                debugDescription: "Unknown ServerFrame type \(type)"
            )
        }
    }
}

/// Frames the client sends over the WebSocket. Mirrors `ClientFrame` in
/// server/src/protocol.ts: a `hello`/`ping` plus the shared `ClientAction`s.
/// Encode-only on the client.
public enum ClientFrame: Encodable, Sendable {
    case hello(seatId: String, token: String)
    case ping
    case action(ClientAction)

    private enum HelloKeys: String, CodingKey { case type, seatId, token }
    private enum PingKeys: String, CodingKey { case type }

    public func encode(to encoder: Encoder) throws {
        switch self {
        case let .hello(seatId, token):
            var c = encoder.container(keyedBy: HelloKeys.self)
            try c.encode("hello", forKey: .type)
            try c.encode(seatId, forKey: .seatId)
            try c.encode(token, forKey: .token)
        case .ping:
            var c = encoder.container(keyedBy: PingKeys.self)
            try c.encode("ping", forKey: .type)
        case let .action(action):
            // ClientAction already encodes its own `type` discriminant.
            try action.encode(to: encoder)
        }
    }
}

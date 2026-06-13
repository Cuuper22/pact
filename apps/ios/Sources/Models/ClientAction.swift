import Foundation

/// An action a seat can take, shared by WebSocket frames and REST `actions`
/// posts. Mirrors `ClientAction` in server/src/protocol.ts — a discriminated
/// union keyed by `type`.
public enum ClientAction: Codable, Equatable, Sendable {
    case lock
    case ask(reason: String?)
    case vote(allow: Bool)
    case emergency
    case leave
    case setPush(pushToken: String, platform: Platform)

    private enum CodingKeys: String, CodingKey {
        case type, reason, allow, pushToken, platform
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .lock:
            try c.encode("lock", forKey: .type)
        case let .ask(reason):
            try c.encode("ask", forKey: .type)
            // `reason` is optional on the wire; only encode when present.
            try c.encodeIfPresent(reason, forKey: .reason)
        case let .vote(allow):
            try c.encode("vote", forKey: .type)
            try c.encode(allow, forKey: .allow)
        case .emergency:
            try c.encode("emergency", forKey: .type)
        case .leave:
            try c.encode("leave", forKey: .type)
        case let .setPush(pushToken, platform):
            try c.encode("setPush", forKey: .type)
            try c.encode(pushToken, forKey: .pushToken)
            try c.encode(platform, forKey: .platform)
        }
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(String.self, forKey: .type)
        switch type {
        case "lock":
            self = .lock
        case "ask":
            self = .ask(reason: try c.decodeIfPresent(String.self, forKey: .reason))
        case "vote":
            self = .vote(allow: try c.decode(Bool.self, forKey: .allow))
        case "emergency":
            self = .emergency
        case "leave":
            self = .leave
        case "setPush":
            self = .setPush(
                pushToken: try c.decode(String.self, forKey: .pushToken),
                platform: try c.decode(Platform.self, forKey: .platform)
            )
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type, in: c,
                debugDescription: "Unknown ClientAction type \(type)"
            )
        }
    }
}

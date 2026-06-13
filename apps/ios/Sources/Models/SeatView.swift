import Foundation

/// A lightweight member descriptor for in-session rosters.
/// Mirrors `MemberView` in packages/engine/src/types.ts.
public struct MemberView: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let name: String
    public let host: Bool

    public init(id: String, name: String, host: Bool) {
        self.id = id
        self.name = name
        self.host = host
    }
}

/// A single line in a vote tally. `vote` is `nil` while the seat is still
/// deciding, `true` for allow, `false` for not-now.
/// Mirrors `TallyEntry` in packages/engine/src/types.ts.
public struct TallyEntry: Codable, Equatable, Identifiable, Sendable {
    public let seatId: String
    public let name: String
    public let vote: Bool?

    public var id: String { seatId }

    public init(seatId: String, name: String, vote: Bool?) {
        self.seatId = seatId
        self.name = name
        self.vote = vote
    }
}

/// The end-of-pact summary card. Mirrors `Recap` in
/// packages/engine/src/types.ts.
public struct Recap: Codable, Equatable, Sendable {
    public let presentMs: Double
    public let asks: Int
    public let granted: Int
    public let denied: Int
    /// First name of whoever broke the pact, or `nil` if nobody did.
    public let brokenBy: String?
    public let stakes: String

    public init(
        presentMs: Double,
        asks: Int,
        granted: Int,
        denied: Int,
        brokenBy: String?,
        stakes: String
    ) {
        self.presentMs = presentMs
        self.asks = asks
        self.granted = granted
        self.denied = denied
        self.brokenBy = brokenBy
        self.stakes = stakes
    }
}

/// The per-seat view: a pure projection of pact state for one person's screen.
/// The relay sends exactly this; the client renders it directly. `screen` is
/// the discriminant. Mirrors the `SeatView` union in
/// packages/engine/src/types.ts.
///
/// Decoding strategy: read `screen`, then decode the matching associated
/// payload. Unknown / future screens decode to `.unknown(screen:)` rather than
/// throwing, so a relay that adds a screen never crashes an old client — it
/// falls back to a safe holding view.
public enum SeatView: Equatable, Sendable {
    case none
    case join(JoinPayload)
    case lobbyHost(LobbyHostPayload)
    case lobbyWait(LobbyWaitPayload)
    case night(NightPayload)
    case ask(AskPayload)
    case waiting(WaitingPayload)
    case pass(PassPayload)
    case broken(BrokenPayload)
    /// A screen this client version doesn't know about. Holds the raw name.
    case unknown(screen: String)

    // MARK: Payloads

    public struct JoinPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let code: String
    }

    public struct LobbyHostPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let code: String
        public let members: [MemberView]
        public let canLock: Bool
        public let stakes: String
    }

    public struct LobbyWaitPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let members: [MemberView]
    }

    public struct NightPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let members: [MemberView]
        public let presentMs: Double
        public let stakes: String
        public let canAsk: Bool?
        public let cooldownMs: Double?
        public let notice: String?
        public let banner: String?
    }

    public struct AskPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let members: [MemberView]
        public let presentMs: Double
        public let stakes: String
        public let asker: String
        public let reason: String
        public let remainMs: Double
        public let tally: [TallyEntry]
    }

    public struct WaitingPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let members: [MemberView]
        public let presentMs: Double
        public let stakes: String
        public let reason: String
        public let tally: [TallyEntry]
        public let remainMs: Double
    }

    public struct PassPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let members: [MemberView]
        public let presentMs: Double
        public let stakes: String
        public let remainMs: Double
        public let emergency: Bool
    }

    public struct BrokenPayload: Codable, Equatable, Sendable {
        public let me: MemberView
        public let recap: Recap
    }

    // MARK: Convenience accessors

    /// The raw `screen` discriminant string (matches the wire value).
    public var screenName: String {
        switch self {
        case .none: return "none"
        case .join: return "join"
        case .lobbyHost: return "lobby-host"
        case .lobbyWait: return "lobby-wait"
        case .night: return "night"
        case .ask: return "ask"
        case .waiting: return "waiting"
        case .pass: return "pass"
        case .broken: return "broken"
        case let .unknown(screen): return screen
        }
    }

    /// True when the screen represents an in-session (post-lock) state, which
    /// is what the night theme and the shield posture key off.
    public var isInSession: Bool {
        switch self {
        case .night, .ask, .waiting, .pass: return true
        default: return false
        }
    }
}

/// `SeatView` is decode-only on the client: the relay is authoritative and the
/// app only ever *renders* the views it receives, never sends them back. We
/// therefore implement `Decodable` (not full `Codable`) to avoid the brittle
/// dual-container encoding of a discriminated union with sibling fields.
extension SeatView: Decodable {
    private enum CodingKeys: String, CodingKey { case screen }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let screen = try c.decode(String.self, forKey: .screen)
        let single = try decoder.singleValueContainer()
        switch screen {
        case "none":
            self = .none
        case "join":
            self = .join(try single.decode(JoinPayload.self))
        case "lobby-host":
            self = .lobbyHost(try single.decode(LobbyHostPayload.self))
        case "lobby-wait":
            self = .lobbyWait(try single.decode(LobbyWaitPayload.self))
        case "night":
            self = .night(try single.decode(NightPayload.self))
        case "ask":
            self = .ask(try single.decode(AskPayload.self))
        case "waiting":
            self = .waiting(try single.decode(WaitingPayload.self))
        case "pass":
            self = .pass(try single.decode(PassPayload.self))
        case "broken":
            self = .broken(try single.decode(BrokenPayload.self))
        default:
            self = .unknown(screen: screen)
        }
    }
}

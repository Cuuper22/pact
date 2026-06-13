import Foundation

/// The `kind` carried by every Pact push, both the actionable `ask` alert and
/// the `content-available` state nudges. Mirrors `RefreshKind` plus `ask` from
/// server/src/push/index.ts.
public enum PushKind: String, Codable, Sendable {
    case ask
    case lock
    case grant
    case deny
    case relock
    case leave
    case emergency
}

/// The decoded contents of a Pact push's `userInfo`. Both APNs alert pushes and
/// background `content-available` pushes carry `{ pactId, kind }`.
public struct PushPayload: Sendable {
    public let pactId: String
    public let kind: PushKind

    /// Parses an APNs `userInfo` dictionary. Returns `nil` if it isn't a Pact
    /// push (missing or unrecognised fields).
    public init?(userInfo: [AnyHashable: Any]) {
        guard
            let pactId = userInfo["pactId"] as? String,
            let kindRaw = userInfo["kind"] as? String,
            let kind = PushKind(rawValue: kindRaw)
        else { return nil }
        self.pactId = pactId
        self.kind = kind
    }
}

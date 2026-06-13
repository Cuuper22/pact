import Foundation

/// Returned by the relay to the seat that created or joined a pact. The `token`
/// is the seat's only proof of identity — there are no accounts. Mirrors
/// `SeatCredentials` in server/src/protocol.ts.
///
/// Stored in the App Group so the ShieldAction extension can act on the seat's
/// behalf (POST a vote) from a locked phone.
public struct SeatCredentials: Codable, Equatable, Sendable {
    public let pactId: String
    public let code: String
    public let seatId: String
    public let token: String
    /// ws:// or wss:// URL to open, token already in the query string.
    public let wsURL: URL
    /// Deep link encoded into the host's QR, e.g. pact://join?code=TBL-K7QP
    public let joinLink: String

    private enum CodingKeys: String, CodingKey {
        case pactId, code, seatId, token
        case wsURL = "wsUrl"
        case joinLink
    }

    public init(
        pactId: String,
        code: String,
        seatId: String,
        token: String,
        wsURL: URL,
        joinLink: String
    ) {
        self.pactId = pactId
        self.code = code
        self.seatId = seatId
        self.token = token
        self.wsURL = wsURL
        self.joinLink = joinLink
    }

    // MARK: Shared persistence (App Group)

    /// Loads the active seat's credentials, if any.
    public static func load() -> SeatCredentials? {
        guard
            let data = AppGroup.defaults.data(forKey: AppGroup.Key.credentials),
            let creds = try? JSONDecoder().decode(SeatCredentials.self, from: data)
        else { return nil }
        return creds
    }

    /// Persists these credentials into the App Group.
    public func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        AppGroup.defaults.set(data, forKey: AppGroup.Key.credentials)
    }

    /// Clears any stored credentials (on leave / broken).
    public static func clear() {
        AppGroup.defaults.removeObject(forKey: AppGroup.Key.credentials)
    }
}

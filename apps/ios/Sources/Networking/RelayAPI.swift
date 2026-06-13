import Foundation

/// Errors surfaced by the REST layer, mapped to user-facing copy where helpful.
public enum RelayError: LocalizedError, Equatable {
    case badURL
    case transport(String)
    case http(status: Int, message: String?)
    case decoding(String)

    public var errorDescription: String? {
        switch self {
        case .badURL:
            return "The relay address is invalid."
        case let .transport(detail):
            return "Couldn't reach the table. \(detail)"
        case let .http(status, message):
            switch status {
            case 404: return "No table with that code."
            case 409: return message ?? "The table is already locked."
            case 401: return "This seat is no longer valid."
            default: return message ?? "The relay returned an error (\(status))."
            }
        case let .decoding(detail):
            return "The relay sent something unexpected. \(detail)"
        }
    }
}

/// Thin async/await wrapper over the relay's REST endpoints. Stateless: it
/// holds only the base URL and a `URLSession`. Used both by the app and, for
/// the `actions` post, by the ShieldAction extension.
public struct RelayAPI: Sendable {
    public let baseURL: URL
    private let session: URLSession

    public init(baseURL: URL = PactConfig.relayURL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    private static let encoder = JSONEncoder()
    private static let decoder = JSONDecoder()

    // MARK: Endpoints

    /// `POST /pacts` — start a pact. Returns the host's seat credentials.
    public func createPact(_ body: CreatePactBody) async throws -> SeatCredentials {
        try await post(path: "pacts", body: body, decode: SeatCredentials.self)
    }

    /// `POST /pacts/:code/join` — join by table code.
    public func joinPact(code: String, body: JoinPactBody) async throws -> SeatCredentials {
        let escaped = code.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? code
        return try await post(path: "pacts/\(escaped)/join", body: body, decode: SeatCredentials.self)
    }

    /// `POST /pacts/:pactId/actions` — act without a live socket (push handler,
    /// locked phone). 200 means applied, 409 means the engine rejected it; both
    /// return the resulting view, so 409 is surfaced as a successful result
    /// with `ok == false` rather than thrown.
    public func sendAction(
        pactId: String,
        seatId: String,
        token: String,
        action: ClientAction
    ) async throws -> ActionResult {
        let body = ActionBody(seatId: seatId, token: token, action: action)
        let escaped = pactId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? pactId
        return try await post(
            path: "pacts/\(escaped)/actions",
            body: body,
            decode: ActionResult.self,
            acceptStatuses: [200, 409]
        )
    }

    // MARK: Transport

    private func post<Body: Encodable, Decoded: Decodable>(
        path: String,
        body: Body,
        decode: Decoded.Type,
        acceptStatuses: Set<Int> = [200, 201]
    ) async throws -> Decoded {
        guard let url = URL(string: path, relativeTo: baseURL) else { throw RelayError.badURL }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        do {
            request.httpBody = try Self.encoder.encode(body)
        } catch {
            throw RelayError.decoding("request encode: \(error.localizedDescription)")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw RelayError.transport(error.localizedDescription)
        }

        guard let http = response as? HTTPURLResponse else {
            throw RelayError.transport("no HTTP response")
        }
        guard acceptStatuses.contains(http.statusCode) else {
            let message = (try? Self.decoder.decode(RelayErrorBody.self, from: data))?.error
            throw RelayError.http(status: http.statusCode, message: message)
        }
        do {
            return try Self.decoder.decode(Decoded.self, from: data)
        } catch {
            throw RelayError.decoding(error.localizedDescription)
        }
    }
}

import SwiftUI
import Combine

/// The top-level coordinator. Owns the `PactClient`, the authorization model,
/// and cross-cutting app state (the push token, a pending join code from a
/// scanned deep link). Views observe it and the `PactClient` it exposes.
@MainActor
public final class AppModel: ObservableObject {
    public let client: PactClient
    public let auth = AuthorizationModel()

    /// The APNs device token hex string, once registered. Sent to the relay at
    /// create/join time and via `setPush` when the socket is live.
    @Published public var pushToken: String?

    /// A join code captured from a `pact://join?code=…` deep link or QR scan,
    /// awaiting the user's name on the Join screen.
    @Published public var pendingJoinCode: String?

    /// Surfaced errors for a lightweight banner (create/join failures, etc.).
    @Published public var errorMessage: String?

    private let shield: ShieldControlling

    public init(shield: ShieldControlling = ShieldController()) {
        self.shield = shield
        self.client = PactClient(shield: shield)
    }

    /// Called once on launch.
    public func bootstrap() {
        PactConfig.syncRelayURLToAppGroup()
        auth.refresh()
        client.restoreIfPossible()
    }

    // MARK: Push

    /// Stores the freshly-registered APNs token (hex) and forwards it to the
    /// relay if a socket is already live.
    public func registerPushToken(_ deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        pushToken = hex
        if client.credentials != nil {
            client.setPush(token: hex)
        }
    }

    // MARK: Deep links / QR

    /// Handles a `pact://join?code=…` URL (universal/custom-scheme link, or a
    /// payload decoded from a scanned QR). Captures the code for the Join flow.
    public func handle(url: URL) {
        guard
            url.scheme == PactConfig.urlScheme,
            url.host == "join",
            let comps = URLComponents(url: url, resolvingAgainstBaseURL: false),
            let code = comps.queryItems?.first(where: { $0.name == "code" })?.value,
            !code.isEmpty
        else { return }
        pendingJoinCode = code
    }

    // MARK: Session actions

    public func createPact(hostName: String, passMinutes: Int, stakes: String?) async {
        errorMessage = nil
        do {
            try await client.createPact(
                hostName: hostName,
                passMinutes: passMinutes,
                stakes: stakes?.isEmpty == true ? nil : stakes,
                pushToken: pushToken
            )
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    public func joinPact(code: String, name: String) async {
        errorMessage = nil
        do {
            try await client.joinPact(code: code, name: name, pushToken: pushToken)
            pendingJoinCode = nil
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    public func leave() async {
        await client.leave()
    }

    /// Reconciles the shield after a background push, off the App Group's
    /// `LockState` (which the push handler updates). Called when the app
    /// returns to the foreground in case it missed live frames.
    public func reconcileShieldFromSharedState() {
        let state = LockState.load()
        guard state.locked else { shield.clear(); return }
        if let until = state.passUntil, until > Date() {
            shield.clearForPass()
        } else {
            shield.lock()
        }
    }
}

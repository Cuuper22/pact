import ManagedSettings
import Foundation

/// Handles taps on the custom shield's buttons. The primary button ("Ask the
/// table") posts an `ask` to the relay on the seat's behalf — works from the
/// blocked-app surface without opening the app. The secondary button ("Stay
/// present") simply dismisses the shield overlay (the app stays shielded).
///
/// Acting here uses the seat credentials shared via the App Group and the
/// relay's REST `actions` endpoint, exactly as the push-action path does.
final class ShieldActionExtension: ShieldActionDelegate {
    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    // MARK: Shared handling

    private func respond(
        to action: ShieldAction,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            // "Ask the table" — post an ask, then close the overlay so the
            // person isn't staring at a frozen shield while the table decides.
            Task {
                await sendAsk()
                completionHandler(.close)
            }
        case .secondaryButtonPressed:
            // "Stay present" — just dismiss; the app remains shielded.
            completionHandler(.defer)
        @unknown default:
            completionHandler(.none)
        }
    }

    /// Posts an `ask` with no reason. The relay's ask flow then notifies the
    /// rest of the table; the asker watches the verdict in-app or via push.
    private func sendAsk() async {
        guard let creds = SeatCredentials.load() else { return }
        let api = RelayAPI()
        _ = try? await api.sendAction(
            pactId: creds.pactId,
            seatId: creds.seatId,
            token: creds.token,
            action: .ask(reason: nil)
        )
    }
}

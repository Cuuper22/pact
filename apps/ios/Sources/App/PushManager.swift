import Foundation
import UserNotifications

/// Notification category and action identifiers for the actionable "Ask the
/// table" push. Defined once and registered at launch.
public enum PushIDs {
    public static let askCategory = "PACT_ASK"
    public static let allowAction = "ALLOW"
    public static let notNowAction = "NOT_NOW"
}

/// Builds and registers the notification categories, and centralises the
/// POST-a-vote-from-a-push logic so both the notification action handler and
/// the ShieldAction extension share one implementation.
public enum PushManager {
    /// Registers the `PACT_ASK` category with ALLOW / NOT_NOW buttons. The
    /// actions are `.authenticationRequired == false`-friendly: they run a
    /// background POST, so a locked phone can vote in one tap.
    public static func registerCategories() {
        let allow = UNNotificationAction(
            identifier: PushIDs.allowAction,
            title: "Allow",
            options: []
        )
        let notNow = UNNotificationAction(
            identifier: PushIDs.notNowAction,
            title: "Not now",
            options: []
        )
        let category = UNNotificationCategory(
            identifier: PushIDs.askCategory,
            actions: [allow, notNow],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    /// Posts a vote to `/pacts/:pactId/actions` using the stored seat
    /// credentials. Works with no live socket — this is the locked-phone path.
    /// Returns silently on any failure; voting is best-effort and the relay's
    /// 60s window plus the live socket are the backstop.
    public static func postVote(allow: Bool, pactId: String) async {
        guard
            let creds = SeatCredentials.load(),
            creds.pactId == pactId
        else { return }
        let api = RelayAPI()
        _ = try? await api.sendAction(
            pactId: creds.pactId,
            seatId: creds.seatId,
            token: creds.token,
            action: .vote(allow: allow)
        )
    }
}

import UIKit
import UserNotifications

/// Handles APNs registration, background `content-available` pushes (shield
/// nudges), and notification action responses (Allow / Not now) — including
/// from a locked phone. Bridges to the `AppModel` for live-app concerns and to
/// the shared `PushShieldReconciler` for backgrounded shield updates.
final class AppDelegate: NSObject, UIApplicationDelegate {
    /// Set by the SwiftUI `App` so the delegate can reach app state.
    weak var model: AppModel?

    /// Backgrounded-shield reconciler. Uses the real ShieldController so it
    /// mutates the same shared ManagedSettings store as the app.
    private let reconciler = PushShieldReconciler(shield: ShieldController())

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        PushManager.registerCategories()
        return true
    }

    // MARK: APNs registration

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        model?.registerPushToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Non-fatal: the app still works over the live socket while foregrounded.
        NSLog("[Pact] APNs registration failed: \(error.localizedDescription)")
    }

    // MARK: Background pushes (content-available shield nudges)

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        guard let payload = PushPayload(userInfo: userInfo) else {
            completionHandler(.noData)
            return
        }
        // Apply the shield transition immediately off the push kind. The live
        // socket (if/when it reconnects) confirms with authoritative state.
        reconciler.apply(payload)
        completionHandler(.newData)
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension AppDelegate: UNUserNotificationCenterDelegate {
    /// Show ask alerts even in the foreground (the table glances at the phone),
    /// but keep them quiet — banner + sound, no list clutter.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    /// Handles taps on the notification and its ALLOW / NOT_NOW actions. Posts
    /// the vote over REST so it works from a locked phone with no live socket.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        guard let payload = PushPayload(userInfo: userInfo) else {
            completionHandler()
            return
        }

        switch response.actionIdentifier {
        case PushIDs.allowAction:
            Task {
                await PushManager.postVote(allow: true, pactId: payload.pactId)
                completionHandler()
            }
        case PushIDs.notNowAction:
            Task {
                await PushManager.postVote(allow: false, pactId: payload.pactId)
                completionHandler()
            }
        default:
            // A plain tap opens the app to the live ask screen.
            completionHandler()
        }
    }
}

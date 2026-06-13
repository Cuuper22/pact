import SwiftUI
import UserNotifications
import UIKit

/// The top-level router. Decides between onboarding, the pre-session home, and
/// the live in-session screens, switching purely on authorization status and
/// the latest `SeatView` the relay sent. The relay owns the truth; this view
/// only renders the screen it's told to.
struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var client: PactClient
    @EnvironmentObject private var auth: AuthorizationModel

    @State private var requestedNotifications = false

    var body: some View {
        content
            .animation(.pactState, value: client.view?.screenName)
            .preferredColorScheme(isNight ? .dark : .light)
    }

    /// Night theming applies to in-session screens, where light is pollution.
    private var isNight: Bool {
        client.view?.isInSession ?? false
    }

    @ViewBuilder
    private var content: some View {
        if !auth.isAuthorized {
            // Family Controls authorization is the floor: without it we can't
            // shield, so onboarding gates everything else.
            OnboardingView(requestedNotifications: $requestedNotifications)
        } else if let view = client.view {
            sessionScreen(for: view)
        } else {
            // Authorized, no live view yet → the pre-session home.
            HomeView()
                .task { await ensureNotifications() }
        }
    }

    @ViewBuilder
    private func sessionScreen(for view: SeatView) -> some View {
        switch view {
        case .none:
            HomeView()
        case let .join(p):
            JoinView(prefillCode: p.code)
        case let .lobbyHost(p):
            LobbyHostView(payload: p)
        case let .lobbyWait(p):
            LobbyWaitView(payload: p)
        case let .night(p):
            NightView(payload: p)
        case let .ask(p):
            AskView(payload: p)
        case let .waiting(p):
            WaitingView(payload: p)
        case let .pass(p):
            PassView(payload: p)
        case let .broken(p):
            RecapView(payload: p)
        case .unknown:
            // A future screen this build doesn't know: hold on the night
            // surface rather than crash, and keep listening for a known state.
            HoldingView()
        }
    }

    /// Requests notification authorization once we're past Family Controls.
    private func ensureNotifications() async {
        guard !requestedNotifications else { return }
        requestedNotifications = true
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        if granted {
            await MainActor.run { UIApplication.shared.registerForRemoteNotifications() }
        }
    }
}

/// A minimal night-themed holding screen for unknown/transition states.
struct HoldingView: View {
    var body: some View {
        ZStack {
            Theme.night.ignoresSafeArea()
            Flame(lit: true)
        }
    }
}

import SwiftUI
import UserNotifications
import UIKit

/// The permission floor. Explains, plainly and without scare tactics, what
/// Pact will and won't do, then requests Family Controls (required to shield)
/// and notifications (required to be asked from a locked phone).
struct OnboardingView: View {
    @EnvironmentObject private var auth: AuthorizationModel
    @Binding var requestedNotifications: Bool
    @State private var working = false

    var body: some View {
        ZStack {
            Theme.surface.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    header
                    explainer
                    Spacer(minLength: 8)
                    actions
                    honestNote
                }
                .padding(24)
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pact")
                .font(Theme.display(34))
                .foregroundStyle(Theme.indigo)
            Text("Phones down. Everyone in.")
                .font(Theme.body(18))
                .foregroundStyle(Theme.inkMuted)
        }
        .padding(.top, 24)
    }

    private var explainer: some View {
        VStack(alignment: .leading, spacing: 18) {
            PermissionRow(
                title: "Screen Time access",
                detail: "So Pact can dim your apps while the table is locked. Calls and emergency services are never blocked.",
                granted: auth.isAuthorized
            )
            PermissionRow(
                title: "Notifications",
                detail: "So you can answer the table from a locked phone in one tap. Pact never messages you outside a live session.",
                granted: requestedNotifications
            )
        }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            if let error = auth.lastError {
                Text(error)
                    .font(Theme.body(14))
                    .foregroundStyle(Theme.notNow)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button {
                Task { await grant() }
            } label: {
                Text(working ? "Requesting…" : "Allow & continue")
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(working)
            .accessibilityHint("Requests Screen Time and notification permissions.")
        }
    }

    private var honestNote: some View {
        Text("Glass, not steel. Pact never traps you — leaving a pact is always two taps, and the table always sees it.")
            .font(Theme.body(13))
            .foregroundStyle(Theme.inkMuted)
            .padding(.top, 8)
    }

    private func grant() async {
        working = true
        defer { working = false }
        await auth.requestAuthorization()
        await requestNotifications()
    }

    private func requestNotifications() async {
        requestedNotifications = true
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        if granted {
            await MainActor.run { UIApplication.shared.registerForRemoteNotifications() }
        }
    }
}

private struct PermissionRow: View {
    let title: String
    let detail: String
    let granted: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: granted ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 22))
                .foregroundStyle(granted ? Theme.allow : Theme.indigo.opacity(0.5))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(Theme.medium(17))
                    .foregroundStyle(Theme.ink)
                Text(detail)
                    .font(Theme.body(15))
                    .foregroundStyle(Theme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title). \(granted ? "Granted." : "Not yet granted.") \(detail)")
    }
}

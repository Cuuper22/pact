import SwiftUI

/// The night screen — the only dramatic surface, and its drama is darkness.
/// Near-black indigo, an ambient flame, the present-time clock, the roster, and
/// the three things you can do: ask the table, declare an emergency, or leave.
/// Designed to be read by the whole group, face-up on the table.
struct NightView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var client: PactClient
    let payload: SeatView.NightPayload

    @State private var showAsk = false
    @State private var confirmLeave = false
    @State private var confirmEmergency = false
    @State private var tick = Date()

    /// Cooldown is surfaced from the client's cosmetic countdown when present.
    private var cooldown: PactClient.Countdown? {
        if let cd = client.countdown, cd.isCooldown { return cd }
        return nil
    }

    private var canAsk: Bool {
        (payload.canAsk ?? true) && cooldown == nil
    }

    var body: some View {
        ZStack {
            Theme.night.ignoresSafeArea()
            VStack(spacing: 0) {
                topBanner
                Spacer()
                clock
                Flame(lit: true, asking: false)
                    .padding(.vertical, 8)
                roster
                Spacer()
                actions
            }
            .padding(24)
            .frame(maxWidth: 540)
            .frame(maxWidth: .infinity)
        }
        .navigationBarHidden(true)
        .onChange(of: payload.presentMs) { _ in
            // Re-anchor the smooth clock each time the relay sends fresh state.
            viewAnchor = Date()
        }
        .sheet(isPresented: $showAsk) {
            AskReasonSheet { reason in
                showAsk = false
                client.ask(reason: reason)
            }
            .presentationDetents([.medium, .large])
        }
        .confirmationDialog("Leave the pact?", isPresented: $confirmLeave, titleVisibility: .visible) {
            Button("Leave", role: .destructive) { Task { await model.leave() } }
            Button("Stay", role: .cancel) {}
        } message: {
            Text("The flame dies on every screen and the recap names you. You can always leave.")
        }
        .confirmationDialog("Emergency unlock?", isPresented: $confirmEmergency, titleVisibility: .visible) {
            Button("Unlock now", role: .destructive) { client.emergency() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Unlocks your phone immediately, no vote. The table is told. Calls and SOS are never blocked anyway.")
        }
    }

    // MARK: Sections

    @ViewBuilder
    private var topBanner: some View {
        if let banner = payload.banner, !banner.isEmpty {
            Text(banner)
                .font(Theme.body(14))
                .foregroundStyle(Theme.nightTextMuted)
                .padding(.vertical, 8)
                .padding(.horizontal, 14)
                .background(Capsule().fill(Theme.nightSurface))
                .padding(.top, 8)
        } else if let notice = payload.notice, !notice.isEmpty {
            Text(notice)
                .font(Theme.body(14))
                .foregroundStyle(Theme.nightTextMuted)
                .padding(.top, 8)
        } else {
            Color.clear.frame(height: 1)
        }
    }

    private var clock: some View {
        TimelineView(.periodic(from: .now, by: 1)) { _ in
            let present = payload.presentMs + max(0, Date().timeIntervalSince(viewAnchor) * 1000)
            VStack(spacing: 6) {
                Text(Format.clock(ms: present))
                    .font(Theme.clock(56))
                    .foregroundStyle(Theme.nightText)
                Text("present together")
                    .font(Theme.body(14))
                    .foregroundStyle(Theme.nightTextMuted)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Present together for \(Format.duration(ms: present))")
        }
    }

    private var roster: some View {
        RosterView(members: payload.members)
            .padding(.top, 8)
    }

    @ViewBuilder
    private var actions: some View {
        VStack(spacing: 14) {
            if let cooldown {
                Text("You can ask again in \(cooldown.seconds)s")
                    .font(Theme.body(14))
                    .foregroundStyle(Theme.nightTextMuted)
            }
            Button {
                showAsk = true
            } label: {
                Text("Ask the table")
                    .font(Theme.medium(18))
                    .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget)
                    .foregroundStyle(Theme.night)
                    .background(
                        RoundedRectangle(cornerRadius: Theme.cornerRadius)
                            .fill(canAsk ? Theme.nightText : Theme.nightTextMuted.opacity(0.4))
                    )
            }
            .disabled(!canAsk)
            .accessibilityHint("Requests a short unlock pass from everyone at the table.")

            HStack(spacing: 24) {
                Button("Emergency") { confirmEmergency = true }
                    .buttonStyle(NightTextButtonStyle(tint: Theme.emberDim))
                Button("Leave") { confirmLeave = true }
                    .buttonStyle(NightTextButtonStyle())
            }
        }
        .padding(.bottom, 8)
    }

    /// A stable anchor so the in-view clock advances smoothly between relay
    /// frames without re-anchoring on each render.
    @State private var viewAnchor = Date()
}

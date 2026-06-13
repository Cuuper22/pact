import SwiftUI

/// The host's lobby: a QR of the join link, the short table code, the roster as
/// people scan in, and the Lock button — enabled only when the relay says
/// `canLock`. Light surface; the drama starts after the lock.
struct LobbyHostView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var client: PactClient
    let payload: SeatView.LobbyHostPayload

    private var joinLink: String { client.credentials?.joinLink ?? "" }

    var body: some View {
        ZStack {
            Theme.surface.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 28) {
                    qrBlock
                    rosterBlock
                    if !payload.stakes.isEmpty { stakesBlock }
                    Spacer(minLength: 8)
                    lockButton
                    leaveButton
                }
                .padding(24)
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationBarBackButtonHidden(true)
    }

    private var qrBlock: some View {
        VStack(spacing: 16) {
            Text("Everyone scans this")
                .font(Theme.medium(17))
                .foregroundStyle(Theme.ink)
            QRImage(string: joinLink, size: 240)
                .padding(16)
                .background(RoundedRectangle(cornerRadius: Theme.cornerRadius).fill(.white).shadow(color: .black.opacity(0.06), radius: 10, y: 3))
            VStack(spacing: 4) {
                Text("or enter the code")
                    .font(Theme.body(13))
                    .foregroundStyle(Theme.inkMuted)
                Text(payload.code)
                    .font(Theme.clock(28))
                    .foregroundStyle(Theme.indigo)
                    .accessibilityLabel("Table code \(spelledOut(payload.code))")
            }
        }
        .padding(.top, 12)
    }

    private var rosterBlock: some View {
        VStack(spacing: 10) {
            Text("\(payload.members.count) at the table")
                .font(Theme.medium(15))
                .foregroundStyle(Theme.inkMuted)
            VStack(spacing: 8) {
                ForEach(payload.members) { m in
                    HStack {
                        Text(m.name).font(Theme.body(17)).foregroundStyle(Theme.ink)
                        if m.host {
                            Text("you, host").font(Theme.body(13)).foregroundStyle(Theme.inkMuted)
                        }
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(Theme.allow)
                            .accessibilityHidden(true)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("\(m.name)\(m.host ? ", you, host" : "") joined")
                }
            }
        }
    }

    private var stakesBlock: some View {
        VStack(spacing: 4) {
            Text("Stakes").font(Theme.body(13)).foregroundStyle(Theme.inkMuted)
            Text(payload.stakes).font(Theme.body(16)).foregroundStyle(Theme.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }

    private var lockButton: some View {
        Button {
            client.lock()
        } label: {
            Text(payload.canLock ? "Lock the table" : "Waiting for the table…")
        }
        .buttonStyle(PrimaryButtonStyle(enabled: payload.canLock))
        .disabled(!payload.canLock)
        .accessibilityHint(payload.canLock
            ? "Locks every phone at the table at once."
            : "Enabled once everyone has scanned in.")
    }

    private var leaveButton: some View {
        Button("Leave") { Task { await model.leave() } }
            .buttonStyle(NightTextButtonStyle(tint: Theme.inkMuted))
    }

    private func spelledOut(_ code: String) -> String {
        code.map { String($0) }.joined(separator: " ")
    }
}

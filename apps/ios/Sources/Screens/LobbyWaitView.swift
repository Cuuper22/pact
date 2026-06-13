import SwiftUI

/// A guest's lobby: scanned in, waiting for the host to lock. Calm and quiet.
struct LobbyWaitView: View {
    @EnvironmentObject private var model: AppModel
    let payload: SeatView.LobbyWaitPayload

    var body: some View {
        ZStack {
            Theme.surface.ignoresSafeArea()
            VStack(spacing: 28) {
                Spacer()
                VStack(spacing: 10) {
                    Text("You're in")
                        .font(Theme.display(28))
                        .foregroundStyle(Theme.ink)
                    Text("Waiting for the host to lock the table.")
                        .font(Theme.body(16))
                        .foregroundStyle(Theme.inkMuted)
                        .multilineTextAlignment(.center)
                }
                VStack(spacing: 8) {
                    Text("\(payload.members.count) at the table")
                        .font(Theme.medium(15))
                        .foregroundStyle(Theme.inkMuted)
                    ForEach(payload.members) { m in
                        Text(m.host ? "\(m.name) · host" : m.name)
                            .font(Theme.body(17))
                            .foregroundStyle(Theme.ink)
                    }
                }
                Spacer()
                Button("Leave") { Task { await model.leave() } }
                    .buttonStyle(SecondaryButtonStyle())
                    .padding(.horizontal, 24)
                    .padding(.bottom, 24)
            }
            .frame(maxWidth: 540)
            .frame(maxWidth: .infinity)
        }
        .navigationBarBackButtonHidden(true)
        .accessibilityElement(children: .contain)
    }
}

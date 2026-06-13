import SwiftUI

/// You hold a pass — your phone is unshielded and counting down. The table sees
/// the same countdown. When it hits zero the relay relocks and sends `night`.
struct PassView: View {
    @EnvironmentObject private var client: PactClient
    let payload: SeatView.PassPayload

    private var fraction: Double {
        client.countdown?.fraction ?? 1
    }
    private var seconds: Int {
        client.countdown?.seconds ?? Int((payload.remainMs / 1000).rounded(.up))
    }

    var body: some View {
        ZStack {
            Theme.night.ignoresSafeArea()
            VStack(spacing: 0) {
                Spacer()
                Flame(lit: true, asking: true)
                VStack(spacing: 10) {
                    Text(payload.emergency ? "Emergency unlock" : "You're unlocked")
                        .font(Theme.display(26))
                        .foregroundStyle(Theme.nightText)
                    Text(timeString)
                        .font(Theme.clock(64))
                        .foregroundStyle(Theme.ember)
                        .accessibilityLabel("\(seconds) seconds of your pass remaining")
                    Text("then the table relocks")
                        .font(Theme.body(14))
                        .foregroundStyle(Theme.nightTextMuted)
                }
                CountdownBar(fraction: fraction, asking: true)
                    .frame(maxWidth: 280)
                    .padding(.top, 16)
                Spacer()
                Text("Use it, then put it down.")
                    .font(Theme.body(15))
                    .foregroundStyle(Theme.nightTextMuted)
                    .padding(.bottom, 24)
            }
            .padding(24)
            .frame(maxWidth: 540)
            .frame(maxWidth: .infinity)
        }
        .navigationBarHidden(true)
    }

    private var timeString: String {
        let s = max(0, seconds)
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}

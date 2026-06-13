import SwiftUI

/// Your ask is on the table; you're waiting on the verdict. Shows the live
/// tally and the 60s bar. Calm — you've done your part.
struct WaitingView: View {
    @EnvironmentObject private var client: PactClient
    let payload: SeatView.WaitingPayload

    private var fraction: Double {
        client.countdown?.fraction ?? max(0, min(1, payload.remainMs / 60_000))
    }
    private var seconds: Int {
        client.countdown?.seconds ?? Int((payload.remainMs / 1000).rounded(.up))
    }

    var body: some View {
        ZStack {
            Theme.night.ignoresSafeArea()
            VStack(spacing: 0) {
                Spacer()
                VStack(spacing: 8) {
                    Text("Your ask is on the table")
                        .font(Theme.display(24))
                        .foregroundStyle(Theme.nightText)
                        .multilineTextAlignment(.center)
                    Text(payload.reason)
                        .font(Theme.body(18))
                        .foregroundStyle(Theme.ember)
                }
                Flame(lit: true, asking: true).padding(.vertical, 8)
                VStack(spacing: 8) {
                    Text("\(seconds)s")
                        .font(Theme.clock(20))
                        .foregroundStyle(Theme.nightTextMuted)
                    CountdownBar(fraction: fraction, asking: true)
                        .frame(maxWidth: 280)
                }
                Spacer()
                tally
                Spacer()
            }
            .padding(24)
            .frame(maxWidth: 540)
            .frame(maxWidth: .infinity)
        }
        .navigationBarHidden(true)
        .accessibilityElement(children: .contain)
    }

    private var tally: some View {
        VStack(spacing: 8) {
            ForEach(payload.tally) { entry in
                HStack(spacing: 10) {
                    switch entry.vote {
                    case .some(true):
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.allow)
                    case .some(false):
                        Image(systemName: "minus.circle.fill").foregroundStyle(Theme.notNow)
                    case .none:
                        Image(systemName: "circle.dashed").foregroundStyle(Theme.nightTextMuted)
                    }
                    Text(entry.name).font(Theme.body(16)).foregroundStyle(Theme.nightText)
                    Spacer()
                    Text(word(entry.vote)).font(Theme.body(14)).foregroundStyle(Theme.nightTextMuted)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(entry.name): \(word(entry.vote))")
            }
        }
        .frame(maxWidth: 320)
    }

    private func word(_ vote: Bool?) -> String {
        switch vote {
        case .some(true): return "allowed"
        case .some(false): return "not now"
        case .none: return "deciding"
        }
    }
}

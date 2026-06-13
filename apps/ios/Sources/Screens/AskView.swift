import SwiftUI

/// Someone at the table asked for a pass and you must vote. Ember accent (the
/// one candle), large Allow / Not now targets, the live tally, and a 60s bar.
/// Never color alone for vote state — every tally row pairs an icon with a word.
struct AskView: View {
    @EnvironmentObject private var client: PactClient
    let payload: SeatView.AskPayload

    @State private var voted: Bool?

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
                header
                Flame(lit: true, asking: true)
                    .padding(.vertical, 4)
                timer
                Spacer()
                tally
                Spacer()
                voteButtons
            }
            .padding(24)
            .frame(maxWidth: 540)
            .frame(maxWidth: .infinity)
        }
        .navigationBarHidden(true)
        .onChange(of: payload.asker) { _ in voted = nil }
    }

    private var header: some View {
        VStack(spacing: 8) {
            Text("\(payload.asker) asks the table")
                .font(Theme.display(24))
                .foregroundStyle(Theme.nightText)
                .multilineTextAlignment(.center)
            Text(payload.reason)
                .font(Theme.body(18))
                .foregroundStyle(Theme.ember)
                .multilineTextAlignment(.center)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(payload.asker) asks the table. Reason: \(payload.reason).")
    }

    private var timer: some View {
        VStack(spacing: 8) {
            Text("\(seconds)s")
                .font(Theme.clock(20))
                .foregroundStyle(Theme.nightTextMuted)
            CountdownBar(fraction: fraction, asking: true)
                .frame(maxWidth: 280)
        }
        .padding(.top, 8)
        .accessibilityLabel("\(seconds) seconds left to vote")
    }

    private var tally: some View {
        VStack(spacing: 8) {
            ForEach(payload.tally) { entry in
                HStack(spacing: 10) {
                    voteGlyph(entry.vote)
                    Text(entry.name)
                        .font(Theme.body(16))
                        .foregroundStyle(Theme.nightText)
                    Spacer()
                    Text(voteWord(entry.vote))
                        .font(Theme.body(14))
                        .foregroundStyle(Theme.nightTextMuted)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(entry.name): \(voteWord(entry.vote))")
            }
        }
        .frame(maxWidth: 320)
    }

    @ViewBuilder
    private func voteGlyph(_ vote: Bool?) -> some View {
        // Icon + label, never color alone (accessibility requirement).
        switch vote {
        case .some(true):
            Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.allow)
        case .some(false):
            Image(systemName: "minus.circle.fill").foregroundStyle(Theme.notNow)
        case .none:
            Image(systemName: "circle.dashed").foregroundStyle(Theme.nightTextMuted)
        }
    }

    private func voteWord(_ vote: Bool?) -> String {
        switch vote {
        case .some(true): return "allowed"
        case .some(false): return "not now"
        case .none: return "deciding"
        }
    }

    private var voteButtons: some View {
        HStack(spacing: 14) {
            voteButton(allow: false, title: "Not now", icon: "minus.circle.fill", tint: Theme.notNow)
            voteButton(allow: true, title: "Allow", icon: "checkmark.circle.fill", tint: Theme.allow)
        }
        .padding(.bottom, 8)
    }

    private func voteButton(allow: Bool, title: String, icon: String, tint: Color) -> some View {
        Button {
            voted = allow
            client.vote(allow: allow)
        } label: {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 26))
                Text(title).font(Theme.medium(17))
            }
            .frame(maxWidth: .infinity, minHeight: 88)
            .foregroundStyle(voted == allow ? Theme.night : Theme.nightText)
            .background(
                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                    .fill(voted == allow ? tint : Theme.nightSurface)
                    .overlay(RoundedRectangle(cornerRadius: Theme.cornerRadius).stroke(tint.opacity(0.5), lineWidth: 1))
            )
        }
        .accessibilityLabel(title)
        .accessibilityAddTraits(voted == allow ? [.isSelected] : [])
    }
}

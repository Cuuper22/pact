import SwiftUI

/// The end-of-pact card: time present, asks made/granted/refused, who broke it,
/// the stakes. The share moment — a ShareLink drops a plain-text summary into
/// the group chat the table was avoiding all evening.
struct RecapView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var client: PactClient
    let payload: SeatView.BrokenPayload

    private var recap: Recap { payload.recap }

    var body: some View {
        ZStack {
            // The recap eases back toward the light surface — the evening's over.
            Theme.surface.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 24) {
                    card
                    ShareLink(item: shareText) {
                        Label("Share to the group", systemImage: "square.and.arrow.up")
                            .font(Theme.medium(17))
                            .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget)
                            .foregroundStyle(.white)
                            .background(RoundedRectangle(cornerRadius: Theme.cornerRadius).fill(Theme.indigo))
                    }
                    Button("Done") {
                        model.client.endSession()
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }
                .padding(24)
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationBarHidden(true)
    }

    private var card: some View {
        VStack(spacing: 20) {
            VStack(spacing: 6) {
                Text("The pact is done")
                    .font(Theme.body(15))
                    .foregroundStyle(Theme.inkMuted)
                Text(Format.duration(ms: recap.presentMs))
                    .font(Theme.clock(48))
                    .foregroundStyle(Theme.indigo)
                Text("present together")
                    .font(Theme.body(14))
                    .foregroundStyle(Theme.inkMuted)
            }

            Divider()

            HStack {
                stat(value: "\(recap.asks)", label: "asks")
                stat(value: "\(recap.granted)", label: "granted")
                stat(value: "\(recap.denied)", label: "refused")
            }

            if !recap.stakes.isEmpty {
                VStack(spacing: 4) {
                    Text("Stakes").font(Theme.body(13)).foregroundStyle(Theme.inkMuted)
                    Text(recap.stakes).font(Theme.body(16)).foregroundStyle(Theme.ink)
                }
            }

            Divider()

            Group {
                if let loser = recap.brokenBy {
                    Text("\(loser) folded first.")
                        .font(Theme.medium(18))
                        .foregroundStyle(Theme.ink)
                } else {
                    Text("Nobody broke it. The whole table held.")
                        .font(Theme.medium(18))
                        .foregroundStyle(Theme.ink)
                        .multilineTextAlignment(.center)
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 20).fill(.white).shadow(color: .black.opacity(0.08), radius: 16, y: 6))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
    }

    private func stat(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value).font(Theme.clock(28)).foregroundStyle(Theme.ink)
            Text(label).font(Theme.body(13)).foregroundStyle(Theme.inkMuted)
        }
        .frame(maxWidth: .infinity)
    }

    private var shareText: String {
        var lines = ["Pact recap"]
        lines.append("\(Format.duration(ms: recap.presentMs)) present together")
        lines.append("\(recap.asks) asks · \(recap.granted) granted · \(recap.denied) refused")
        if !recap.stakes.isEmpty { lines.append("Stakes: \(recap.stakes)") }
        if let loser = recap.brokenBy {
            lines.append("\(loser) folded first.")
        } else {
            lines.append("Nobody broke it.")
        }
        return lines.joined(separator: "\n")
    }

    private var accessibilitySummary: String {
        let loser = recap.brokenBy.map { "\($0) folded first." } ?? "Nobody broke it."
        return "Present together \(Format.duration(ms: recap.presentMs)). \(recap.asks) asks, \(recap.granted) granted, \(recap.denied) refused. \(loser)"
    }
}

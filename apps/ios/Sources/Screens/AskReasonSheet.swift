import SwiftUI

/// The reason picker shown when asking the table. Quick chips plus a free-text
/// field. "No reason" is a valid choice — the table can judge it. Sent reasons
/// map to the `ask` action's optional `reason`.
struct AskReasonSheet: View {
    /// Called with the chosen reason, or `nil` for "No reason".
    let onAsk: (String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var custom = ""

    private let chips = [
        "Expecting a call", "Need to pay", "Directions",
        "Kids", "Work",
    ]

    var body: some View {
        ZStack {
            Theme.night.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 20) {
                Text("Ask the table")
                    .font(Theme.display(24))
                    .foregroundStyle(Theme.nightText)
                Text("Everyone gets your ask. All yes, and you get a pass.")
                    .font(Theme.body(15))
                    .foregroundStyle(Theme.nightTextMuted)

                FlowChips(chips: chips) { reason in onAsk(reason) }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Or say why")
                        .font(Theme.medium(14))
                        .foregroundStyle(Theme.nightTextMuted)
                    HStack(spacing: 10) {
                        TextField("", text: $custom, prompt: Text("Type a reason").foregroundColor(Theme.nightTextMuted))
                            .font(Theme.body(16))
                            .foregroundStyle(Theme.nightText)
                            .padding(12)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Theme.nightSurface))
                        Button("Ask") {
                            let trimmed = custom.trimmingCharacters(in: .whitespaces)
                            onAsk(trimmed.isEmpty ? nil : trimmed)
                        }
                        .buttonStyle(NightTextButtonStyle(tint: Theme.nightText))
                    }
                }

                Spacer()

                Button("Ask with no reason") { onAsk(nil) }
                    .buttonStyle(NightTextButtonStyle())
                    .frame(maxWidth: .infinity)
            }
            .padding(24)
        }
    }
}

/// A simple wrapping row of tappable reason chips.
private struct FlowChips: View {
    let chips: [String]
    let onTap: (String) -> Void

    var body: some View {
        // A two-column adaptive grid keeps it readable in low light.
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 10)], spacing: 10) {
            ForEach(chips, id: \.self) { chip in
                Button { onTap(chip) } label: {
                    Text(chip)
                        .font(Theme.body(16))
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .foregroundStyle(Theme.nightText)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Theme.nightSurface))
                }
            }
        }
    }
}

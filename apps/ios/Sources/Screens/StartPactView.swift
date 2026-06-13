import SwiftUI

/// Start a pact: host name, pass length (2 / 5 / 10), optional stakes line.
/// On success the relay returns lobby-host and the router swaps to the lobby.
struct StartPactView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var passMinutes = 5
    @State private var stakes = ""
    @State private var working = false

    private let passOptions = [2, 5, 10]

    var body: some View {
        ZStack {
            Theme.surface.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    field(title: "Your first name") {
                        TextField("Yousef", text: $name)
                            .textInputAutocapitalization(.words)
                            .textContentType(.givenName)
                            .submitLabel(.done)
                            .font(Theme.body(18))
                            .padding(14)
                            .background(fieldBackground)
                    }

                    field(title: "Pass length") {
                        Picker("Pass length", selection: $passMinutes) {
                            ForEach(passOptions, id: \.self) { m in
                                Text("\(m) min").tag(m)
                            }
                        }
                        .pickerStyle(.segmented)
                        .accessibilityHint("How long each unlock pass lasts.")
                    }

                    field(title: "Stakes (optional)") {
                        TextField("First break buys dessert", text: $stakes)
                            .font(Theme.body(18))
                            .padding(14)
                            .background(fieldBackground)
                        Text("Money never moves inside Pact. The recap just names the loser.")
                            .font(Theme.body(13))
                            .foregroundStyle(Theme.inkMuted)
                    }

                    Button {
                        Task { await start() }
                    } label: {
                        Text(working ? "Setting the table…" : "Create the table")
                    }
                    .buttonStyle(PrimaryButtonStyle(enabled: canCreate))
                    .disabled(!canCreate || working)
                }
                .padding(24)
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("Start a pact")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var canCreate: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private var fieldBackground: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color(.secondarySystemBackground))
    }

    @ViewBuilder
    private func field<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(Theme.medium(15))
                .foregroundStyle(Theme.inkMuted)
            content()
        }
    }

    private func start() async {
        working = true
        defer { working = false }
        await model.createPact(
            hostName: name.trimmingCharacters(in: .whitespaces),
            passMinutes: passMinutes,
            stakes: stakes.trimmingCharacters(in: .whitespaces)
        )
    }
}

import SwiftUI

/// The pre-session home: start a pact or join one. Pure white, restrained.
struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var path: [Route] = []

    enum Route: Hashable { case start, join }

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                Theme.surface.ignoresSafeArea()
                VStack(spacing: 0) {
                    Spacer()
                    VStack(spacing: 10) {
                        Text("Pact")
                            .font(Theme.display(40))
                            .foregroundStyle(Theme.indigo)
                        Text("The phone stack, with teeth.")
                            .font(Theme.body(17))
                            .foregroundStyle(Theme.inkMuted)
                    }
                    Spacer()
                    VStack(spacing: 12) {
                        Button("Start a pact") { path.append(.start) }
                            .buttonStyle(PrimaryButtonStyle())
                        Button("Join a table") { path.append(.join) }
                            .buttonStyle(SecondaryButtonStyle())
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 32)
                }
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .start: StartPactView()
                case .join: JoinView(prefillCode: nil)
                }
            }
            .onChange(of: model.pendingJoinCode) { code in
                // A scanned/opened join deep link jumps straight to Join.
                if code != nil, path.last != .join { path.append(.join) }
            }
            .onAppear {
                if model.pendingJoinCode != nil, path.last != .join { path.append(.join) }
            }
            .overlay(alignment: .bottom) {
                if let error = model.errorMessage {
                    ErrorBanner(message: error) { model.errorMessage = nil }
                        .padding()
                }
            }
        }
    }
}

/// A lightweight dismissible error banner used across the pre-session screens.
struct ErrorBanner: View {
    let message: String
    let dismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Theme.notNow)
                .accessibilityHidden(true)
            Text(message)
                .font(Theme.body(15))
                .foregroundStyle(Theme.ink)
            Spacer(minLength: 8)
            Button(action: dismiss) {
                Image(systemName: "xmark")
                    .foregroundStyle(Theme.inkMuted)
            }
            .accessibilityLabel("Dismiss")
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        )
        .accessibilityElement(children: .combine)
    }
}

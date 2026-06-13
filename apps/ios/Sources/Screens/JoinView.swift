import SwiftUI
import AVFoundation

/// Join a table: scan the host's QR or type the short code, then your name.
/// Accepts both a raw `TBL-XXXX` code and a full `pact://join?code=…` deep link
/// from the scanner.
struct JoinView: View {
    @EnvironmentObject private var model: AppModel
    let prefillCode: String?

    @State private var code: String = ""
    @State private var name: String = ""
    @State private var scanning = false
    @State private var cameraDenied = false
    @State private var working = false

    var body: some View {
        ZStack {
            Theme.surface.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    scanSection
                    Divider()
                    manualSection
                    nameSection
                    joinButton
                }
                .padding(24)
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("Join a table")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let prefill = prefillCode, code.isEmpty { code = prefill }
            if let pending = model.pendingJoinCode { code = pending }
        }
        .onChange(of: model.pendingJoinCode) { pending in
            if let pending { code = pending }
        }
    }

    // MARK: Scan

    private var scanSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Scan the host's code")
                .font(Theme.medium(15))
                .foregroundStyle(Theme.inkMuted)

            if scanning {
                scannerSurface
            } else {
                Button {
                    Task { await startScanning() }
                } label: {
                    Label("Open camera", systemImage: "qrcode.viewfinder")
                }
                .buttonStyle(SecondaryButtonStyle())
            }

            if cameraDenied {
                Text("Camera access is off. Enable it in Settings, or type the code below.")
                    .font(Theme.body(13))
                    .foregroundStyle(Theme.notNow)
            }
        }
    }

    private var scannerSurface: some View {
        QRScannerView { value in
            scanning = false
            apply(scanned: value)
        }
        .frame(height: 260)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.indigo.opacity(0.3), lineWidth: 1)
        )
        .accessibilityLabel("Camera viewfinder. Point it at the host's QR code.")
    }

    // MARK: Manual entry

    private var manualSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Or enter the table code")
                .font(Theme.medium(15))
                .foregroundStyle(Theme.inkMuted)
            TextField("TBL-K7QP", text: $code)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .font(Theme.clock(20))
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
        }
    }

    private var nameSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your first name")
                .font(Theme.medium(15))
                .foregroundStyle(Theme.inkMuted)
            TextField("Maya", text: $name)
                .textInputAutocapitalization(.words)
                .textContentType(.givenName)
                .font(Theme.body(18))
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
        }
    }

    private var joinButton: some View {
        Button {
            Task { await join() }
        } label: {
            Text(working ? "Joining…" : "Join the table")
        }
        .buttonStyle(PrimaryButtonStyle(enabled: canJoin))
        .disabled(!canJoin || working)
    }

    // MARK: Logic

    private var canJoin: Bool {
        !normalizedCode.isEmpty && !name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private var normalizedCode: String {
        code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    /// Accepts either a raw code or a `pact://join?code=…` deep link payload.
    private func apply(scanned value: String) {
        if let url = URL(string: value),
           url.scheme == PactConfig.urlScheme,
           let comps = URLComponents(url: url, resolvingAgainstBaseURL: false),
           let scannedCode = comps.queryItems?.first(where: { $0.name == "code" })?.value {
            code = scannedCode
        } else {
            code = value
        }
    }

    private func startScanning() async {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            scanning = true
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            scanning = granted
            cameraDenied = !granted
        default:
            cameraDenied = true
        }
    }

    private func join() async {
        working = true
        defer { working = false }
        await model.joinPact(code: normalizedCode, name: name.trimmingCharacters(in: .whitespaces))
    }
}

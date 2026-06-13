import Foundation
import FamilyControls
import Combine

/// Drives the Family Controls authorization flow with `.individual` enrollment
/// (the user shielding their own device, not a guardian shielding a child).
/// Publishes the current status so the onboarding screen can react.
///
/// Note: `AuthorizationCenter.requestAuthorization(for: .individual)` requires
/// the `com.apple.developer.family-controls` entitlement, which Apple grants
/// only after a distribution-entitlement application (see README). On the
/// Simulator the request fails; test on a provisioned device.
@MainActor
public final class AuthorizationModel: ObservableObject {
    @Published public private(set) var status: AuthorizationStatus
    @Published public private(set) var lastError: String?

    private let center = AuthorizationCenter.shared

    public init() {
        self.status = center.authorizationStatus
    }

    /// Whether the app may apply shields right now.
    public var isAuthorized: Bool { status == .approved }

    /// Refreshes the cached status (e.g. on `scenePhase` becoming active, in
    /// case the user changed Screen Time permission in Settings mid-pact).
    public func refresh() {
        status = center.authorizationStatus
    }

    /// Requests individual authorization. Throws are caught and surfaced via
    /// `lastError`; the published `status` is updated either way.
    public func requestAuthorization() async {
        lastError = nil
        do {
            try await center.requestAuthorization(for: .individual)
        } catch {
            lastError = error.localizedDescription
        }
        status = center.authorizationStatus
    }
}

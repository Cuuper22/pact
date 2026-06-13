import Foundation

/// Constants and the shared `UserDefaults` suite the main app and both app
/// extensions use to exchange state. The app group is the only channel the
/// extensions have back to the app's world: the relay base URL, the current
/// seat credentials, and the live lock state all flow through here.
///
/// Keep `suiteName` in sync with the `.entitlements` files and `project.yml`.
public enum AppGroup {
    /// The App Group identifier. Must match every target's entitlements.
    public static let suiteName = "group.app.pact"

    /// Shared defaults suite. Force-unwrap is intentional: a misconfigured
    /// app group is a build-time provisioning error, not a runtime condition
    /// we can recover from, and surfacing it loudly in development is correct.
    public static var defaults: UserDefaults {
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            fatalError("App Group \(suiteName) is not configured. Check entitlements.")
        }
        return defaults
    }

    /// Keys written into the shared suite.
    public enum Key {
        /// Relay origin (e.g. https://relay.pact.app). Mirrors Info.plist's
        /// PactRelayURL so the extensions, which don't read the app's
        /// Info.plist, can reach the relay too.
        public static let relayURL = "pact.relayURL"
        /// JSON-encoded `SeatCredentials` for the active pact, or absent.
        public static let credentials = "pact.credentials"
        /// JSON-encoded `LockState` describing the current shield posture.
        public static let lockState = "pact.lockState"
    }
}

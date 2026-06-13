import Foundation

/// Resolves the relay base URL. Resolution order:
/// 1. A value already mirrored into the App Group (so extensions agree with
///    the app and an in-app override survives relaunch).
/// 2. The main bundle's `PactRelayURL` Info.plist key (injected from the
///    `PACT_RELAY_URL` build setting in project.yml).
/// 3. The localhost default, for a developer running the relay locally.
///
/// The app writes the resolved value back into the App Group on launch so the
/// shield extensions — which cannot read the app's Info.plist — reach the same
/// relay.
public enum PactConfig {
    public static let fallbackRelayURL = URL(string: "http://localhost:8787")!

    /// The platform discriminant the client always reports.
    public static let platform: Platform = .ios

    /// The custom URL scheme used for join deep links (`pact://join?code=…`).
    public static let urlScheme = "pact"

    /// The currently configured relay origin.
    public static var relayURL: URL {
        if let shared = AppGroup.defaults.string(forKey: AppGroup.Key.relayURL),
           let url = URL(string: shared) {
            return url
        }
        if let plist = Bundle.main.object(forInfoDictionaryKey: "PactRelayURL") as? String,
           !plist.isEmpty,
           let url = URL(string: plist) {
            return url
        }
        return fallbackRelayURL
    }

    /// Mirrors the bundle's configured relay URL into the App Group so the
    /// extensions can read it. Call once on app launch.
    public static func syncRelayURLToAppGroup() {
        if let plist = Bundle.main.object(forInfoDictionaryKey: "PactRelayURL") as? String,
           !plist.isEmpty {
            AppGroup.defaults.set(plist, forKey: AppGroup.Key.relayURL)
        } else if AppGroup.defaults.string(forKey: AppGroup.Key.relayURL) == nil {
            AppGroup.defaults.set(fallbackRelayURL.absoluteString, forKey: AppGroup.Key.relayURL)
        }
    }

    /// Lets a developer override the relay at runtime (e.g. a debug settings
    /// screen). Persists into the App Group so it takes effect everywhere.
    public static func overrideRelayURL(_ url: URL) {
        AppGroup.defaults.set(url.absoluteString, forKey: AppGroup.Key.relayURL)
    }
}

import ManagedSettings
import ManagedSettingsUI
import UIKit

/// Renders the custom shield shown over any blocked app — the night screen,
/// reproduced: near-black indigo, the table's name/state, and an "Ask the
/// table" primary button (whose tap is handled by the ShieldAction extension).
///
/// The temptation surface becomes the request surface: when you reach for a
/// blocked app mid-pact, you see the night and a way to ask, not your feed.
///
/// This extension cannot run networking on its own schedule, so it reads the
/// shared `LockState` from the App Group to tailor its copy (e.g. naming a
/// pending asker). Colours mirror Theme.swift but are restated here as raw
/// `UIColor`s because `ShieldConfiguration` takes UIKit colours.
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    // Night palette (sRGB), mirroring Theme.swift.
    private let night = UIColor(red: 0.051, green: 0.055, blue: 0.086, alpha: 1)
    private let nightText = UIColor(red: 0.92, green: 0.93, blue: 0.96, alpha: 1)
    private let nightMuted = UIColor(red: 0.58, green: 0.60, blue: 0.68, alpha: 1)

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        makeConfiguration()
    }

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        makeConfiguration()
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        makeConfiguration()
    }

    override func configuration(
        shielding webDomain: WebDomain,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        makeConfiguration()
    }

    // MARK: Shared builder

    private func makeConfiguration() -> ShieldConfiguration {
        let state = LockState.load()
        let subtitle: String
        if let asker = state.pendingAsker, !asker.isEmpty {
            subtitle = "\(asker) is asking the table right now."
        } else {
            subtitle = "Phones are down. Ask the table if you need a few minutes."
        }

        return ShieldConfiguration(
            backgroundBlurStyle: .dark,
            backgroundColor: night,
            icon: UIImage(systemName: "flame.fill"),
            title: ShieldConfiguration.Label(text: "The table is locked", color: nightText),
            subtitle: ShieldConfiguration.Label(text: subtitle, color: nightMuted),
            primaryButtonLabel: ShieldConfiguration.Label(text: "Ask the table", color: night),
            primaryButtonBackgroundColor: nightText,
            secondaryButtonLabel: ShieldConfiguration.Label(text: "Stay present", color: nightMuted)
        )
    }
}

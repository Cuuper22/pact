import SwiftUI

/// The single source of design tokens. Colors are sRGB approximations of the
/// oklch seeds in IDEA.md; refine on-device if needed. The palette is
/// deliberately tiny: one indigo, one near-black night, one ember accent used
/// ONLY when the table is being asked something.
public enum Theme {
    // MARK: Colors

    /// Primary indigo ≈ oklch(0.40 0.15 270). Used on light surfaces.
    public static let indigo = Color(red: 0.231, green: 0.184, blue: 0.561) // #3B2F8F

    /// Night background ≈ oklch(0.12 0.013 275). The one drenched surface.
    public static let night = Color(red: 0.051, green: 0.055, blue: 0.086) // #0D0E16

    /// A slightly lifted night surface for cards on the dark screen.
    public static let nightSurface = Color(red: 0.090, green: 0.094, blue: 0.137) // #171823

    /// Ember accent ≈ oklch(0.72 0.13 70). ONLY when the table is being asked.
    public static let ember = Color(red: 0.910, green: 0.608, blue: 0.357) // #E89B5B

    /// A dimmer ember for the resting flame / static reduced-motion ember.
    public static let emberDim = Color(red: 0.788, green: 0.471, blue: 0.255) // #C97841

    /// Light surfaces: pure white, per the design direction.
    public static let surface = Color.white

    /// Primary text on light surfaces.
    public static let ink = Color(red: 0.071, green: 0.078, blue: 0.118) // near-black

    /// Muted text on light surfaces.
    public static let inkMuted = Color(red: 0.42, green: 0.43, blue: 0.48)

    /// Primary text on the night surface.
    public static let nightText = Color(red: 0.92, green: 0.93, blue: 0.96)

    /// Muted text on the night surface (e.g. roster, labels).
    public static let nightTextMuted = Color(red: 0.58, green: 0.60, blue: 0.68)

    /// Allow / yes — a calm, non-alarming positive. Never the only signal:
    /// always paired with an icon + label.
    public static let allow = Color(red: 0.40, green: 0.70, blue: 0.52)

    /// Not-now / decline. Muted, never aggressive red.
    public static let notNow = Color(red: 0.62, green: 0.45, blue: 0.45)

    // MARK: Typography
    // One sans family (the system sans, San Francisco), weights 400/500 only.

    public static func display(_ size: CGFloat) -> Font {
        .system(size: size, weight: .medium, design: .default)
    }

    public static func body(_ size: CGFloat = 17) -> Font {
        .system(size: size, weight: .regular, design: .default)
    }

    public static func medium(_ size: CGFloat = 17) -> Font {
        .system(size: size, weight: .medium, design: .default)
    }

    /// Tabular monospaced digits for clocks and countdowns so they don't jitter.
    public static func clock(_ size: CGFloat) -> Font {
        .system(size: size, weight: .regular, design: .default)
            .monospacedDigit()
    }

    // MARK: Metrics

    /// Minimum tap target (WCAG / HIG) — used by Allow / Not now in low light.
    public static let minTapTarget: CGFloat = 56

    /// Standard corner radius for cards and buttons.
    public static let cornerRadius: CGFloat = 16

    /// State motion duration (150–250ms band; we pick the middle).
    public static let motion: Double = 0.2
}

public extension Animation {
    /// The standard state transition (200ms ease).
    static let pactState = Animation.easeInOut(duration: Theme.motion)
}

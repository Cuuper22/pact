import SwiftUI

// MARK: - Buttons

/// The primary action button on light surfaces (indigo fill, white label).
public struct PrimaryButtonStyle: ButtonStyle {
    public var enabled: Bool = true
    public init(enabled: Bool = true) { self.enabled = enabled }

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.medium(17))
            .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget)
            .foregroundStyle(.white)
            .background(
                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                    .fill(enabled ? Theme.indigo : Theme.indigo.opacity(0.35))
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.pactState, value: configuration.isPressed)
    }
}

/// A quiet, bordered secondary button on light surfaces.
public struct SecondaryButtonStyle: ButtonStyle {
    public init() {}
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.medium(17))
            .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget)
            .foregroundStyle(Theme.indigo)
            .background(
                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                    .stroke(Theme.indigo.opacity(0.25), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// A restrained text button for the night screen (low-light, low-emphasis).
public struct NightTextButtonStyle: ButtonStyle {
    public var tint: Color = Theme.nightTextMuted
    public init(tint: Color = Theme.nightTextMuted) { self.tint = tint }
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.medium(16))
            .frame(minHeight: Theme.minTapTarget)
            .foregroundStyle(tint)
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

// MARK: - Formatting

public enum Format {
    /// "1h 23m" / "23m 04s" / "47s" — for time present together.
    public static func duration(ms: Double) -> String {
        let total = Int(max(0, ms) / 1000)
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        if h > 0 { return "\(h)h \(String(format: "%02d", m))m" }
        if m > 0 { return "\(m)m \(String(format: "%02d", s))s" }
        return "\(s)s"
    }

    /// "1:23:45" / "23:45" clock form for the present-time clock on night.
    public static func clock(ms: Double) -> String {
        let total = Int(max(0, ms) / 1000)
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        if h > 0 { return String(format: "%d:%02d:%02d", h, m, s) }
        return String(format: "%d:%02d", m, s)
    }
}

// MARK: - Roster

/// A compact, readable list of who's at the table, styled for the night screen.
/// Dimmed for any seat that has left (the flame dies; the roster shows it).
public struct RosterView: View {
    public var members: [MemberView]
    public init(members: [MemberView]) { self.members = members }

    public var body: some View {
        VStack(spacing: 6) {
            ForEach(members) { member in
                HStack(spacing: 8) {
                    Text(member.name)
                        .font(Theme.body(16))
                        .foregroundStyle(Theme.nightText)
                    if member.host {
                        Text("host")
                            .font(Theme.body(12))
                            .foregroundStyle(Theme.nightTextMuted)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                Capsule().stroke(Theme.nightTextMuted.opacity(0.4), lineWidth: 1)
                            )
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(member.host ? "\(member.name), host" : member.name)
            }
        }
    }
}

// MARK: - Countdown bar

/// A thin progress bar for the 60s vote window / pass countdown. Ember-tinted
/// only when `asking`.
public struct CountdownBar: View {
    public var fraction: Double
    public var asking: Bool
    public init(fraction: Double, asking: Bool) {
        self.fraction = fraction
        self.asking = asking
    }
    public var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.nightTextMuted.opacity(0.2))
                Capsule()
                    .fill(asking ? Theme.ember : Theme.nightTextMuted)
                    .frame(width: max(0, geo.size.width * fraction))
                    .animation(.linear(duration: 0.25), value: fraction)
            }
        }
        .frame(height: 6)
        .accessibilityHidden(true)
    }
}

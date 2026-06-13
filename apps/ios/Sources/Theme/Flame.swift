import SwiftUI

/// The ambient flame — the one candle on the table. The only element allowed a
/// slow, continuous animation. Honors Reduce Motion by rendering a still ember
/// (no flicker, no sway), per PRODUCT.md accessibility requirements.
///
/// `lit` is true while the seat is present; when a seat leaves, the flame on
/// every screen dies (the caller passes `lit: false`).
public struct Flame: View {
    public var lit: Bool
    /// When `asking` is true the flame warms to the full ember accent — the one
    /// time ember is allowed. Otherwise it rests at the dim ember.
    public var asking: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = 0

    public init(lit: Bool = true, asking: Bool = false) {
        self.lit = lit
        self.asking = asking
    }

    private var color: Color {
        guard lit else { return Theme.nightTextMuted.opacity(0.35) }
        return asking ? Theme.ember : Theme.emberDim
    }

    public var body: some View {
        ZStack {
            // Soft glow halo.
            Circle()
                .fill(
                    RadialGradient(
                        colors: [color.opacity(lit ? 0.45 : 0.0), .clear],
                        center: .center,
                        startRadius: 2,
                        endRadius: 60
                    )
                )
                .frame(width: 120, height: 120)
                .scaleEffect(reduceMotion || !lit ? 1.0 : 1.0 + 0.06 * sin(phase))
                .opacity(reduceMotion || !lit ? 0.9 : 0.75 + 0.25 * (0.5 + 0.5 * sin(phase)))

            // The flame body.
            FlameShape()
                .fill(
                    LinearGradient(
                        colors: [color, color.opacity(0.7)],
                        startPoint: .bottom,
                        endPoint: .top
                    )
                )
                .frame(width: 28, height: 44)
                .rotationEffect(.degrees(reduceMotion || !lit ? 0 : 2.5 * sin(phase * 1.3)))
        }
        .frame(width: 120, height: 120)
        .onAppear {
            guard !reduceMotion, lit else { return }
            // The single slow ambient exception to the 150–250ms motion rule.
            withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) {
                phase = .pi * 2
            }
        }
        .accessibilityHidden(true) // decorative; status is conveyed in text.
    }
}

/// A simple teardrop flame outline.
private struct FlameShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width
        let h = rect.height
        p.move(to: CGPoint(x: w * 0.5, y: 0))
        p.addCurve(
            to: CGPoint(x: w, y: h * 0.62),
            control1: CGPoint(x: w * 0.78, y: h * 0.20),
            control2: CGPoint(x: w, y: h * 0.40)
        )
        p.addArc(
            center: CGPoint(x: w * 0.5, y: h * 0.62),
            radius: w * 0.5,
            startAngle: .degrees(0),
            endAngle: .degrees(180),
            clockwise: false
        )
        p.addCurve(
            to: CGPoint(x: w * 0.5, y: 0),
            control1: CGPoint(x: 0, y: h * 0.40),
            control2: CGPoint(x: w * 0.22, y: h * 0.20)
        )
        p.closeSubpath()
        return p
    }
}

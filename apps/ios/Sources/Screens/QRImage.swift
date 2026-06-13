import SwiftUI
import CoreImage.CIFilterBuiltins
import UIKit

/// Renders a string (the `joinLink`) as a crisp QR code using CoreImage's
/// `CIQRCodeGenerator`. Cached per-string so re-renders don't recompute.
struct QRImage: View {
    let string: String
    var size: CGFloat = 220

    private static let context = CIContext()

    var body: some View {
        if let image = Self.generate(from: string, side: size) {
            Image(uiImage: image)
                .interpolation(.none) // keep the modules sharp
                .resizable()
                .frame(width: size, height: size)
                .accessibilityLabel("QR code to join this table")
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color(.secondarySystemBackground))
                .frame(width: size, height: size)
                .overlay(Text("QR unavailable").font(Theme.body(13)).foregroundStyle(Theme.inkMuted))
        }
    }

    static func generate(from string: String, side: CGFloat) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        // Scale the 1-module-per-pixel output up to the requested side.
        let scale = side / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cg = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

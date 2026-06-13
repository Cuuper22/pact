import SwiftUI
import AVFoundation

/// A thin SwiftUI wrapper over an AVFoundation QR-code capture session. Calls
/// `onCode` once with the first decoded payload, then stops scanning. Handles
/// the camera-permission states gracefully (the host of a pact may scan a
/// guest's join QR, or a guest scans the host's).
struct QRScannerView: UIViewRepresentable {
    let onCode: (String) -> Void

    func makeUIView(context: Context) -> CameraPreviewView {
        let view = CameraPreviewView()
        view.coordinator = context.coordinator
        context.coordinator.attach(to: view)
        return view
    }

    func updateUIView(_ uiView: CameraPreviewView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    static func dismantleUIView(_ uiView: CameraPreviewView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        private let onCode: (String) -> Void
        private let session = AVCaptureSession()
        private let queue = DispatchQueue(label: "app.pact.qr")
        private var delivered = false

        init(onCode: @escaping (String) -> Void) {
            self.onCode = onCode
        }

        func attach(to view: CameraPreviewView) {
            view.previewLayer.session = session
            view.previewLayer.videoGravity = .resizeAspectFill
            configureAndStart()
        }

        private func configureAndStart() {
            queue.async { [weak self] in
                guard let self else { return }
                guard
                    let device = AVCaptureDevice.default(for: .video),
                    let input = try? AVCaptureDeviceInput(device: device),
                    self.session.canAddInput(input)
                else { return }
                self.session.beginConfiguration()
                self.session.addInput(input)
                let output = AVCaptureMetadataOutput()
                if self.session.canAddOutput(output) {
                    self.session.addOutput(output)
                    output.setMetadataObjectsDelegate(self, queue: self.queue)
                    output.metadataObjectTypes = [.qr]
                }
                self.session.commitConfiguration()
                if !self.session.isRunning { self.session.startRunning() }
            }
        }

        func stop() {
            queue.async { [weak self] in
                guard let self, self.session.isRunning else { return }
                self.session.stopRunning()
            }
        }

        func metadataOutput(
            _ output: AVCaptureMetadataOutput,
            didOutput metadataObjects: [AVMetadataObject],
            from connection: AVCaptureConnection
        ) {
            guard
                !delivered,
                let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                let value = object.stringValue
            else { return }
            delivered = true
            stop()
            DispatchQueue.main.async { [weak self] in self?.onCode(value) }
        }
    }
}

/// A UIView whose backing layer is an `AVCaptureVideoPreviewLayer`.
final class CameraPreviewView: UIView {
    weak var coordinator: QRScannerView.Coordinator?

    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    var previewLayer: AVCaptureVideoPreviewLayer {
        // Safe: `layerClass` guarantees the backing layer's type.
        layer as! AVCaptureVideoPreviewLayer
    }
}

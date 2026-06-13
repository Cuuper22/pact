import Foundation

/// The shield posture the app and push handler drive. Abstracted behind a
/// protocol so `PactClient` is unit-testable without FamilyControls (which only
/// works on a provisioned device), and so a debug build can run with a no-op.
public protocol ShieldControlling: AnyObject, Sendable {
    /// Shield all app categories (the table is locked, this seat has no pass).
    func lock()
    /// Clear the shield because this seat holds a pass / emergency.
    func clearForPass()
    /// Clear the shield entirely (leave / broken / pre-lock).
    func clear()
}

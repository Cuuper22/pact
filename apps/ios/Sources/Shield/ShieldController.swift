import Foundation
import ManagedSettings

/// Wraps a `ManagedSettingsStore` to apply and clear the Pact shield. The store
/// is namespaced so the app and the (separate-process) extensions mutate the
/// *same* settings; ManagedSettings stores are shared across the App Group by
/// the system when addressed by the same name.
///
/// What is shielded: `shield.applicationCategories = .all()` blocks every app
/// category. What is NOT, and can never be, shielded: the phone/dialer, calls,
/// emergency calls, and Emergency SOS — those are guaranteed open at the OS
/// level and are not subject to Screen Time shields. We never touch them, by
/// architecture and by policy (see PRODUCT.md / PROTOCOL.md).
public final class ShieldController: ShieldControlling, @unchecked Sendable {
    /// A named store shared by the app and both extensions. The name is stable
    /// so all three processes address the same settings.
    private let store = ManagedSettingsStore(named: .pact)

    public init() {}

    /// Lock: shield every app category. Calls and emergency services remain
    /// reachable — ManagedSettings cannot and does not shield them.
    public func lock() {
        store.shield.applicationCategories = .all()
        store.shield.webDomainCategories = .all()
    }

    /// Pass / emergency: lift this seat's shield until the pass expires. The
    /// relay's next `relock` / `night` reapplies it via `lock()`.
    public func clearForPass() {
        store.shield.applicationCategories = nil
        store.shield.webDomainCategories = nil
    }

    /// Leave / broken / pre-lock: clear all shield settings this app set.
    public func clear() {
        store.shield.applicationCategories = nil
        store.shield.webDomainCategories = nil
        store.clearAllSettings()
    }
}

extension ManagedSettingsStore.Name {
    /// The shared store name used by the app and the shield extensions.
    static let pact = Self("app.pact.shield")
}

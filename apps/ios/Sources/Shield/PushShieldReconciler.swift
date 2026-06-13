import Foundation

/// Applies shield transitions in response to a push `kind`, without needing a
/// live socket. Used by the AppDelegate's background-push handler so the shield
/// updates even when the app is backgrounded or the phone is locked.
///
/// The relay is still authoritative; this is a fast local reaction that the
/// next live `state` frame will confirm. It also updates the shared
/// `LockState` so the shield extensions render correctly.
public struct PushShieldReconciler: Sendable {
    private let shield: ShieldControlling

    public init(shield: ShieldControlling) {
        self.shield = shield
    }

    /// Maps a push `kind` to a shield transition and persists `LockState`.
    /// `passSeconds` is the pass length for `grant`/`emergency`, read from the
    /// payload when present (the relay also sends a live `state` with the exact
    /// `remainMs`, which corrects any drift).
    public func apply(_ payload: PushPayload, passSeconds: Double? = nil) {
        var state = LockState.load()
        state.pactId = payload.pactId
        switch payload.kind {
        case .lock, .relock:
            state.locked = true
            state.passUntil = nil
            state.pendingAsker = nil
            shield.lock()
        case .grant, .emergency:
            state.locked = true
            if let secs = passSeconds {
                state.passUntil = Date().addingTimeInterval(secs)
            }
            shield.clearForPass()
        case .deny:
            // Ask resolved as "not now": stay shielded, clear the pending ask.
            state.locked = true
            state.pendingAsker = nil
            shield.lock()
        case .ask:
            // An ask is on the table; shield stays as-is. The asker name is set
            // by the notification handler, which has the alert title.
            state.locked = true
        case .leave:
            state.locked = false
            state.passUntil = nil
            state.pendingAsker = nil
            state.pactId = nil
            shield.clear()
        }
        state.save()
    }
}

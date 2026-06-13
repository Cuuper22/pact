import Foundation

/// The shield posture, shared between the app and the shield extensions via the
/// App Group. The extensions cannot run arbitrary networking on their own
/// schedule, so the app keeps this snapshot current and the extensions read it
/// to decide what to render and which action to take.
public struct LockState: Codable, Equatable, Sendable {
    /// Whether the table is currently locked (the pact is in session).
    public var locked: Bool
    /// Whether *this* seat currently holds a pass (unshielded), and until when.
    public var passUntil: Date?
    /// The asker's name if an ask is currently on the table, for shield copy.
    public var pendingAsker: String?
    /// The active pact id, so the shield action can POST to the relay.
    public var pactId: String?

    public init(
        locked: Bool = false,
        passUntil: Date? = nil,
        pendingAsker: String? = nil,
        pactId: String? = nil
    ) {
        self.locked = locked
        self.passUntil = passUntil
        self.pendingAsker = pendingAsker
        self.pactId = pactId
    }

    /// The cleared state: nothing locked, no pass, no pact.
    public static let cleared = LockState()

    // MARK: Shared persistence

    /// Reads the current lock state from the App Group, defaulting to cleared.
    public static func load() -> LockState {
        guard
            let data = AppGroup.defaults.data(forKey: AppGroup.Key.lockState),
            let state = try? JSONDecoder().decode(LockState.self, from: data)
        else { return .cleared }
        return state
    }

    /// Persists this lock state into the App Group for the extensions to read.
    public func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        AppGroup.defaults.set(data, forKey: AppGroup.Key.lockState)
    }
}

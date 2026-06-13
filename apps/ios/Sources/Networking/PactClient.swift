import Foundation
import Combine

/// The live connection to one seat's screen. Owns a `URLSessionWebSocketTask`,
/// publishes the latest `SeatView`, auto-reconnects with backoff, and drives
/// the platform shield off lock / pass / broken transitions.
///
/// The relay is authoritative: the client renders the `view` it's sent and runs
/// only cosmetic, client-side countdowns (`remainMs` / `cooldownMs`) for
/// smoothness — the next `state` frame is always treated as truth.
///
/// `@MainActor` because everything it publishes drives SwiftUI; the URLSession
/// callbacks hop back to the main actor before mutating state.
@MainActor
public final class PactClient: NSObject, ObservableObject {
    // MARK: Published state

    /// The latest view the relay projected for this seat. `nil` before connect.
    @Published public private(set) var view: SeatView?
    /// Live connection status, for the small connection indicator in the UI.
    @Published public private(set) var connection: ConnectionStatus = .idle
    /// The active seat's credentials (set on create/join), or nil when none.
    @Published public private(set) var credentials: SeatCredentials?
    /// A smoothed, client-side countdown derived from the view's remain field,
    /// recomputed each tick. `nil` when no countdown is on screen.
    @Published public private(set) var countdown: Countdown?

    public enum ConnectionStatus: Equatable {
        case idle, connecting, connected, reconnecting, closed
    }

    /// A cosmetic countdown the UI renders; truth still comes from the relay.
    public struct Countdown: Equatable {
        /// Remaining whole seconds, never negative.
        public var seconds: Int
        /// 0…1 fraction remaining of the original window, for progress bars.
        public var fraction: Double
        /// Whether this is a cooldown (can't ask yet) vs an active window.
        public var isCooldown: Bool
    }

    // MARK: Dependencies

    private let shield: ShieldControlling
    private var api: RelayAPI

    // MARK: Connection internals

    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        config.timeoutIntervalForRequest = 30
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()

    private var task: URLSessionWebSocketTask?
    private var reconnectAttempt = 0
    private var reconnectTask: Task<Void, Never>?
    private var pingTask: Task<Void, Never>?
    private var countdownTimer: Timer?
    /// True between an explicit `disconnect()` and the next `connect()`, so a
    /// socket close from our side never schedules a reconnect.
    private var intentionalClose = false

    /// The wall-clock instant a remain window was last (re)anchored, and the ms
    /// it started at, so the cosmetic countdown advances between frames.
    private var remainAnchor: (date: Date, totalMs: Double, isCooldown: Bool)?

    public init(shield: ShieldControlling, api: RelayAPI = RelayAPI()) {
        self.shield = shield
        self.api = api
        super.init()
    }

    // MARK: Session lifecycle (create / join)

    /// Starts a pact as host, stores credentials, and opens the live socket.
    public func createPact(
        hostName: String,
        passMinutes: Int,
        stakes: String?,
        pushToken: String?
    ) async throws {
        let body = CreatePactBody(
            hostName: hostName,
            passMinutes: passMinutes,
            stakes: stakes,
            pushToken: pushToken,
            platform: pushToken == nil ? nil : PactConfig.platform
        )
        let creds = try await api.createPact(body)
        adopt(creds)
    }

    /// Joins a pact by table code, stores credentials, and opens the socket.
    public func joinPact(code: String, name: String, pushToken: String?) async throws {
        let body = JoinPactBody(
            name: name,
            pushToken: pushToken,
            platform: pushToken == nil ? nil : PactConfig.platform
        )
        let creds = try await api.joinPact(code: code, body: body)
        adopt(creds)
    }

    /// Adopts credentials obtained elsewhere (e.g. restored on relaunch) and
    /// connects. Persists them into the App Group for the extensions.
    public func adopt(_ creds: SeatCredentials) {
        credentials = creds
        creds.save()
        connect()
    }

    /// Restores a prior session from the App Group, if one is stored, so a
    /// relaunch mid-pact reconnects rather than dropping the table.
    public func restoreIfPossible() {
        guard credentials == nil, let creds = SeatCredentials.load() else { return }
        credentials = creds
        connect()
    }

    // MARK: WebSocket

    public func connect() {
        guard let creds = credentials else { return }
        intentionalClose = false
        reconnectTask?.cancel()
        task?.cancel(with: .goingAway, reason: nil)
        connection = reconnectAttempt == 0 ? .connecting : .reconnecting

        let task = session.webSocketTask(with: creds.wsURL)
        self.task = task
        task.resume()
        receiveLoop(on: task)
        startPing()
        // A `hello` re-syncs the current view even though the upgrade already
        // authorized us; harmless and useful after a reconnect.
        send(.hello(seatId: creds.seatId, token: creds.token))
    }

    public func disconnect() {
        intentionalClose = true
        reconnectTask?.cancel()
        pingTask?.cancel()
        stopCountdownTimer()
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        connection = .closed
    }

    private func receiveLoop(on task: URLSessionWebSocketTask) {
        task.receive { [weak self] result in
            guard let self else { return }
            Task { @MainActor in
                switch result {
                case let .success(message):
                    self.handle(message)
                    // Keep listening only if this is still the live task.
                    if self.task === task { self.receiveLoop(on: task) }
                case .failure:
                    if self.task === task { self.handleDrop() }
                }
            }
        }
    }

    private func handle(_ message: URLSessionWebSocketTask.Message) {
        let data: Data
        switch message {
        case let .data(d): data = d
        case let .string(s): data = Data(s.utf8)
        @unknown default: return
        }
        guard let frame = try? JSONDecoder().decode(ServerFrame.self, from: data) else { return }
        switch frame {
        case let .welcome(_, _, view):
            apply(view: view)
        case let .state(_, view):
            apply(view: view)
        case .pong:
            break
        case .error:
            // Protocol-level error (e.g. bad_json); the relay keeps the socket
            // open, so we simply ignore and await the next state.
            break
        }
    }

    /// The single funnel for a new view: publishes it, reconciles the shield,
    /// and re-anchors the cosmetic countdown.
    private func apply(view: SeatView) {
        reconnectAttempt = 0
        connection = .connected
        self.view = view
        reconcileShield(for: view)
        anchorCountdown(for: view)
    }

    private func handleDrop() {
        guard !intentionalClose else { return }
        connection = .reconnecting
        pingTask?.cancel()
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        reconnectTask?.cancel()
        let attempt = reconnectAttempt
        reconnectAttempt += 1
        // Exponential backoff with a 30s ceiling and ±20% jitter.
        let base = min(30.0, pow(2.0, Double(attempt)))
        let jitter = Double.random(in: 0.8...1.2)
        let delay = max(0.5, base * jitter)
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard let self, !Task.isCancelled else { return }
            self.connect()
        }
    }

    private func startPing() {
        pingTask?.cancel()
        pingTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 20_000_000_000) // 20s
                guard let self, !Task.isCancelled else { return }
                self.send(.ping)
            }
        }
    }

    // MARK: Sending actions

    /// Sends a client frame over the socket. Encoding failures are dropped
    /// silently — they can only be programmer error, not runtime conditions.
    public func send(_ frame: ClientFrame) {
        guard let task, let data = try? JSONEncoder().encode(frame) else { return }
        task.send(.string(String(decoding: data, as: UTF8.self))) { _ in }
    }

    public func lock() { send(.action(.lock)) }
    public func ask(reason: String?) { send(.action(.ask(reason: reason))) }
    public func vote(allow: Bool) { send(.action(.vote(allow: allow))) }
    public func emergency() { send(.action(.emergency)) }

    /// Sends the seat's push token to the relay so it can deliver asks/nudges.
    public func setPush(token: String) {
        send(.action(.setPush(pushToken: token, platform: PactConfig.platform)))
    }

    /// Leaves the pact: broadcast over the socket if live, otherwise via REST,
    /// then tear down locally and clear the shield. Always succeeds locally —
    /// leaving is never blocked.
    public func leave() async {
        if task != nil {
            send(.action(.leave))
        } else if let creds = credentials {
            _ = try? await api.sendAction(
                pactId: creds.pactId, seatId: creds.seatId,
                token: creds.token, action: .leave
            )
        }
        shield.clear()
        endSession()
    }

    /// Tears down the session locally (after leave / broken) and clears shared
    /// state so the extensions see a quiet phone.
    public func endSession() {
        disconnect()
        credentials = nil
        view = nil
        countdown = nil
        SeatCredentials.clear()
        LockState.cleared.save()
        reconnectAttempt = 0
    }

    // MARK: Shield reconciliation

    /// Maps the current view to a shield posture and persists a `LockState`
    /// snapshot the extensions can read. This is the WS-driven half of shield
    /// control; the push handler covers the backgrounded half.
    private func reconcileShield(for view: SeatView) {
        switch view {
        case .night, .ask, .waiting:
            // In session and not currently passed → everything is shielded.
            shield.lock()
            persistLock(passUntil: nil, asker: askerName(in: view))
        case let .pass(p):
            // Holding a pass → unshield until it expires.
            let until = Date().addingTimeInterval(p.remainMs / 1000)
            shield.clearForPass()
            persistLock(passUntil: until, asker: nil)
        case .broken:
            shield.clear()
            persistLock(passUntil: nil, asker: nil, locked: false, keepPactId: false)
        case .none, .join, .lobbyHost, .lobbyWait, .unknown:
            // Pre-lock or unknown: never shield.
            shield.clear()
            persistLock(passUntil: nil, asker: nil, locked: false, keepPactId: false)
        }
    }

    private func askerName(in view: SeatView) -> String? {
        if case let .ask(p) = view { return p.asker }
        return nil
    }

    private func persistLock(
        passUntil: Date?,
        asker: String?,
        locked: Bool = true,
        keepPactId: Bool = true
    ) {
        LockState(
            locked: locked,
            passUntil: passUntil,
            pendingAsker: asker,
            pactId: keepPactId ? credentials?.pactId : nil
        ).save()
    }

    // MARK: Cosmetic countdown

    private func anchorCountdown(for view: SeatView) {
        switch view {
        case let .ask(p):
            anchor(totalMs: p.remainMs, isCooldown: false)
        case let .waiting(p):
            anchor(totalMs: p.remainMs, isCooldown: false)
        case let .pass(p):
            anchor(totalMs: p.remainMs, isCooldown: false)
        case let .night(p):
            if let cd = p.cooldownMs, cd > 0 {
                anchor(totalMs: cd, isCooldown: true)
            } else {
                clearCountdown()
            }
        default:
            clearCountdown()
        }
    }

    private func anchor(totalMs: Double, isCooldown: Bool) {
        remainAnchor = (Date(), max(0, totalMs), isCooldown)
        tickCountdown()
        startCountdownTimer()
    }

    private func clearCountdown() {
        remainAnchor = nil
        countdown = nil
        stopCountdownTimer()
    }

    private func startCountdownTimer() {
        guard countdownTimer == nil else { return }
        let timer = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tickCountdown() }
        }
        RunLoop.main.add(timer, forMode: .common)
        countdownTimer = timer
    }

    private func stopCountdownTimer() {
        countdownTimer?.invalidate()
        countdownTimer = nil
    }

    private func tickCountdown() {
        guard let anchor = remainAnchor, anchor.totalMs > 0 else {
            countdown = nil
            stopCountdownTimer()
            return
        }
        let elapsed = Date().timeIntervalSince(anchor.date) * 1000
        let remaining = max(0, anchor.totalMs - elapsed)
        countdown = Countdown(
            seconds: Int((remaining / 1000).rounded(.up)),
            fraction: anchor.totalMs > 0 ? remaining / anchor.totalMs : 0,
            isCooldown: anchor.isCooldown
        )
        if remaining <= 0 {
            // Window elapsed locally; stop ticking and wait for the relay's
            // authoritative next state.
            stopCountdownTimer()
        }
    }
}

// MARK: - URLSessionWebSocketDelegate

extension PactClient: URLSessionWebSocketDelegate {
    nonisolated public func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        Task { @MainActor in
            if self.task === webSocketTask { self.connection = .connected }
        }
    }

    nonisolated public func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        Task { @MainActor in
            if self.task === webSocketTask { self.handleDrop() }
        }
    }
}

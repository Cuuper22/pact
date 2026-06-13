// Pact consent engine. Platform-independent core: one shared session, many
// seats, a lock that opens for one person only by unanimous consent. No DOM,
// no platform APIs, no I/O. The clock is injectable so the engine is fully
// deterministic for tests and drivable by real time on the relay.
//
// This is the authoritative state machine. The relay runs one instance per
// pact and broadcasts `viewFor(seatId)` to each client. The native apps are
// thin renderers of that view plus the platform-specific shielding that
// reacts to lock / pass / broken transitions.

import type {
  EngineOptions,
  MemberView,
  PactEvent,
  PactPass,
  PactRequest,
  PactStatus,
  Platform,
  Recap,
  Seat,
  SeatView,
  TallyEntry,
} from "./types.js";

export const REQUEST_TIMEOUT_MS = 60 * 1000; // 60s to answer an ask
export const COOLDOWN_MS = 5 * 60 * 1000; // wait this long before re-asking
export const MIN_SEATS_TO_LOCK = 2; // a table needs a table

type Listener = (e: PactEngine) => void;

export class PactEngine {
  // ----- clock -----
  /** Manual logical clock, used only when no real clock is injected. */
  private _manual = 0;
  private readonly _hasRealClock: boolean;
  private readonly _now: () => number;

  // ----- state -----
  status: PactStatus = "lobby";
  code: string;
  passMinutes: number;
  stakes: string;
  seats: Seat[] = [];
  lockedAt: number | null = null;
  request: PactRequest | null = null;
  pass: PactPass | null = null;
  cooldowns: Record<string, number> = {}; // seatId -> time until allowed to ask
  notices: Record<string, string | null> = {}; // seatId -> transient string
  events: PactEvent[] = [];
  brokenBy?: string;
  brokenAt?: number;

  private _listeners: Listener[] = [];

  constructor(opts: EngineOptions = {}) {
    this.code = opts.code ?? "TBL-492";
    this.passMinutes = opts.passMinutes ?? 5;
    this.stakes = opts.stakes ?? "First break buys dessert";
    if (opts.now) {
      this._hasRealClock = true;
      this._now = opts.now;
    } else {
      this._hasRealClock = false;
      this._now = () => this._manual;
    }
  }

  /** Current clock time in ms. */
  get t(): number {
    return this._now();
  }

  // ---- seats ----
  addSeat(
    id: string,
    name: string,
    { host = false, joined = false }: { host?: boolean; joined?: boolean } = {}
  ): string {
    this.seats.push({ id, name, host, joined, left: false });
    this._emit();
    return id;
  }

  seat(id: string): Seat | undefined {
    return this.seats.find((s) => s.id === id);
  }

  activeSeats(): Seat[] {
    return this.seats.filter((s) => s.joined && !s.left);
  }

  joinedCount(): number {
    return this.activeSeats().length;
  }

  setPush(id: string, token: string, platform: Platform): boolean {
    const s = this.seat(id);
    if (!s) return false;
    s.pushToken = token;
    s.platform = platform;
    return true; // no view change — push tokens are private plumbing
  }

  join(id: string): boolean {
    const s = this.seat(id);
    if (!s || this.status !== "lobby") return false;
    if (s.joined) return false;
    s.joined = true;
    this._log("join", { seat: id });
    this._emit();
    return true;
  }

  // ---- lock ----
  lock(): boolean {
    if (this.status !== "lobby") return false;
    if (this.joinedCount() < MIN_SEATS_TO_LOCK) return false;
    this.status = "locked";
    this.lockedAt = this.t;
    this._log("lock", {});
    this._emit();
    return true;
  }

  // ---- asking the table ----
  canAsk(id: string): boolean {
    const s = this.seat(id);
    if (!s || !s.joined || s.left) return false;
    if (this.status !== "locked") return false;
    if (this.request || this.pass) return false; // one thing at a time
    if ((this.cooldowns[id] ?? 0) > this.t) return false;
    return true;
  }

  ask(id: string, reason?: string): boolean {
    if (!this.canAsk(id)) return false;
    this.request = {
      seatId: id,
      reason: reason && reason.trim() ? reason.trim() : "No reason",
      votes: {},
      deadline: this.t + REQUEST_TIMEOUT_MS,
    };
    this.notices[id] = null;
    this._log("ask", { seat: id, reason: this.request.reason });
    this._emit();
    return true;
  }

  /** Active seats who are eligible to vote on the current request. */
  voters(): Seat[] {
    if (!this.request) return [];
    const req = this.request;
    return this.activeSeats().filter((s) => s.id !== req.seatId);
  }

  vote(id: string, allow: boolean): boolean {
    if (!this.request) return false;
    if (id === this.request.seatId) return false; // can't vote on your own ask
    const s = this.seat(id);
    if (!s || !s.joined || s.left) return false;
    if (id in this.request.votes) return false; // one vote each
    this.request.votes[id] = !!allow;
    if (!allow) return this._deny(); // a single no ends it
    // granted only when every other active seat has said yes
    const everyYes = this.voters().every((v) => this.request!.votes[v.id] === true);
    if (everyYes) return this._grant();
    this._emit();
    return true;
  }

  private _grant(): boolean {
    const id = this.request!.seatId;
    this.pass = {
      seatId: id,
      expires: this.t + this.passMinutes * 60 * 1000,
      emergency: false,
    };
    this._log("grant", { seat: id });
    this.request = null;
    this._emit();
    return true;
  }

  private _deny(): boolean {
    const id = this.request!.seatId;
    this.cooldowns[id] = this.t + COOLDOWN_MS;
    this.notices[id] = "Not now. The table said wait.";
    this._log("deny", { seat: id });
    this.request = null;
    this._emit();
    return true;
  }

  // emergency: the one path that never needs consent (mirrors the OS-level
  // guarantee that calls and SOS can never be blocked).
  emergency(id: string): boolean {
    const s = this.seat(id);
    if (!s || !s.joined || s.left || this.status !== "locked") return false;
    this.request = null;
    this.pass = {
      seatId: id,
      expires: this.t + this.passMinutes * 60 * 1000,
      emergency: true,
    };
    this._log("emergency", { seat: id });
    this._emit();
    return true;
  }

  // ---- leaving breaks the pact for everyone (the flame dies) ----
  leave(id: string): boolean {
    const s = this.seat(id);
    if (!s || s.left) return false;
    s.left = true;
    if (this.status === "locked") {
      this.status = "broken";
      this.brokenBy = id;
      this.brokenAt = this.t;
      this._log("leave", { seat: id });
    }
    this._emit();
    return true;
  }

  // ---- time-driven transitions ----
  // `sync` fires any transitions that are due as of the current clock: a
  // request whose deadline passed becomes a deny (silence is no), a pass whose
  // expiry passed relocks. The relay calls this on a timer and after every
  // action. Returns true if anything changed.
  sync(): boolean {
    let changed = false;
    if (this.request && this.t >= this.request.deadline) {
      this._deny();
      changed = true;
    }
    if (this.pass && this.t >= this.pass.expires) {
      this._log("relock", { seat: this.pass.seatId });
      this.pass = null;
      this._emit();
      changed = true;
    }
    return changed;
  }

  /**
   * Advance the manual clock by `dtMs` and fire due transitions. Only valid
   * when no real clock was injected; throws otherwise to prevent confusing
   * the authoritative server's real time with a test's logical time.
   */
  tick(dtMs: number): number {
    if (this._hasRealClock) {
      throw new Error("tick() is only valid on a manual-clock engine");
    }
    this._manual += dtMs;
    if (!this.sync()) this._emit();
    return this._manual;
  }

  // ---- recap ----
  recap(): Recap {
    const end = this.brokenAt ?? this.t;
    const present = this.lockedAt == null ? 0 : end - this.lockedAt;
    const asks = this.events.filter((e) => e.kind === "ask").length;
    const granted = this.events.filter(
      (e) => e.kind === "grant" || e.kind === "emergency"
    ).length;
    const denied = this.events.filter((e) => e.kind === "deny").length;
    return {
      presentMs: present,
      asks,
      granted,
      denied,
      brokenBy: this.brokenBy ? this.seat(this.brokenBy)?.name ?? null : null,
      stakes: this.stakes,
    };
  }

  private _member(s: Seat): MemberView {
    return { id: s.id, name: s.name, host: s.host };
  }

  private _members(): MemberView[] {
    return this.activeSeats().map((s) => this._member(s));
  }

  private _tally(): TallyEntry[] {
    if (!this.request) return [];
    const req = this.request;
    return this.voters().map((v) => ({
      seatId: v.id,
      name: v.name,
      vote: req.votes[v.id],
    }));
  }

  // ---- per-seat view (pure function of state) ----
  viewFor(id: string): SeatView {
    const s = this.seat(id);
    if (!s) return { screen: "none" };
    const me = this._member(s);

    if (this.status === "broken") {
      return { screen: "broken", recap: this.recap(), me };
    }
    if (this.status === "lobby") {
      if (!s.joined) return { screen: "join", me, code: this.code };
      if (s.host) {
        return {
          screen: "lobby-host",
          me,
          code: this.code,
          members: this._members(),
          canLock: this.joinedCount() >= MIN_SEATS_TO_LOCK,
          stakes: this.stakes,
        };
      }
      return { screen: "lobby-wait", me, members: this._members() };
    }

    // locked
    const presentMs = this.t - (this.lockedAt ?? this.t);
    const members = this._members();

    if (this.pass) {
      const remain = Math.max(0, this.pass.expires - this.t);
      if (this.pass.seatId === id) {
        return {
          screen: "pass",
          me,
          members,
          presentMs,
          stakes: this.stakes,
          remainMs: remain,
          emergency: this.pass.emergency,
        };
      }
      const who = this.seat(this.pass.seatId);
      const tag = this.pass.emergency ? " (emergency)" : "";
      return {
        screen: "night",
        me,
        members,
        presentMs,
        stakes: this.stakes,
        banner: `${who?.name ?? "Someone"} is out · ${fmtClock(remain)}${tag}`,
      };
    }

    if (this.request) {
      const remain = Math.max(0, this.request.deadline - this.t);
      const tally = this._tally();
      if (this.request.seatId === id) {
        return {
          screen: "waiting",
          me,
          members,
          presentMs,
          stakes: this.stakes,
          reason: this.request.reason,
          tally,
          remainMs: remain,
        };
      }
      if (!(id in this.request.votes)) {
        const asker = this.seat(this.request.seatId);
        return {
          screen: "ask",
          me,
          members,
          presentMs,
          stakes: this.stakes,
          asker: asker?.name ?? "Someone",
          reason: this.request.reason,
          remainMs: remain,
          tally,
        };
      }
      return {
        screen: "night",
        me,
        members,
        presentMs,
        stakes: this.stakes,
        banner: "Waiting on the table…",
      };
    }

    const cooling = (this.cooldowns[id] ?? 0) > this.t;
    return {
      screen: "night",
      me,
      members,
      presentMs,
      stakes: this.stakes,
      canAsk: !cooling,
      cooldownMs: cooling ? this.cooldowns[id]! - this.t : 0,
      notice: cooling ? this.notices[id] ?? null : null,
    };
  }

  // ---- plumbing ----
  subscribe(fn: Listener): () => void {
    this._listeners.push(fn);
    return () => {
      this._listeners = this._listeners.filter((f) => f !== fn);
    };
  }

  private _emit(): void {
    for (const fn of this._listeners) fn(this);
  }

  private _log(kind: PactEvent["kind"], data: Partial<PactEvent>): void {
    this.events.push({ kind, t: this.t, ...data });
  }
}

export function fmtClock(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => (n < 10 ? "0" + n : "" + n);
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

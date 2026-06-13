// Pact consent engine. Platform-independent core: one shared session,
// many seats, a lock that opens for one person only by unanimous consent.
// No DOM, no platform APIs. Time is an injected logical clock so the whole
// thing is drivable and testable. This is the part that is real software;
// the native phone-shielding is deliberately out of scope here.

export const REQUEST_TIMEOUT_MS = 60 * 1000;     // 60s to answer an ask
export const COOLDOWN_MS = 5 * 60 * 1000;        // wait this long before re-asking

export class PactEngine {
  constructor(opts = {}) {
    this.t = 0;                         // logical clock, ms
    this.status = "lobby";              // lobby | locked | broken
    this.code = opts.code || "TBL-492";
    this.passMinutes = opts.passMinutes ?? 5;
    this.stakes = opts.stakes || "First break buys dessert";
    this.seats = [];                    // {id, name, host, joined, left}
    this.lockedAt = null;
    this.request = null;                // {seatId, reason, votes:{}, deadline}
    this.pass = null;                   // {seatId, expires, emergency}
    this.cooldowns = {};                // seatId -> time until allowed to ask
    this.notices = {};                  // seatId -> transient string
    this.events = [];                   // recap log
    this._listeners = [];
  }

  // ---- seats ----
  addSeat(id, name, { host = false, joined = false } = {}) {
    this.seats.push({ id, name, host, joined, left: false });
    this._emit();
    return id;
  }
  seat(id) { return this.seats.find((s) => s.id === id); }
  activeSeats() { return this.seats.filter((s) => s.joined && !s.left); }
  joinedCount() { return this.seats.filter((s) => s.joined && !s.left).length; }

  join(id) {
    const s = this.seat(id);
    if (!s || this.status !== "lobby") return false;
    s.joined = true;
    this._log("join", { seat: id });
    this._emit();
    return true;
  }

  // ---- lock ----
  lock() {
    if (this.status !== "lobby") return false;
    if (this.joinedCount() < 2) return false;          // a table needs a table
    this.status = "locked";
    this.lockedAt = this.t;
    this._log("lock", {});
    this._emit();
    return true;
  }

  // ---- asking the table ----
  canAsk(id) {
    const s = this.seat(id);
    if (!s || !s.joined || s.left) return false;
    if (this.status !== "locked") return false;
    if (this.request || this.pass) return false;       // one thing at a time
    if ((this.cooldowns[id] ?? 0) > this.t) return false;
    return true;
  }

  ask(id, reason) {
    if (!this.canAsk(id)) return false;
    this.request = {
      seatId: id,
      reason: reason || "No reason",
      votes: {},                                        // seatId -> bool
      deadline: this.t + REQUEST_TIMEOUT_MS,
    };
    this.notices[id] = null;
    this._log("ask", { seat: id, reason: this.request.reason });
    this._emit();
    return true;
  }

  voters() {
    if (!this.request) return [];
    return this.activeSeats().filter((s) => s.id !== this.request.seatId);
  }

  vote(id, allow) {
    if (!this.request) return false;
    if (id === this.request.seatId) return false;       // can't vote on your own ask
    const s = this.seat(id);
    if (!s || !s.joined || s.left) return false;
    if (id in this.request.votes) return false;         // one vote each
    this.request.votes[id] = !!allow;
    if (!allow) return this._deny();                    // a single no ends it
    // granted only when every other active seat has said yes
    const everyYes = this.voters().every((v) => this.request.votes[v.id] === true);
    if (everyYes) return this._grant();
    this._emit();
    return true;
  }

  _grant() {
    const id = this.request.seatId;
    this.pass = { seatId: id, expires: this.t + this.passMinutes * 60 * 1000, emergency: false };
    this._log("grant", { seat: id });
    this.request = null;
    this._emit();
    return true;
  }

  _deny() {
    const id = this.request.seatId;
    this.cooldowns[id] = this.t + COOLDOWN_MS;
    this.notices[id] = "Not now. The table said wait.";
    this._log("deny", { seat: id });
    this.request = null;
    this._emit();
    return true;
  }

  // emergency: the one path that never needs consent (mirrors the OS-level
  // guarantee that calls and SOS can never be blocked).
  emergency(id) {
    const s = this.seat(id);
    if (!s || !s.joined || s.left || this.status !== "locked") return false;
    this.request = null;
    this.pass = { seatId: id, expires: this.t + this.passMinutes * 60 * 1000, emergency: true };
    this._log("emergency", { seat: id });
    this._emit();
    return true;
  }

  // ---- leaving breaks the pact for everyone (the flame dies) ----
  leave(id) {
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

  // ---- clock ----
  tick(dtMs) {
    this.t += dtMs;
    if (this.request && this.t >= this.request.deadline) this._deny();   // silence is no
    if (this.pass && this.t >= this.pass.expires) {
      this._log("relock", { seat: this.pass.seatId });
      this.pass = null;
      this._emit();
    } else {
      this._emit();
    }
    return this.t;
  }

  // ---- recap ----
  recap() {
    const end = this.brokenAt ?? this.t;
    const present = this.lockedAt == null ? 0 : end - this.lockedAt;
    const asks = this.events.filter((e) => e.kind === "ask").length;
    const granted = this.events.filter((e) => e.kind === "grant" || e.kind === "emergency").length;
    const denied = this.events.filter((e) => e.kind === "deny").length;
    return {
      presentMs: present,
      asks, granted, denied,
      brokenBy: this.brokenBy ? this.seat(this.brokenBy)?.name : null,
      stakes: this.stakes,
    };
  }

  // ---- per-seat view (pure function of state) ----
  viewFor(id) {
    const s = this.seat(id);
    if (!s) return { screen: "none" };

    if (this.status === "broken") {
      return { screen: "broken", recap: this.recap(), me: s };
    }
    if (this.status === "lobby") {
      if (!s.joined) return { screen: "join", me: s, code: this.code };
      if (s.host) return { screen: "lobby-host", me: s, code: this.code, members: this.activeSeats(), canLock: this.joinedCount() >= 2, stakes: this.stakes };
      return { screen: "lobby-wait", me: s, members: this.activeSeats() };
    }

    // locked
    const base = {
      me: s,
      members: this.activeSeats(),
      presentMs: this.t - this.lockedAt,
      stakes: this.stakes,
    };

    if (this.pass) {
      const who = this.seat(this.pass.seatId);
      const remain = Math.max(0, this.pass.expires - this.t);
      if (this.pass.seatId === id) return { screen: "pass", ...base, remainMs: remain, emergency: this.pass.emergency };
      return { screen: "night", ...base, banner: `${who?.name} is out · ${fmtClock(remain)}${this.pass.emergency ? " (emergency)" : ""}` };
    }

    if (this.request) {
      const remain = Math.max(0, this.request.deadline - this.t);
      const tally = this.voters().map((v) => ({ name: v.name, vote: this.request.votes[v.id] }));
      if (this.request.seatId === id) {
        return { screen: "waiting", ...base, reason: this.request.reason, tally, remainMs: remain };
      }
      if (!(id in this.request.votes)) {
        const asker = this.seat(this.request.seatId);
        return { screen: "ask", ...base, asker: asker?.name, reason: this.request.reason, remainMs: remain, tally };
      }
      return { screen: "night", ...base, banner: `Waiting on the table…` };
    }

    const cooling = (this.cooldowns[id] ?? 0) > this.t;
    return {
      screen: "night",
      ...base,
      canAsk: !cooling,
      cooldownMs: cooling ? this.cooldowns[id] - this.t : 0,
      notice: cooling ? this.notices[id] || null : null,   // the notice belongs to the cooldown window
    };
  }

  // ---- plumbing ----
  subscribe(fn) { this._listeners.push(fn); return () => { this._listeners = this._listeners.filter((f) => f !== fn); }; }
  _emit() { for (const fn of this._listeners) fn(this); }
  _log(kind, data) { this.events.push({ kind, t: this.t, ...data }); }
}

export function fmtClock(ms) {
  const total = Math.max(0, Math.round(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n) => (n < 10 ? "0" + n : "" + n);
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

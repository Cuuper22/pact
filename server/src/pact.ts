import { PactEngine } from "@pact/engine";
import type { Seat } from "@pact/engine";
import type { Notifier, RefreshKind } from "./push/index.js";
import type { ClientAction, ServerFrame } from "./protocol.js";
import { token as makeToken } from "./ids.js";

// A transport-agnostic connection. The HTTP server wires a WebSocket to this;
// tests wire a stub. One seat can have several (phone + reconnect races).
export interface Connection {
  send(frame: ServerFrame): void;
}

interface SeatSecret {
  token: string;
}

/**
 * A live pact: the authoritative engine plus the connections watching it and
 * the push orchestration that reacts to its events. Everything outward-facing
 * (broadcast, push) is driven from engine events so there is exactly one
 * source of truth for what happened.
 */
export class Pact {
  readonly engine: PactEngine;
  readonly createdAt = Date.now();
  private readonly secrets = new Map<string, SeatSecret>();
  private readonly conns = new Map<string, Set<Connection>>();
  private processedEvents = 0;
  private seq = 0;

  constructor(
    readonly id: string,
    opts: { code: string; passMinutes?: number; stakes?: string },
    private readonly notifier: Notifier
  ) {
    this.engine = new PactEngine({ ...opts, now: () => Date.now() });
    // Any state change broadcasts to every watching socket.
    this.engine.subscribe(() => this.broadcast());
  }

  // ---- seats ----
  addSeat(name: string, host: boolean): { seatId: string; token: string } {
    const seatId = `s${++this.seq}`;
    const tok = makeToken();
    this.secrets.set(seatId, { token: tok });
    this.engine.addSeat(seatId, name, { host, joined: true });
    return { seatId, token: tok };
  }

  authorize(seatId: string, tok: string): boolean {
    const secret = this.secrets.get(seatId);
    return !!secret && secret.token === tok;
  }

  seat(seatId: string): Seat | undefined {
    return this.engine.seat(seatId);
  }

  // ---- connections ----
  attach(seatId: string, conn: Connection): () => void {
    let set = this.conns.get(seatId);
    if (!set) {
      set = new Set();
      this.conns.set(seatId, set);
    }
    set.add(conn);
    // Send the current view immediately on attach.
    conn.send({ type: "welcome", pactId: this.id, serverTime: Date.now(), view: this.engine.viewFor(seatId) });
    return () => {
      set!.delete(conn);
      if (set!.size === 0) this.conns.delete(seatId);
    };
  }

  private broadcast(): void {
    const serverTime = Date.now();
    for (const [seatId, set] of this.conns) {
      const view = this.engine.viewFor(seatId);
      for (const conn of set) {
        conn.send({ type: "state", serverTime, view });
      }
    }
  }

  // ---- actions ----
  // Applies an action authored by `seatId`. Returns the resulting view so a
  // REST caller (acting from a push, no socket) gets immediate feedback.
  applyAction(seatId: string, action: ClientAction): { ok: boolean } {
    let ok = false;
    switch (action.type) {
      case "lock":
        ok = this.engine.lock();
        break;
      case "ask":
        ok = this.engine.ask(seatId, action.reason);
        break;
      case "vote":
        ok = this.engine.vote(seatId, action.allow);
        break;
      case "emergency":
        ok = this.engine.emergency(seatId);
        break;
      case "leave":
        ok = this.engine.leave(seatId);
        break;
      case "setPush":
        ok = this.engine.setPush(seatId, action.pushToken, action.platform);
        break;
    }
    // setPush is private plumbing and emits nothing; everything else routes
    // push via the events it produced.
    void this.dispatchPush();
    return { ok };
  }

  // ---- time ----
  // Called on a timer by the store. Fires due transitions (silence→deny,
  // pass→relock); the engine emits, broadcasting, and we dispatch push for any
  // new events.
  tick(): void {
    this.engine.sync();
    void this.dispatchPush();
  }

  // ---- push, driven purely off the engine's event log ----
  private async dispatchPush(): Promise<void> {
    const events = this.engine.events;
    while (this.processedEvents < events.length) {
      const ev = events[this.processedEvents++]!;
      const others = (exclude?: string) =>
        this.engine.activeSeats().filter((s) => s.id !== exclude);
      switch (ev.kind) {
        case "ask": {
          const asker = ev.seat ? this.seat(ev.seat) : undefined;
          for (const v of others(ev.seat)) {
            void this.notifier.ask(v, this.id, asker?.name ?? "Someone", ev.reason ?? "No reason");
          }
          break;
        }
        case "lock":
          for (const s of others()) void this.notifier.refresh(s, this.id, "lock");
          break;
        case "leave":
          // The flame dies on every screen — push everyone so shields drop.
          for (const s of this.engine.activeSeats()) void this.notifier.refresh(s, this.id, "leave");
          break;
        case "grant":
        case "emergency":
        case "deny":
        case "relock": {
          if (ev.seat) {
            const s = this.seat(ev.seat);
            if (s) void this.notifier.refresh(s, this.id, ev.kind as RefreshKind);
          }
          break;
        }
        default:
          break; // join: lobby is foregrounded, no push needed
      }
    }
  }
}

// Wire and domain types shared by the engine, the relay, and (mirrored) the
// native clients. These are the single source of truth for the shape of a
// pact's state. Native ports (Swift, Kotlin) mirror these names deliberately.

export type Platform = "ios" | "android";

/** A pact's lifecycle. */
export type PactStatus = "lobby" | "locked" | "broken";

/** One person at the table. Identity is a first name and an ephemeral id. */
export interface Seat {
  id: string;
  name: string;
  host: boolean;
  /** Has this seat scanned in (lobby) — present at the table. */
  joined: boolean;
  /** Has this seat left the pact. Leaving is always allowed, always visible. */
  left: boolean;
  /** Opaque push token, set by the client; never leaves the relay. */
  pushToken?: string;
  platform?: Platform;
}

/** A live request to unlock for one seat, awaiting the table's verdict. */
export interface PactRequest {
  seatId: string;
  reason: string;
  /** seatId -> true (allow) | false (not now). Absent means not yet voted. */
  votes: Record<string, boolean>;
  /** Absolute clock time (ms) at which silence becomes a "no". */
  deadline: number;
}

/** An active unlock pass: this seat's phone is unshielded until it expires. */
export interface PactPass {
  seatId: string;
  /** Absolute clock time (ms) at which the phone relocks. */
  expires: number;
  /** Emergency passes bypass consent (mirrors the OS-level call/SOS guarantee). */
  emergency: boolean;
}

/** An entry in the recap log. */
export interface PactEvent {
  kind:
    | "join"
    | "lock"
    | "ask"
    | "grant"
    | "deny"
    | "emergency"
    | "relock"
    | "leave";
  t: number;
  seat?: string;
  reason?: string;
}

/** The end-of-pact summary card. */
export interface Recap {
  presentMs: number;
  asks: number;
  granted: number;
  denied: number;
  brokenBy: string | null;
  stakes: string;
}

/** A vote line shown in a tally. `vote` is undefined while still deciding. */
export interface TallyEntry {
  seatId: string;
  name: string;
  vote: boolean | undefined;
}

/** A lightweight member descriptor for in-session rosters. */
export interface MemberView {
  id: string;
  name: string;
  host: boolean;
}

/**
 * The per-seat view: a pure projection of pact state for one person's screen.
 * The relay sends exactly this to each client; the client renders it directly.
 * `screen` is the discriminant.
 */
export type SeatView =
  | { screen: "none" }
  | { screen: "join"; me: MemberView; code: string }
  | {
      screen: "lobby-host";
      me: MemberView;
      code: string;
      members: MemberView[];
      canLock: boolean;
      stakes: string;
    }
  | { screen: "lobby-wait"; me: MemberView; members: MemberView[] }
  | {
      screen: "night";
      me: MemberView;
      members: MemberView[];
      presentMs: number;
      stakes: string;
      canAsk?: boolean;
      cooldownMs?: number;
      notice?: string | null;
      banner?: string;
    }
  | {
      screen: "ask";
      me: MemberView;
      members: MemberView[];
      presentMs: number;
      stakes: string;
      asker: string;
      reason: string;
      remainMs: number;
      tally: TallyEntry[];
    }
  | {
      screen: "waiting";
      me: MemberView;
      members: MemberView[];
      presentMs: number;
      stakes: string;
      reason: string;
      tally: TallyEntry[];
      remainMs: number;
    }
  | {
      screen: "pass";
      me: MemberView;
      members: MemberView[];
      presentMs: number;
      stakes: string;
      remainMs: number;
      emergency: boolean;
    }
  | { screen: "broken"; me: MemberView; recap: Recap };

export interface EngineOptions {
  code?: string;
  passMinutes?: number;
  stakes?: string;
  /** Injectable clock returning ms. Defaults to a manual logical clock at 0. */
  now?: () => number;
}

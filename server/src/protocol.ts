// The wire protocol. REST is used for the two cold-start operations (create,
// join) and for acting from a backgrounded / locked phone via a push action.
// A live screen holds a WebSocket and receives a `state` push on every change.
//
// Native clients (Swift, Kotlin) mirror these shapes. Keep them in lockstep
// with docs/PROTOCOL.md.

import type { Platform, SeatView } from "@pact/engine";

// ---- REST ----

export interface CreatePactBody {
  hostName: string;
  passMinutes?: 2 | 5 | 10;
  stakes?: string;
  pushToken?: string;
  platform?: Platform;
}

export interface JoinPactBody {
  name: string;
  pushToken?: string;
  platform?: Platform;
}

/** Returned to the seat that created or joined. The token is secret. */
export interface SeatCredentials {
  pactId: string;
  code: string;
  seatId: string;
  token: string;
  /** ws:// or wss:// URL the client opens, token in the query string. */
  wsUrl: string;
  /** Deep link encoded in the host's QR, e.g. pact://join?code=TBL-K7QP */
  joinLink: string;
}

/** Body of POST /pacts/:pactId/actions — act without a live socket. */
export interface ActionBody {
  seatId: string;
  token: string;
  action: ClientAction;
}

// ---- actions a seat can take (shared by WS frames and REST actions) ----

export type ClientAction =
  | { type: "lock" }
  | { type: "ask"; reason?: string }
  | { type: "vote"; allow: boolean }
  | { type: "emergency" }
  | { type: "leave" }
  | { type: "setPush"; pushToken: string; platform: Platform };

// ---- WebSocket frames ----

/** Sent by the client over the socket. `hello` authenticates the connection. */
export type ClientFrame =
  | { type: "hello"; seatId: string; token: string }
  | { type: "ping" }
  | ClientAction;

/** Sent by the server over the socket. */
export type ServerFrame =
  | { type: "welcome"; pactId: string; serverTime: number; view: SeatView }
  | { type: "state"; serverTime: number; view: SeatView }
  | { type: "pong"; serverTime: number }
  | { type: "error"; code: string; message: string };

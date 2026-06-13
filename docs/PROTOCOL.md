# Pact wire protocol

The relay is authoritative. It runs one consent engine per pact and pushes each
seat its own projected view. Clients are thin: they render the view they're
sent, send actions, and drive the platform-specific shielding off lock / pass /
broken transitions. There is no client-side engine logic to keep in sync.

Base URL is the relay origin (e.g. `https://relay.pact.app`). All bodies are
JSON. There are no accounts and no auth headers — a seat proves identity with
the `token` it received at create/join time.

## REST

### `POST /pacts` — start a pact

```jsonc
// request
{ "hostName": "Yousef", "passMinutes": 5, "stakes": "First break buys dessert",
  "pushToken": "<apns or fcm token>", "platform": "ios" }   // push fields optional
// 201 response — SeatCredentials
{ "pactId": "…", "code": "TBL-K7QP", "seatId": "s1", "token": "<secret>",
  "wsUrl": "wss://relay/ws?pactId=…&seatId=s1&token=…",
  "joinLink": "pact://join?code=TBL-K7QP" }
```

`passMinutes` is one of `2 | 5 | 10` (defaults to 5). The host is seated and
joined immediately. Encode `joinLink` into the QR shown on the host's screen.

### `POST /pacts/:code/join` — join by table code

```jsonc
// request — :code is the table code, e.g. TBL-K7QP
{ "name": "Maya", "pushToken": "…", "platform": "android" }
// 201 response — SeatCredentials (same shape as above)
```

`404` if the code is unknown, `409` if the table is already locked.

### `POST /pacts/:pactId/actions` — act without a live socket

Used by a push action handler (e.g. an iOS notification "Allow" button, an
Android notification action) when no socket is open.

```jsonc
// request — ActionBody
{ "seatId": "s2", "token": "<secret>", "action": { "type": "vote", "allow": true } }
// 200 (applied) or 409 (rejected by the engine), body:
{ "ok": true, "view": { "screen": "night", … } }   // your view after the action
// 401 if seatId/token don't match
```

### `GET /health`

```jsonc
{ "ok": true, "pacts": 3, "push": { "ios": true, "android": false } }
```

## WebSocket — a live screen

Open `wsUrl` (token is in the query string; the upgrade is rejected `401`
without a valid one). The connection is bound to one seat.

### Server → client frames

```jsonc
{ "type": "welcome", "pactId": "…", "serverTime": 1700000000000, "view": { … } }
{ "type": "state",   "serverTime": 1700000000000, "view": { … } }   // on every change
{ "type": "pong",    "serverTime": 1700000000000 }
{ "type": "error",   "code": "bad_json", "message": "…" }
```

`welcome` arrives on connect with the current view; `state` arrives on every
subsequent change. Render `view` directly. Run client-side countdowns off
`remainMs` / `cooldownMs` for smoothness, but treat the next `state` as truth.

### Client → server frames

```jsonc
{ "type": "ping" }
{ "type": "hello", "seatId": "s1", "token": "…" }   // optional re-sync
{ "type": "lock" }
{ "type": "ask", "reason": "Need to pay" }           // reason optional → "No reason"
{ "type": "vote", "allow": true }
{ "type": "emergency" }
{ "type": "leave" }
{ "type": "setPush", "pushToken": "…", "platform": "ios" }
```

## The view object (`SeatView`)

A discriminated union keyed by `screen`. The client switches on `screen` and
renders. Shapes (see `packages/engine/src/types.ts` for the exact types):

| `screen`     | Meaning | Key fields |
|--------------|---------|------------|
| `none`       | unknown seat | — |
| `join`       | scanned-in needed | `code` |
| `lobby-host` | host waiting to lock | `code`, `members[]`, `canLock`, `stakes` |
| `lobby-wait` | guest waiting on host | `members[]` |
| `night`      | the dark in-session screen | `presentMs`, `members[]`, `stakes`, and optionally `canAsk`, `cooldownMs`, `notice`, `banner` |
| `ask`        | someone asks the table (you vote) | `asker`, `reason`, `remainMs`, `tally[]` |
| `waiting`    | your ask is on the table | `reason`, `remainMs`, `tally[]` |
| `pass`       | you're unshielded, counting down | `remainMs`, `emergency` |
| `broken`     | the pact ended | `recap` |

`members[]` entries are `{ id, name, host }`. `tally[]` entries are
`{ seatId, name, vote }` where `vote` is `true` (allowed), `false` (not now),
or absent (still deciding).

## What the client owns: shielding

The relay never touches the OS. Each client maps view/transitions to its
platform's shield:

| Transition (push `kind` / view) | iOS (FamilyControls / ManagedSettings) | Android (overlay + usage access) |
|---|---|---|
| `lock` / first `night` | apply shield over all but the allowlist | start shield service, monitor foreground app |
| `grant`/`emergency` for me / `pass` | clear my shield until `remainMs` | stop overlaying for me |
| `relock` / `night` after pass | re-apply shield | resume overlaying |
| `leave` / `broken` | clear shield entirely | stop service |

Emergency dialer, calls, and SOS are never shielded, on either platform, by
architecture and by policy.

## Push payloads

**APNs** — actionable ask uses category `PACT_ASK` (define `ALLOW` / `NOT_NOW`
actions in the app). State nudges are `content-available: 1` background pushes.
Both carry `{ "pactId": "…", "kind": "ask|lock|grant|deny|relock|leave|emergency" }`.

**FCM** — data-only messages (the app builds the notification so the night
theme is preserved) carrying the same `{ pactId, kind, … }` keys, sent
`priority: high`.

On receiving a `kind`, the app applies the matching shield transition;
for `ask` it presents the actionable notification and, on tap, posts to
`/pacts/:pactId/actions`.

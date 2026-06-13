# Pact

**Phones down. Everyone in.**

One table, one session. Everyone joins with a scan, no accounts, and every phone
locks together, iPhone and Android. The lock opens for one person only when
everyone at the table says yes. Leaving is always possible and always visible.

It is the analog phone-stack game given software teeth. The table stays the unit
of enforcement; Pact just gives it teeth and a memory.

→ **Live concept page:** https://cuuper22.github.io/pact/

## The honest core

No consumer app can imprison a phone, and one that tried shouldn't exist. Apple
and Google both let the owner reclaim control by design, and for anyone in a
coercive situation that exit has to be there. So Pact does not pretend. The lock
is maximum honest friction plus total visibility: you can always leave in two
taps, but the flame dies on every screen, the recap names you, and the streak
resets. Enforcement is the table itself.

Emergency calls and SOS are never blocked, on either platform, by architecture
and by policy.

## Repository layout

This is a monorepo. The relay and the consent engine are the platform-independent
core; the two apps are thin renderers of the engine's per-seat view plus the
platform-specific shielding.

| Path | What it is |
|------|------------|
| `packages/engine` | The authoritative consent state machine (TypeScript). Unanimous-unlock, cooldowns, passes, emergency bypass, leave-breaks-the-pact, recap. Pure, deterministic, fully unit-tested. |
| `server` | The dumb, ephemeral relay (Node). One engine per pact, WebSocket state fan-out, REST for create/join and push-driven actions, APNs + FCM, 24h self-purge, no database. |
| `apps/ios` | Native iOS app (Swift/SwiftUI + FamilyControls / ManagedSettings + Shield extensions). |
| `apps/android` | Native Android app (Kotlin/Compose + UsageStats + overlay shield service + FCM). |
| `docs/PROTOCOL.md` | The wire contract both apps build against. |
| `IDEA.md`, `PRODUCT.md` | The product treatment and strategic brief. |
| `index.html`, `styles.css`, `main.js` | The GitHub Pages concept page. |
| `prototype/` | The original browser session simulator. |

## Build & test

The core builds and tests anywhere with Node:

```bash
npm install
npm test          # engine (22 tests) + relay (7 integration tests)
npm run dev       # run the relay locally on :8787
```

The native apps are built by CI (`.github/workflows/ci.yml`) because they need
toolchains a plain Linux box doesn't have — the Android job needs Google's Maven
repo, the iOS job needs macOS + Xcode. **Enable GitHub Actions on the repo**
(Settings → Actions → General → Allow all actions) for the workflow to run; the
runners then compile `assembleDebug` (Android) and an unsigned simulator build
(iOS). You can also build locally — see each app's `README.md` for build
instructions and the store-submission steps that require human accounts.

## Status

The engine and relay are complete and tested (29 passing tests, run locally).
Both native apps are complete, reviewed source with reproducible build configs;
they compile via the CI jobs once GitHub Actions is enabled on the repo (this
build sandbox can't compile them — no macOS for iOS, and its network can't reach
Google's Maven for Android). What remains is intrinsically human and
account-bound, not code:

- Apple's **Family Controls distribution entitlement** (the longest lead-time
  gate — apply early), an Apple Developer account, signing, and App Store review.
- A Google Play Console account, the usage-access **prominent disclosure**
  declaration, signing, and Play review.
- Push credentials (an APNs `.p8`; a Firebase project + `google-services.json`)
  and a deployed relay.

## Run the concept page locally

```bash
python -m http.server 8000
```

Then open http://localhost:8000.

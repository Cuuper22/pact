# Pact — iOS app

The native iOS client for Pact: the phone stack, with teeth. One table, one
session, every phone shielded at once; the lock opens for one person only when
everyone at the table says yes. The relay is authoritative — this app is a thin
renderer of the per-seat view plus the platform shielding (FamilyControls /
ManagedSettings).

- Swift 5.9+, SwiftUI, **iOS 16+**, iPhone only.
- Project generated with **XcodeGen** (`project.yml`) — do not hand-edit the
  `.pbxproj`.
- The relay wire contract lives in `../../docs/PROTOCOL.md`; the engine types
  it mirrors are in `../../packages/engine/src/types.ts`.

> **This app cannot be compiled in the repo's Linux CI** — it needs macOS +
> Xcode. The code is written to be correct by construction; the unit tests pin
> the wire contract so a Mac can verify decoding/encoding without a device.

---

## Prerequisites

- **macOS** with **Xcode 15+** (iOS 16 SDK or later).
- **XcodeGen**: `brew install xcodegen`.
- An **Apple Developer account** (paid) — required for the Family Controls and
  Push entitlements, and for any on-device run (the shield does nothing in the
  Simulator).
- A running **relay** (see `../../server`). Locally: `npm run dev` in `server/`
  serves `http://localhost:8787`.

## Generate the project

```sh
cd apps/ios
xcodegen generate        # writes Pact.xcodeproj from project.yml
open Pact.xcodeproj
```

Re-run `xcodegen generate` whenever you add/remove files or edit `project.yml`.
The generated `Pact.xcodeproj` is disposable and should not be committed.

## Targets

| Target | Kind | Role |
|---|---|---|
| `Pact` | app | Main app: onboarding, lobby, the night/ask/pass/recap screens, the live WebSocket client, push handling. |
| `ShieldConfiguration` | app extension | The **custom shield UI** drawn over a blocked app — the night screen with an "Ask the table" button. |
| `ShieldAction` | app extension | Handles taps on the shield's buttons; "Ask the table" POSTs an `ask` to the relay. |
| `PactTests` | unit tests | Pins the wire contract (SeatView / frame decoding, action encoding). |

All three product targets share an **App Group** (`group.app.pact`). The app and
extensions exchange state through a shared `UserDefaults` suite (see
`Sources/Shared/AppGroup.swift`): the relay base URL, the active seat
credentials, and the current `LockState`. The shield itself is a named
`ManagedSettingsStore` (`app.pact.shield`) the system shares across the group.

## Source layout

```
apps/ios/
  project.yml                         XcodeGen spec (targets, entitlements, schemes)
  README.md
  Sources/
    App/                              entry point, router, AppDelegate (push), AppModel
      PactApp.swift  RootView.swift  AppModel.swift
      AppDelegate.swift  PushManager.swift
      Info.plist  Pact.entitlements
    Models/                           Codable wire types mirroring the protocol
      SeatView.swift  WireFrames.swift  ClientAction.swift
      SeatCredentials.swift  RESTBodies.swift  PushPayload.swift  Platform.swift
    Networking/
      PactConfig.swift                relay URL resolution
      RelayAPI.swift                  REST (create/join/actions) via async URLSession
      PactClient.swift                live WebSocket + reconnect + countdown + shield drive
    Shield/
      ShieldControlling.swift         protocol (testable seam)
      ShieldController.swift          ManagedSettings wrapper (lock/pass/clear)
      AuthorizationModel.swift        FamilyControls .individual authorization
      PushShieldReconciler.swift      backgrounded shield updates off push `kind`
    Screens/                          SwiftUI: onboarding, home, start, join (+QR), lobby,
                                      night, ask, waiting, pass, recap, ask-reason sheet
    Theme/                            design tokens, the flame, shared components
    Shared/                           App Group constants + shared LockState (used by extensions)
  Extensions/
    ShieldConfiguration/              custom shield UI + Info.plist + entitlements
    ShieldAction/                     shield button handler + Info.plist + entitlements
  Resources/Assets.xcassets/          AppIcon slot, AccentColor, notes
  Tests/PactTests/                    decoding / encoding tests
```

## Architecture, briefly

- **The relay is truth.** `PactClient` (a `@MainActor ObservableObject`) holds a
  `URLSessionWebSocketTask`, decodes each `welcome`/`state` frame into a
  `SeatView`, and publishes it. `RootView` switches on the `SeatView` and
  renders. There is no client engine.
- **`SeatView` is a discriminated union** keyed by the `screen` string and is
  **decode-only** (the client never sends views back). An unrecognised future
  `screen` decodes to `.unknown` and shows a safe holding screen instead of
  crashing.
- **Countdowns** (`remainMs` / `cooldownMs`) tick client-side for smoothness via
  a 250 ms timer anchored on the last frame; the next `state` is treated as
  authoritative and re-anchors.
- **Reconnect** uses exponential backoff (cap 30 s, ±20% jitter) plus a 20 s
  app-level ping. The socket re-`hello`s on connect to re-sync.
- **Shielding** is driven from two places so it works foregrounded *and*
  backgrounded:
  1. Live: `PactClient.reconcileShield(for:)` maps the current view to
     `lock()` / `clearForPass()` / `clear()` and writes a `LockState` snapshot.
  2. Backgrounded: the APNs `content-available` push lands in
     `AppDelegate.didReceiveRemoteNotification`, and `PushShieldReconciler`
     applies the transition off the push `kind`.
  Either way the same shared `ManagedSettingsStore` and `LockState` are updated,
  so the shield extensions render correctly.
- **Asks from a locked phone**: the `PACT_ASK` notification category defines
  `ALLOW` / `NOT_NOW`. The handler POSTs `/pacts/:pactId/actions` over REST, so
  voting needs neither the app foregrounded nor a live socket. The shield's own
  "Ask the table" button does the same via the `ShieldAction` extension.
- **Emergency / SOS / calls are never shielded** — that's an OS-level
  guarantee; `ShieldController` only ever sets `shield.applicationCategories`
  and never touches telephony. Leaving is always two taps and always broadcast.

## Accessibility

WCAG AA: large (≥56 pt) Allow / Not now targets, VoiceOver labels on the shield,
night, and vote screens, vote state shown with **icon + word** (never colour
alone), and a **Reduce Motion** path that renders a static ember instead of the
animated flame.

---

## What a human must do before it compiles / runs / ships

These steps require a Mac, an Apple Developer account, and Apple's approval —
they cannot be done from this repo.

### 1. Family Controls distribution entitlement (the long-lead gate)

`com.apple.developer.family-controls` is a **restricted, manually-approved**
entitlement. Apply **in week one** — review is the longest lead time in the
whole project, and nothing about the shield runs without it.

1. Sign in at <https://developer.apple.com/contact/request/family-controls-distribution>
   and request the **Family Controls (Distribution)** entitlement for your app's
   bundle id (`app.pact`). Describe Pact as a present-together / digital
   wellbeing app where the user shields *their own* device by consent. Opal,
   Jomo, and one sec were all granted for this category.
2. While you wait, you can still build and test against the **development**
   capability: in Xcode add the *Family Controls* capability to the `Pact`,
   `ShieldConfiguration`, and `ShieldAction` targets. On a development device
   `requestAuthorization(for: .individual)` works once the capability is on the
   provisioning profile.
3. Once approved, the distribution entitlement attaches to your App Store
   profile and TestFlight/release builds can ship the shield.

The `.entitlements` files already declare the key for all three targets.

### 2. Signing & capabilities

Set your **Team** in `project.yml` (`DEVELOPMENT_TEAM`) or in Xcode after
generating, then for **each** product target confirm these capabilities (their
entitlements are pre-written, but the IDs must exist in your account):

- **App Groups** → create `group.app.pact` in the developer portal and enable it
  on `Pact`, `ShieldConfiguration`, `ShieldAction`. (Change the suite name in
  `Sources/Shared/AppGroup.swift`, the three `.entitlements`, and `project.yml`
  if you use a different group id.)
- **Push Notifications** → enable on the `Pact` target; create an **APNs Auth
  Key (.p8)** and configure the relay's `APNS_*` env vars (see
  `../../server/src/config.ts`). Set `aps-environment` to `production` in
  `Sources/App/Pact.entitlements` for release builds (it's `development` now).
- **Family Controls** → on all three targets (see step 1).

### 3. Set the relay URL

The relay origin is the `PACT_RELAY_URL` build setting in `project.yml`
(default `http://localhost:8787`). It is injected into the app's `Info.plist`
as `PactRelayURL` and mirrored into the App Group on launch so the extensions
reach the same relay.

- **Per environment**: edit `PACT_RELAY_URL` in `project.yml`, or add an
  `.xcconfig` per configuration and reference it from the target settings, then
  re-run `xcodegen generate`.
- **Production must use `https://` / `wss://`.** The bundled
  `NSAppTransportSecurity` exception only permits cleartext to the local
  network for development — remove or tighten it before release.
- A debug build can also override at runtime via
  `PactConfig.overrideRelayURL(_:)` (persisted to the App Group).

### 4. App icon

Add a 1024×1024 `AppIcon` image (the ember on the night field) — see
`Resources/Assets.xcassets/ASSETS_NOTES.md`. The slot is declared; no binary
image is committed here.

### 5. Run the tests (verifies the wire contract on a Mac)

```sh
xcodegen generate
xcodebuild test -scheme Pact -destination 'platform=iOS Simulator,name=iPhone 15'
```

The shield itself does nothing in the Simulator (FamilyControls is a no-op
there); test the lock/ask/pass/recap flow against a real device with the
development Family Controls capability and a running relay.

### 6. TestFlight / App Store submission

1. Set `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`, switch
   `aps-environment` to `production`, and ensure the **distribution** Family
   Controls entitlement is approved and on your profile.
2. In App Store Connect, create the app record for `app.pact`.
3. In Xcode: **Product ▸ Archive** the `Pact` scheme (Release), then
   **Distribute App ▸ App Store Connect ▸ Upload**. The two extensions are
   embedded automatically by the scheme.
4. **Review notes**: explain that Pact uses Screen Time / Family Controls for
   *individual, consent-based* shielding among people physically together, that
   leaving is always available and visible, and that calls / Emergency SOS are
   never blocked. Surveillance-style framing fails review; "stay present
   together" is the honest and correct framing (see `../../PRODUCT.md`).
5. Submit for TestFlight first; dogfood at a real dinner table before release.

# Pact

Phones down. Everyone in.

One table, one session. Everyone joins with a scan, no accounts, and every phone locks at once, iPhone and Android together. The lock opens for one person only when everyone at the table says yes. Leaving is always possible and always visible.

## The moment

You finally got the people you love into one room. Within ten minutes everyone is half gone, thumbing a feed under the table. Nobody wants this. Everybody does it anyway. The analog fix already exists, the phone stack: pile the phones in the middle, first one to grab theirs pays the bill. It works because the table enforces it, not an app. Its weakness is that it has no teeth. Notifications still buzz the stack, "I just need to check one thing" dissolves it, and nothing remembers who folded.

Pact is the phone stack with teeth. The table becomes the unit. Not you against your phone; us against the pull.

## Three beats

1. Stack in. One person starts a pact. A QR code and a short table code appear. Everyone scans. First names only, no accounts, no profiles. When the last person is in, the table locks together.
2. Go dark. Every phone now shows the same screen: time present, who is in, a small flame. Your phone stops being yours. Flip any phone on the table and it shows the table, never your apps. Underneath, every distracting app is shielded.
3. Ask the table. Need your phone for something real? One tap, pick a reason, and the request lands on every other screen. Everyone taps allow and you get a five minute pass with a countdown the whole table can see. Then it locks again. One "not now" and you wait.

## The honest core: glass, not steel

No consumer app can imprison a phone. Apple and Google both, by design, let the owner claw control back, and an app that tried to prevent that should not exist. Picture someone with a controlling partner: the exit has to be there. So Pact does not pretend. The lock is maximum honest friction plus total visibility. You can always leave in two taps. The flame on every screen dies when you do, the recap names you, the streak resets, and you are buying dessert. Enforcement is the table itself. Pact gives the table teeth and a memory.

This is not a compromise, it is the only version that ships, and it is also the right product. The goal was never a cage. The goal is making the cheap glance expensive.

## What "locked" actually means, per platform

iOS, via the Screen Time API:

- FamilyControls with individual authorization (iOS 16+), ManagedSettings shields over all apps except an allowlist. Phone stays on by default: calls ring, and emergency calls plus SOS can never be blocked. That guarantee is OS level.
- The shield is custom (ShieldConfiguration extension). A blocked app shows the night screen and an "Ask the table" button right there (ShieldAction extension). The temptation surface becomes the request surface.
- Requires Apple's family-controls distribution entitlement. Apply in week one; this is the longest lead time in the whole project. Opal, Jomo and one sec all received it, and a present-together app is squarely the intended category.
- A user can revoke Screen Time permission in Settings mid pact. We cannot stop that, we surface it: the heartbeat drops and the table sees "Yousef unshielded."

Android:

- v1: usage access (UsageStatsManager) watching the foreground app, plus an overlay shield (SYSTEM_ALERT_WINDOW). This is the standard blocker architecture Play already approves for digital wellbeing apps with prominent disclosure.
- v2 experiment: screen pinning for a literal single-app lock. Validate incoming-call behavior on real devices before promising it to anyone.
- The emergency dialer is never shielded. Hard rule, and also Play policy.

Parity is a messaging job: both platforms shield apps, neither imprisons. Same promise, same words on both stores.

## Sessions without accounts

A pact is an ephemeral object: an id, first names, push tokens, a started-at time. It lives on a dumb relay and purges itself after 24 hours. Nothing to sign into, nothing to breach, nothing to sell. Asks and approvals ride push notifications with action buttons, so approving takes one tap from a locked phone.

Streaks for a recurring crew (the Tuesday dinner) live on the devices, keyed by the set of phones present, not on a server. Privacy is not a settings page here, it is the architecture.

Later, not v1: scan to join without installing (App Clip on iOS, Instant App on Android) for the friend who refuses to download things. They join in honor mode, counted and visible but unshielded, and get nudged to go full.

## Ask the table, the full flow

- Tap "Ask the table" on the night screen, or on the shield of the app you just tried to open.
- Pick a reason: expecting a call, need to pay, directions, kids, work, or type one. "No reason" is a valid chip; the table can judge it.
- Everyone else gets the ask on their screen and as a push with allow and not-now buttons. Sixty seconds to answer.
- All allow: you get a pass, default five minutes, set per pact (2, 5 or 10). The countdown shows on every screen at the table. Relock is automatic.
- Any "not now," or silence: no pass. You can ask again in five minutes, not before. Asking has a cost by design.
- Leave the pact: always available, two taps, instant, broadcast to the table. Never buried, never disabled.

## Stakes and the recap

At stack-in the table can set stakes in one line. The classic: first break buys dessert. Pact records the contract and the recap names the loser. Money never moves inside the app.

When the pact ends, one card: time present together, asks made and granted, breaks, longest clean stretch. Recurring crews see their streak. The card is the share moment and the growth loop. It lands in the group chat the table was avoiding all evening, which is the joke that markets the product.

## The field

Forest plants trees and has a group mode, but it is account based, solo first, and the lock is soft. Opal, Jomo, one sec and ScreenZen are solo focus tools, subscription products, nobody at a table. Brick and Unpluq sell hardware. The phone stack game has the right soul and no teeth. Nobody owns the table: no accounts, cross platform, consent-gated unlocking for a group physically together. That is the wedge.

Why now: the Screen Time API matured (custom shields with actionable buttons since iOS 16), phone stacking is already a social norm looking for software, and the dumbphone wave shows people pay real money for less phone.

## Build order

- Milestone 0, this week: apply for the Apple family-controls entitlement. Spike the Android overlay shield and pinned-mode call handling on two real devices.
- Milestone 1: session create and join (QR plus code), the relay, push with action buttons.
- Milestone 2: iOS shields with the custom ask button, Android overlay shield, the night screen.
- Milestone 3: the ask flow end to end, passes, relock, leave, recap card. Ship it to one real dinner table and iterate from there.

Cut from v1: BLE table-presence detection, App Clips, streaks, themes, watch apps, any payment feature.

## Risks, named

- The Apple entitlement is a gate. Mitigation: apply first, build Android first in parallel.
- Revoking permissions mid pact is always possible. The mitigation is the product thesis itself: visibility over prevention.
- Play review for usage-access apps takes a declaration cycle. Known path, follow it precisely.
- Cold start: a table-sized product needs the whole table. Mitigations: honor mode, the recap card landing in the group chat, and a one-phone pact mode (host phone face up as the table clock) that still seeds installs.
- Abuse: someone pressuring a partner into pacts. Leave is two taps, asks are rate limited, no location, no accounts, sessions evaporate. Build the bailout before the lock.

## Money

The core is free for everyone, forever. A paywall at stack-in kills the table. Pact Plus later, priced like one coffee a month: long pacts, crew history, recap themes. No ads ever. Selling attention inside an app about attention is selling cigarettes at the gym.

## Name and look

Pact, because the mechanic is consent. Sessions are pacts, the request is asking the table, the failure is breaking it. Alternates if trademark fights back: Tablestack, Facedown, Ember.

Design direction, from seed oklch(0.400 0.150 270): considered indigo, no theatrics.

- Surfaces before the pact: pure white, restrained, one indigo primary oklch(0.40 0.15 270).
- One drenched surface, during the pact: a near-black indigo night, oklch(0.12 0.013 275), so a lit screen at the table reads as night sky, not billboard. This is the only screen allowed to be dramatic, and its drama is darkness.
- One ember accent, oklch(0.72 0.13 70), appears only when the table is being asked something. The one candle on the table.
- One sans family, weights 400 and 500 only. Motion 150 to 250 ms for state, with a single slow ambient exception for the flame. Reduced motion gets a crossfade.

The app should feel like it wants to be put down.

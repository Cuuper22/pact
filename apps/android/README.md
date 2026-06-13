# Pact — Android

Phones down. Everyone in.

A native Android client for Pact: one table, one session, no accounts. Every
phone at the table locks together; it unlocks for one person only when everyone
says yes. Leaving is always two taps and always visible.

The relay is authoritative. This app is a thin renderer of the per-seat
`SeatView` the relay pushes, plus the Android-side shielding (an overlay driven
by usage access). There is no client-side pact engine.

---

## Build

Prerequisites: a JDK 21 toolchain and the Android SDK with `compileSdk 34`
(`build-tools` 34, platform 34). The Gradle wrapper pins Gradle 8.7.

```bash
cd apps/android
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Pinned versions (see `gradle/libs.versions.toml`):

- Android Gradle Plugin 8.5.2, Kotlin 1.9.24, Gradle 8.7
- compileSdk/targetSdk 34, minSdk 26, Java 21
- Compose BOM 2024.06.00, Material3, navigation-compose 2.7.7
- Compose compiler extension 1.5.14 (matches Kotlin 1.9.24)
- kotlinx-coroutines 1.8.1, kotlinx-serialization-json 1.6.3
- OkHttp 4.12.0 (REST + WebSocket)
- ZXing core 3.5.3 (QR generation) + zxing-android-embedded 4.3.0 (scanning)
- Firebase BoM 33.1.1 / firebase-messaging — **optional, guarded** (see below)

### Relay URL

Default is `http://10.0.2.2:8787` (the host loopback as seen from the Android
emulator), exposed as `BuildConfig.RELAY_BASE_URL`. Override without editing
source by adding to `apps/android/local.properties`:

```properties
pact.relayBaseUrl=https://relay.yourhost.app
```

Cleartext HTTP is permitted only to `10.0.2.2` / `localhost` / `127.0.0.1`
(`res/xml/network_security_config.xml`); production relays must be HTTPS/WSS.

---

## Permissions, and why

The shield needs three things. The onboarding screen explains each in-app and
deep-links to the exact Settings surface; grant state refreshes on resume.

1. **Usage access** (`PACKAGE_USAGE_STATS`, `Settings.ACTION_USAGE_ACCESS_SETTINGS`)
   — read **only** which app is currently in the foreground, so the shield knows
   when to replace a distracting app with the table. No history is logged;
   nothing leaves the device.
2. **Draw over other apps** (`SYSTEM_ALERT_WINDOW`,
   `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`) — show the night screen on top of
   a non-allowlisted app. The dialer, in-call UI, and emergency surfaces are
   **never** covered.
3. **Notifications** (`POST_NOTIFICATIONS`, runtime on API 33+) — deliver an ask
   from the table with one-tap **Allow / Not now** actions, even with the screen
   off.

The shield runs as a foreground service (`foregroundServiceType="specialUse"`)
so it survives backgrounding during a locked pact. It stops on leave/broken.

### Hard rule: never shield emergencies

`ShieldAllowlist` resolves and always exempts the default dialer (via
`TelecomManager.defaultDialerPackage` and `ACTION_DIAL`/`ACTION_CALL` resolution),
the in-call UI, the system emergency app/SOS, SystemUI, Settings, the launcher,
and Pact itself. This is both an architectural guarantee and Play policy. During
this seat's own pass/emergency the overlay is cleared; if usage access is revoked
mid-pact the shield fails **open** (it never blindly overlays) — visibility over
prevention.

---

## Play Store: prominent disclosure for usage access

Apps that request `PACKAGE_USAGE_STATS` must comply with Google Play's
Permissions & APIs policy. Before publishing:

- **In-app prominent disclosure**: the `PermissionsScreen` already states, before
  the user opens Settings, exactly what is accessed (the foreground app only),
  why (to show the table instead of a distracting app), and that nothing leaves
  the device. Keep this disclosure visible and affirmative.
- **Privacy policy** covering the usage-access and overlay usage; link it on the
  store listing and in-app.
- **Data safety form**: declare that app-usage signals are read on-device and not
  collected/transmitted (the relay only ever sees first names, ephemeral ids, and
  optional push tokens — never usage data).
- **Permissions declaration form**: justify `PACKAGE_USAGE_STATS` as a
  digital-wellbeing / "present together" feature. This is the standard,
  Play-approved blocker architecture; expect a declaration review cycle.
- Foreground-service `specialUse` type requires a justification in Play Console
  (declared in the manifest via `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).

---

## Push (optional) — enabling real FCM

The app builds and runs with **no** Firebase configuration. We deliberately do
**not** apply the `com.google.gms.google-services` plugin and do not require a
`google-services.json`. `FirebaseGuard` detects whether config resources exist
and only then initializes Firebase; `PushTokens` and `PactMessagingService` are
inert (no crash) when it's absent. A pact without push works fine — push fields
are optional in the protocol; the live WebSocket carries everything in-app.

To enable real push:

1. Create a Firebase project and add an Android app with applicationId
   `app.pact.android`.
2. Drop the generated `google-services.json` into `apps/android/app/`.
3. Add the plugin (it generates the config resources Firebase reads):
   - In `gradle/libs.versions.toml` add
     `googleServices = { id = "com.google.gms.google-services", version = "4.4.2" }`
     under `[plugins]`.
   - In the root `build.gradle.kts`: `alias(libs.plugins.googleServices) apply false`.
   - In `app/build.gradle.kts` plugins block: `alias(libs.plugins.googleServices)`.
4. Configure the relay with your FCM service-account credentials (see
   `server/src/push`). Data-only messages `{ pactId, kind, title, body }` are
   handled by `PactMessagingService`:
   - `kind=ask` → an actionable Allow / Not now notification whose PendingIntents
     fire `AskActionReceiver`, which votes over the live socket if open, else
     POSTs to `/pacts/:pactId/actions`.
   - other kinds (`lock`/`grant`/`relock`/`emergency`/`leave`) drive the shield.

---

## Signed release AAB + Play submission

1. Turn on shrinking for release in `app/build.gradle.kts`
   (`isMinifyEnabled = true`); keep rules are in `app/proguard-rules.pro`.
2. Create an upload keystore:
   ```bash
   keytool -genkeypair -v -keystore pact-upload.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias pact
   ```
3. Add signing config to `app/build.gradle.kts` (read secrets from
   `local.properties` or environment, never commit them):
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file(providers.gradleProperty("PACT_STORE_FILE").get())
           storePassword = providers.gradleProperty("PACT_STORE_PASSWORD").get()
           keyAlias = providers.gradleProperty("PACT_KEY_ALIAS").get()
           keyPassword = providers.gradleProperty("PACT_KEY_PASSWORD").get()
       }
   }
   // buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
   ```
4. Build the bundle:
   ```bash
   ./gradlew bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`.
5. Upload to Play Console (use Play App Signing). Complete the Data safety,
   privacy-policy, prominent-disclosure, the `PACKAGE_USAGE_STATS` permissions
   declaration, and the `specialUse` foreground-service justification.

---

## Project layout

```
apps/android/
├── settings.gradle.kts        repos + module include
├── build.gradle.kts           root plugins (apply false)
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml      version catalog (pinned)
│   └── wrapper/                gradle 8.7 wrapper (jar + properties)
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts        module build, BuildConfig.RELAY_BASE_URL
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                themes, colors, strings, icons, net config
        └── java/app/pact/android/
            ├── PactApplication.kt
            ├── MainActivity.kt
            ├── model/Models.kt          @Serializable SeatView union, frames, actions
            ├── net/
            │   ├── PactClient.kt        OkHttp REST + WebSocket, StateFlow, backoff
            │   └── PactRepository.kt     session store + Config
            ├── shield/
            │   ├── ShieldController.kt   view/push → service commands
            │   ├── ShieldService.kt      foreground service, poll + overlay
            │   ├── ShieldOverlay.kt      WindowManager Compose overlay host
            │   ├── ShieldAllowlist.kt    never-shield set (dialer/in-call/SOS…)
            │   └── ForegroundAppMonitor.kt  UsageStatsManager reader
            ├── push/
            │   ├── PactMessagingService.kt  guarded FCM receiver
            │   ├── PactNotifications.kt      ask + shield notifications
            │   ├── AskActionReceiver.kt      Allow/Not now → vote
            │   ├── PushTokens.kt             token, guarded
            │   └── FirebaseGuard.kt          config detection, no plugin
            └── ui/
                ├── PactViewModel.kt
                ├── Components.kt, Format.kt, Qr.kt
                ├── nav/PactNavHost.kt
                ├── theme/ Color, Theme, Flame
                └── screens/ Home, Permissions, Start, Join, LobbyHost,
                    LobbyWait, Night, Ask, Waiting, Pass, Broken,
                    AskReasonSheet, ShieldOverlayContent
```

## Design notes

- Pre-pact surfaces are pure white with one indigo primary (#3B2F8F). The night
  screen is the only dramatic surface: near-black indigo (#0D0E16) with an
  ambient flame. The ember accent (#E89B5B) appears **only** when the table is
  being asked something.
- Accessibility: WCAG-AA contrast, ≥56–64dp targets for Allow/Not now, vote state
  is always icon **+** label (never color alone), TalkBack content descriptions
  on the shield/night/vote surfaces, and a reduced-motion static-ember flame
  (honours the system "remove animations" setting).
- Countdowns tick locally off `remainMs`/`cooldownMs` for smoothness; the next
  relay `state` frame is always treated as truth.
```

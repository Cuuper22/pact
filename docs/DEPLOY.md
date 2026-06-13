# Deploying Pact

Three things ship independently: the **relay** (a server you run), the **iOS
app** (App Store), and the **Android app** (Play Store). The relay is the only
piece that is pure code with no human gate — you can deploy it today. The apps
need developer accounts, signing, and review.

## 1. The relay

Stateless, in-memory, horizontally trivial — except that a given pact lives in
one process's memory. For a single instance that's fine. To run multiple
instances behind a load balancer, pin a pact's traffic to one instance (sticky
sessions by `pactId`) or add a shared pub/sub; the current build targets a
single instance, which comfortably handles many tables.

### Run it

```bash
# from the repo root
npm install
npm run build
PUBLIC_URL=https://relay.yourhost.app node server/dist/index.js
```

or with Docker (build context is the repo root):

```bash
docker build -f server/Dockerfile -t pact-relay .
docker run -p 8787:8787 --env-file server/.env pact-relay
```

Put it behind TLS (a reverse proxy terminating https/wss). Set `PUBLIC_URL` to
the public https origin so the `wsUrl` it hands clients uses `wss://`.

### Configuration

See `server/.env.example`. The relay runs fully without push credentials
(notifications become logged no-ops), which is enough to develop both apps
against. Configure push for production:

**APNs (iOS).** In the Apple Developer portal create a Key with the
Apple Push Notifications service (APNs) enabled; download the `.p8`. Set
`APNS_KEY` (the file contents, newlines as `\n`), `APNS_KEY_ID`, `APNS_TEAM_ID`,
`APNS_TOPIC` (your bundle id), and `APNS_SANDBOX=true` for development builds.

**FCM (Android).** Create a Firebase project, add an Android app, download the
service-account JSON. Set `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, and
`FCM_PRIVATE_KEY` (newlines as `\n`) from it. The Android app also needs a
`google-services.json` from the same project (see `apps/android/README.md`).

Point each app at the relay: `apps/ios` reads the base URL from its config,
`apps/android` from `BuildConfig`/`Config`. Defaults are `http://localhost:8787`
(iOS sim) and `http://10.0.2.2:8787` (Android emulator).

## 2. iOS — App Store

The long pole is **Apple's Family Controls distribution entitlement**
(`com.apple.developer.family-controls`). Without it the Screen Time shielding
will not run outside development. Apply as early as possible — it is reviewed by
a human at Apple and gates everything else.

1. Apple Developer Program membership.
2. Apply for the Family Controls distribution entitlement for the app id.
3. Configure the App Group and push capability on the app id; create the APNs key.
4. `cd apps/ios && xcodegen generate`, open `Pact.xcodeproj`, set your team and
   bundle ids on all three targets (app + two extensions).
5. Archive, upload to App Store Connect, distribute via TestFlight, then submit
   for review. The review notes must explain the present-together use case and
   that emergency calling is never impeded.

## 3. Android — Play Store

1. Google Play Console account.
2. Add `apps/android/app/google-services.json` and re-enable the
   `com.google.gms.google-services` plugin (see `apps/android/README.md`) for
   real push.
3. Create an upload keystore; configure signing in `app/build.gradle.kts` (or
   use Play App Signing). Build the release bundle:
   `cd apps/android && ./gradlew bundleRelease`.
4. In the Play Console, complete the **prominent disclosure** and Permissions
   declaration for usage access (`PACKAGE_USAGE_STATS`) and the overlay
   permission, framing it as a digital-wellbeing feature. This is a known
   review path; follow it precisely.
5. Roll out to internal testing, then production.

## CI

`.github/workflows/ci.yml` already builds all three on every push: backend
tests on Linux, `assembleDebug` on Linux for Android, and an unsigned simulator
build on macOS for iOS. Wire your signing secrets into CI when you're ready to
produce store-bound artifacts.

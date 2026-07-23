# NutRun

NutRun is an Android training and health tracker built with Kotlin and Jetpack
Compose. It combines workouts, food and calorie logging, hydration reminders,
supplements, BMI and energy estimates, weight history, and recorded GPS walks.

## Android App

The app uses a local-first production architecture:

- Account-scoped Room storage for profiles, weight history, nutrition,
  hydration, training, supplements, walk sessions, and route points.
- DataStore for the current account, theme, and per-account entitlement cache.
- A WorkManager outbox for authenticated, idempotent Firestore/Storage sync.
- Hilt, KSP, Navigation Compose, Flow, and WorkManager.
- Firebase email/password authentication when `google-services.json` is supplied.
- Fused Location Provider, foreground recording, and the step-counter sensor.
- Google Maps Compose with an offline route preview when no Maps key is configured.
- Google Play Billing and entitlement-gated AdMob test ads.

Debug builds provide a local authentication fallback so the app can be evaluated
before Firebase credentials are added. Release authentication fails closed when
the production services are not configured.

Open the repository in Android Studio and run the `app` configuration on an API
26+ emulator or device. From PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Backend and MCP

`backend/` contains the Cloud Run REST and MCP service. It uses Firebase Admin,
account-scoped Firestore and Cloud Storage access, Play subscription
verification, deterministic conflict handling, OAuth JWT verification, scoped
route privacy, shared rate limits, confirmations, revocation, and audit logging.

```powershell
cd backend
npm.cmd install
npm.cmd test
npm.cmd start
```

Production keys, Firebase, OAuth, Maps, AdMob, Play Billing, and Cloud Run setup
are documented in [Production Services Setup](docs/setup/production-services.md).
The ordered product plans are indexed in [Planning Documents](docs/plans/README.md).

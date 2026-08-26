# Internal Play Release Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden, configure, build, and validate NutRun through a signed Google Play Internal Testing release.

**Architecture:** Complete repository-owned safeguards first, then stop at an explicit operator checkpoint for protected Google/Play resources. Resume only after those resources are supplied, build a signed candidate, inspect it, upload it, and gather emulator, physical-device, and customer-acceptance evidence.

**Tech Stack:** Android/Kotlin/Compose, Gradle 8.9, Java 17, Node.js 22, Express 5, Firebase Admin/Auth, Firestore/Storage, Cloud Run, Google Play Billing, AdMob/UMP, GitHub Actions.

## Global Constraints

- Do not commit `keystore.properties`, `*.jks`, `*.keystore`, `google-services.json`, service-account JSON, backend `.env`, production API keys, or unrestricted credentials.
- Keep `versionCode = 2` and `versionName = "0.2.0"` until Play Console requires a new code.
- Run Gradle and connected tests sequentially; do not build concurrently from Android Studio and the terminal.
- Customer acceptance issue #5 remains open until Avi reviews evidence and closes it.
- Treat emulator `keyDispatchingTimedOut` as infrastructure evidence only after the affected class passes on rerun.
- Do not add RTDN, Crashlytics, Google Sign-In, R8, mobile rate limiting, or sync pagination unless internal testing makes one a blocker.
- Do not commit or push implementation changes unless Avi explicitly requests it.

---

## Task 1: Establish a reproducible baseline and CI gate

**Files:**
- Create: `.github/workflows/verify.yml`
- Modify: `.gitignore`
- Modify: `docs/testing/task-21-stabilization-acceptance.md`

**Produces:**
- Push/PR verification for backend tests and Android JVM/lint/debug builds.
- Secret-safe ignore coverage.
- Documented Windows lock recovery and exact sequential gate.

- [ ] **Step 1: Add secret-ignore regression coverage**

Add:

```gitignore
app/google-services.json
google-services.json
backend/.env
backend/.env.*
!backend/.env.example
service-account*.json
```

Run:

```bash
git check-ignore app/google-services.json backend/.env
```

Expected: both paths are printed.

- [ ] **Step 2: Add the GitHub verification workflow**

Create `.github/workflows/verify.yml` with two independent jobs:

```yaml
name: Verify
on:
  pull_request:
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: backend/package-lock.json
      - run: npm ci
      - run: npm test

  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
```

- [ ] **Step 3: Document local lock recovery**

Add to the Task 21 validation document:

```powershell
.\gradlew.bat --stop
taskkill /F /IM java.exe
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue
```

State that this is used only after a confirmed stale lock and that Android Studio must not be building concurrently.

- [ ] **Step 4: Run the baseline**

```bash
cd backend && npm ci && npm test && cd ..
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon --console=plain
git diff --check
```

Expected: all commands pass. If Gradle locks a generated file, apply the documented recovery once and rerun.

---

## Task 2: Make release configuration fail closed

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/avitoohband/nutrun/ProfileContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProfileContentTest.kt`
- Modify: `docs/setup/production-services.md`

**Produces:**
- One release guard shared by `assembleRelease` and `bundleRelease`.
- Required signing and non-placeholder production configuration.
- In-app privacy and terms links.

- [ ] **Step 1: Write Profile link tests**

Extend `ProfileContentTest` so Help/Data sections expose clickable `Privacy policy` and `Terms of service` rows and invoke injected callbacks.

Expected initial result: tests fail because the rows and callbacks do not exist.

- [ ] **Step 2: Add policy URL BuildConfig values**

Read Gradle properties:

```kotlin
val privacyPolicyUrl = providers.gradleProperty("PRIVACY_POLICY_URL").orNull
val termsOfServiceUrl = providers.gradleProperty("TERMS_OF_SERVICE_URL").orNull
```

Add both to `releaseGradleProperties` and expose them as escaped `BuildConfig` string fields. Debug may use empty values.

- [ ] **Step 3: Replace the release task guard**

Apply the guard to both release artifact tasks:

```kotlin
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
    doFirst {
        check(keystorePropertiesFile.exists()) {
            "Release builds require local keystore.properties."
        }
        val missing = releaseGradleProperties.filter {
            providers.gradleProperty(it).orNull.isNullOrBlank()
        }
        check(missing.isEmpty()) {
            "Release builds require Gradle properties: ${missing.joinToString()}."
        }
    }
}
```

Also reject:

- Maps key `REPLACE_WITH_RESTRICTED_MAPS_KEY`;
- URLs that are not `https://`;
- Google sample AdMob app/banner IDs beginning `ca-app-pub-3940256099942544`.

- [ ] **Step 4: Add Profile policy links**

Add callbacks to `ProfileOverviewContent`:

```kotlin
onOpenPrivacyPolicy: () -> Unit,
onOpenTermsOfService: () -> Unit
```

Use `Intent.ACTION_VIEW` from the `MainActivity` caller only when the corresponding BuildConfig URL is a valid HTTPS URI. Render fully clickable settings rows with stable test tags.

- [ ] **Step 5: Verify release failure modes**

Run each command and confirm the expected message:

```bash
./gradlew bundleRelease --no-daemon
./gradlew bundleRelease -PMAPS_API_KEY=x -PBACKEND_BASE_URL=http://invalid --no-daemon
```

Expected: first fails for missing signing/properties; second fails for non-HTTPS or missing values. No AAB is produced.

- [ ] **Step 6: Run focused Android tests**

```bash
./gradlew testDebugUnitTest assembleDebugAndroidTest --no-daemon
adb shell am instrument -w \
  -e class com.avitoohband.nutrun.ProfileContentTest \
  com.avitoohband.nutrun.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: Profile policy link tests pass.

---

## Task 3: Add Firebase data-boundary configuration

**Files:**
- Create: `firebase.json`
- Create: `firebase/firestore.rules`
- Create: `firebase/storage.rules`
- Create: `firebase/firestore.indexes.json`
- Modify: `docs/setup/production-services.md`

**Produces:**
- Versioned, deny-by-default client rules.
- Deployable Firebase configuration.

- [ ] **Step 1: Add deny-by-default Firestore rules**

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

The Android app does not use the Firestore client SDK; the Cloud Run Admin SDK bypasses these client rules.

- [ ] **Step 2: Add deny-by-default Storage rules**

```text
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{object=**} {
      allow read, write: if false;
    }
  }
}
```

- [ ] **Step 3: Add Firebase project mapping**

Create `firebase.json` pointing Firestore rules/indexes and Storage rules to the files above. Use an empty indexes list initially; only add composites required by emulator/deploy errors.

- [ ] **Step 4: Document deployment and verification**

```bash
firebase use <production-project-id>
firebase deploy --only firestore:rules,firestore:indexes,storage
```

Verification: an authenticated client SDK read under `users/{uid}` is denied, while the Cloud Run service account can read through Admin SDK.

---

## Task 4: Harden backend startup and deployment

**Files:**
- Create: `backend/src/config.js`
- Create: `backend/test/config.test.js`
- Create: `backend/.env.example`
- Create: `backend/scripts/deploy-cloud-run.ps1`
- Modify: `backend/src/server.js`
- Modify: `docs/setup/production-services.md`

**Produces:**
- Pure, tested environment validation.
- `/healthz` endpoint.
- Repeatable Cloud Run deployment command.

- [ ] **Step 1: Write failing configuration tests**

Test `loadConfig(environment)` for:

- missing `PUBLIC_BASE_URL`;
- missing `ANDROID_PACKAGE_NAME`;
- placeholder Open Food Facts user agent;
- non-HTTPS production URL;
- a complete valid production configuration.

Expected: tests fail before `backend/src/config.js` exists.

- [ ] **Step 2: Implement the configuration parser**

Export:

```javascript
export function loadConfig(env = process.env) {
  const production = env.NODE_ENV === "production";
  const required = ["PUBLIC_BASE_URL", "ANDROID_PACKAGE_NAME"];
  const missing = production ? required.filter((name) => !env[name]?.trim()) : [];
  if (missing.length) throw new Error(`Missing production environment: ${missing.join(", ")}`);
  if (production && !env.PUBLIC_BASE_URL.startsWith("https://")) {
    throw new Error("PUBLIC_BASE_URL must use HTTPS");
  }
  return {
    port: Number(env.PORT ?? 8080),
    publicBaseUrl: env.PUBLIC_BASE_URL ?? `http://localhost:${env.PORT ?? 8080}`,
    androidPackageName: env.ANDROID_PACKAGE_NAME ?? "com.avitoohband.nutrun"
  };
}
```

Keep OAuth variables optional for the mobile-only internal release; MCP returns its existing `oauth_not_configured` response until configured.

- [ ] **Step 3: Add health output and fail-fast startup**

Load config before Firebase initialization and add:

```javascript
app.get("/healthz", (_req, res) => {
  res.status(200).json({ status: "ok", service: "nutrun-service" });
});
```

Never include environment values, project IDs, tokens, or credentials in this response.

- [ ] **Step 4: Add a protected deploy script**

The PowerShell script accepts project, region, service account, and non-secret settings as parameters; it invokes `gcloud run deploy` using the existing `backend/Dockerfile`. Secrets are read from environment/Secret Manager and never echoed.

- [ ] **Step 5: Run backend tests**

```bash
cd backend
npm ci
npm test
```

Expected: config, policy, and tools tests pass.

---

## Task 5: Add release-focused security and behavior tests

**Files:**
- Create: `backend/test/mobile-contract.test.js`
- Create: `app/src/test/java/com/avitoohband/nutrun/ReleaseConfigurationTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`
- Modify: `docs/testing/task-21-stabilization-acceptance.md`

**Produces:**
- Regression evidence for account boundaries and release-only exclusions.

- [ ] **Step 1: Make backend dependencies injectable**

Extract route handlers or an `createApp(dependencies)` factory only as far as needed to inject fake Auth, Firestore, Storage, and Play clients. Preserve production defaults in `server.js`.

- [ ] **Step 2: Add mobile contract tests**

Cover:

- user A cannot load user B’s walk route;
- account deletion removes user-scoped Firestore/Storage data before Auth deletion;
- billing verification accepts only the two configured products;
- unknown products and mismatched Android package names are rejected;
- `/healthz` needs no authentication and returns no configuration.

- [ ] **Step 3: Add Android release-configuration unit tests**

Move pure checks into a small Kotlin function if needed and verify:

- demo login is allowed only when `debug == true`;
- debug subscription mutation is rejected in release;
- production policy links require HTTPS;
- test AdMob IDs are recognized as invalid for release.

- [ ] **Step 4: Extend manual release assertions**

Record exact checks for:

- no `demo-login`;
- no `profile-dev-toggle-subscription`;
- no ads on authentication, tutorial, active workout, or active walk;
- List/Grid mode persists per account;
- account deletion and account switching remain isolated.

- [ ] **Step 5: Run focused suites**

```bash
cd backend && npm test && cd ..
./gradlew testDebugUnitTest assembleDebugAndroidTest --no-daemon
adb shell am instrument -w \
  -e class com.avitoohband.nutrun.ProductionFlowTest,com.avitoohband.nutrun.ActiveWorkoutContentTest \
  com.avitoohband.nutrun.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: all focused tests pass.

---

## Task 6: Complete the repository stabilization gate

**Files:**
- Modify: `docs/testing/task-21-stabilization-acceptance.md`
- Modify: `docs/handovers/2026-08-21-ux-ui-tasks-10-19-handover.md`

**Produces:**
- Current evidence after the active-workout List/Grid feature and release hardening.

- [ ] **Step 1: Run backend and static Android checks**

```bash
cd backend && npm ci && npm test && cd ..
./gradlew testDebugUnitTest --no-daemon --console=plain
./gradlew lintDebug --no-daemon --console=plain
./gradlew assembleDebug --no-daemon --console=plain
./gradlew assembleDebugAndroidTest --no-daemon --console=plain
```

- [ ] **Step 2: Run one connected suite**

```bash
export ANDROID_SERIAL=emulator-5554
./gradlew connectedDebugAndroidTest --no-daemon --console=plain
```

If the emulator stalls, cold boot once and rerun only the affected class. Record both the infrastructure failure and passing rerun.

- [ ] **Step 3: Update evidence**

Record date, emulator/device, commands, counts, failures, reruns, and remaining external blockers. Do not mark physical-device or customer acceptance complete.

---

## Task 7: Operator production-resource checkpoint

**Owner:** Avi for account creation and secret material; agent for validation and documentation.

**Files:**
- Create locally only: `keystore.properties`
- Create locally only: `app/google-services.json`
- Modify: `docs/setup/production-services.md` only if actual provider settings differ

**Produces:**
- Verified inputs needed for a signed release.

- [ ] **Step 1: Create release signing material**

Generate or provide a release/upload keystore, record SHA-256 for Maps/Firebase/Play, and configure local `keystore.properties`. Back up the keystore securely outside the repository.

- [ ] **Step 2: Configure Firebase and Cloud Run**

Create production Firebase/GCP resources, enable Email/Password Auth, deploy Task 3 rules, deploy Task 4 backend, configure the service account, and verify:

```bash
curl https://<service-url>/healthz
```

Expected: HTTP 200 with `{"status":"ok","service":"nutrun-service"}`.

- [ ] **Step 3: Configure Android services**

Supply protected `google-services.json`, restricted Maps key, production AdMob IDs, UMP consent message, and production backend URL.

- [ ] **Step 4: Configure Play Console**

Create the app and products:

- `nutrun_ad_free_monthly`
- `nutrun_ad_free_annual`

Add licensed testers, Android Publisher access, hosted policy URLs, Data Safety, Health apps, content rating, and store listing.

- [ ] **Step 5: Validate without exposing secrets**

Run checks that print only presence, URL hostnames, key fingerprints, and product IDs. Never print passwords, private keys, API keys, service-account JSON, or complete AdMob identifiers.

**Stop condition:** Do not start Task 8 until every required production resource is present and validated.

---

## Task 8: Build and inspect the signed candidate

**Files:**
- Generated only: `app/build/outputs/bundle/release/app-release.aab`
- Modify: `docs/release/internal-play-store-rc.md`

**Produces:**
- Signed, inspected `0.2.0` candidate.

- [ ] **Step 1: Build**

```bash
./gradlew bundleRelease --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`; release guards confirm signing and production values.

- [ ] **Step 2: Verify signature and metadata**

Use Android SDK bundle/APK tooling to verify:

- package `com.avitoohband.nutrun`;
- version code 2 and name 0.2.0;
- certificate fingerprint matches the configured upload key;
- manifest contains production Maps/AdMob values;
- backend and policy URLs are HTTPS;
- no demo/debug controls are discoverable in the release build.

- [ ] **Step 3: Record checksum**

```bash
sha256sum app/build/outputs/bundle/release/app-release.aab
```

Record the checksum and build commit in the RC document; do not add the AAB to Git.

---

## Task 9: Upload, smoke test, and hand off acceptance

**Owner:** Agent where authenticated tools permit; Avi for Play Console confirmations and final acceptance.

**Files:**
- Modify: `docs/release/internal-play-store-rc.md`
- Modify: `docs/testing/task-21-stabilization-acceptance.md`

- [ ] **Step 1: Upload to Internal Testing**

Upload the signed AAB, add release notes, assign licensed testers, and wait for Play processing.

- [ ] **Step 2: Perform install and upgrade smoke**

Install from the Play testing link and verify cold start plus upgrade from the previous debug/internal build without data loss.

- [ ] **Step 3: Run the production smoke matrix**

Verify:

- email/password auth and password reset;
- onboarding and one-time tutorial;
- List/Grid active workout, set completion, rest timer, and finish;
- nutrition, water, supplements, walk route/history, and progress;
- notification scheduling;
- subscriber purchase/restore and server verification;
- UMP consent and ads only for the free entitlement on allowed screens;
- two-account isolation and account deletion.

- [ ] **Step 4: Run physical-device accessibility checks**

Use at least one physical Android device. Record model, OS, build, large text, TalkBack, GPS route, notification timing, and billing results.

- [ ] **Step 5: Prepare issue #5**

Post build checksum, tested devices, automated gate results, smoke evidence, and known limitations. Avi reviews and closes issue #5.

- [ ] **Step 6: Define rollback**

Retain the previous internal AAB/version, document release-blocking thresholds, and stop promotion if auth, sync, billing, deletion, or crash behavior regresses.

## Completion boundary

Repository hardening is complete after Task 6. Tasks 7–9 require Avi’s protected resources and console actions. The overall milestone is not complete until the signed candidate is installed from Internal Testing and issue #5 is accepted.

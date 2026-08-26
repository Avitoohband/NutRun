# Internal Play Release Readiness Design

## Goal

Prepare NutRun for a signed Google Play Internal Testing release without committing secrets or claiming external console work is complete before it is verified.

## Delivery model

Work proceeds through three explicit stages:

1. **Repository hardening:** release guards, Firebase rules, backend deployment support, targeted tests, CI, and a clean local gate.
2. **Operator checkpoint:** Avi creates and supplies protected signing, Firebase, Cloud Run, Maps, AdMob, Play Console, and hosted-policy configuration.
3. **Release candidate:** build and inspect a signed AAB, upload it to Internal Testing when access is available, then collect emulator, physical-device, and customer-acceptance evidence.

The repository stage may complete independently. The release-candidate stage remains blocked until every required external value is available and verified.

## Repository scope

### Android release hardening

- Apply production validation to both `assembleRelease` and `bundleRelease`.
- Require a valid `keystore.properties` file for release tasks.
- Reject Google test AdMob IDs and placeholder URLs/keys in release builds.
- Add `google-services.json` and backend environment files to ignore coverage.
- Add configurable privacy-policy and terms URLs and expose them from Profile.
- Prove release artifacts contain no demo login or debug subscription controls.

### Backend and Firebase hardening

- Add deny-by-default Firestore and Storage client rules; backend Admin SDK remains the only data path.
- Add a production environment parser that fails fast on missing or placeholder values.
- Add unauthenticated `/healthz` readiness output that contains no sensitive configuration.
- Add a documented Cloud Run deployment command and post-deploy smoke checks.
- Add focused contract tests for environment validation, cross-account route access, billing verification policy, and deletion behavior.

### Verification and automation

- Add GitHub Actions for backend tests and Android JVM/lint/debug assembly.
- Keep connected Android tests sequential on one local emulator.
- Document Windows Gradle lock recovery (`gradlew --stop`, no concurrent IDE/terminal builds).
- Re-run the full gate after the active-workout List/Grid commits.

## External checkpoint

Avi must create or provide:

- release keystore and local `keystore.properties`;
- production Firebase project, Email/Password Auth, Storage, and protected `app/google-services.json`;
- Cloud Run project/service account and production backend URL;
- restricted Maps API key;
- AdMob app/banner IDs and UMP consent message;
- Play Console application, monthly/annual subscription products, licensed testers, and Android Publisher access;
- hosted privacy-policy and terms URLs;
- completed Data Safety, Health apps, content-rating, and store-listing forms.

No secret, signing key, unrestricted API key, or service-account credential enters Git.

## Candidate and acceptance

- Build `versionCode 2` / `versionName 0.2.0` as a signed AAB.
- Inspect the merged release manifest and generated BuildConfig values.
- Upload to Internal Testing and perform install/upgrade smoke tests.
- Validate auth, tutorial, Profile, List/Grid active workout, nutrition, walking/routes, progress, notifications, billing, ads/UMP, account switching, and deletion.
- Record at least one physical-device matrix.
- Post evidence to GitHub issue #5; only Avi closes customer acceptance.

## Deferred until internal testing identifies a blocker

- Play RTDN subscription lifecycle and automatic entitlement downgrade;
- Crashlytics or another telemetry SDK;
- Google Sign-In;
- R8/minification;
- mobile API rate limiting and large-sync pagination;
- automated Play upload and promotion beyond Internal Testing.

## Success criteria

The milestone is complete only when:

1. repository and CI gates are green;
2. production rules and backend are deployed;
3. signed AAB validation passes without placeholders or debug controls;
4. Internal Testing install/upgrade and licensed billing/ads checks pass;
5. physical-device evidence is recorded; and
6. Avi accepts and closes issue #5.

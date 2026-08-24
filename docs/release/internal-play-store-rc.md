# Internal Play Store release candidate (Task 23)

## Version

- `versionCode`: 2
- `versionName`: 0.2.0

## Preconditions

- Task 20 tutorial gate green on emulator.
- Task 21 stabilization and customer acceptance evidence posted for issue #5 (customer closes issue).
- Task 22 production properties, signing, UMP consent, privacy/terms, and Play Console declarations complete.

## Build signed release bundle

1. Configure `keystore.properties` locally (never commit):

```properties
storeFile=/path/to/release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

2. Set production Gradle properties per [production-services.md](../setup/production-services.md).

3. Build:

```bash
./gradlew bundleRelease --no-daemon
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## Pre-upload verification

- [ ] Release manifest uses production AdMob app ID (not test ID).
- [ ] No `demo-login` or debug subscription UI in release APK.
- [ ] `BACKEND_BASE_URL` points to production Cloud Run.
- [ ] Maps key restricted to package + release signing certificate.
- [ ] Firebase `google-services.json` injected from protected CI/local secret.
- [ ] Privacy policy URL and terms published for Play Console.
- [ ] Data Safety, Health apps, and content rating forms completed.

## Internal track

1. Upload AAB to Play Console **Internal testing**.
2. Install/upgrade smoke: cold start, sign-in, tutorial, one workout log, one meal, one walk, Progress chart.
3. Verify crash reporting and operational telemetry with privacy-conscious defaults.
4. Test billing and ads/consent with licensed tester accounts.

## Promotion criteria

Promote to closed testing only after:

- Stabilization gates green.
- Issue #5 acceptance evidence complete.
- Rollback criteria defined (revert to previous internal build if crash rate or billing regression).

## Rollback

Keep previous internal `versionCode` AAB available. If critical regression occurs, halt promotion and upload prior bundle as emergency rollback.

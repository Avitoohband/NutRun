# Production Services Setup

NutRun runs locally without cloud credentials. The integrations below must be
configured before a production release; secrets and unrestricted keys must not
be committed.

## Android

Add these values to the user-level Gradle properties file or a protected CI
environment:

```properties
MAPS_API_KEY=your_package_and_signing_restricted_key
BACKEND_BASE_URL=https://your-cloud-run-service.example
ADMOB_APP_ID=ca-app-pub-your-production-app-id
ADMOB_BANNER_ID=ca-app-pub-your-production-banner-id
PRIVACY_POLICY_URL=https://your-domain.example/privacy
TERMS_OF_SERVICE_URL=https://your-domain.example/terms
```

Release builds fail at `assembleRelease` or `bundleRelease` when any property above is missing,
when `keystore.properties` is absent locally, when Maps/AdMob values are still placeholders or
Google test IDs, or when backend and policy URLs are not HTTPS. Debug builds continue to use
Google test AdMob IDs and placeholder Maps keys.

Before showing production ads, NutRun runs the Google User Messaging Platform (UMP) consent flow
when `ADMOB_APP_ID` and `ADMOB_BANNER_ID` are configured. Ads appear only for the free
ad-supported entitlement and never on onboarding, tutorial, active workout, or active walk recording
screens.

The Maps key must be restricted to `com.avitoohband.nutrun` and the release
signing certificate. Create separate development and production Firebase
projects, add `google-services.json` only through protected local/CI secret
injection, and connect the authentication gateway before release.

Create Play products named:

- `nutrun_ad_free_monthly`
- `nutrun_ad_free_annual`

Purchase verification and entitlement changes must come from the backend. The
debug-only subscription toggle must never be exposed in release builds.

NutRun requests Health Connect access only from the Progress screen. Before a
Play release, complete the Health apps declaration and privacy-policy
disclosures for steps, weight, hydration, nutrition, and exercise sessions.
The project uses Health Connect `1.1.0-alpha11` while it remains on
`compileSdk 35` and AGP `8.7.3`; moving to the stable `1.1.0` client requires
upgrading to `compileSdk 36` and a compatible Android Gradle Plugin together.

## Cloud Run

Deploy `backend/` with Application Default Credentials and these environment
variables:

```text
PUBLIC_BASE_URL=https://your-cloud-run-service.example
ALLOWED_MCP_ORIGINS=https://claude.ai,https://chatgpt.com
OAUTH_ISSUER=https://your-managed-oauth-issuer.example
OAUTH_AUDIENCE=nutrun-mcp
ANDROID_PACKAGE_NAME=com.avitoohband.nutrun
OPEN_FOOD_FACTS_USER_AGENT=NutRun/0.1 support@your-domain.example
```

The OAuth issuer must support authorization code with PKCE, publish a JWKS, and
include the linked `firebase_uid` in access tokens. Detailed provider setup is
in [MCP OAuth Setup](mcp-oauth.md).

Grant Firebase Admin, Firestore, Storage, and Android Publisher subscription
read access only to the Cloud Run service account. Link the Google Play app to
the Google Cloud project before enabling purchase verification. Use Firestore
rules that deny direct cross-account access.

The backend exposes:

- Firebase-ID-token REST endpoints under `/v1/*`.
- MCP Streamable HTTP at `/mcp`.
- OAuth protected-resource metadata at
  `/.well-known/oauth-protected-resource`.

Route geometry is stored under `users/{uid}/routes/` and is available only to
MCP access tokens carrying `location.read`.

Firestore TTL should be enabled for the `expiresAt` field in `mcpRateLimits`.
This keeps the shared Cloud Run rate-limit collection bounded.

## Validation

```powershell
cd backend
npm install
npm test
```

Run the Android checks from the repository root:

```powershell
.\gradlew.bat clean test lint assembleDebug
```

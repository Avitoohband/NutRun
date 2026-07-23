# NutRun Production MVP Expansion

## Summary

Upgrade NutRun from an in-memory prototype to a production Android MVP with account-required onboarding, persistent health tracking, calorie and hydration logging, recorded GPS walks, subscriptions, advertising, and an authenticated remote MCP server.

Use five primary destinations: **Today, Training, Nutrition, Walk, Progress**. Profile and settings move to the top-bar account menu.

The commercial model is fixed:

- New accounts receive a 30-day ad-free trial without payment details.
- After 30 days, all core functionality remains available with ads.
- Monthly and annual subscriptions remove ads.
- MCP remains available on every plan, subject to authentication and rate limits.

## Implementation Changes

### Foundation and Data

- Replace `PrototypeViewModel` with repositories backed by Room, Coroutines/Flow, Hilt, Navigation Compose, DataStore, and WorkManager.
- Organize code into `app`, shared model/data/UI modules, and onboarding, nutrition, walking, training, progress, and profile features.
- Store canonical measurements as kilograms, centimeters, milliliters, kilometers, and UTC timestamps; convert only at presentation and input boundaries.
- Use Firebase Authentication, Firestore, Cloud Storage, and server-side entitlement records. Keep Room as the local source of truth and synchronize with retryable, idempotent operations.

Introduce stable models for:

- `UserProfile`, `WeightEntry`, `HealthEstimate`
- `FoodCatalogItem`, `FoodLogEntry`, `DailyNutritionSummary`
- `HydrationPlan`, `WaterLogEntry`
- `WalkSession`, `WalkPoint`, `WalkSummary`
- Existing supplements, programs, workouts, and progression data
- `Entitlement` with `TRIAL`, `FREE_AD_SUPPORTED`, and `SUBSCRIBER`

### Authentication, Onboarding, and Metrics

- Require Google or email/password authentication before app access.
- After first authentication, require birth date, biological sex, height, weight, activity level, goal, and preferred units.
- Save every weight change as history rather than overwriting it.
- Calculate:
  - BMI from metric height and weight.
  - BMR using Mifflin-St Jeor.
  - TDEE using the selected activity multiplier.
  - An editable calorie target using maintenance, `-300 kcal` for loss, or `+300 kcal` for gain.
- Display estimates with clear "general guidance, not medical advice" wording.
- Start the trial once using a server timestamp so reinstalling cannot restart it.

### Nutrition and Hydration

- Add meal-based food logging with breakfast, lunch, dinner, and snack categories.
- Support Open Food Facts search through a backend adapter, serving size selection, calories, protein, carbohydrates, and fat.
- Use current Open Food Facts product data and search services, with caching and a manual-entry fallback whenever data is missing or unavailable.
- Allow entries to be edited, duplicated, and deleted; daily totals must work offline.
- Add a user-configurable hydration goal, serving size, waking window, and reminder interval.
- Default hydration settings to `2,000 mL`, `250 mL`, `08:00-22:00`, every two hours.
- Stop reminders once the daily goal is reached and resume the next local day.
- Use WorkManager-based notifications; exact alarms are not required.

### Recorded Walks

- Implement user-initiated start, pause, resume, and finish states in a foreground location service.
- Request fine location, activity recognition, and notification permissions only when required.
- Use Fused Location Provider for route and distance, and `TYPE_STEP_COUNTER` with a session baseline for steps. If the sensor is unavailable, show steps as unavailable rather than presenting an unlabelled estimate.
- Persist points incrementally so recording survives screen changes, app process interruption, and device locking.
- Filter inaccurate or impossible GPS jumps before adding distance.
- Display the live and completed route using Google Maps Compose and a polyline. Google Maps requires a billing-enabled project and a package/signing-restricted API key. [Google Maps Compose documentation](https://developers.google.com/maps/documentation/android-sdk/maps-compose)
- Save walk summaries in Firestore and compressed route geometry in Cloud Storage; retain full local points in Room.
- Do not implement all-day or always-on tracking in this release. Android 10+ step access requires `ACTIVITY_RECOGNITION`. [Android motion sensor guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)

### Ads and Subscriptions

- Integrate Google Play Billing with `nutrun_ad_free_monthly` and `nutrun_ad_free_annual`.
- Verify purchases through the backend and support restoration, grace periods, cancellation, expiration, and temporary billing failures.
- Integrate AdMob only after entitlement resolution.
- Place restrained ads on Today, Nutrition summaries, and Progress.
- Never display ads during onboarding, active workouts, active walks, exercise safety content, or blocking dialogs.
- Remove ads immediately after server-confirmed subscription entitlement.

## API and MCP

Deploy one Cloud Run service exposing:

- `/v1/*` REST endpoints for the Android app.
- `/mcp` using MCP Streamable HTTP with OAuth authorization-code flow and PKCE.
- Firebase ID tokens authenticate mobile REST calls; scoped OAuth access tokens authenticate MCP clients.
- Validate MCP `Origin`, apply per-user rate limits, maintain audit logs, and require idempotency keys for writes. [MCP transport specification](https://modelcontextprotocol.io/specification/2025-03-26/basic/transports)

MCP scopes:

- `health.read`: profile estimates and daily summaries.
- `logs.write`: food, water, supplement, and workout logging.
- `walks.read`: walk summaries without coordinates.
- `location.read`: full route geometry, granted separately.
- `profile.write`: profile and target changes.
- `destructive`: deletion, always requiring explicit confirmation.

Initial MCP tools:

- `get_profile_summary`, `get_daily_summary`
- `search_food`, `log_food`, `log_water`
- `log_supplement_status`, `log_workout`
- `list_walks`, `get_walk_summary`, `get_walk_route`
- `update_profile`, `delete_log`

Profile changes and deletion must use MCP elicitation/confirmation. If a client does not support confirmation, return `confirmation_required` without changing data. Never expose GPS coordinates through the default walk-summary scope.

## Delivery Sequence

1. Refactor the prototype into production modules; add Room, Hilt, repositories, navigation, Firebase environments, and migration-safe models.
2. Implement authentication, onboarding, health estimates, weight history, and server-owned trial entitlement.
3. Implement nutrition search/manual logging, daily totals, hydration logging, and reminders.
4. Implement the foreground walk recorder, step sensor, route persistence, Google Maps UI, and synchronization.
5. Add REST and MCP services with OAuth, scopes, audit logging, confirmation, and account deletion.
6. Add Play Billing and AdMob, then release through internal testing and a closed beta.

## Test and Acceptance Plan

- Unit-test BMI/BMR/TDEE formulas, unit conversion, calorie totals, hydration rollover, reminder eligibility, GPS filtering, step baselines, and entitlement transitions.
- Test Room migrations, offline writes, synchronization retries, duplicate idempotency keys, and multi-device conflicts.
- Test location and activity permission denial, absent step sensor, GPS loss, pause/resume, process interruption, timezone changes, reboot, and daylight-saving transitions.
- Contract-test every REST and MCP operation, OAuth scope, route-privacy boundary, confirmation flow, rate limit, and unauthorized cross-account request.
- UI-test onboarding, food search/manual fallback, meal editing, water reminders, walk recording/map rendering, weight updates, subscriptions, ads, themes, large fonts, and compact screens.
- Validate synthetic routes on an emulator and field-test real walks on at least two physical devices; distance should remain within 10% of a trusted reference route.
- Acceptance requires:
  - A new user cannot bypass required profile setup.
  - Daily calories/macros and hydration remain correct offline.
  - An interrupted walk resumes without losing accepted points.
  - MCP clients can log data but cannot access routes without `location.read`.
  - Trial users and subscribers see no ads.
  - Expired trial users retain all core data and functionality with ads.
  - Account deletion removes Firestore records, Cloud Storage routes, MCP tokens, and local data.

## Assumptions

- Android only, minimum SDK 26 and target SDK 35.
- English is the initial language; all UI strings move to resources and remain RTL-ready.
- Google Maps, Firebase, Cloud Run, AdMob, and Play Console projects will be supplied with separate development and production credentials.
- Open Food Facts is accessed through a replaceable backend adapter because coverage and data quality vary; manual entry is always available. [Open Food Facts API](https://openfoodfacts.github.io/openfoodfacts-server/api/)
- No barcode scanner, all-day step tracking, medical diagnosis, automatic meal plans, or feature-gated premium tier is included in this MVP.

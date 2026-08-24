# Task 20 — Tutorial validation

## Scope

Versioned account-scoped tutorial welcome prompt, five-page guided overview, Profile Help replay, and persistence across sign-out.

## Automated gate (2026-08-24)

Run sequentially on one emulator:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
```

Release assembly requires production Gradle properties (see [production-services.md](../setup/production-services.md)):

```bash
./gradlew assembleRelease \
  -PMAPS_API_KEY=your_key \
  -PBACKEND_BASE_URL=https://your-backend.example \
  -PADMOB_APP_ID=ca-app-pub-your-app-id \
  -PADMOB_BANNER_ID=ca-app-pub-your-banner-id \
  --no-daemon
```

## Unit tests

- `TutorialStateTest` — eligibility, demo exclusion, version acknowledgement, five ordered pages.
- `AppPreferencesTutorialTest` (androidTest) — acknowledge/skip, sign-out preservation, `clearAccount` removal.

## Compose / instrumentation tests

- `TutorialContentTest` — five steps, Back/Next/Done, welcome Start/Skip.
- `ProfileContentTest` — Help section and replay callback.
- `ProductionFlowTest` — no demo auto-prompt, demo Help replay, signed-in welcome → tutorial.

## Accessibility review

- Tutorial step indicator exposes `Step N of 5` as content description.
- Welcome dialog and tutorial controls use test tags for automation; buttons have visible text labels.
- Large-text smoke: run `TutorialContentTest` on default emulator font scale; manual large-text check on physical device during Task 21.

## Product rules verified

- Non-demo accounts below `CURRENT_TUTORIAL_VERSION` see one welcome prompt on main tabs.
- Start or Skip suppresses that version; demo never auto-prompts.
- Profile → Help → Run tutorial again navigates to tutorial without changing acknowledgement state.
- Tutorial does not mutate data or request permissions.

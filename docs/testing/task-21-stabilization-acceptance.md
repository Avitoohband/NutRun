# Task 21 — Stabilization and customer acceptance

## Regression matrix (physical device required)

Exercise on at least one physical Android device after the emulator gate is green. Record device model, OS version, and build `versionName`.

| Area | Checks |
|------|--------|
| Auth / reset | Sign in, sign out, password reset email flow, invalid credential errors |
| Onboarding / tutorial | Welcome prompt once per version, skip persistence, Profile replay, demo exclusion |
| Profile | Edit health, notifications, dark mode, delete account (typed email), sign out |
| Training | Weekly schedule, workout library, exercise reorder in editor, Quick workout, active add/skip, sticky rest timer, process restoration, notification countdown |
| Nutrition | Food search/log, water, favorites, supplements |
| Walk | Start/pause/finish, route map, history detail |
| Progress | Charts, Health Connect permission from Progress only |
| Notifications | Supplement/hydration/training reminder settings |
| Billing | Entitlement labels, subscriber hides ads, debug toggle only in debug builds |
| Account switching | Two accounts keep isolated tutorial and session preferences |

## Emulator stabilization

- Run `connectedDebugAndroidTest` sequentially on one AVD; rerun flaky classes on `keyDispatchingTimedOut`.
- Treat infrastructure timeouts as evidence, not silent passes.
- Freeze feature work when this milestone is green; fix release-blocking defects only.

## Windows Gradle lock recovery

Use only after a confirmed stale lock (for example `Unable to delete directory` under `app\build`). Close Android Studio builds first; do not run Gradle from the terminal and Android Studio at the same time.

```powershell
.\gradlew.bat --stop
taskkill /F /IM java.exe
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue
```

Rerun the failed Gradle command once after recovery.

## Sequential release gate

Run from the repository root without concurrent Android Studio builds:

```bash
cd backend && npm ci && npm test && cd ..
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon --console=plain
git diff --check
```

## Customer acceptance (GitHub issue #5)

Engineering prepares evidence; only the customer/user closes issue #5.

### Acceptance checklist

- [ ] Today dashboard reflects training, nutrition, water, and supplements for a real week of use.
- [ ] Training schedule and set logging feel reliable during live workouts.
- [ ] Nutrition logging and macro totals match expectations for manual spot checks.
- [ ] Walk recording produces sensible distance/route and history entries.
- [ ] Progress trends align with logged weight, workouts, walks, and nutrition.
- [ ] Auth, onboarding, profile edit, and account deletion behave as documented.
- [ ] Tutorial welcome appears once for non-demo accounts; replay works from Profile Help.
- [ ] Notifications fire at configured times (or WorkManager inspection confirms scheduling).
- [ ] Free plan shows ads only on allowed screens; subscriber/trial hides ads.
- [ ] No demo login or debug subscription controls in release builds.
- [ ] App remains usable with large text and TalkBack on primary flows.

Attach build version, device list, and known limitations when posting acceptance evidence to issue #5.

# PulseFit Android prototype

This is the Stage 1 interactive prototype for the fitness app plan. It uses Kotlin and Jetpack Compose and intentionally keeps prototype data in memory.

## Implemented prototype flows

- Google and email registration simulations, trial onboarding, and notification choice.
- Today dashboard with supplement creation, daily/every-N-days/weekday schedules, and completion tracking.
- Weekly program creation, exercise search, target configuration, and session editing.
- Active workouts with pause, resume, completion, summary, and updated history.
- Exercise instructions, safety notes, affected muscles, and editable progression suggestions.
- Light/dark theme switch and a trial-to-free-plan ad preview simulation.

## Run it

Open this folder in Android Studio, let it use the included Gradle wrapper, then run the `app` configuration on an emulator or Android device.

From PowerShell, run:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test lint
```

All authentication, notification, subscription, and advertising behavior is simulated for prototype validation. Production authentication, purchases, ads, notifications, local storage, and backend synchronization belong to later plan missions.

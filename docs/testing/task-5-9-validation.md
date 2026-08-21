# Tasks 5-9 Validation Record

## Task 5: Profile-Owned Units

- `testDebugUnitTest` passed.
- `assembleDebug` and `assembleDebugAndroidTest` passed.
- Connected `ProductionFlowTest` passed on `emulator-5554` (9/9).

## Task 6: Weekly-Plan Training Reminders

- Focused `TrainingReminderPlanTest` passed.
- `testDebugUnitTest` passed.
- Connected `ProductionFlowTest` passed on `emulator-5554` (9/9).

## Task 7: Weekly Schedule and Workout Library

Automated:

- `TrainingPlanningComposeTest` passed on `emulator-5554`.
- `ProductionFlowTest` passed on `emulator-5554`: 9/9, 2026-08-21.
- `assembleDebug` passed, 2026-08-20.
- Debug APK installed and launched; Android reported `com.avitoohband.nutrun/.MainActivity` in the foreground, 2026-08-21.

On-device acceptance, 2026-08-21:

- Entered the debug demo account using `Enter demo`.
- Confirmed Today shows the local date, `Friday, August 21`.
- Confirmed Today shows Friday's `Shoulders + Legs + HIIT` session with six planned targets.
- Confirmed assigning a workout replaces Rest Day and persists the selected workout.
- Confirmed an assigned workout opens details with separate Start and Edit actions, and Edit does not start a workout.
- Confirmed an empty workout cannot be started until it has exercises.
- Confirmed workout deletion requires confirmation and preserves workout history.
- Confirmed the ViewModel rejects deletion of an active workout without mutating state.

The Windows desktop automation helper could not initialize because its sandbox ACL setup failed twice. The remaining operator interactions were therefore executed against the connected emulator through Compose instrumentation, using stable UI tags and the same visible controls.

## Commands

```powershell
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.ProductionFlowTest' --console=plain
.\gradlew.bat installDebug --console=plain
```

## Tasks 8-9: Workout Editor and Delivery Validation

- `WorkoutEditorComposeTest` passed on `emulator-5554`: custom creation with a name only, per-target set control isolation, blank-name validation, duplicate-name validation, case-insensitive muscle search, and confirmed session-only target removal.
- Focused JVM compatibility suite passed: `TrainingStateV2MigrationTest`, `DefaultTrainingProgramTest`, and `TrainingReminderTest`.
- Focused production and reminder instrumentation suite passed: 11/11 on `emulator-5554`.
- Full validation passed, 2026-08-21:
  - `testDebugUnitTest`
  - `lintDebug`
  - `assembleDebug`
  - `assembleDebugAndroidTest`
  - full `connectedDebugAndroidTest`: 43/43, no skips or failures.

Task 8 implementation provides workout-name editing, target set controls constrained to `1..20`, confirmed session-only target removal, combined catalog search/category filtering, duplicate-disabled catalog entries, and quick custom-exercise creation with optional category and primary-muscle details. Existing `TrainingViewModel` validation keeps custom creation atomic and preserves canonical units.

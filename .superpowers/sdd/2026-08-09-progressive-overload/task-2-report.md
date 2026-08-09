# Task 2 Report: ViewModel and Compose Integration

## Scope

Added read-only progression accessors to `TrainingViewModel` and rendered concise advisory text on Program session cards and active exercise cards. Suggestions are derived on demand from the current `workoutHistory` and `usesMetricUnits`; no progression state or persistence fields were added.

## TDD Evidence

### ViewModel RED/GREEN

- RED command: `.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest`
- RED outcome: failed at test compilation with unresolved `progressionSuggestion` and `progressionSuggestions` references, confirming the required ViewModel API was absent.
- GREEN command: `.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest`
- GREEN outcome: build successful; all 13 `TrainingViewModelTest` tests passed.

The new tests cover recalculation after `finishWorkout`, `updateWorkoutRecord`, `deleteWorkoutRecord`, and `updateUsesMetricUnits`. They also assert that `ExerciseTarget.weightKg` stays at 60 kg and that deriving a suggestion across a unit change leaves prefilled `activeSetLogs` unchanged.

### Compose RED/GREEN

- RED command: `.\gradlew.bat compileDebugAndroidTestKotlin`
- RED outcome: failed because the deterministic test could not access the file-private `TrainingScreen`; no suggestion UI or tags had been added.
- GREEN command: `.\gradlew.bat compileDebugAndroidTestKotlin`
- GREEN outcome: build successful after exposing the screen as `internal` and adding both advisory surfaces.

`ProgressionSuggestionComposeTest` uses a standalone real `TrainingViewModel`, configures a weighted target, and records a successful workout through public ViewModel actions. It renders the real training screen, asserts `Increase to 62.5 kg` on the Program card, clicks the real Start control, and asserts the same advice on the active exercise card. No production demo fixture or persisted fake state was added.

## Validation

- Full command: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`
- Outcome: build successful in 31 seconds; all requested tasks passed.
- Unit XML results: 65 tests, 0 failures, 0 errors, 0 skipped.
- Lint: `lintDebug` passed and generated `app/build/reports/lint-results-debug.html`.
- APK builds: `assembleDebug` and `assembleDebugAndroidTest` passed.
- Connected tests: not run because `adb devices` reported no attached devices or emulators.
- `git diff --check`: passed with no whitespace errors.

## Files

- `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`
- `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`
- `.superpowers/sdd/2026-08-09-progressive-overload/task-2-report.md`

## Commit

Commit message: `feat: show progressive overload suggestions`

## Self-Review

- Both ViewModel methods delegate to the reviewed Task 1 engine and only transform its returned values for session grouping.
- Program and active UI use the same presentation helper with distinct stable test tags.
- Suggestions read current snapshot state during composition, so history finish/edit/delete operations and unit changes trigger fresh derivation.
- No code changes assign to `ExerciseTarget.weightKg` or `activeSetLogs` while deriving or displaying suggestions.
- `TrainingStateCodec.kt` and persisted schemas are untouched, preserving legacy JSON decoding and adding no persistence.

## Concerns

The deterministic Compose test compiled and its APK assembled, but its runtime assertions could not be executed without an attached emulator. No other concerns found.

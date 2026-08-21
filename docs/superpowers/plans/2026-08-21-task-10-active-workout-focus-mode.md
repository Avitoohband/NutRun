# Active Workout Focus Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make long workouts fast to log by focusing one exercise at a time while keeping progress, rest status, Cancel, and Finish continuously reachable.

**Architecture:** Extract the active-workout branch from `TrainingScreen` into a focused `ActiveWorkoutContent` composable. Keep `TrainingViewModel.activeSetLogs` as the authoritative persisted state; UI focus is ephemeral and keyed by active session ID. Add pure input validation and a narrow ViewModel operation for copying the matching previous set.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt ViewModel, `rememberSaveable`, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 10.

**Status:** Implemented and validated on 2026-08-21 in commits `5a151a1` through `b3835e9`.

## Global Constraints

- Do not change Room, REST, MCP, or training JSON schemas.
- Preserve active-workout process restoration, canonical kilogram storage, alternative-group behavior, and rest-timer notifications.
- Profile remains the only owner of Metric/Imperial preference.
- Cancel must discard only the active session; Finish must create exactly one history record.
- Use numeric keyboards, inline errors, 48 dp touch targets, and labels that remain usable at 200% font scale.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 10.1: Set Input Validation and Previous-Set Copy

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/ActiveWorkoutInputTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`

**Interfaces:**
- Produces: `WorkoutSetInput(weight: String, reps: String, minutes: String, rpe: String)`.
- Produces: `validateWorkoutSetInput(input, durationTarget, metric): WorkoutSetInputValidation`.
- Produces: `TrainingViewModel.copyPreviousSet(targetId: String, setNumber: Int): TrainingMutationResult`.

- [x] **Step 1: Write failing pure validation tests**

Cover blank optional values, reps `0..1000`, non-negative weight, minutes `0..1440`, RPE `0..10`, comma/period decimal input, and pounds-to-kilograms conversion. Assert field-specific errors rather than one generic failure.

```kotlin
val result = validateWorkoutSetInput(
    WorkoutSetInput(weight = "-1", reps = "8", minutes = "", rpe = "11"),
    durationTarget = false,
    metric = true
)
assertEquals("Weight cannot be negative.", result.weightError)
assertEquals("RPE must be between 0 and 10.", result.rpeError)
```

- [x] **Step 2: Run the focused tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ActiveWorkoutInputTest --tests com.avitoohband.nutrun.TrainingViewModelTest --console=plain
```

Expected: compilation fails because the input types and copy operation do not exist.

- [x] **Step 3: Implement the pure parser and copy operation**

Return parsed canonical values only when every visible field is valid. `copyPreviousSet` resolves the target's exercise, finds the prior workout's matching set number, and calls the existing `updateWorkoutSet`; return `ValidationError` if no matching prior set exists.

- [x] **Step 4: Re-run the focused tests and verify GREEN**

Run the Step 2 command. Expected: all selected tests pass.

- [x] **Step 5: Commit the domain slice**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt app/src/test/java/com/avitoohband/nutrun/ActiveWorkoutInputTest.kt app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt
git commit -m "feat: validate focused workout set input"
```

### Task 10.2: Focused Exercise Navigation and Sticky Actions

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/ActiveWorkoutContentTest.kt`

**Interfaces:**
- Produces: `ActiveWorkoutContent(model, onEditRestTimer, onCancelRequest, onFinishRequest)`.
- Consumes: existing `TrainingViewModel.activeSession`, `activeSetLogs`, `updateWorkoutSet`, and rest-timer methods.

- [x] **Step 1: Write failing Compose tests**

Test a six-exercise session at compact width and 200% font scale. Assert the header reports `Exercise 1 of 6`, only the focused exercise is expanded, Previous is disabled, Next advances without losing input, and Cancel/Finish remain displayed before and after navigation.

- [x] **Step 2: Run the test and verify RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.ActiveWorkoutContentTest' --console=plain
```

Expected: assertions fail because the focused content and stable tags do not exist.

- [x] **Step 3: Implement the focused layout**

Use a `Scaffold` inside `ActiveWorkoutContent`: compact progress/header at the top, a `LazyColumn` for the selected exercise, and a persistent bottom bar with Previous, Next, Cancel, and Finish. Key `rememberSaveable` focus by `session.id`, and clamp it when alternative selection changes the visible target list.

- [x] **Step 4: Move set rows into the extracted content**

Replace silent `emit` returns with visible supporting errors. Set `KeyboardOptions(keyboardType = KeyboardType.Decimal)` for weight, minutes, and RPE and `KeyboardType.Number` for repetitions. Add a labeled completion control and a `Copy previous` action when a matching set exists.

- [x] **Step 5: Replace the old active branch**

`TrainingScreen` should delegate to `ActiveWorkoutContent` and retain its existing dialogs and timer-finished feedback. Delete the moved `WorkoutSetRow` implementation from `MainActivity.kt`.

- [x] **Step 6: Run the connected test and verify GREEN**

Run the Step 2 command. Expected: all focused UI tests pass.

- [x] **Step 7: Commit the UI slice**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/androidTest/java/com/avitoohband/nutrun/ActiveWorkoutContentTest.kt
git commit -m "feat: add focused active workout logging"
```

### Task 10.3: Incomplete Finish Review and Regression Gate

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ActiveWorkoutContentTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

- [x] **Step 1: Add failing incomplete-finish tests**

Assert Finish opens a review dialog when any logical target is incomplete, reports completed/total targets, offers `Keep training` and `Finish anyway`, and does not save before confirmation. Assert a complete workout finishes directly.

- [x] **Step 2: Implement the review decision**

Compute completion from current `activeSetLogs` using the same logical-target rules as `finishWorkout`. Only call `model.finishWorkout()` from the direct complete path or the explicit `Finish anyway` action.

- [x] **Step 3: Run focused and full validation**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

- [x] **Step 4: Update handover and commit**

Record device, test counts, known UX limitations, and exact commit in `docs/handovers/2026-08-21-ux-ui-tasks-10-19-handover.md`, then commit only Task 10 files.

## Completion Record

The implementation follows the planned architecture without Room, REST, MCP, or training-payload schema changes. Validation passed `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, and all 47 connected instrumentation tests on `emulator-5554`. The compact-width, 200% font-scale draft-retention regression also passed after the final refinement.

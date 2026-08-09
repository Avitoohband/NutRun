# Progressive Overload Suggestions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Derive conservative next-load suggestions from recorded sets and show them before and during the next workout.

**Architecture:** Add a pure suggestion engine beside the training domain models and expose derived results through `TrainingViewModel`. Render advisory copy in existing session and exercise cards; do not add persistence or mutate program targets.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit, existing JSON-compatible training state.

## Global Constraints

- Suggestions never modify saved targets or active set values.
- Existing training JSON remains backward-compatible.
- Canonical stored weights remain kilograms.
- Metric increases are 2.5 kg; imperial increases are exactly 5 lb.

---

### Task 1: Pure Progression Engine

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ProgressionSuggestions.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/ProgressionSuggestionsTest.kt`

**Interfaces:**
- Produces: `ProgressionAction { INCREASE, KEEP, REDUCE }`
- Produces: `ProgressionSuggestion(action, currentWeightKg, suggestedWeightKg, reason)`
- Produces: `progressionSuggestion(ExerciseTarget, List<WorkoutRecord>, Boolean): ProgressionSuggestion?`

- [ ] **Step 1: Write failing tests for all progression branches**

```kotlin
assertEquals(ProgressionAction.INCREASE, progressionSuggestion(target, listOf(topRangeWorkout), true)?.action)
assertEquals(ProgressionAction.KEEP, progressionSuggestion(target, listOf(highRpeWorkout), true)?.action)
assertEquals(ProgressionAction.REDUCE, progressionSuggestion(target, missedTwice, true)?.action)
assertNull(progressionSuggestion(bodyweightTarget, history, true))
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.avitoohband.nutrun.ProgressionSuggestionsTest"`

- [ ] **Step 3: Implement deterministic latest-attempt evaluation and unit-aware rounding**

Evaluate records newest first, require complete reps/weight/RPE inputs for increase or hold advice, and require two consecutive misses before reduction. Convert the exact 5 lb increment back to canonical kilograms.

- [ ] **Step 4: Run the focused test and confirm it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.avitoohband.nutrun.ProgressionSuggestionsTest"`

### Task 2: ViewModel and Compose Integration

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Consumes: `progressionSuggestion(...)` from Task 1.
- Produces: `progressionSuggestion(ExerciseTarget): ProgressionSuggestion?`
- Produces: `progressionSuggestions(TrainingSession): List<Pair<ExerciseTarget, ProgressionSuggestion>>`.

- [ ] **Step 1: Add failing ViewModel tests for derived recalculation**

Verify suggestions update after `finishWorkout`, `updateWorkoutRecord`, `deleteWorkoutRecord`, and `updateUsesMetricUnits` without changing `ExerciseTarget.weightKg`.

- [ ] **Step 2: Expose pure derived methods from `TrainingViewModel`**

```kotlin
fun progressionSuggestion(target: ExerciseTarget): ProgressionSuggestion? =
    progressionSuggestion(target, workoutHistory, usesMetricUnits)
```

- [ ] **Step 3: Render advisory text before and during workouts**

Add concise suggestion text to Program session cards and active exercise cards with test tags. Keep Start, Finish, Cancel, set logging, and prior-performance behavior unchanged.

- [ ] **Step 4: Add Compose assertions for pre-workout and active-workout suggestions**

Use deterministic demo workout records or a test-only state fixture, then assert suggestion text is visible before Start and after entering the workout.

- [ ] **Step 5: Run full validation**

Run: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`


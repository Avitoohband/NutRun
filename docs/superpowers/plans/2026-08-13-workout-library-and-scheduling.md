# Workout Library and Weekly Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable workout library, multi-workout weekly schedule with Rest Days, 220+ built-in and user-created exercises, per-workout set controls, and profile-owned weight units.

**Architecture:** Replace weekday-bound `TrainingSession` records with reusable `WorkoutTemplate` records plus ordered `WeeklyDayPlan` assignments. Keep Room, REST, and MCP boundaries unchanged by versioning the existing training JSON, decoding legacy payloads into the new model, and persisting custom exercises in that payload. Training and reminder code consume shared schedule-domain helpers, while Profile is the sole unit-preference source.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt ViewModel, Room-backed repository flows, `org.json`, WorkManager, JUnit 4, AndroidX Compose UI Test.

## Global Constraints

- Preserve every existing built-in exercise ID and add at least 220 meaningful built-in exercises total.
- Store custom-exercise IDs and user-created workout IDs as UUID-backed stable strings.
- Store measurements canonically in kilograms, centimeters, kilometers, and milliliters.
- Profile Settings is the only editable Metric/Imperial control.
- Each weekday supports an ordered list of unique workouts or an explicit Rest Day; Rest Day and workout assignments are mutually exclusive.
- Set counts are validated at `1..20` in both UI and domain code.
- Existing Room schema, REST routes, and MCP contracts remain unchanged.
- Legacy training JSON, active workouts, history, reminder settings, and date-specific overrides must survive migration.
- Do not delete workout history when templates or schedule assignments are removed.
- Use TDD for every task: observe the targeted test fail before changing production code.
- Do not stage or modify files outside the paths named by the active task.

---

### Task 1: Reusable Workout and Weekly Schedule Domain

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/WorkoutPlanning.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/PrototypeModels.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/WorkoutPlanningTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/DefaultTrainingProgramTest.kt`

**Interfaces:**
- Produces: `WorkoutTemplate`, `WeeklyDayPlan`, `DefaultTrainingProgram`.
- Produces: `defaultTrainingProgram(exercises: List<Exercise>): DefaultTrainingProgram`.
- Produces: `templatesForDate(templates, plans, overrides, date): List<WorkoutTemplate>`.
- Produces: `replaceDayAssignments(plans, weekday, templateIds): List<WeeklyDayPlan>` and `markRestDay(plans, weekday): List<WeeklyDayPlan>`.
- Preserves: `TrainingScheduleOverride.sessionId` and `WorkoutRecord.sessionId` as compatibility field names whose values now identify templates.

- [ ] **Step 1: Write failing schedule-domain tests**

Create `WorkoutPlanningTest.kt` with these concrete cases:

```kotlin
@Test fun oneTemplateCanBeAssignedToSeveralDays() {
    val walk = WorkoutTemplate("walk", "Walk", emptyList())
    val plans = listOf(
        WeeklyDayPlan(DayOfWeek.SUNDAY, listOf("walk")),
        WeeklyDayPlan(DayOfWeek.TUESDAY, listOf("walk"))
    )
    assertEquals(listOf(walk), templatesForDate(listOf(walk), plans, emptyList(), sunday))
    assertEquals(listOf(walk), templatesForDate(listOf(walk), plans, emptyList(), tuesday))
}

@Test fun severalTemplatesPreserveAssignmentOrder() {
    val plans = replaceDayAssignments(emptyList(), DayOfWeek.MONDAY, listOf("push", "walk", "push"))
    assertEquals(listOf("push", "walk"), plans.single().templateIds)
}

@Test fun restDayClearsAssignmentsAndAssignmentClearsRestDay() {
    val resting = markRestDay(
        listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push"))),
        DayOfWeek.MONDAY
    )
    assertTrue(resting.single().isRestDay)
    assertTrue(resting.single().templateIds.isEmpty())
    val assigned = replaceDayAssignments(resting, DayOfWeek.MONDAY, listOf("push"))
    assertFalse(assigned.single().isRestDay)
}

@Test fun removingFinalAssignmentLeavesDayUnplanned() {
    val plans = replaceDayAssignments(
        listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push"))),
        DayOfWeek.MONDAY,
        emptyList()
    )
    assertFalse(plans.single().isRestDay)
    assertTrue(plans.single().templateIds.isEmpty())
}
```

Update `DefaultTrainingProgramTest` to assert one cardio template is assigned to Sunday, Tuesday, and Thursday; strength templates are assigned Monday, Wednesday, and Friday; Saturday is Rest Day.

- [ ] **Step 2: Run the domain tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.WorkoutPlanningTest --tests com.avitoohband.nutrun.DefaultTrainingProgramTest --console=plain
```

Expected: compilation fails because `WorkoutTemplate`, `WeeklyDayPlan`, and the schedule functions do not exist.

- [ ] **Step 3: Add the minimal domain types and pure functions**

Create `WorkoutPlanning.kt` with these signatures and invariants:

```kotlin
data class WorkoutTemplate(
    val id: String,
    val name: String,
    val exercises: List<ExerciseTarget> = emptyList(),
    val guidance: List<String> = emptyList()
)

data class WeeklyDayPlan(
    val weekday: DayOfWeek,
    val templateIds: List<String> = emptyList(),
    val isRestDay: Boolean = false
) {
    init { require(!isRestDay || templateIds.isEmpty()) }
}

data class DefaultTrainingProgram(
    val templates: List<WorkoutTemplate>,
    val dayPlans: List<WeeklyDayPlan>
)

fun replaceDayAssignments(
    plans: List<WeeklyDayPlan>,
    weekday: DayOfWeek,
    templateIds: List<String>
): List<WeeklyDayPlan>

fun markRestDay(
    plans: List<WeeklyDayPlan>,
    weekday: DayOfWeek
): List<WeeklyDayPlan>

fun templatesForDate(
    templates: List<WorkoutTemplate>,
    plans: List<WeeklyDayPlan>,
    overrides: List<TrainingScheduleOverride>,
    date: LocalDate
): List<WorkoutTemplate>
```

Use `distinct()` for assignment IDs, preserve template-list order from the day plan, apply existing moved/skipped overrides after recurring assignments, and ignore dangling IDs.

Move `TrainingSession.logicalTargetCount` and completion helpers to `WorkoutTemplate`. Replace `defaultSessions` with `defaultTrainingProgram`; use stable template ID `session-sunday-cardio` for the single default cardio template.

- [ ] **Step 4: Run the tests and verify GREEN**

Run the Step 2 command. Expected: both suites pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/WorkoutPlanning.kt app/src/main/java/com/avitoohband/nutrun/PrototypeModels.kt app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt app/src/test/java/com/avitoohband/nutrun/WorkoutPlanningTest.kt app/src/test/java/com/avitoohband/nutrun/DefaultTrainingProgramTest.kt
git commit -m "refactor: separate workouts from weekly schedule"
```

### Task 2: Versioned Training JSON and Legacy Migration

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/DefaultTrainingProgramTest.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/TrainingStateV2MigrationTest.kt`

**Interfaces:**
- Consumes: Task 1 domain types and `defaultTrainingProgram`.
- Produces: `PersistedTrainingState.workoutTemplates`, `.weeklyDayPlans`, `.customExercises`, and `.legacyUsesMetricUnits`.
- Produces: JSON schema version `2` with keys `workoutTemplates`, `weeklyDayPlans`, and `customExercises`.
- Preserves: `decodeTrainingState(payload, builtInExercises)` as the public decoder entry point.

- [ ] **Step 1: Write failing round-trip and migration tests**

Cover these exact assertions in `TrainingStateV2MigrationTest`:

```kotlin
@Test fun v2RoundTripPreservesTemplatesPlansAndCustomExercises() {
    val custom = Exercise(
        id = "custom-1",
        name = "My carry",
        category = "Custom",
        primaryMuscles = "Grip",
        secondaryMuscles = "",
        instructions = "Walk steadily.",
        safetyNote = ""
    )
    val template = WorkoutTemplate(
        "template-1",
        "Carry day",
        listOf(ExerciseTarget("target-1", custom, sets = 4, reps = 30))
    )
    val payload = encodeTrainingState(
        customExercises = listOf(custom),
        workoutTemplates = listOf(template),
        weeklyDayPlans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf(template.id)))
    )
    val restored = requireNotNull(decodeTrainingState(payload, builtInExerciseCatalog()))
    assertEquals(2, JSONObject(payload).getInt("schemaVersion"))
    assertEquals(custom, restored.customExercises.single())
    assertEquals(4, restored.workoutTemplates.single().exercises.single().sets)
}

@Test fun legacySessionsBecomeTemplatesAndOrderedDayPlans() {
    val restored = requireNotNull(decodeTrainingState(legacyPayload(), builtInExerciseCatalog()))
    assertEquals(listOf("legacy-push", "legacy-walk"),
        restored.weeklyDayPlans.first { it.weekday == DayOfWeek.MONDAY }.templateIds)
    assertEquals(setOf("legacy-push", "legacy-walk"), restored.workoutTemplates.map { it.id }.toSet())
}

@Test fun migrationPreservesActiveWorkoutHistoryAndOverrides() {
    val restored = requireNotNull(decodeTrainingState(legacyPayloadWithActivity(), builtInExerciseCatalog()))
    assertEquals("legacy-push", restored.activeWorkoutSessionId)
    assertEquals("legacy-push", restored.workoutHistory.single().sessionId)
    assertEquals("legacy-push", restored.scheduleOverrides.single().sessionId)
}

@Test fun danglingPlanReferencesAreDiscardedWithoutDroppingTemplates() {
    val restored = requireNotNull(decodeTrainingState(v2PayloadWithDanglingId(), builtInExerciseCatalog()))
    assertEquals(listOf("valid"), restored.weeklyDayPlans.single().templateIds)
    assertEquals("valid", restored.workoutTemplates.single().id)
}
```

- [ ] **Step 2: Run the codec suites and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingStateV2MigrationTest --tests com.avitoohband.nutrun.ProductionDomainTest --tests com.avitoohband.nutrun.DefaultTrainingProgramTest --console=plain
```

Expected: compile failures for the new persisted fields and encoder parameters.

- [ ] **Step 3: Implement schema version 2 and legacy decoding**

Change `PersistedTrainingState` to include:

```kotlin
val customExercises: List<Exercise>
val workoutTemplates: List<WorkoutTemplate>
val weeklyDayPlans: List<WeeklyDayPlan>
val legacyUsesMetricUnits: Boolean?
```

Encode custom exercises before templates. Decode custom exercises first, merge them with built-ins by stable ID, then decode targets. For schema `1` or payloads without `schemaVersion`, decode `sessions`, strip their weekday into grouped `WeeklyDayPlan` records, preserve session order inside each day, and create templates with the same IDs. For schema `2`, sanitize duplicate and dangling day-plan IDs and enforce Rest Day exclusivity.

Keep reading `usesMetricUnits` into `legacyUsesMetricUnits`; do not emit it from schema version 2.

- [ ] **Step 4: Run codec tests and verify GREEN**

Run the Step 2 command. Expected: all selected suites pass.

- [ ] **Step 5: Commit Task 2**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt app/src/test/java/com/avitoohband/nutrun/DefaultTrainingProgramTest.kt app/src/test/java/com/avitoohband/nutrun/TrainingStateV2MigrationTest.kt
git commit -m "feat: migrate training state to reusable workouts"
```

### Task 3: Expand the Built-in Exercise Catalog

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/ExerciseCatalog.kt`
- Create: `app/src/main/java/com/avitoohband/nutrun/ExerciseCatalogSeeds.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/ExerciseCatalogCoverageTest.kt`

**Interfaces:**
- Produces: `builtInExerciseCatalog(): List<Exercise>` with at least 220 unique stable entries.
- Preserves: all 81 IDs present at `origin/main`.
- Produces categories: `Free weights`, `Machine`, `Cable`, `Bodyweight`, `Calisthenics`, `Cardio`, `Mobility`, `Rehabilitation`, and `Home`.

- [ ] **Step 1: Write failing catalog-contract tests**

```kotlin
@Test fun catalogHasAtLeast220UniqueStableExercises() {
    val catalog = builtInExerciseCatalog()
    assertTrue(catalog.size >= 220)
    assertEquals(catalog.size, catalog.map(Exercise::id).distinct().size)
    assertTrue(catalog.map(Exercise::id).containsAll(existingStableIds))
}

@Test fun requestedCalisthenicsExercisesAreSearchable() {
    val catalog = builtInExerciseCatalog()
    assertTrue(filterExercises(catalog, "pike push", "All").any { it.id == "pike-push-up" })
    assertTrue(filterExercises(catalog, "pull up", "All").any { it.id == "pull-up" })
    assertTrue(filterExercises(catalog, "planche", "All").size >= 4)
}

@Test fun catalogCoversEveryApprovedCategory() {
    val counts = builtInExerciseCatalog().groupingBy(Exercise::category).eachCount()
    approvedCategories.forEach { category -> assertTrue("$category is sparse", counts.getOrDefault(category, 0) >= 10) }
}
```

Define `existingStableIds` as the exact 81-ID set from the current catalog so accidental removal is visible in review.

- [ ] **Step 2: Run catalog tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ExerciseCatalogCoverageTest --tests com.avitoohband.nutrun.ProductionDomainTest --console=plain
```

Expected: count, requested exercise, and category coverage assertions fail.

- [ ] **Step 3: Add explicit catalog seeds**

Create a private seed type in `ExerciseCatalogSeeds.kt`:

```kotlin
internal data class ExerciseSeed(
    val id: String,
    val name: String,
    val category: String,
    val primaryMuscles: String,
    val secondaryMuscles: String = "",
    val instructions: String,
    val safetyNote: String,
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val defaultWeightKg: Double? = null,
    val defaultDurationMinutes: Int? = null,
    val defaultDistanceKm: Double? = null
)
```

Add explicit, human-reviewed seeds grouped by the nine approved categories. Include at minimum `pike-push-up`, `pull-up`, `wide-grip-pull-up`, `neutral-grip-pull-up`, `tuck-planche-hold`, `advanced-tuck-planche`, `straddle-planche-progression`, and `planche-lean`. Do not create count-padding aliases that differ only by spelling. Convert seeds to `Exercise` in `builtInExerciseCatalog`, fail fast on duplicate IDs, and return a stable order.

- [ ] **Step 4: Run catalog tests and verify GREEN**

Run the Step 2 command. Expected: all selected tests pass with catalog size at least 220.

- [ ] **Step 5: Commit Task 3**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/ExerciseCatalog.kt app/src/main/java/com/avitoohband/nutrun/ExerciseCatalogSeeds.kt app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt app/src/test/java/com/avitoohband/nutrun/ExerciseCatalogCoverageTest.kt
git commit -m "feat: expand the exercise catalog"
```

### Task 4: ViewModel Workout, Schedule, and Custom Exercise Mutations

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 1-3 domain and codec interfaces.
- Produces: observable `workoutTemplates`, `weeklyDayPlans`, `customExercises`, and combined `exerciseLibrary`.
- Produces: `TrainingMutationResult` and `CustomExerciseDraft`.
- Produces ViewModel operations listed below.

- [ ] **Step 1: Write failing mutation tests**

Start with executable domain-facing tests:

```kotlin
@Test fun createWorkoutTrimsNameAndStartsEmpty() {
    val model = TrainingViewModel(null, null)
    assertEquals(TrainingMutationResult.Success, model.createWorkout("  Push B  "))
    val created = model.workoutTemplates.last()
    assertEquals("Push B", created.name)
    assertTrue(created.exercises.isEmpty())
    assertTrue(created.id.startsWith("workout-"))
}

@Test fun setCountIsStoredPerTemplateAndRejectsZeroOrTwentyOne() {
    val model = TrainingViewModel(null, null)
    val first = model.workoutTemplates.first { it.exercises.isNotEmpty() }
    val second = model.workoutTemplates.first { it.id != first.id && it.exercises.isNotEmpty() }
    val target = first.exercises.first()
    assertEquals(TrainingMutationResult.Success, model.updateTargetSets(first.id, target.id, 7))
    assertEquals(7, model.workoutTemplates.first { it.id == first.id }.exercises.first { it.id == target.id }.sets)
    assertEquals(second, model.workoutTemplates.first { it.id == second.id })
    assertTrue(model.updateTargetSets(first.id, target.id, 0) is TrainingMutationResult.ValidationError)
    assertTrue(model.updateTargetSets(first.id, target.id, 21) is TrainingMutationResult.ValidationError)
}

@Test fun customExerciseIsAddedToCatalogAndSelectedWorkout() {
    val model = TrainingViewModel(null, null)
    val template = model.workoutTemplates.first()
    val result = model.createCustomExerciseAndAdd(
        template.id,
        CustomExerciseDraft(name = "Suitcase march", primaryMuscles = "Core")
    )
    assertEquals(TrainingMutationResult.Success, result)
    val custom = model.customExercises.single { it.name == "Suitcase march" }
    assertTrue(custom.id.startsWith("exercise-"))
    assertEquals(custom.id, model.workoutTemplates.first { it.id == template.id }.exercises.last().exercise.id)
}
```

Add the remaining tests with these explicit assertions:

- Blank workout names return `ValidationError` and do not change template count.
- Replacing Monday assignments with `[push, walk, push]` stores `[push, walk]` and sets `isRestDay` false.
- Setting Monday to Rest Day clears every Monday assignment.
- Adding an exercise already present in a template returns `ValidationError` and leaves its target count unchanged.
- Deleting a template removes all day-plan references and overrides dated on or after a fixed `today`, preserves older overrides and workout-history records, and removes no custom exercises.
- Deleting the active template returns `ActiveWorkoutConflict` and leaves all state unchanged.
- Blank custom names and a second case-folded `suitcase march` return `ValidationError` without changing catalog or template targets.
- Switching `TestTrainingRuntime` from account A to B restores only B's custom exercises after B's first payload emission.
- A runtime save failure restores the exact pre-mutation templates, plans, custom exercises, overrides, selection, and active state; sets `mutationError`; and `dismissMutationError()` clears it.
- If generation N fails after generation N+1 persisted successfully, generation N's snapshot is not restored.

- [ ] **Step 2: Run ViewModel tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest --console=plain
```

Expected: compilation fails for the new state and methods.

- [ ] **Step 3: Implement mutation APIs and persistence wiring**

Add:

```kotlin
sealed interface TrainingMutationResult {
    data object Success : TrainingMutationResult
    data object NotReady : TrainingMutationResult
    data object ActiveWorkoutConflict : TrainingMutationResult
    data class ValidationError(val message: String) : TrainingMutationResult
}

data class CustomExerciseDraft(
    val name: String,
    val category: String = "Custom",
    val primaryMuscles: String = "",
    val secondaryMuscles: String = "",
    val instructions: String = "",
    val safetyNote: String = "",
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val defaultWeightKg: Double? = null,
    val defaultDurationMinutes: Int? = null,
    val defaultDistanceKm: Double? = null
)

fun createWorkout(name: String): TrainingMutationResult
fun renameWorkout(templateId: String, name: String): TrainingMutationResult
fun deleteWorkout(templateId: String, today: LocalDate = LocalDate.now()): TrainingMutationResult
fun replaceAssignments(day: DayOfWeek, templateIds: List<String>): TrainingMutationResult
fun setRestDay(day: DayOfWeek): TrainingMutationResult
fun createCustomExerciseAndAdd(templateId: String, draft: CustomExerciseDraft): TrainingMutationResult
fun updateTargetSets(templateId: String, targetId: String, sets: Int): TrainingMutationResult
```

Use `UUID.randomUUID()` IDs prefixed with `workout-`, `exercise-`, and `target-`. Validate before mutating. Delete only overrides whose `originalDate >= today`; retain history. Encode the complete v2 payload after each successful mutation and request training-reminder rescheduling through the existing persistence path.

Replace `sessions` access throughout this class with `workoutTemplates`; retain compatibility aliases only temporarily inside Task 4 and remove them before commit.
Add a private `TrainingMutationSnapshot` containing templates, plans, custom exercises, overrides, selection, and active state. Capture it immediately before each mutation and pass it to persistence. If `saveTrainingState` fails and no newer persistence generation exists, restore that snapshot and expose the repository message through:

```kotlin
var mutationError by mutableStateOf<String?>(null)
    private set
fun dismissMutationError() { mutationError = null }
```

A newer successful mutation must not be rolled back by an older failed generation. Test both failure ordering and dismissal.


- [ ] **Step 4: Run ViewModel tests and verify GREEN**

Run the Step 2 command. Expected: all `TrainingViewModelTest` tests pass.

- [ ] **Step 5: Commit Task 4**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt
git commit -m "feat: manage reusable workouts and custom exercises"
```

### Task 5: Make Profile the Unit Preference Owner

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/NutRunRepository.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Produces: `NutRunRepository.profileEntity(userId: String): Flow<UserProfileEntity?>`.
- Extends: `TrainingViewModelRuntime.profile(userId: String): Flow<UserProfileEntity?>`.
- Produces: `profileUnitReadyAccountId` and read-only `usesMetricUnits` derived from `UserProfileEntity.unitSystem`.
- Removes: `TrainingViewModel.updateUsesMetricUnits` and all non-Profile `WeightUnitSelector` calls.

- [ ] **Step 1: Write failing account and UI tests**

Add ViewModel tests proving:

```kotlin
@Test fun profileUnitOverridesLegacyTrainingUnit() {
    assertFalse(resolveMetricUnits(profileEntity(unitSystem = UnitSystem.IMPERIAL.name), true))
    assertTrue(resolveMetricUnits(profileEntity(unitSystem = UnitSystem.METRIC.name), false))
}

@Test fun missingProfileFallsBackToLegacyUnitAfterFirstNullEmission() {
    assertFalse(resolveMetricUnits(null, false))
    assertTrue(resolveMetricUnits(null, true))
    assertTrue(resolveMetricUnits(null, null))
}
```

Extend `TestTrainingRuntime` with `MutableSharedFlow<UserProfileEntity?>` per account and add tests that assert:

- A target stored at canonical `60.0` remains exactly `60.0` before and after emitting an Imperial profile; only `displayWeight` changes.
- After switching from account A to B, `trainingMutationsReady` remains false after B's training payload arrives and becomes true only after B's first profile emission.
- A late A profile emission cannot change B's unit or readiness state.
- A second profile emission changing Metric to Imperial updates `usesMetricUnits` immediately without writing a training payload.

Add Compose assertions that `Kilograms (kg)` and `Pounds (lb)` do not appear in Training, active workout, Progress, workout details, or history editing, while they remain in Edit Health Details. Verify a Profile unit save changes a `60 kg` training/history display to its pound display.

- [ ] **Step 2: Run targeted tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest --tests com.avitoohband.nutrun.ProductionDomainTest assembleDebugAndroidTest --console=plain
```

Expected: missing runtime profile contract and stale unit selectors fail the tests.

- [ ] **Step 3: Observe account-scoped profile units**

Add repository and runtime flows. Combine each account's first training-state and profile emissions into one restoration snapshot. Set `profileUnitReadyAccountId` after the first profile emission, including null. Resolve units with:

```kotlin
private fun resolveMetricUnits(profile: UserProfileEntity?, legacyMetric: Boolean?): Boolean =
    profile?.unitSystem?.let { UnitSystem.valueOf(it) == UnitSystem.METRIC }
        ?: legacyMetric
        ?: true
```

Require both `restoredUserId` and `profileUnitReadyAccountId` to equal the active account before allowing mutations. Remove training-level unit mutation and stop encoding it in v2 payloads.

Remove `WeightUnitSelector` calls from Training and Progress flows. Keep conversions at display/input boundaries using the ViewModel's profile-derived read-only value.

- [ ] **Step 4: Run JVM and Compose tests and verify GREEN**

Run the Step 2 command, then:

```powershell
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.ProductionFlowTest' --console=plain
```

Expected: selected JVM and connected tests pass.

- [ ] **Step 5: Commit Task 5**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/data/NutRunRepository.kt app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt
git commit -m "fix: own weight units in profile settings"
```

### Task 6: Resolve Training Reminders from Weekly Plans

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/reminders/TrainingReminderWorker.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingReminderTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`

**Interfaces:**
- Consumes: `templatesForDate` from Task 1 and v2 decoder from Task 2.
- Produces: `trainingReminderNames(state, date): List<String>` as a pure helper.
- Preserves: the two unique WorkManager jobs and delivery-ledger behavior.

- [ ] **Step 1: Write failing reminder tests**

```kotlin
@Test fun reminderListsEveryAssignedWorkoutInPlanOrder() {
    val state = persistedState(
        templates = listOf(
            WorkoutTemplate("push", "Push", emptyList()),
            WorkoutTemplate("walk", "Walk", emptyList())
        ),
        plans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push", "walk")))
    )
    assertEquals(listOf("Push", "Walk"), trainingReminderNames(state, monday))
}

@Test fun explicitRestDayAndUnplannedDayProduceNoNames() {
    val resting = persistedState(plans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, isRestDay = true)))
    assertTrue(trainingReminderNames(resting, monday).isEmpty())
    assertTrue(trainingReminderNames(persistedState(), tuesday).isEmpty())
}
```

Add exact assertions that the same template assigned to Monday and Thursday appears on both dates, moved and skipped overrides affect only their targeted occurrence, and each successful template deletion or day-assignment mutation records one training scheduling attempt for the active account. Assert ordinary scheduling failure creates only `ReminderSystem.TRAINING` recovery and does not roll back the persisted mutation.

- [ ] **Step 2: Run reminder tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingReminderTest --tests com.avitoohband.nutrun.TrainingViewModelTest --console=plain
```

Expected: reminder worker still reads legacy session weekdays.

- [ ] **Step 3: Switch reminders to weekly plans**

Decode v2/legacy payloads, call `templatesForDate`, map to names in plan order, and skip notification and ledger writes when the result is empty. Ensure template edits, assignments, Rest Day changes, deletion, and date overrides all route through persistence with `ReminderSystem.TRAINING` rescheduling.

- [ ] **Step 4: Run reminder tests and verify GREEN**

Run the Step 2 command. Expected: both suites pass.

- [ ] **Step 5: Commit Task 6**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/reminders/TrainingReminderWorker.kt app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt app/src/test/java/com/avitoohband/nutrun/TrainingReminderTest.kt app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt
git commit -m "fix: schedule reminders from weekly workout plans"
```

### Task 7: Weekly Schedule and Workout Library UI

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/TrainingPlanningContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Consumes: Task 4 ViewModel state and mutation APIs.
- Produces: `TrainingPlanningContent(model, onOpenTemplate)`.
- Produces stable test tags: `weekly-schedule`, `day-plan-<DAY>`, `assign-day-<DAY>`, `rest-day-<DAY>`, `workout-library`, `create-workout`, `workout-card-<id>`, `edit-workout-<id>`, and `delete-workout-<id>`.

- [ ] **Step 1: Write failing Compose tests**

Create a `TrainingPlanningComposeTest` class. Start with a structural test:

```kotlin
@Test fun trainingSeparatesWeeklyScheduleFromWorkoutLibrary() {
    val model = TrainingViewModel(null, null)
    composeRule.setContent { MaterialTheme { TrainingScreen(model) } }
    composeRule.onNodeWithTag("weekly-schedule").assertIsDisplayed()
    composeRule.onNodeWithTag("workout-library").assertIsDisplayed()
}
```

Add exact UI assertions that:

- The current local weekday row displays `Today` without reordering the week.
- Selecting two template IDs for one day renders both in saved order, and reopening the dialog can remove only one.
- Choosing Rest Day clears assignments; assigning a workout later clears Rest Day.
- Tapping an assigned workout opens details with exercise summaries plus Start and Edit actions.
- A newly created empty workout appears in the library with Start disabled.
- Delete opens confirmation; accepting removes the template while an existing history card remains.
- Delete is disabled for the template referenced by the active workout.

- [ ] **Step 2: Compile instrumentation tests and verify RED**

```powershell
.\gradlew.bat assembleDebugAndroidTest --console=plain
```

Expected: missing planning composables and test tags fail compilation/assertions.

- [ ] **Step 3: Build the weekly schedule section**

Create `TrainingPlanningContent.kt` with unframed full-width sections. Each weekday row shows `Today`, ordered assigned workout actions, `Rest Day`, or `Unplanned`. Use an icon button for assignment editing with a tooltip/content description. The assignment dialog uses checkboxes and saves the selected template IDs atomically through `replaceAssignments`. Selecting an assigned workout opens `WorkoutTemplateDetailsDialog`, which lists its exercise summaries and exposes Start and Edit actions without starting implicitly.

- [ ] **Step 4: Build the workout library section**

Show reusable cards with name, exercise count, assigned weekday abbreviations, Start, Edit, and Delete commands. Put Create Workout in the section header. Disable Start for empty templates. Confirm deletion and surface `ActiveWorkoutConflict` without mutating state.

- [ ] **Step 5: Run connected planning tests and verify GREEN**

```powershell
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.TrainingPlanningComposeTest' --console=plain
```

Expected: all planning UI tests pass.

- [ ] **Step 6: Commit Task 7**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/TrainingPlanningContent.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt
git commit -m "feat: separate weekly plans from workout library"
```

### Task 8: Workout Editor, Set Controls, and Quick Custom Exercise

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/WorkoutEditorContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`

**Interfaces:**
- Consumes: `CustomExerciseDraft`, combined `exerciseLibrary`, and Task 4 mutations.
- Produces: `WorkoutEditorContent(model, templateId, onDone)`.
- Produces stable tags: `workout-editor`, `target-sets-<id>`, `remove-target-<id>`, `exercise-search`, `exercise-category-<name>`, `create-custom-exercise`, `custom-exercise-name`, `custom-exercise-details`, and `save-custom-exercise`.

- [ ] **Step 1: Write failing editor tests**

Create a `WorkoutEditorComposeTest` class. Start with the required quick-create flow:

```kotlin
@Test fun customExerciseCanSaveWithOnlyANameAndIsAddedImmediately() {
    openWorkoutEditor()
    composeRule.onNodeWithTag("create-custom-exercise").performClick()
    composeRule.onNodeWithTag("custom-exercise-name").performTextInput("Suitcase march")
    composeRule.onNodeWithTag("save-custom-exercise").performClick()
    composeRule.onNodeWithText("Suitcase march").assertIsDisplayed()
    assertEquals("Suitcase march", model.workoutTemplates.single { it.id == templateId }.exercises.last().exercise.name)
}
```

Add exact UI and state assertions that:

- Minus, plus, and numeric input change only the selected target and enforce `1..20`.
- Removing a target requires confirmation and affects only this workout.
- Search matches name, category, primary muscle, and secondary muscle case-insensitively.
- Renaming trims the name; blank input shows an inline error without closing the editor.
- Exercises already included in the workout are disabled in catalog results.
- Optional category, muscles, instructions, safety, sets, reps, weight, duration, and distance persist and become searchable where applicable.
- A case-insensitive duplicate custom name keeps the dialog open and displays the repository validation message.
- `Pike pull-up`, planche progressions, and the newly seeded catalog entries render in search results.
- Custom weights entered in pounds round-trip to canonical kilograms, while duration and distance defaults keep their canonical values.

- [ ] **Step 2: Compile/run focused tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest assembleDebugAndroidTest --console=plain
```

Expected: editor composable/tags are absent and custom creation assertions fail.

- [ ] **Step 3: Implement current-workout editing and set controls**

Render an editable workout name and the current targets first. Use minus/plus icon buttons and a numeric field constrained to `1..20`; keep dimensions stable so values do not shift the row. Confirm removal. Keep existing reps, weight, duration, distance, and guidance editing, but do not add a unit selector.

- [ ] **Step 4: Implement catalog and quick custom form**

Reuse `filterExercises` across combined built-in/custom data. Add category filter chips. Show required name immediately and category, muscles, instructions, safety note, default sets/reps, canonical weight, duration, and distance fields under an expandable `Details` control. Convert displayed pounds to canonical kilograms before constructing `CustomExerciseDraft`; display `ValidationError.message` inline and close only on `Success`. Saving must add the new custom exercise to the catalog and current template in one persisted mutation.

- [ ] **Step 5: Run focused JVM and connected tests and verify GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest --tests com.avitoohband.nutrun.ExerciseCatalogCoverageTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.WorkoutEditorComposeTest' --console=plain
```

Expected: selected JVM and editor UI tests pass.

- [ ] **Step 6: Commit Task 8**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/WorkoutEditorContent.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt
git commit -m "feat: edit workouts and create custom exercises"
```

### Task 9: Backward Compatibility, Regression Validation, and Issue Delivery

**Files:**
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingStateV2MigrationTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ReminderRescheduleReceiverProductionTest.kt`
- Modify: `docs/superpowers/specs/2026-08-13-workout-library-and-scheduling-design.md`

**Interfaces:**
- Verifies all prior task contracts together.
- Produces no new product API.

- [ ] **Step 1: Add end-to-end legacy-account regression coverage**

Add a fixture using the pre-v2 JSON shape with customized sessions, an active workout, history, schedule overrides, and legacy Imperial units. Restore it through the production runtime, emit an Imperial profile, then assert templates, day assignments, active sets, history, reminders, and pound displays all survive. Mutate one assignment and assert the persisted payload has `schemaVersion = 2` and no `usesMetricUnits` key.

- [ ] **Step 2: Run focused migration and production flows**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingStateV2MigrationTest --tests com.avitoohband.nutrun.DefaultTrainingProgramTest --tests com.avitoohband.nutrun.TrainingReminderTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.ProductionFlowTest,com.avitoohband.nutrun.ReminderRescheduleReceiverProductionTest' --console=plain
```

Expected: all focused compatibility tests pass.

- [ ] **Step 3: Run fresh full validation**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest --rerun-tasks --console=plain
git diff --check
```

Expected: every Gradle command reports `BUILD SUCCESSFUL`, connected tests have zero failures and zero skips, and `git diff --check` prints nothing.

- [ ] **Step 4: Review acceptance criteria against the approved design**

Record exact evidence in the task report for: catalog count and IDs, custom exercise persistence, set counts, unit ownership, weekly multi-assignment, Rest Day, deletion/history behavior, reminder behavior, legacy migration, and all validation commands. Correct any missed criterion with a new failing test before changing production code.

- [ ] **Step 5: Mark the design implemented and commit final test adjustments**

Change the design status from `Approved` to `Implemented`, then:

```powershell
git add app/src/test/java/com/avitoohband/nutrun/TrainingStateV2MigrationTest.kt app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt app/src/androidTest/java/com/avitoohband/nutrun/ReminderRescheduleReceiverProductionTest.kt docs/superpowers/specs/2026-08-13-workout-library-and-scheduling-design.md
git commit -m "test: validate reusable workout delivery"
```

- [ ] **Step 6: Push the branch and update issue #2**

```powershell
git push -u origin codex/issue-2-workout-library
```

Post the commit range and validation counts to GitHub issue #2. Keep the issue open until the branch is merged into `main`.

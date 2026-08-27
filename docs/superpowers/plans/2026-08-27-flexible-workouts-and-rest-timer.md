# Flexible Workouts and Rest Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users reorder saved-workout exercises, modify an active workout without silently changing its source program, start unscheduled Quick workouts, and keep the rest timer visible in-app and in Android notifications.

**Architecture:** Replace the active workout's live reference to `WorkoutTemplate` with a schema-v3 `ActiveWorkoutSession` snapshot. Saved-workout ordering remains list-based, while active additions and skips mutate only the snapshot; finishing always records history before offering an explicit save-back action. A sticky Compose timer reads the snapshot's persisted end time, and an account-scoped notification coordinator displays a system chronometer and schedules a validated WorkManager completion fallback.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room-backed training JSON, Hilt, WorkManager, Android notifications, JUnit 4, AndroidX Compose UI tests.

## Approved English Requirements

The original Hebrew issues translate to:

- Users must be able to change the order of exercises in a program without deleting and re-adding them.
- During a workout, users must be able to add an exercise and mark another exercise as not relevant.
- Users may start a workout without a program and add exercises while training.
- The rest timer should stay visible wherever the user has scrolled on the active-workout page and should also appear in device notifications.

## Approved Product Decisions

- Saved-workout exercises use a long-press drag handle, plus accessible **Move up** and **Move down** actions.
- Active-workout edits apply only to the current session.
- After finishing a sourced workout, **Save changes to workout** includes added exercises and final order and removes skipped exercises.
- A skipped exercise remains visible, can be restored with **Undo**, and is recorded as skipped in history.
- Completed set data is retained if an exercise is later skipped; history labels it as partially completed, then skipped.
- **Quick workout** asks for an optional name, starts with no exercises, and offers **Add first exercise**.
- Quick workouts always create history but are added to the workout library only through **Save as workout**.
- The in-app rest timer is a compact sticky bar below the workout header with remaining time, **+30s**, and **Skip**.
- Android shows an ongoing system-chronometer countdown and a WorkManager-backed **Rest complete** fallback.
- Do not add a foreground service, `SCHEDULE_EXACT_ALARM`, or another exact-alarm permission.

## Global Constraints

- Preserve account isolation, List/Grid mode, set drafts, progression calculations, and existing workout history.
- Schema-v1 and schema-v2 payloads must continue decoding; schema-v2 active workouts must migrate without losing completed sets.
- Active UI must never read its exercise list from a mutable source template.
- Skipped logical targets count as resolved for finish-review purposes but not as completed.
- For an alternative group, skipping one target leaves its alternatives available; skipping every target resolves the group as skipped.
- New target, active-session, workout-record, and work-request IDs must be collision-safe and account-scoped where relevant.
- Notification work must verify user ID, active-session ID, and exact timer end before alerting.
- WorkManager completion is best-effort after process death and may be delayed by Android power management.
- Keep imports at the top of each file.
- Every Kotlin `when` over a union or enum must have exhaustive handling with a `never`-equivalent failure branch when applicable.
- Run Gradle and connected tests sequentially; do not build concurrently from Android Studio.
- Do not commit generated APKs, AABs, local properties, credentials, or notification test artifacts.
- Do not commit or push implementation changes unless Avi explicitly requests it.

---

## Task 1: Reorder exercises in saved workouts

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/WorkoutPlanning.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/WorkoutEditorContent.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/WorkoutPlanningTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  fun moveExerciseGroup(
      targets: List<ExerciseTarget>,
      targetId: String,
      destinationGroupIndex: Int
  ): List<ExerciseTarget>
  ```
- Consumes: existing `WorkoutTemplate.exercises` list ordering and `TrainingViewModel.saveWorkoutDraft(...)`.

- [ ] **Step 1: Write pure ordering tests**

Add tests proving that:

```kotlin
@Test
fun moveExerciseGroupMovesASingleTargetWithoutChangingIdsOrValues() {
    val result = moveExerciseGroup(listOf(first, second, third), third.id, 0)
    assertEquals(listOf(third.id, first.id, second.id), result.map(ExerciseTarget::id))
}

@Test
fun moveExerciseGroupMovesAlternativeTargetsAsOneLogicalSlot() {
    val result = moveExerciseGroup(
        listOf(strength, walkAlternative, swimAlternative, cooldown),
        swimAlternative.id,
        0
    )
    assertEquals(
        listOf(walkAlternative.id, swimAlternative.id, strength.id, cooldown.id),
        result.map(ExerciseTarget::id)
    )
}

@Test
fun moveExerciseGroupRejectsUnknownTargetAndOutOfRangeDestination() {
    assertEquals(targets, moveExerciseGroup(targets, "missing", 0))
    assertEquals(targets, moveExerciseGroup(targets, targets.first().id, -1))
    assertEquals(targets, moveExerciseGroup(targets, targets.first().id, 99))
}
```

- [ ] **Step 2: Run the pure tests and verify the missing helper fails**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.WorkoutPlanningTest" \
  --no-daemon --console=plain
```

Expected: compilation fails because `moveExerciseGroup` is not defined.

- [ ] **Step 3: Implement logical grouping and movement**

Treat each target without `alternativeGroupId` as one group. Collect every target sharing a non-null `alternativeGroupId` into one stable group at its first occurrence, move that group, then flatten:

```kotlin
fun moveExerciseGroup(
    targets: List<ExerciseTarget>,
    targetId: String,
    destinationGroupIndex: Int
): List<ExerciseTarget> {
    val groups = targets.toLogicalExerciseGroups()
    val source = groups.indexOfFirst { group -> group.any { it.id == targetId } }
    if (source == -1 || destinationGroupIndex !in groups.indices || source == destinationGroupIndex) {
        return targets
    }
    return groups.toMutableList().apply {
        add(destinationGroupIndex, removeAt(source))
    }.flatten()
}
```

Keep the grouping helper private unless another production component requires it.

- [ ] **Step 4: Add reorder controls to the workout editor**

In `WorkoutEditorContent`:

- render current targets through logical groups;
- add a drag handle with stable tag `workout-reorder-<targetId>`;
- track dragged group and destination using `LazyListState.layoutInfo`;
- call `moveExerciseGroup` whenever the dragged group's center crosses another group;
- expose semantics actions named **Move up** and **Move down**;
- disable impossible moves;
- leave the existing dirty check and `saveWorkoutDraft` call unchanged.

The visible order must update immediately, but persistence happens only when the user taps **Save**.

- [ ] **Step 5: Add Compose ordering coverage**

Add instrumentation assertions for:

- Move down followed by Save persists the visible order.
- Reopening the editor shows the saved order.
- Cancel/Back discards the reordered draft.
- An alternative group moves as one slot.
- Accessibility Move up/down actions produce the same order as drag.

- [ ] **Step 6: Run focused verification**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.WorkoutPlanningTest" \
  --no-daemon --console=plain
./gradlew assembleDebugAndroidTest --no-daemon --console=plain
adb shell am instrument -w \
  -e class com.avitoohband.nutrun.ProductionFlowTest \
  com.avitoohband.nutrun.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: pure ordering and editor persistence tests pass.

---

## Task 2: Introduce the schema-v3 active-workout snapshot

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutSession.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingStateV2MigrationTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class ActiveWorkoutSession(
      val id: String,
      val sourceTemplateId: String?,
      val name: String,
      val exercises: List<ExerciseTarget>,
      val guidance: List<String>,
      val skippedTargetIds: Set<String>,
      val completedTargetIds: Set<String>,
      val setLogs: Map<String, List<WorkoutSetLog>>,
      val startedAtMillis: Long,
      val restTimerEndAtMillis: Long?
  )
  ```
- Produces schema-v3 JSON field `activeWorkout`.
- Consumes schema-v2 fields `activeWorkoutSessionId`, `completedExerciseIds`, `activeSetLogs`, and `activeWorkoutStartedAtMillis` only during migration.

- [ ] **Step 1: Write active-snapshot invariant tests**

Cover constructor sanitization through a factory or decode boundary:

- exercise IDs and target IDs are unique;
- skipped/completed IDs not present in `exercises` are discarded;
- skipped wins over completed for display/status;
- set logs not belonging to a target are discarded;
- `restTimerEndAtMillis` may be null or later than `startedAtMillis`;
- changing a source `WorkoutTemplate` after start does not alter `ActiveWorkoutSession.exercises`.

- [ ] **Step 2: Write schema-v3 round-trip and schema-v2 migration tests**

The round trip must preserve:

- nullable source-template ID;
- exact exercise order and targets;
- skip/completion sets;
- partial set logs;
- start and timer timestamps.

The v2 fixture must decode into a snapshot using the referenced template:

```kotlin
assertEquals(3, JSONObject(reencoded).getInt("schemaVersion"))
assertEquals(v2ActiveTemplate.exercises, restored.activeWorkout?.exercises)
assertEquals(v2SetLogs, restored.activeWorkout?.setLogs)
assertEquals(v2StartedAt, restored.activeWorkout?.startedAtMillis)
```

If a v2 active template is missing or empty, decode no active workout while retaining templates and history.

- [ ] **Step 3: Run migration tests and confirm failure**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.TrainingStateV2MigrationTest" \
  --no-daemon --console=plain
```

Expected: tests fail because `ActiveWorkoutSession` and `activeWorkout` do not exist.

- [ ] **Step 4: Add the active-session model and codec**

Update `PersistedTrainingState` to carry:

```kotlin
val activeWorkout: ActiveWorkoutSession?
```

Write schema version `3` and serialize all active state under one `activeWorkout` object. Do not emit the four legacy active-state fields for new payloads.

Decoder behavior:

- schema 3: decode and sanitize `activeWorkout`;
- schema 2: construct a snapshot from the matching template and legacy fields;
- schema 1: keep existing session-to-template migration, then apply the same snapshot migration if an active session exists;
- malformed active state: clear only active state, not the rest of the account payload.

- [ ] **Step 5: Refactor ViewModel ownership**

Replace the scattered mutable active fields with:

```kotlin
var activeWorkout by mutableStateOf<ActiveWorkoutSession?>(null)
    private set
```

Retain temporary read-only compatibility accessors where they reduce churn:

```kotlin
val activeWorkoutSessionId: String? get() = activeWorkout?.id
val activeWorkoutStartedAtMillis: Long? get() = activeWorkout?.startedAtMillis
val restTimerEndAtMillis: Long? get() = activeWorkout?.restTimerEndAtMillis
```

Make `activeSession()` map only from `activeWorkout`, never from `workoutTemplates`.

- [ ] **Step 6: Verify source-template isolation and restoration**

Add ViewModel tests that start a workout, mutate/replace the source template payload, and assert:

```kotlin
assertEquals(startedSnapshot, model.activeSession()?.exercises)
assertNotEquals(model.workoutTemplates.single().exercises, model.activeSession()?.exercises)
```

Also verify account switching clears account A's active state before restoring account B.

- [ ] **Step 7: Run focused JVM tests**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.TrainingStateV2MigrationTest" \
  --tests "com.avitoohband.nutrun.TrainingViewModelTest" \
  --no-daemon --console=plain
```

Expected: schema round-trip, migration, restoration, and isolation tests pass without leaking test coroutines.

---

## Task 3: Add active exercise changes and Quick workouts

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutActions.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/PrototypeModels.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingPlanningContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ActiveWorkoutContentTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  fun startQuickWorkout(name: String?): TrainingMutationResult
  fun addExerciseToActiveWorkout(exerciseId: String): TrainingMutationResult
  fun skipActiveExercise(targetId: String): TrainingMutationResult
  fun restoreSkippedActiveExercise(targetId: String): TrainingMutationResult
  fun saveLastWorkoutAsTemplate(name: String): TrainingMutationResult
  fun applyLastWorkoutToSource(): TrainingMutationResult
  ```
- Produces:
  ```kotlin
  data class SkippedWorkoutExercise(
      val targetId: String,
      val exerciseId: String,
      val exerciseName: String,
      val completedSetCount: Int
  )
  ```
- Extends `WorkoutRecord` with `skippedExercises: List<SkippedWorkoutExercise> = emptyList()`.

- [ ] **Step 1: Write ViewModel behavior tests**

Add failing tests for:

- saved workout start snapshots source ID, name, guidance, and exercises;
- Quick workout trims the optional name and defaults blank to `Quick workout`;
- Quick workout starts with an empty exercise list;
- adding an exercise appends one fresh `ExerciseTarget` and initializes its set logs;
- adding the same `Exercise.id` twice is rejected without mutation;
- skipping keeps the target in order and marks it resolved;
- undo restores the target and its pre-skip set/completion state;
- partial set logs survive skip and finish;
- finishing history includes `SkippedWorkoutExercise`;
- a skipped target does not count as completed;
- all alternatives skipped resolve one logical group;
- save-back includes active non-skipped targets and excludes skipped targets;
- save-back fails safely if the source template disappeared, while **Save as workout** remains available.

- [ ] **Step 2: Run behavior tests and confirm failure**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.TrainingViewModelTest" \
  --no-daemon --console=plain
```

Expected: new mutation APIs and skipped-history fields are missing.

- [ ] **Step 3: Implement active mutation APIs**

Each mutation must:

1. require `trainingMutationsReady`;
2. validate the active account/session;
3. derive a new immutable `ActiveWorkoutSession`;
4. persist with rollback through the existing mutation pipeline;
5. leave `workoutTemplates` untouched.

`addExerciseToActiveWorkout` must use canonical library metadata and a fresh target ID. `skipActiveExercise` must not delete set logs.

- [ ] **Step 4: Extend history and finish calculations**

When finishing:

- calculate completed, skipped, and unresolved logical target counts separately;
- retain every completed set in `WorkoutRecord.sets`;
- serialize skipped exercise identity/name in `skippedExercises`;
- save history first;
- retain a non-persisted post-finish template draft in `WorkoutSummary` for the optional save action.

Extend `WorkoutSummary` with:

```kotlin
val skippedExercises: Int
val sourceTemplateId: String?
val reusableExercises: List<ExerciseTarget>
val guidance: List<String>
```

For a sourced workout, `sessionId` in history remains the source template ID so progression keeps linking to the correct program. For a Quick workout, use the active-session ID.

- [ ] **Step 5: Add active-workout action UI**

Create focused composables:

- `ActiveWorkoutActions`;
- `ActiveExercisePicker`;
- `SkippedExerciseCard`.

Required behavior and tags:

- `active-add-exercise` opens the searchable catalog;
- `active-add-<exerciseId>` adds and focuses the new target;
- `active-skip-<targetId>` marks the current target skipped and advances to the next available target;
- `active-undo-skip-<targetId>` restores it;
- skipped cards are collapsed but remain reachable through Previous/Next;
- an empty Quick workout renders `quick-workout-empty` and `quick-workout-add-first`.

List/Grid set entry remains unchanged for non-skipped targets.

- [ ] **Step 6: Add Quick workout entry**

In the workout-library header, add `quick-workout` beside `create-workout`. Show a naming dialog:

- title: `Start Quick workout`;
- optional name field;
- primary action: `Start`;
- blank name becomes `Quick workout`;
- starting navigates directly to `ActiveWorkoutContent`.

Do not create a `WorkoutTemplate` at start.

- [ ] **Step 7: Add post-finish save actions**

For a sourced workout with session changes:

- show `save-changes-to-workout`;
- call `applyLastWorkoutToSource`;
- use active non-skipped exercises in final order;
- keep existing template name and guidance.

For Quick workouts:

- show `save-quick-workout`;
- request/confirm a library name;
- create a user-owned unassigned template;
- do not change history if template save fails.

- [ ] **Step 8: Add Compose and end-to-end tests**

Cover:

- Quick workout starts empty and accepts its first exercise;
- active addition does not change source template;
- skip remains visible, advances focus, and can be undone;
- partial-then-skipped copy appears in completion history;
- finish review treats skipped targets as resolved;
- save-back mutates only after explicit confirmation;
- Quick workout can finish without being saved to the library;
- List/Grid switching still retains drafts after an active exercise is added.

- [ ] **Step 9: Run focused verification**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.TrainingViewModelTest" \
  --no-daemon --console=plain
./gradlew assembleDebugAndroidTest --no-daemon --console=plain
adb shell am instrument -w \
  -e class com.avitoohband.nutrun.ActiveWorkoutContentTest,com.avitoohband.nutrun.ProductionFlowTest \
  com.avitoohband.nutrun.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: active mutation, Quick workout, history, and UI tests pass.

---

## Task 4: Make the in-app rest timer sticky and persistent

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutRestTimer.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ActiveWorkoutContentTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Consumes: `ActiveWorkoutSession.restTimerEndAtMillis`.
- Produces:
  ```kotlin
  @Composable
  internal fun ActiveWorkoutRestTimer(
      endAtMillis: Long,
      nowMillis: Long,
      onAddTime: () -> Unit,
      onSkip: () -> Unit
  )
  ```

- [ ] **Step 1: Write timer-state tests**

Test:

- completing a previously incomplete set starts the default timer;
- completing the same set again does not restart it;
- `addRestTime(30)` updates and persists the snapshot end by exactly 30 seconds;
- `skipRestTimer()` persists null;
- finish and cancel clear the timer;
- restored active state resumes a future timer;
- restored expired state clears the timer and does not restart it.

- [ ] **Step 2: Run timer-state tests and confirm failure**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.TrainingViewModelTest.configurableRestTimerUsesTheSavedDefault" \
  --no-daemon --console=plain
```

Expected: persistence assertions fail because the current timer end is not encoded.

- [ ] **Step 3: Make the snapshot the timer source**

Update `startRestTimer`, `addRestTime`, and `skipRestTimer` through immutable snapshot copies and persist each transition. Remove duplicate standalone timer ownership.

On restore:

```kotlin
val restoredTimerEnd = activeWorkout.restTimerEndAtMillis
    ?.takeIf { it > System.currentTimeMillis() }
```

Do not restart a full-duration timer after process recreation.

- [ ] **Step 4: Extract and place the sticky timer**

Move timer rendering out of the `LazyColumn`. Render `ActiveWorkoutRestTimer` inside the `Scaffold.topBar` column after workout progress, only while the end timestamp is non-null.

Required tags and semantics:

- root: `active-rest-timer-sticky`;
- time: `active-rest-timer-time`;
- add: `active-rest-timer-add`;
- skip: `active-rest-timer-skip`;
- state description: `Rest time remaining, <minutes and seconds>`.

Keep one Compose clock for elapsed workout and rest display. `MainActivity` remains responsible for foreground completion feedback until Task 5 centralizes notification side effects.

- [ ] **Step 5: Add sticky UI coverage**

Compose test:

1. start a rest timer;
2. scroll the active exercise content to the end;
3. assert `active-rest-timer-sticky` is still displayed;
4. press **+30s** and verify the displayed end increases;
5. press **Skip** and verify the bar disappears.

Also verify no sticky bar when the timer is inactive.

- [ ] **Step 6: Run focused verification**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.TrainingViewModelTest" \
  --no-daemon --console=plain
./gradlew assembleDebugAndroidTest --no-daemon --console=plain
adb shell am instrument -w \
  -e class com.avitoohband.nutrun.ActiveWorkoutContentTest,com.avitoohband.nutrun.ProductionFlowTest \
  com.avitoohband.nutrun.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: timer persistence, expiry, and sticky visibility tests pass.

---

## Task 5: Add live countdown and validated completion notifications

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/reminders/RestTimerNotificationCoordinator.kt`
- Create: `app/src/main/java/com/avitoohband/nutrun/reminders/RestTimerCompletionWorker.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/RestTimerNotificationTest.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  interface RestTimerNotifier {
      fun schedule(userId: String, activeWorkoutId: String, endAtMillis: Long)
      fun cancel(userId: String)
  }
  ```
- Extends `TrainingViewModelRuntime`:
  ```kotlin
  fun scheduleRestTimer(userId: String, activeWorkoutId: String, endAtMillis: Long)
  fun cancelRestTimer(userId: String)
  ```
- Work name: `rest-timer:<userId>`.
- Notification IDs: one stable ongoing ID and one stable completion ID.

- [ ] **Step 1: Write stale-work validation tests**

Extract a pure decision:

```kotlin
fun shouldDeliverRestTimerCompletion(
    expectedUserId: String,
    expectedActiveWorkoutId: String,
    expectedEndAtMillis: Long,
    currentUserId: String?,
    currentActiveWorkout: ActiveWorkoutSession?,
    nowMillis: Long
): Boolean
```

Test false for:

- signed-out state;
- another account;
- another active workout;
- skipped/null timer;
- extended/replaced end time;
- work executing before the expected end.

Test true only for an exact account/session/end match at or after expiry.

- [ ] **Step 2: Implement the live notification coordinator**

Create two channels:

- `rest_timer_active_v1`: low importance, no sound, no vibration;
- reuse or centralize `rest_timer_finished_v1`: high importance with current completion behavior.

Build the ongoing notification:

```kotlin
NotificationCompat.Builder(context, ACTIVE_CHANNEL)
    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
    .setContentTitle("Rest timer")
    .setContentText("Return to your active workout")
    .setWhen(endAtMillis)
    .setUsesChronometer(true)
    .setChronometerCountDown(true)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setContentIntent(activeWorkoutPendingIntent)
```

The pending intent opens `MainActivity` with destination `training` and existing `CLEAR_TOP | SINGLE_TOP` handling.

If `POST_NOTIFICATIONS` is denied, skip posting but retain in-app timing and scheduling state.

- [ ] **Step 3: Schedule unique completion work**

Enqueue `RestTimerCompletionWorker` with:

```kotlin
workDataOf(
    KEY_USER_ID to userId,
    KEY_ACTIVE_WORKOUT_ID to activeWorkoutId,
    KEY_END_AT_MILLIS to endAtMillis
)
```

Use `ExistingWorkPolicy.REPLACE` and delay:

```kotlin
(endAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
```

`cancel(userId)` cancels `rest-timer:<userId>` and both notification IDs.

- [ ] **Step 4: Implement worker verification and delivery**

The worker:

1. reads current `AppPreferences` session;
2. reads the account's `TrainingStateEntity`;
3. decodes schema-v3 state;
4. calls `shouldDeliverRestTimerCompletion`;
5. exits success without a notification if validation fails;
6. cancels the ongoing notification and posts **Rest complete** if validation succeeds.

Do not include project IDs, account IDs, workout IDs, or exercise names in notification text.

- [ ] **Step 5: Connect ViewModel timer lifecycle**

After the corresponding state save is requested:

- start/replacement/add time -> `scheduleRestTimer`;
- skip -> `cancelRestTimer`;
- finish/cancel -> `cancelRestTimer`;
- account switch/sign-out -> cancel the previous account before restoring the new account.

Fake runtimes record schedules/cancellations for deterministic unit tests.

The existing foreground `LaunchedEffect` remains the exact in-app expiry path. It must cancel pending work after clearing the timer so a later worker cannot duplicate the alert.

- [ ] **Step 6: Add Android notification tests**

Using `NotificationManager`/instrumentation assertions, verify:

- active channel exists at low importance;
- posted notification is ongoing and uses countdown chronometer metadata;
- content intent routes to training;
- replacing a timer keeps one ongoing notification;
- skip removes it;
- completion channel remains high importance;
- denied permission does not break active timer state;
- stale work for account A does not notify while account B is active.

- [ ] **Step 7: Run focused verification**

```bash
./gradlew testDebugUnitTest \
  --tests "com.avitoohband.nutrun.TrainingViewModelTest" \
  --no-daemon --console=plain
./gradlew assembleDebugAndroidTest --no-daemon --console=plain
adb shell am instrument -w \
  -e class com.avitoohband.nutrun.RestTimerNotificationTest,com.avitoohband.nutrun.ProductionFlowTest \
  com.avitoohband.nutrun.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: notification metadata, cancellation, fallback, and account-boundary tests pass.

---

## Task 6: Complete the feature stabilization gate

**Files:**
- Modify: `docs/testing/task-21-stabilization-acceptance.md`
- Modify: `docs/superpowers/plans/2026-08-26-internal-play-release-readiness.md`

**Produces:**
- Current regression evidence for flexible workouts and rest timers.
- An explicit prerequisite before Internal Play Release Readiness Task 3.

- [ ] **Step 1: Run backend and Android static gates sequentially**

```bash
cd backend
npm ci
npm test
cd ..
./gradlew testDebugUnitTest --no-daemon --console=plain
./gradlew lintDebug --no-daemon --console=plain
./gradlew assembleDebug --no-daemon --console=plain
./gradlew assembleDebugAndroidTest --no-daemon --console=plain
git diff --check
```

Expected: backend tests, all JVM tests, lint, APK assembly, androidTest assembly, and whitespace checks pass.

- [ ] **Step 2: Apply Windows lock recovery only if confirmed**

If Gradle reports a stale generated-file lock and Android Studio is not building:

```powershell
.\gradlew.bat --stop
taskkill /F /IM java.exe
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue
```

Run recovery once, then rerun only the failed command. Do not classify product test failures as lock infrastructure.

- [ ] **Step 3: Run one connected suite**

```bash
export ANDROID_SERIAL=emulator-5554
./gradlew connectedDebugAndroidTest --no-daemon --console=plain
```

If the emulator reports `keyDispatchingTimedOut`, cold boot once and rerun the affected class. Record both the infrastructure failure and passing rerun.

- [ ] **Step 4: Perform the manual smoke matrix**

Verify:

- reorder with drag and TalkBack actions;
- save/discard order behavior;
- start sourced workout and add an exercise;
- skip, undo, and partial-then-skip;
- finish and save changes back;
- finish without save-back;
- Quick workout with and without library save;
- List/Grid draft retention after active addition;
- sticky timer after scrolling;
- +30 seconds and Skip;
- background/foreground countdown;
- process recreation with active timer;
- notification deep link;
- notification denied behavior;
- account switch with pending timer;
- finish/cancel clears notification and work.

- [ ] **Step 5: Record evidence and release-plan ordering**

Update `task-21-stabilization-acceptance.md` with:

- date;
- commit;
- emulator/device and Android version;
- commands and pass counts;
- any reruns;
- WorkManager delay limitation;
- remaining physical-device checks.

Add a prerequisite note before Task 3 of the release-readiness plan:

```markdown
## Flexible workout prerequisite

Complete `2026-08-27-flexible-workouts-and-rest-timer.md` and its stabilization
gate before resuming Task 3. Tasks 3-9 remain unchanged.
```

- [ ] **Step 6: Review before any implementation commit**

Check:

```bash
git status --short
git diff --check
git diff --stat
```

Confirm no credentials, generated binaries, `local.properties`, `backend/node_modules`, APKs, or AABs are staged. Commit and push implementation only after Avi explicitly requests it.

## Completion Boundary

This feature package is complete when:

- saved workout order can be changed and persists;
- active exercise additions/skips remain session-local;
- sourced and Quick workout save actions behave as approved;
- history distinguishes completed, skipped, and incomplete targets;
- active state and timer survive process recreation;
- the timer remains visible while scrolling;
- the live notification and stale-work protections pass;
- the full repository and connected-test gates are green.

Afterward, resume Task 3 of `2026-08-26-internal-play-release-readiness.md`.

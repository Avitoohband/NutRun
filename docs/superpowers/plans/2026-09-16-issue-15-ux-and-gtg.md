# Issue 15 UX and GTG Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Validate the delivered flexible-workout UX, repair confirmed regressions, and add independent Grease the Groove practice tracking.

**Architecture:** Keep existing schema-v3 active sessions and template editing. Add focused GTG model/codec/analytics/UI files, coordinated by the existing account-scoped training writer. Extend training JSON to v4; reuse Room's existing training-state row and sync envelope.

**Tech Stack:** Kotlin, Jetpack Compose, Room 7, JSON training payload, WorkManager, JUnit, Compose instrumentation, Gradle 8.9.

**Spec:** [Issue 15 design and data rules](../specs/2026-09-16-issue-15-ux-and-gtg.md).

**Implementation status (2026-09-16):** Core GTG planning, logging, persistence, Today, Progress review/edit/delete, schema-v4 compatibility, downgrade protection, payload limits, and confirmed acceptance regressions are implemented on `codex/issue-15-gtg`. Automated gates are green. Physical-device checks and the broader client walkthrough remain pending.

## Global constraints

- Base reviewed: `main` at `4ab7d01`. Recheck HEAD and worktree status before execution.
- One writer for production files, especially `MainActivity.kt` and `TrainingViewModel.kt`.
- Read the [fresh acceptance report](../../testing/2026-09-16-client-acceptance.md), not only historical validation documents.
- Do not duplicate the reorder/add/skip/Quick workout/timer implementation already on main.
- No existing templates, history, supplements, units, or active sessions may be reseeded on upgrade.
- GTG is Grease the Groove; it is separate practice, not an automatically prescribed program.
- Canonical weight unit is kg; Profile owns display units everywhere.
- Emulator evidence cannot prove physical vibration, audible volume, or real-world battery behavior. Those checks remain pending at Avi's request.
- Run Gradle builds and connected tests sequentially. Commit only when asked; never close a partly verified issue.

## Step 1: Restore a trustworthy acceptance gate

**Files:** `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`, `BottomNavAccessibilityTest.kt`, `AccessibilityResponsiveTest.kt`, `ActiveWorkoutContentTest.kt`; corresponding production UI only for demonstrated defects.

**Consumes:** Fresh failing test names and evidence in the September audit.
**Produces:** Deterministic test startup, reachable compact-layout controls, and a passing gate or explicit remaining defects.

- [x] Reproduce each reported failure alone before changing code. Distinguish app loading/auth transitions from actual missing controls.
- [x] In app-hosted tests, wait for resolved session state and a known login/home screen. Ensure sign-out completes before `enterDemo`.
- [x] For compact workout tests, scroll to a set control before asserting it is displayed when scrolling is the intended UX.
- [x] Re-run the affected classes, then the complete suite. Initial failures remain recorded in the acceptance report; final suite is 140/140.

Commands (PowerShell, repository root):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
# Process-local workaround for this PC's Unix-domain socket failure:
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\NutRun-UnixSockets-Unavailable'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.ProductionFlowTest,com.avitoohband.nutrun.BottomNavAccessibilityTest,com.avitoohband.nutrun.AccessibilityResponsiveTest' --console=plain
```

## Step 2: Close flexible-workout correctness gaps

**Files:** `ActiveWorkoutSession.kt`, `ActiveWorkoutContent.kt`, `TrainingViewModel.kt`, `WorkoutPlanning.kt`, `ActiveWorkoutActions.kt` under `app/src/main/java/com/avitoohband/nutrun/`.
**Tests:** `ActiveWorkoutSessionTest.kt`, `TrainingViewModelTest.kt`, `WorkoutPlanningTest.kt`, `ActiveWorkoutContentTest.kt`, `ProductionFlowTest.kt`.

**Consumes:** schema-v3 `ActiveWorkoutSession`, `moveExerciseGroup`, `skipActiveExercise`, `finishWorkout`.
**Produces:** Stable one-of groups and correct finish review for mixed skipped/incomplete/completed targets.

- [x] Add a regression for independent targets where skipped exercises must not suppress incomplete review.
- [x] Base incomplete review on remaining non-skipped logical targets: compare `completedLogicalTargetCount()` with `logicalTargetCount()`.
- [x] Test alternative groups and all-skipped/mixed states so each group counts once.
- [ ] Test reorder up/down boundaries and alternative-group moves without changing IDs, set values, or logged history. Cancel leaves stored ordering untouched; Save survives restart.
- [ ] Test add/skip/undo during an active session, switching exercises, app restart, and concurrent persistence failure. Source templates remain unchanged until explicit post-finish Update original/Save as new.
- [ ] Test Quick workout: empty state, add catalog exercise, finish, history details, optional save as a reusable workout, Cancel without creating history.

Core predicate regression (add to the existing fixture-based suite):

```kotlin
val changed = active.copy(skippedTargetIds = active.exercises.take(2).map { it.id }.toSet())
assertEquals(1, changed.logicalTargetCount())
assertEquals(0, changed.completedLogicalTargetCount())
assertTrue(changed.completedLogicalTargetCount() < changed.logicalTargetCount())
```

## Step 3: Verify timer ownership and lifecycle

**Files:** `ActiveWorkoutRestTimer.kt`, `MainActivity.kt`, `TrainingViewModel.kt`, `reminders/RestTimerNotificationCoordinator.kt`, `reminders/RestTimerCompletionWorker.kt`.
**Tests:** `RestTimerNotificationTest.kt`, `ActiveWorkoutSessionTest.kt`, plus new `RestTimerLifecycleTest.kt` under androidTest.

**Consumes:** `(userId, activeWorkoutId, endAtMillis)` timer identity.
**Produces:** One completion signal per timer, no stale alerts, visible and reopenable notification.

- [ ] Test sticky countdown during scroll and keyboard entry, +30 seconds replacing scheduled work, and Skip cancelling both countdown and pending completion.
- [ ] Instrument background expiry and notification tap back to the same active workout. Check both notification permissions granted and denied.
- [ ] Test foreground and worker expiry racing. Returning after worker delivery must not replay the same alert. If reproduced, store a delivery claim keyed by timer identity and share it between both paths.
- [ ] Test finish/cancel/logout/account switch/timer replacement/process restart. Old jobs cannot alert for a different account or restarted session.
- [ ] Inspect WorkManager delay behavior honestly: current design is best-effort, not an exact alarm. Do not add exact-alarm permissions or a foreground service without a separate product decision.
- [ ] Leave physical sound/vibration/locked-phone timing pending; provide steps in the acceptance report.

## Step 4: Add GTG domain and codec

**Create:** `GtgModels.kt`, `GtgStateCodec.kt`, `GtgAnalytics.kt` in the existing main package.
**Modify:** `TrainingStateCodec.kt`.
**Tests:** new JVM `GtgModelsTest.kt`, `GtgStateCodecTest.kt`, `GtgAnalyticsTest.kt`; extend `TrainingStateV2MigrationTest.kt`.

**Produces these interfaces:**

```kotlin
enum class GtgMeasure { REPS, HOLD_SECONDS }
data class GtgPlan(
    val id: String, val exerciseId: String, val exerciseName: String,
    val measure: GtgMeasure, val targetPerSet: Int, val dailySetGoal: Int,
    val weekdays: Set<java.time.DayOfWeek>, val weightKg: Double? = null,
    val archived: Boolean = false
)
data class GtgLog(
    val id: String, val planId: String, val exerciseId: String,
    val exerciseName: String, val measure: GtgMeasure,
    val reps: Int?, val durationSeconds: Int?, val weightKg: Double?,
    val rir: Int?, val notes: String, val performedAtMillis: Long,
    val zoneId: String, val performedOn: java.time.LocalDate,
    val dailySetGoalSnapshot: Int
)
data class GtgState(val plans: List<GtgPlan> = emptyList(), val logs: List<GtgLog> = emptyList())
data class GtgDayTotals(val sets: Int, val reps: Int, val holdSeconds: Int)
fun gtgDayTotals(logs: List<GtgLog>, planId: String, date: java.time.LocalDate): GtgDayTotals
fun encodeGtgState(state: GtgState): org.json.JSONObject
fun decodeGtgState(root: org.json.JSONObject): GtgState
```

- [x] Add model validation for numeric bounds, finite weights, mutually exclusive reps/duration, identifiers, weekdays, zones, and notes.
- [x] Add v1/v2/v3 compatibility and v4 round-trip coverage; reject unsupported future schemas without reseeding.
- [x] Use stored `performedOn` for historical totals and deduplicate repeated log IDs.
- [x] Keep GTG aggregates separate from normal workout analytics.

Example aggregate test:

```kotlin
val date = java.time.LocalDate.of(2026, 9, 16)
val first = GtgLog("log-1", "plan-1", "pull-up", "Pull up", GtgMeasure.REPS,
    5, null, null, 3, "", 1_789_560_000_000, "Asia/Jerusalem", date, 5)
val second = first.copy(id = "log-2", reps = 4)
assertEquals(GtgDayTotals(2, 9, 0), gtgDayTotals(listOf(first, second), "plan-1", date))
assertEquals(GtgDayTotals(1, 5, 0), gtgDayTotals(listOf(first, first), "plan-1", date))
```

## Step 5: Persist GTG through the existing account writer

**Modify:** `TrainingViewModel.kt`, `TrainingStateCodec.kt`, relevant serialization/sync tests. Read `data/NutRunRepository.kt` and backend sync before changing contracts.
**Tests:** extend `TrainingViewModelTest.kt`, `data/NutRunRepositoryAccountScopeTest.kt`; add instrumentation coverage in `GtgPersistenceTest.kt`.

**Consumes:** `GtgState` from Step 4 and existing `TrainingMutationResult`.
**Produces:** `saveGtgPlan(plan: GtgPlan)`, `archiveGtgPlan(planId: String)`, `saveGtgLog(log: GtgLog)`, `deleteGtgLog(logId: String)` returning `TrainingMutationResult`, plus observable `gtgState`.

- [x] Include GTG in mutation snapshots, restore, reset, rollback, and every encode call, including supplement-only saves.
- [x] Validate the active account/payload state and use the existing serialized persistence path.
- [ ] Test failed save rollback, rapid consecutive logs, duplicate submission, supplement edit followed by GTG log, account switch during save, sign-out/sign-in, process restart, and account deletion.
- [ ] Confirm demo persistence never calls cloud sync. Prove real-account sync and export retain the v4 fields; do not claim this from a demo test.
- [x] Block backend schema downgrades that could strip v4 fields and cover the policy with backend tests.
- [x] Enforce a 900 KiB UTF-8 payload ceiling locally and on the backend with an actionable error; never truncate records.

## Step 6: Add GTG creation, practice logging, and Today entry points

**Create:** `GtgContent.kt`, `GtgEditorContent.kt`, `GtgLogContent.kt`.
**Modify:** `TrainingPlanningContent.kt`, `TodayContent.kt`, `MainActivity.kt` (wiring only).
**Tests:** new `GtgContentTest.kt` and `GtgFlowTest.kt` under androidTest.

- [x] Add Training's GTG mode with empty/create state, active/archived plans, and catalog/custom exercise selection.
- [x] Provide weekday selection, reps/hold mode, bounded inputs, and Profile-derived weight units stored canonically in kg.
- [x] Add GTG set logging with reps/hold seconds, optional load, RIR, notes, validation, and stable IDs.
- [x] Show only scheduled plans on Today and update daily progress from durable state.
- [ ] Test plan creation, off-day logging, archive/unarchive, edit/delete log, cancel, validation errors, keyboard layout, 320dp width, large text, accessibility semantics, and restarting the app.
- [ ] Start a normal workout, log GTG from another tab, return: set drafts, focused exercise, elapsed time, and active rest timer must be unchanged.

## Step 7: Add GTG progress and handover

**Create:** `GtgProgressContent.kt`; tests `GtgProgressContentTest.kt`.
**Modify:** `ProgressContent.kt`, `docs/plans/README.md`, this plan and the acceptance report.

- [x] Use existing Progress ranges and show sets, reps or hold duration, and days practiced without combining seconds and reps.
- [x] Open a plan and day to review/edit/delete logs and recalculate totals; archived-plan history remains visible.
- [ ] Test no data, one entry, mixed modes, range boundaries, unit conversion, archived plans, and recalculation after edit/delete. Confirm normal training charts are unchanged.
- [x] Run JVM tests, lint, assembleDebug, assembleDebugAndroidTest, backend tests, and connected instrumentation. See the acceptance report and implementation handover for counts.
- [ ] Complete the issue #15 walkthrough: reorder/save/restart; add/skip/undo; Quick workout/history; sticky/notification timer; GTG create/log/progress/restart/account isolation.
- [x] Update the handover with branch/HEAD, touched files, tests, remaining manual checks, and next action. Keep #15 open for client/physical acceptance.

## Execution order and handover

Steps 1-3 precede GTG implementation. Step 4 is the shared interface boundary; Steps 5-6 must not run concurrently against `TrainingViewModel.kt`. Step 7 follows persisted logging.

The September audit is the source for which failures are confirmed versus suspected. Planning is complete when this file, its spec, and the acceptance report agree; implementation checkboxes remain unchecked until actual execution.

# Issue #2 Workout Library Handover

## Final Handover Completion (2026-08-21)

**Status:** Tasks 1-9 are implemented and delivered to `main`. GitHub issue #2 is closed as completed. Issue #5 remains open for customer acceptance testing and must not be closed by engineering validation alone.

**Final additions:** Task 7 now has connected interaction coverage for Rest Day replacement, weekday assignment persistence, workout details, separate Edit/Start behavior, empty-workout start prevention, confirmed deletion, and retained history. Task 8 editor coverage now includes blank and duplicate name errors, case-insensitive muscle search, session-only exercise removal, quick custom exercise creation, and isolated set controls.

**Validation record:** See `docs/testing/task-5-9-validation.md`. The complete JVM, lint, APK, Android-test APK, and connected-emulator results are recorded there.

**Post-delivery UX audit:** Tasks 10-19 are documented in `docs/plans/04-ux-ui-improvement-backlog.md`, indexed at `docs/superpowers/plans/2026-08-21-ux-ui-task-index.md`, and handed over in `docs/handovers/2026-08-21-ux-ui-tasks-10-19-handover.md`. Task 10 is complete and validated; Tasks 11-19 remain planned, and customer acceptance remains tracked in issue #5.

**Manual-testing note:** Direct Windows UI automation could not initialize because the desktop helper failed its sandbox ACL setup twice. The outstanding Task 7 operator flows were executed against `emulator-5554` through connected Compose instrumentation using the actual controls and stable UI tags. Customer acceptance remains tracked separately in issue #5.

**Repository state:** The final test tags, interaction tests, handover, and validation record are included in the closing verification commit pushed to `main`.

## Task 8 Start (2026-08-21)

**Scope:** Replace the legacy exercise picker with `WorkoutEditorContent`: editable workout name, target set controls, confirmed target removal, searchable/category-filtered combined catalog, and quick custom-exercise creation. Persist using the existing canonical `TrainingViewModel` mutations; profile remains the single unit-preference owner.

**Baseline:** Task 7 and its validation report were pushed to `main` at `4505c9b`. `ProductionFlowTest` passed 9/9 on `emulator-5554`; debug app installed and launched.

**Files:** create `WorkoutEditorContent.kt`; modify `MainActivity.kt`, `ProductionFlowTest.kt`, and only add ViewModel APIs where UI cannot reuse existing validated mutations.

**Validation:** add focused editor Compose coverage, run related JVM tests, `assembleDebug`, install debug APK, and run the focused editor test plus `ProductionFlowTest` on `emulator-5554` before committing/pushing Task 8.

## Task 7 Checkpoint (2026-08-20)

**Implemented:** Created `TrainingPlanningContent.kt` and wired the inactive Training screen to the canonical `workoutTemplates` and `weeklyDayPlans` model. The UI now has calendar-ordered weekday rows, Today indication, ordered assignment controls, Rest Day controls, a reusable workout library, create/delete controls, and a workout-details dialog with Start/Edit actions. Active workout logging, rest timers, cancellation, and completion behavior are unchanged.

**Tests:** Added `TrainingPlanningComposeTest.weeklyScheduleAndWorkoutLibraryUseCalendarOrder`. It was first run red (missing planner UI), then passed on `emulator-5554` after implementation with:

```powershell
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.TrainingPlanningComposeTest' --console=plain
```

**Completed:** Interaction coverage now verifies Rest Day replacement and assignment persistence, details/Edit without an implicit start, empty-workout start prevention, active-workout delete conflict, and history preservation after confirmed deletion. The connected test evidence is recorded in `docs/testing/task-5-9-validation.md`.

## Original Handover Context

**Status at creation:** Implementation was in progress. This historical context is retained for traceability; the final status is recorded at the top of this file.

**Goal:** Finish the reusable workout library, multi-workout weekly schedule, Rest Days, custom exercises, profile-owned units, weekly-plan reminders, and the related Compose UI without losing existing training data.

**Approved design:** `docs/superpowers/specs/2026-08-13-workout-library-and-scheduling-design.md`

**Approved implementation plan:** `docs/superpowers/plans/2026-08-13-workout-library-and-scheduling.md`

## Start Here

The active feature worktree is the only safe place to resume this work:

```powershell
Set-Location 'C:\Users\Avi_OP_PC\Documents\FitnessApp\.issue-2-worktree'
git branch --show-current
git status --short --branch
git log --oneline -5
```

Expected branch: `codex/issue-2-workout-library`.

Do not work in `C:\Users\Avi_OP_PC\Documents\FitnessApp`. That checkout is an empty/unborn `master` worktree and is not the feature branch.

There are deliberately preserved, uncommitted Task 4 changes in these files:

- `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- `app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt`
- `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`

Do not use `git reset`, `git checkout`, `git restore`, or delete these changes. They are a partial implementation of Task 4 fix round 1. Inspect and complete or amend them, then commit a coherent fix.

The SDD execution record and detailed prior reports are local, ignored artifacts:

- `.superpowers/sdd/2026-08-13-workout-library-and-scheduling/progress.md`
- `.superpowers/sdd/2026-08-13-workout-library-and-scheduling/task-4-report.md`

Keep these files local and untracked. Do not remove unrelated, already tracked `.superpowers` files belonging to older work.

## Current Branch State

The branch was created from `origin/main` commit `d99cf9b`. The completed feature commits are:

| Commit | Purpose |
| --- | --- |
| `bb28a1b` | Reusable workout and weekly schedule domain |
| `7a1005a` | UUID-backed custom workout IDs and canonical day plans |
| `e226d03` | Training-state v2 migration |
| `2420797` | Canonical v2 restore/save, typed custom IDs, lossless history |
| `f92aeac` | Compatibility mutation persistence bridge |
| `83490d7` | Initial catalog expansion |
| `504f0cf`, `767fa2a`, `efbfad4`, `9b92c1e` | Catalog ID and metadata corrections |
| `d3af292` | Canonical workout/schedule/custom-exercise mutation API |

Tasks 1 through 3 are complete and independently reviewed. Task 4 is committed but has an open fix round. Tasks 5 through 9 have not started.

Before editing, establish the actual partial-fix baseline:

```powershell
git diff --check
git diff --stat
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest --tests com.avitoohband.nutrun.TrainingStateV2MigrationTest --console=plain
```

The last interrupted agent reported `58/62` focused tests passing and four remaining coordination or validation failures. Treat the current diff and test output as authoritative over that count. The partial code is not yet review-ready and may include incomplete edits.

If Gradle cannot find the Android SDK, create an untracked `local.properties` containing the machine's existing `sdk.dir` value. Do not commit it.

## Completed Architecture

- Canonical persisted training state is schema v2: `workoutTemplates`, `weeklyDayPlans`, and `customExercises`.
- Built-ins have 225 audited, stable entries. All 81 historical built-in IDs remain present.
- User-created IDs use stable typed forms: `workout-<UUID>`, `exercise-<UUID>`, and `target-<UUID>`.
- `WeeklyDayPlan` stores ordered unique template IDs or an explicit Rest Day; the two states are mutually exclusive.
- Existing history, active-workout state, set logs, timers, supplements, and future/current schedule overrides survive migration.
- `TrainingViewModel.sessions` is now a read-only compatibility projection only. It is not canonical state and must never be used as a persistence source.

## Resume Task 4: Make Mutations Safe

Task 4 is the immediate blocker. Finish this before starting Tasks 5 through 8.

### Open Fix Round 1

The independent review found these direct correctness issues in commit `d3af292`:

1. A new authoritative payload for the same account can arrive while an older mutation is still saving. If the older save then fails, its rollback snapshot can overwrite the newer payload.
2. `CustomExerciseDraft` accepts invalid numeric defaults, including negative values and non-finite floating point values. Payload encoding happens outside rollback handling, so an encoding exception can leave an optimistic mutation in memory.
3. `addExerciseToSelectedSession` can accept an exercise object not present in the canonical `exerciseLibrary`. The saved ID then vanishes on decode, or reloads with different metadata after an ID collision.
4. Decoder lookup must not resolve target exercises from stale typed custom exercises supplied by a caller but absent from the payload. This is a minor finding, but fix it while editing the codec.

The prior agent began these changes but exhausted its tool quota. The current uncommitted diff includes partial work for generation invalidation, numeric checks, canonical exercise lookup, payload try/catch, and new tests. Read it carefully; retain correct portions and repair incomplete ones.

### Required Behavior

Implement and test all of the following:

- When a distinct same-account payload is accepted, invalidate older pending mutation rollback eligibility. Do not let an older save failure restore its snapshot over the newly restored payload or set a stale `mutationError`.
- Keep the ViewModel's own save echo from being mistaken for an external authoritative payload. Use explicit generation/payload ownership rather than timing assumptions.
- Correct the existing stale-generation test: prove generation N+1 has actually persisted successfully before N fails. A test that merely mutates N+1 while N holds the mutex is insufficient.
- Validate custom defaults before mutation:
  - `defaultWeightKg`: `null` or finite and `>= 0`.
  - `defaultDurationMinutes`: `null` or `> 0`.
  - `defaultDistanceKm`: `null` or finite and `> 0`.
  - `defaultSets`: `1..20`.
  - `defaultReps`: `>= 1`.
- Invalid defaults must return `TrainingMutationResult.ValidationError`, make no state change, and make no save attempt.
- Build the JSON payload inside the protected persistence failure path. An encoding exception must restore the exact mutation snapshot and expose a dismissible `mutationError`.
- Exercise-add methods must look up the exercise ID in `exerciseLibrary`, reject unknown IDs, and insert the canonical library object instead of trusting caller-supplied metadata.
- Add save/decode/reload tests proving an unknown exercise cannot be persisted and an altered caller object with a valid ID reloads with the canonical metadata.
- In `decodeTrainingState`, resolve template targets only from built-ins plus custom exercises decoded from that payload. Never use stale caller-supplied typed custom exercises absent from the payload.

### Task 4 Test Gate

Start with focused red tests, then run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.TrainingViewModelTest --tests com.avitoohband.nutrun.TrainingStateV2MigrationTest --tests com.avitoohband.nutrun.ProductionDomainTest --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
git diff --check
```

Commit only after focused and full JVM suites are green. Suggested commit subject:

```powershell
git add app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt app/src/main/java/com/avitoohband/nutrun/TrainingStateCodec.kt app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt app/src/test/java/com/avitoohband/nutrun/TrainingStateV2MigrationTest.kt
git commit -m "fix: harden workout mutation persistence"
```

A fresh reviewer must then inspect the exact `d3af292..HEAD` fix range before Task 4 is marked complete.

### Known Projection Boundary

The read-only `sessions` projection is inherently lossy: a template can be assigned to multiple weekdays or to none, while the legacy `TrainingSession` shape represents at most one weekday. Do not attempt to make this projection canonical.

This branch is intentionally not merge-ready until these later tasks migrate the remaining consumers:

- Task 6 must make `TrainingReminderWorker` decode v2 state and derive names with `templatesForDate`, rather than read session weekdays.
- Task 7 must make the Training screen render `workoutTemplates` and `weeklyDayPlans`, including unassigned templates, rather than use `sessions` as the workout-library source.

Do not change `TrainingReminderWorker.kt` or `MainActivity.kt` as part of Task 4's direct fix. Those files are owned by Tasks 6 and 7.

## Remaining Delivery Sequence

### Task 5: Profile Owns Units

Files: `NutRunRepository.kt`, `TrainingViewModel.kt`, `TrainingStateCodec.kt`, `MainActivity.kt`, relevant JVM and Compose tests.

Implement `profileEntity(userId): Flow<UserProfileEntity?>` and a matching runtime flow. `usesMetricUnits` must be read-only and derived from the active account's profile. Wait for both that account's first training payload and first profile emission before allowing mutations. Remove training-level unit mutation and every non-Profile unit selector. Store weights canonically in kilograms; only presentation changes when the profile unit changes.

### Task 6: Weekly-Plan Training Reminders

Files: `TrainingReminderWorker.kt`, `TrainingViewModel.kt`, `TrainingReminderTest.kt`, `TrainingViewModelTest.kt`.

Add pure `trainingReminderNames(state, date): List<String>`. Use `templatesForDate` so multiple templates, multi-day template assignments, Rest Days, unplanned days, and date overrides work correctly. Keep the two unique WorkManager jobs and delivery ledger. Each successful schedule mutation must request training reminder rescheduling; scheduling failure must create training recovery without rolling back saved data.

### Task 7: Weekly Schedule and Workout Library UI

Files: new `TrainingPlanningContent.kt`, `MainActivity.kt`, `ProductionFlowTest.kt`.

Replace the session-oriented Training presentation with two sections:

- Weekly schedule: every weekday, Today marker, ordered assignments, Rest Day or Unplanned, atomic assignment dialog, and workout detail dialog with Start/Edit.
- Workout library: all reusable templates including unassigned ones, assigned-day abbreviations, Create, Start, Edit, Delete, empty-workout Start disabled, and active-workout delete conflict.

Use the stable test tags specified in the approved plan. This task resolves the UI side of the lossy compatibility projection.

### Task 8: Workout Editor and Quick Custom Exercise UI

Files: new `WorkoutEditorContent.kt`, `MainActivity.kt`, `TrainingViewModel.kt`, `ProductionFlowTest.kt`, `TrainingViewModelTest.kt`.

Add editable workout name, current target list, confirmed removal, set stepper/numeric input constrained to `1..20`, catalog search/category filters, duplicate disablement, and an expandable custom-exercise form. Name is required; category, muscles, instructions, safety, and defaults are optional. Save must add the custom exercise and target in one persisted mutation. Do not add a per-screen kg/lb selector; Profile owns units.

### Task 9: Final Compatibility and Delivery

Add production-level legacy-account regression coverage, run all unit/instrumentation/lint/assemble validation, perform a final branch review, mark the design Implemented, then push `codex/issue-2-workout-library` and update GitHub issue #2 only after all gates pass.

## Safe Parallelization

Actual feature implementation is mostly serial. The remaining tasks share high-conflict files and state contracts:

| Work | Parallel code changes? | Reason |
| --- | --- | --- |
| Finish Task 4 | No | Preserved partial patch touches the canonical ViewModel and codec. |
| Task 5 vs Task 6 | No | Both modify `TrainingViewModel.kt` and its tests. |
| Task 7 vs Task 8 | No | Both modify `MainActivity.kt` and `ProductionFlowTest.kt`. |
| Task 9 | No | It validates the integrated product. |
| Review after a committed task | Yes | Use a read-only reviewer in another session or worktree. |
| Documentation/report preparation | Yes | Keep it untracked and do not edit production files. |

For another session, use one writer at a time on this branch. Parallel agents are useful as independent reviewers after each task commit; they should receive the exact commit range, the task brief, the task report, and the global constraints. Do not run two Gradle test suites concurrently in the same worktree because Gradle test-worker and output locks have already caused misleading failures here.

If a separate implementation worktree is necessary, create it from a committed clean checkpoint, not from this partial Task 4 state. The owner of the active worktree must finish and commit Task 4 first.

## Review and Commit Discipline

For every remaining task:

1. Read the task section in the approved plan and its predecessor interfaces.
2. Add focused failing tests first and record the red command/output in the local task report.
3. Implement only the task's named files unless a reviewer-approved safety fix requires a narrowly documented boundary expansion.
4. Run focused tests, then one full JVM suite. Run instrumentation only for Tasks 5, 7, 8, and 9 as specified.
5. Commit the task in a focused commit.
6. Have a fresh, read-only reviewer inspect the exact commit range for spec compliance, data loss, regressions, tests, and unnecessary complexity.
7. Resolve Important/Critical findings before moving to the next task. Record minor deferrals in the SDD ledger.

Never commit `local.properties`, `.superpowers/sdd/2026-08-13-workout-library-and-scheduling/`, generated test output, or unrelated user changes.

## Current Resume Attempt (2026-08-20)

**Active task:** Task 4 persistence hardening only. This attempt will first reproduce the focused ViewModel and codec failures, then complete the four already documented fixes: authoritative same-account payload invalidation, custom-draft numeric validation, encoding-failure rollback, and canonical exercise lookup. It will not implement Tasks 5-9 or change the reminder worker/UI projection.

**Preserved starting state:** The three existing uncommitted Task 4 files are intentionally retained. Inspect and amend them; do not reset or recreate the branch from `origin/main`.

**Planned validation:** Run the focused `TrainingViewModelTest`, `TrainingStateV2MigrationTest`, and `ProductionDomainTest` suite after every behavioral change, followed by the full JVM suite and `git diff --check` before committing a focused Task 4 fix.

## Task 4 Resume Result (2026-08-20)

Task 4 is locally complete and ready for its focused commit. The persisted mutation path is serialized again, authoritative same-account Room payloads invalidate stale rollback work, invalid custom defaults are rejected, encoding failures restore the exact mutation snapshot, and exercise adds use only canonical current-library data. A new test also covers the no-selected-workout boundary.

Validation passed: focused `TrainingViewModelTest`, `TrainingStateV2MigrationTest`, and `ProductionDomainTest`; then full `testDebugUnitTest`; and `git diff --check`.

**Next:** commit only the three Task 4 files, request a read-only review of that commit, then begin Task 5. Keep this handover document uncommitted unless the user specifically requests documentation to be committed.

## Task 5 Start (2026-08-20)

**Scope:** Make Profile Settings the only owner of kg/lb preference. Training must observe the account-scoped `UserProfileEntity` flow, wait for both training and profile first emissions before mutations, retain legacy unit fallback only for profiles that are absent, and remove unit selectors outside Profile.

**Files in scope:** `NutRunRepository.kt`, `TrainingViewModel.kt`, `TrainingStateCodec.kt`, `MainActivity.kt`, `TrainingViewModelTest.kt`, `ProductionDomainTest.kt`, and `ProductionFlowTest.kt`.

**Order:** Add failing profile/readiness and Compose selector tests; add repository/runtime profile flow and account gating; remove training-owned unit writing; then remove the Training/Progress UI selectors. Do not change canonical kg storage, reminder scheduling, or the new workout schedule domain.

**Validation target:** selected JVM tests plus `assembleDebugAndroidTest`, then focused connected `ProductionFlowTest` when an emulator is available.

### Task 5 Checkpoint

Implemented but not committed: the pure profile-versus-legacy unit resolver, `NutRunRepository.profileEntity(userId)`, the runtime profile-flow contract, and a combined account observer that gates training mutations until the profile and training-state flows have both emitted. `TrainingViewModelTest` and `ProductionDomainTest` pass after this checkpoint.

Still required: explicit account-switch/profile-emission tests, remove non-Profile weight selectors from MainActivity, remove `updateUsesMetricUnits`, run Android tests, review, and commit the Task 5 file set.

### Task 5 Completion Checkpoint

Completed in code: Profile entity flow is available to Training, profile/training readiness is account-scoped, Profile is the sole editable unit owner, and Training, Progress, workout detail, and history-edit screens no longer show kg/lb selectors. Canonical stored kg values remain unchanged.

Validation: full `testDebugUnitTest` passed; `assembleDebug` and `assembleDebugAndroidTest` produced both APKs; `git diff --check` passed. Connected instrumentation was not run because `adb` is unavailable in this environment.

Next task remains Task 6 (training reminders against reusable weekly plans). This Task 5 documentation remains uncommitted by design.

### Task 5 On-Device Validation

After connecting `emulator-5554`, the focused `ProductionFlowTest` ran successfully: 9 tests, 0 failures, 0 errors. The instrumentation assertion now verifies that Training has no kg/lb selectors; the obsolete test previously tried to tap the intentionally removed selector.

## Task 6 Start (2026-08-20)

**Scope:** Migrate training reminders from the lossy legacy `sessions` projection to canonical workout templates and ordered weekly day plans. Keep the existing two unique WorkManager jobs, notification grouping, delivery ledger, and recovery behavior unchanged.

**Implementation order:** Add pure reminder-name tests for plan ordering, Rest Days, unplanned days, shared templates, and overrides; implement `trainingReminderNames`; switch the worker; verify reminder rescheduling from workout and day-plan mutations.

**Validation:** focused reminder/ViewModel JVM tests, full JVM suite, then the connected `ProductionFlowTest` if the emulator remains available. Do not start Task 7 UI work in this task.

### Task 6 Completion Checkpoint

Completed: `TrainingReminderWorker` now resolves notification names through canonical `workoutTemplates`, ordered `weeklyDayPlans`, and date-specific overrides. The legacy compatibility `sessions` projection is no longer used by reminders.

Tests cover plan order, Rest Days, unplanned days, moved occurrences, and skipped occurrences. Focused reminder/ViewModel tests and the full JVM suite pass. No UI changed, so no new connected instrumentation run was required.

Next: Task 7 renders the canonical workout library and weekly schedule UI directly, removing remaining UI reliance on the lossy compatibility projection.

## Task 7 Start (2026-08-20)

**Scope:** Separate Training into a canonical weekly schedule and a reusable workout library. The UI will consume `workoutTemplates` and `weeklyDayPlans` directly, show Today without reordering, support ordered assignment and Rest Days, and retain existing workout-history data on deletion.

**Files:** create `TrainingPlanningContent.kt`; modify `MainActivity.kt` and `ProductionFlowTest.kt`.

**Implementation order:** add failing Compose structural/interaction tests; create schedule and library sections with stable test tags; wire dialogs and ViewModel mutations; run connected planning tests on `emulator-5554`; then review and commit only Task 7 files.

**Constraints:** no Task 8 editor implementation, no custom exercise form, no unit selector, and no compatibility `sessions` projection as canonical UI data.

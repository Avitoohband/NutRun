# NutRun Tasks 12-19 Execution and Takeover Handover

**Status:** Tasks 10 and 11 are complete. Task 12 is ready and unstarted. Tasks 13-19 are fully planned.

**Published baseline:** `origin/main` at `80ca9a8` (`docs: complete Task 11 handover`).

**Coordination channel:** [Cursor/Codex takeover handoff](2026-08-22-cursor-codex-takeover.md) in [PR #8](https://github.com/Avitoohband/NutRun/pull/8).

**Approved backlog:** [UX/UI improvement backlog](../plans/04-ux-ui-improvement-backlog.md).

**Plan index:** [Tasks 10-19 plan index](../superpowers/plans/2026-08-21-ux-ui-task-index.md).

This file is the resume map for all remaining planned work. The individual task plan is authoritative for its RED/GREEN implementation steps. This handover is authoritative for order, ownership, branch state, cross-task constraints, completion evidence, and transfer between agents.

## Current Checkpoint

- Current writer: `CODEX_ACTIVE` until Avi explicitly transfers ownership.
- Designated takeover agent: Cursor cloud agent, currently `STANDBY`.
- Current local branch: `codex/task-12-reminder-settings-time-controls`.
- Current local branch HEAD: `80ca9a8096a127c3a8c171681af4dac9ae302f0b`.
- Task 12 branch state: clean, local only, no unique commits, and no production edits.
- Last completed task: Task 11, Training Information Architecture.
- Last full connected evidence: Task 11 `connectedDebugAndroidTest` passed 56/56 with zero skips and failures.
- Fresh pre-integration local evidence: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` passed.
- Open customer acceptance trackers: issues [#5](https://github.com/Avitoohband/NutRun/issues/5) and [#7](https://github.com/Avitoohband/NutRun/issues/7). Do not close them as part of engineering delivery.

## One-Writer Protocol

1. Only one agent may edit production files at a time.
2. The current writer records branch, base, HEAD, worktree, commits, dirty files, test evidence, blockers, and next step before pausing.
3. The current writer commits and pushes every change that must survive transfer.
4. The current writer sets the takeover channel status to `HANDED_OFF` only after remote verification.
5. The incoming writer fetches `origin`, verifies the recorded commit, and creates a new worktree from the published branch or `origin/main`.
6. The incoming writer sets the channel to `CURSOR_ACTIVE` or `CODEX_ACTIVE` before production edits.
7. Never continue from another agent's uncommitted worktree.
8. Never edit `MainActivity.kt`, a database migration, or shared ViewModel code in parallel.
9. Read-only review, test-matrix writing, and plan analysis may run in parallel when they do not change files.
10. Run only one connected Android suite against `emulator-5554` at a time.

Avi saying `take over` is the ownership-transfer trigger. Naming Cursor as the standby agent does not itself transfer write ownership.

## Required Execution Order

| Order | Task | Estimate | Start dependency | Status |
| --- | --- | --- | --- | --- |
| 12 | Reminder Settings and Time Controls | 2-3 days | `origin/main` at Task 11 completion | Ready |
| 13 | Form Components and Validation | 3-4 days | Task 12 integrated | Planned |
| 14 | Today Dashboard Actions and Empty States | 2-3 days | Tasks 12-13 integrated | Planned |
| 15 | Accessibility and Responsive Foundation | 3-5 days | Task 14 creates `TodayContent.kt`; Tasks 12-14 extractions integrated | Planned |
| 16 | Progress Trends and Drill-Down | 5-8 days | Task 15 shared UI foundation | Planned |
| 17 | Nutrition Logging Refinement | 4-6 days | Tasks 13 and 15 | Planned |
| 18 | Walk Recording Confidence and Safety | 4-6 days | Task 15 shared UI foundation | Planned |
| 19 | Authentication, Profile, and Product Polish | 3-5 days | Tasks 13 and 15; run last as release gate | Planned |

Total remaining estimate: 26-40 development days before external customer acceptance. Keep the sequence even where domain work appears independent because Tasks 12-19 all touch `MainActivity.kt` or shared state during integration.

## Invariants Across Every Task

- Use TDD: write a focused failing test, verify the expected failure, implement the minimum behavior, and verify GREEN.
- Preserve account isolation for settings, history, reminders, nutrition, walking, and authentication.
- Preserve canonical storage: kilograms, centimeters, meters, milliliters, and existing date/epoch conventions.
- Profile remains the only owner of kg/lb presentation preference.
- Keep REST and MCP contracts unchanged unless a separately approved versioned plan says otherwise.
- Keep notification permission denial from erasing configured switches or times.
- Preserve the local-only debug demo and require both UI and ViewModel `BuildConfig.DEBUG` guards.
- Never delete completed workout or walk history as a side effect of deleting templates or active drafts.
- Confirm destructive actions and verify the owning account before mutation.
- Keep stable test tags while adding semantics.
- Support 320 dp width, landscape, and 200% font scale for touched UI.
- Do not close issues #5 or #7 during implementation.
- Update this file and the UX handover before starting the next numbered task.

## Task 12: Reminder Settings and Time Controls

**Plan:** [Task 12 implementation plan](../superpowers/plans/2026-08-21-task-12-reminder-settings-time-controls.md)

**Objective:** Unify water, training, and supplement time entry and prevent hidden unsaved changes.

**Primary ownership:** `NotificationSettingsContent.kt`, `ReminderTimeInput.kt`, `SupplementReminderSettingsCard.kt`, Notification Settings integration in `MainActivity.kt`, and focused unit/Compose tests.

**Required behavior:**

- Model one immutable draft covering three master switches and every individual supplement switch/time.
- Use clock pickers plus typed time entry for all water and training fields.
- Use labels `First reminder`, `Last reminder`, `Day-before reminder`, and `Training-day reminder` without visible `HH:mm` jargon.
- Collapse disabled sections to next-reminder summaries without clearing stored values.
- Keep a persistent Save action and confirm dirty Back navigation.
- Preserve the existing atomic supplement/settings save stages, account-change checks, WorkManager names, delivery rules, Room schema, and defaults.

**Completion evidence:** Focused draft and time-input JVM tests, `NotificationSettingsContentTest`, account-switch/persistence/scheduling regressions, shared full gate, exact connected count, and handover update.

**Checkpoint after completion:** Merge or publish Task 12 as requested, record commit range and test evidence, then create Task 13 from the integrated head.

## Task 13: Form Components and Validation

**Plan:** [Task 13 implementation plan](../superpowers/plans/2026-08-21-task-13-form-components-validation.md)

**Objective:** Replace fragile free-form date and number entry with reusable validated fields that never save silent fallback values.

**Primary ownership:** new `ValidatedInputs.kt`, its JVM/Compose tests, health/profile forms, weight and workout date forms, nutrition/hydration/timer forms, and their existing domain/flow tests.

**Required behavior:**

- Add localized Material date selection and reusable integer/decimal inputs.
- Accept locale decimal separators but normalize before parsing.
- Reject empty, negative, infinite, implausible, and out-of-range values with field-specific inline errors.
- Keep dates and measurements in existing canonical domain representations.
- Preserve metric/imperial conversion semantics, unchanged-weight history behavior, custom calorie targets, and read-only email presentation.
- Confirm leaving dirty forms.
- Do not add a Room migration or change REST/MCP contracts.

**Completion evidence:** Validator boundary tests, picker and keyboard Compose tests, conversion tests, invalid-save repository assertions, process/rotation draft checks, and shared full gate.

**Checkpoint after completion:** Record the final validation bounds and reusable interfaces because Tasks 17 and 19 consume them.

## Task 14: Today Dashboard Actions and Empty States

**Plan:** [Task 14 implementation plan](../superpowers/plans/2026-08-21-task-14-today-dashboard.md)

**Objective:** Make Today a dependable launch surface where every summary clearly leads to its action or detail.

**Primary ownership:** new `TodayContent.kt`, Today navigation integration in `MainActivity.kt`, and `TodayDashboardTest.kt`.

**Required behavior:**

- Make water, protein, walk, and training summaries visibly actionable with complete TalkBack labels.
- Preserve unique consumable navigation request IDs and water-section focus.
- Add visible water amount selection instead of relying on long press.
- Add compact food and today's-workout quick actions.
- Distinguish no supplements configured from none due today.
- Continue showing only supplements due on the local date, incomplete first and completed gray/last.
- Keep repeated destination taps reliable and content reachable above bottom navigation at 200% font scale.

**Completion evidence:** Navigation and repeated-tap tests, empty/loading/completed states, 200% font-scale coverage, shared full gate, and exact connected count.

**Checkpoint after completion:** Task 15 must start only after `TodayContent.kt` is integrated because its accessibility audit owns that file.

## Task 15: Accessibility and Responsive Foundation

**Plan:** [Task 15 implementation plan](../superpowers/plans/2026-08-21-task-15-accessibility-responsive-foundation.md)

**Objective:** Establish NutRun theme/components and make all primary workflows usable with TalkBack, compact displays, landscape, dark theme, and 200% font scale.

**Primary ownership:** new `NutRunTheme.kt`, `NutRunComponents.kt`, root theme/screen integration, and accessibility changes across active workout, training planning/editor, notifications, Today, and supplements.

**Required behavior:**

- Add balanced light/dark color schemes, typography, spacing tokens, and cards no rounder than 8 dp.
- Add `NutRunScreen`, metric, empty, inline-message, loading, and feedback primitives.
- Announce headings once and provide role, state, object, and action semantics.
- Meet 48 dp touch targets and WCAG AA text contrast without color-only state.
- Adapt layouts at 320 dp and font scale 2.0 using constraints rather than viewport-scaled fonts.
- Preserve domain, persistence, REST, MCP, billing, reminder scheduling, and stable test tags.

**Completion evidence:** Shared component tests, focused semantics/compact tests, full automated gate, and `docs/testing/task-15-accessibility-validation.md` covering TalkBack, light/dark, portrait/landscape, and font scales 1.0/2.0.

**Checkpoint after completion:** Record any device-only accessibility checks explicitly. Tasks 16-19 branch from the integrated UI foundation.

## Task 16: Progress Trends and Drill-Down

**Plan:** [Task 16 implementation plan](../superpowers/plans/2026-08-21-task-16-progress-trends.md)

**Objective:** Turn existing records into understandable, accessible progress trends without changing stored data.

**Primary ownership:** new `ProgressAnalytics.kt`, `ProgressContent.kt`, pure analytics tests, Progress integration in `MainActivity.kt`, and chart Compose tests.

**Required behavior:**

- Support 7-day, 30-day, 90-day, and all-time ranges using device-local dates.
- Derive weight, workout frequency, training volume, walking distance, calorie adherence, hydration adherence, and exercise progression series.
- Handle empty, single-point, duplicate-day, future, incomplete, bodyweight, and zero-goal inputs deterministically.
- Use profile units for display without mutating historical source values.
- Provide textual chart summaries and a dated `View data` list.
- Add searchable exercise drill-down for weight, reps, volume, and estimated 1RM from completed sets only.
- Keep Progress read-only except existing history edit/delete actions and avoid medical-diagnosis wording.

**Completion evidence:** Pure reducer tests, chart semantics/range tests, exercise filtering and unit tests, shared full gate, and handover update.

## Task 17: Nutrition Logging Refinement

**Plan:** [Task 17 implementation plan](../superpowers/plans/2026-08-21-task-17-nutrition-logging-refinement.md)

**Objective:** Make food/water logging faster and safer while adding calorie/macro targets, explicit search states, and undoable deletion.

**Primary ownership:** Room entities/DAO/database/repository, `NutRunViewModel`, new `NutritionContent.kt`, migration and search/domain/Compose tests, and Nutrition integration in `MainActivity.kt`.

**Required behavior:**

- Add account-scoped `nutrition_targets` with the only planned schema change: Room version 6 to 7.
- Preserve every existing record and add `MIGRATION_6_7` to the shared migration-aware builder.
- Derive editable default macros from calories at 25% protein, 45% carbohydrate, and 30% fat and label them as general guidance.
- Debounce normalized food search by 300 ms; suppress stale/account-crossing results.
- Distinguish Idle, Loading, Results, Empty, and Error states.
- Use Task 13 validation and never save negative or silently defaulted values.
- Make water amount selection visible.
- Delay destructive deletion for five seconds, allow Undo, commit exactly once, and preserve owner isolation.
- Keep REST and MCP contracts unchanged.

**Completion evidence:** Version-6 data survival migration test, account-isolation and target tests, search cancellation tests, deletion timing tests, Nutrition Compose tests, shared full gate, and rollback notes.

**Migration safety:** Do not start Task 18 or 19 from a branch that lacks the committed and validated version-7 database migration.

## Task 18: Walk Recording Confidence and Safety

**Plan:** [Task 18 implementation plan](../superpowers/plans/2026-08-21-task-18-walk-recording-confidence.md)

**Objective:** Make GPS readiness, permissions, recording, Finish, Discard, and recovery clear without accidental route loss.

**Primary ownership:** new `WalkLocationMonitor.kt` and `WalkContent.kt`, DI/ViewModel exposure, `WalkRecordingService`, DAO/repository discard transaction, and walk/account-isolation tests.

**Required behavior:**

- Keep `WalkRecordingService` and Room as the only route-writing authority.
- Monitor GPS only while Walk is visible and permission is granted; monitor fixes are never persisted.
- Show Permission required, Acquiring, Ready, Weak, and Unavailable states with explicit accuracy rules.
- Show a rationale before requesting location/activity permission and keep history available after denial.
- Keep elapsed time, distance, steps, GPS, Pause/Resume, Finish, and Discard reachable during recording.
- Restore an active account-owned session after recreation without starting another session.
- Keep Finish idempotent and history-preserving; reset the map only after Room completion.
- Discard only the named unfinished account-owned session and points after explicit data-loss confirmation.

**Completion evidence:** GPS state tests, permission and active-screen tests, process-recovery tests, finish/discard transaction and cross-account tests, service recovery tests, shared full gate, and handover update.

## Task 19: Authentication, Profile, and Product Polish

**Plan:** [Task 19 implementation plan](../superpowers/plans/2026-08-21-task-19-auth-profile-product-polish.md)

**Objective:** Finish authentication, onboarding, profile organization, destructive safety, and visual consistency, then run the final release gate.

**Primary ownership:** authentication gateway/ViewModel state, new authentication/onboarding/profile content, `MainActivity.kt` integration, account/profile Compose tests, and release verification.

**Required behavior:**

- Add password visibility, field errors, loading/IME behavior, and non-enumerating Firebase password reset.
- Never log, persist, prefill, or expose passwords.
- Preserve the debug demo behind both UI and ViewModel `BuildConfig.DEBUG` checks; release must have no usable bypass.
- Use three short onboarding steps with saved draft state, Task 13 inputs, health-estimate preview, and one final save.
- Group Profile into Account, Health, Notifications, Appearance, Subscription, and Data.
- Separate Sign out from Delete account; require typed signed-in email and backend-first deletion outside debug.
- Preserve profile-owned units, canonical storage, billing product IDs, and entitlement rules.
- Remove duplicate headings and standardize navigation/feedback without changing domain behavior.

**Completion evidence:** Authentication state tests, demo-gateway isolation, onboarding rotation/process tests, account deletion/isolation tests, shared full gate, `assembleRelease`, and a final Tasks 10-19 handover containing commits, migration, test counts, device checks, and release blockers.

## Cross-Task File Ownership

| Shared area | Tasks | Rule |
| --- | --- | --- |
| `MainActivity.kt` | 12-19 | Strictly sequential production edits |
| `NotificationSettingsContent.kt` | 12, 15 | Task 12 creates/extracts; Task 15 audits after integration |
| `TodayContent.kt` | 14, 15 | Task 14 creates; Task 15 audits after integration |
| `ValidatedInputs.kt` | 13, 17, 19 | Task 13 owns interfaces; later tasks consume without incompatible redesign |
| `NutRunViewModel.kt` | 17-19, plus focused Task 12 integration | One writer; preserve account guards and cancellation semantics |
| Room database/DAO/repository | 17, 18 | Task 17 migration first; Task 18 adds no schema change |
| Theme/shared components | 15-19 | Task 15 owns foundation; later tasks reuse rather than fork styles |
| Connected emulator | all | One suite at a time |

## Branch and Commit Procedure

For each global task:

1. Confirm the previous task is integrated or identify the exact published dependency branch.
2. Fetch `origin` and create a fresh task branch/worktree from the recorded commit.
3. Update the takeover channel and this handover with writer, branch, base, HEAD, worktree, scope, and baseline test result.
4. Follow the individual plan checkbox-by-checkbox with RED/GREEN evidence.
5. Commit independently reviewable slices using the messages suggested by the plan.
6. Review the task commit range for correctness, edge cases, regressions, security/data loss, complexity, accessibility, and missing tests.
7. Run focused tests, the shared full gate, and every task-specific manual gate.
8. Update plan status, this handover, the UX handover, and testing records before pushing or integration.
9. Push or merge only when Avi requests the repository action.
10. Create the next task branch only from the integrated, remotely verified head.

## Shared Validation Gate

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

Additional mandatory gates:

- Task 12: reminder account-switch, permission-denied, persistence-stage, and rescheduling checks.
- Task 13: locale decimals, date bounds, canonical conversion, invalid-save, and dirty-form checks.
- Task 14: repeated navigation, water focus, Today empty states, and font scale 2.0.
- Task 15: TalkBack/manual matrix, light/dark, 320 dp, landscape, and font scale 2.0.
- Task 16: deterministic reducers and accessible chart data summaries.
- Task 17: migration 6 to 7, account isolation, stale-search suppression, and undo timing.
- Task 18: real permission/GPS states where available, process recovery, idempotent Finish, and destructive Discard.
- Task 19: debug/release bypass inspection, account deletion ordering, process recreation, and `assembleRelease`.

If a connected or release gate cannot run, record the exact command, environment, failure, completed partial evidence, and remaining owner. Never convert an environmental gap into a passing claim.

## Mandatory Handoff Record

Before pausing or transferring, append a dated checkpoint to this file and update the takeover channel with:

```text
Status:
Current writer:
Task and subtask:
Branch:
Base commit:
HEAD commit:
Worktree:
Committed and pushed commits:
Dirty files:
Production behavior completed:
RED tests observed:
GREEN tests passed:
Full validation and counts:
Manual/device validation:
Review findings resolved:
Open findings or blockers:
Schema/contract changes:
Next exact step:
Incoming writer may edit after:
```

Use `HANDED_OFF` only when all preserved work is pushed and the remote commit has been verified. Use `BLOCKED` only for a genuine external blocker, not simply because validation or implementation remains unfinished.

## End State After Task 19

The final handover must include:

- Task 10-19 commit ranges and integration commits.
- Room migration history, including version 6 to 7 and migration-test evidence.
- Debug, release, lint, JVM, connected, accessibility, and manual test results with counts/devices.
- REST, MCP, billing, authentication, canonical-unit, and account-isolation compatibility statements.
- Open customer acceptance items from issues #5 and #7.
- Any device-only checks still requiring the customer's hardware.
- The recommended release branch, signed APK/AAB process, and rollback point.

No task is complete only because code exists. Completion requires plan coverage, fresh validation evidence, review, updated handover, and a remotely identifiable commit.

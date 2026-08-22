# NutRun UX/UI Tasks 10-19 Plan Index

**Status:** Tasks 10 and 11 completed and validated; Task 12 is next; Tasks 13-19 planned.

**Audit/spec:** [`../../plans/04-ux-ui-improvement-backlog.md`](../../plans/04-ux-ui-improvement-backlog.md)

**Execution handover:** [`../../handovers/2026-08-21-ux-ui-tasks-10-19-handover.md`](../../handovers/2026-08-21-ux-ui-tasks-10-19-handover.md)

**Tasks 12-19 takeover roadmap:** [`../../handovers/2026-08-22-tasks-12-19-execution-handover.md`](../../handovers/2026-08-22-tasks-12-19-execution-handover.md)

## Plans

| Order | Plan | Priority | Estimate | Depends on | Status |
| --- | --- | --- | --- | --- | --- |
| 10 | [`Task 10 - Active Workout Focus Mode`](2026-08-21-task-10-active-workout-focus-mode.md) | P0 | 4-6 days | Tasks 1-9 baseline | Completed and validated |
| 11 | [`Task 11 - Training Information Architecture`](2026-08-21-task-11-training-information-architecture.md) | P0 | 5-7 days | Task 10 active-workout extraction | Completed and validated |
| 12 | [`Task 12 - Reminder Settings and Time Controls`](2026-08-21-task-12-reminder-settings-time-controls.md) | P1 | 2-3 days | Task 11 integrated at `80ca9a8` | Ready |
| 13 | [`Task 13 - Form Components and Validation`](2026-08-21-task-13-form-components-validation.md) | P1 | 3-4 days | Task 12 may reuse time-input conventions | Planned |
| 14 | [`Task 14 - Today Dashboard`](2026-08-21-task-14-today-dashboard.md) | P1 | 2-3 days | Stable navigation from Tasks 10-13 | Planned |
| 15 | [`Task 15 - Accessibility and Responsive Foundation`](2026-08-21-task-15-accessibility-responsive-foundation.md) | P1 | 3-5 days | Task 14 extracted screens; apply rules during Tasks 12-14 | Planned |
| 16 | [`Task 16 - Progress Trends`](2026-08-21-task-16-progress-trends.md) | P2 | 5-8 days | Task 15 shared components | Planned |
| 17 | [`Task 17 - Nutrition Logging Refinement`](2026-08-21-task-17-nutrition-logging-refinement.md) | P2 | 4-6 days | Tasks 13 and 15 | Planned |
| 18 | [`Task 18 - Walk Recording Confidence`](2026-08-21-task-18-walk-recording-confidence.md) | P2 | 4-6 days | Task 15 shared components | Planned |
| 19 | [`Task 19 - Auth, Profile, and Product Polish`](2026-08-21-task-19-auth-profile-product-polish.md) | P2 | 3-5 days | Tasks 13 and 15 | Planned |

## Dependency Rules

- Implement Task 10 before Task 11 so the Training screen is not edited concurrently by two feature branches.
- Task 12 can run independently after planning documents are committed, but do not edit `MainActivity.kt` concurrently with Task 10 or 11 in the same worktree.
- Task 13 supplies date/number components required by Tasks 17 and 19.
- Task 15 supplies shared theme/screen/accessibility components required by Tasks 16-19. Apply its rules during Tasks 10-14, then run its dedicated full-app audit.
- Task 17 is the only plan that intentionally changes the Room schema: version 6 to 7 for account-scoped nutrition targets.
- Tasks 16, 18, and 19 do not require Room migrations under their current plans.

## Commit Discipline

Each numbered plan contains smaller independently reviewable commits. Before moving to the next global task:

1. Complete every checkbox and focused RED/GREEN cycle.
2. Run the full JVM, lint, build, and connected Android gates named by the plan.
3. Update the UX handover with commit range, test counts, device, and deferred limitations.
4. Review the exact task commit range for correctness, data loss, accessibility, regressions, and unnecessary complexity.
5. Merge or push only after the user requests the repository action.

## Shared Full Gate

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

Task 19 additionally runs `assembleRelease`. A missing release signing/configuration value is recorded as a blocker; it is not bypassed.

## Parallel Work Guidance

Safe read-only parallel work:

- Review a completed task commit while the primary agent updates its handover.
- Write test matrices or inspect accessibility without editing production files.
- Analyze Tasks 16, 18, or 19 before implementation once Task 15 interfaces are committed.

Unsafe parallel work:

- Two agents editing `MainActivity.kt` in the same worktree.
- Task 10 and Task 11 implementation at the same time.
- Task 13 and Task 17 modifying validation/save behavior before Task 13 is committed.
- Concurrent Gradle connected suites against the same emulator.

## Next Task

Tasks 10 and 11 are complete. Task 12 is next and must preserve the reminder scheduling, permission-denied, and account-isolation behavior already covered by tests.

# Client acceptance audit - 2026-09-16

Status: AUTOMATED REMEDIATION COMPLETE; PHYSICAL AND CLIENT ACCEPTANCE PENDING.

## Baseline

- Repository: C:\Avi\Studies\Projects\FitnessApp.
- Source: main at 4ab7d01, updated from origin/main before testing.
- App: debug 0.2.0 (2).
- Emulator: Pixel_10 AVD, emulator-5554, Android 17.
- Scope: acceptance issues #5, #7, #12, #13, #14 and YossiTuch issue #15.
- Issues #2 and #3 were already closed. No production Kotlin changed in this audit.

## Validation

Gradle initially failed because Java could not establish its Windows loopback pipe. The successful process-only workaround used Android Studio JBR with JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\NutRun-UnixSockets-Unavailable. This changed no machine setting.

- Unit tests: 331 passed, 0 failed, 0 skipped.
- Lint: 0 errors, 78 warnings, 2 information findings.
- assembleDebug and assembleDebugAndroidTest passed.
- Full connected suite: 126 passed, 9 failed, 0 skipped of 135.
- BottomNavAccessibilityTest passed when rerun alone.
- Targeted ProductionFlowTest plus ProfileContentTest: 16 passed, 3 failed of 19.

Full-suite failure classification:

1. AccessibilityResponsiveTest active-workout control exists outside the compact viewport; the test does not scroll.
2. BottomNavAccessibilityTest passed alone; full-suite startup/state interference.
3. Five ProductionFlow cases initially missed demo-login; they passed in the isolated class rerun.
4. The walk-history flow still fails after Back because walk-details-heading remains.
5. The Profile test asserts lower sections displayed before scrolling.
6. The workout flow assumes Weight (kg) after a legitimate imperial preference was saved.

Reports and screenshots are in evidence/2026-09-16.

## Remediation result

Implemented on branch `codex/issue-15-gtg` from baseline `4ab7d01`:

- Skipped targets no longer count as completed/resolved, and incomplete workout review is restored.
- Progress values have one formatting owner; metric/imperial summaries and rows no longer produce mixed units such as `lb kg`.
- Instrumentation startup, scrolling, profile-unit, and walk-back tests were made deterministic.
- GTG plans and logs now support reps or holds, weekday schedules, goals, optional external load, RIR, notes, archive, Today logging, and Progress day review/edit/delete.
- Training payload schema v4 remains backward-compatible with v1-v3, rejects unsupported future data, prevents backend schema downgrade, and enforces a 900 KiB UTF-8 payload ceiling without truncation.

Fresh post-implementation validation:

- JVM tests: 348 passed, 0 failed, 0 skipped.
- Backend policy tests: 14 passed, 0 failed.
- Lint: passed with no errors (existing warnings/information remain).
- `assembleDebug` and `assembleDebugAndroidTest`: passed.
- Complete connected suite: 140 passed, 0 failed, 0 skipped on Pixel_10 AVD, Android 17.
- `git diff --check`: passed; Windows line-ending notices are informational.

## Direct emulator walkthrough

Passed:

- Demo sign-in, local English date, and today's workout.
- Water opens Nutrition at Water. Returning to Today does not redirect again.
- Today's training opens Training and marks Wednesday Today.
- Separate Schedule/Workouts, all weekdays, Rest Day, actions, Quick workout, and create workout.
- Active-workout Add, Skip/Undo, navigation, Cancel, Finish, set fields, list/grid, and rest controls.
- Profile conversion: 175 cm / 75 kg became 68.9 inches / 165.3 lb and persisted.
- Notification cards, plain labels, clock picker, permission state, Manage supplements, and sticky Save.

Confirmed defects:

1. Start Push + Biceps, skip 3 of 6, leave the other 3 unfinished, then Finish. It saves immediately and reports 0 of 3 completed instead of showing incomplete review.
2. With Imperial units, Progress reads Training volume: 0 lb kg and Weight: 165.3 lb kg.

Evidence screenshots: skipped-finish-bypasses-review.png and imperial-progress-mixed-units.png.

## Issue decisions

- #12 eligible to close: 7 notification-settings and 11 supplement-settings UI tests passed; direct picker/layout checks and JVM persistence/account/permission tests passed.
- #13 eligible to close: 6 validated-input UI tests and relevant JVM tests passed; isolated health edit/dirty-back and direct conversion/preview passed.
- #14 eligible to close: 5 dashboard tests and direct water/training navigation passed; labels and empty states have Compose coverage.
- #5 code defect is fixed and automated coverage passes; close only after client acceptance if desired.
- #7 skipped/incomplete Finish is fixed; keep open for the pending physical timer checks.
- #15 GTG and related regression work are implemented; keep open for the client walkthrough and physical checks.

## Physical-phone checks pending

Avi selected document these as pending for now. Emulator tests cannot prove sound volume, vibration strength, locked-phone behavior, or battery timing.

1. Start a workout, set rest to 15 seconds, complete a set, background/lock, and confirm ongoing countdown.
2. Confirm one noticeable sound/vibration at expiry and tap returns to the active workout.
3. Repeat with notification permission denied; settings and workout must remain usable.
4. Schedule water/training/supplement reminders soon; confirm one correct alert and suppression after completion/goal.
5. Check primary flows with TalkBack and large font, especially set controls and sticky timer.

## Follow-up

- Design: ../superpowers/specs/2026-09-16-issue-15-ux-and-gtg.md
- Plan: ../superpowers/plans/2026-09-16-issue-15-ux-and-gtg.md

Resume from the [GTG implementation handover](../handovers/2026-09-16-gtg-implementation.md). Production work is complete on the feature branch; next action is physical/client acceptance, then issue disposition and commit/push when requested.

# Cursor Continuation Plan, Tasks 12-19

**Author:** Cursor cloud agent
**Created:** 2026-08-22T07:26Z
**Trigger:** Codex exhausted its credits during Task 12; Avi asked Cursor to continue.

**Coordination channel:** [Cursor/Codex takeover](2026-08-22-cursor-codex-takeover.md)
**Execution roadmap:** [Tasks 12-19 execution handover](2026-08-22-tasks-12-19-execution-handover.md)
**Live Task 12 state:** [Task 12 live handover](2026-08-22-task-12-live-handover.md) on the Codex branch

This file plans the next moves only. It does not replace the roadmap,
which stays authoritative for per-task scope, invariants, and evidence.

## Where Codex Actually Stopped

Verified against `origin`, not against the prose.

- `origin/main` is `80ca9a8`. No Task 12 work is merged.
- `origin/codex/task-12-reminder-settings-time-controls` is `ff0d8f8`,
  seven commits above `main`, all pushed.
- Task 12.1 draft model, 12.2 extracted content, and 12.3 regressions
  each reached a recorded GREEN.
- Last recorded evidence: 28 focused JVM tests and 7 connected Compose
  tests, plus the 11-test supplement card regression.
- The remaining Task 12 step is the full validation gate, the manual
  emulator checklist, and a review of the complete diff.
- The `ff0d8f8` transfer record understates itself by one commit: it
  lists Task 12.3 tests as dirty, but that commit contains them.

Nothing is lost, and nothing needs reimplementing.

## Environment Change

Codex worked in `C:\Users\Avi_OP_PC\Documents\FitnessApp\task-12-worktree`
on Windows with a physical Pixel 10 AVD. Cursor runs on a Linux cloud VM.

- JDK 21 and Gradle wrapper work. Use `sh gradlew`; the wrapper is not
  executable on a fresh checkout here.
- The Android SDK is not preinstalled. Cursor installs command line
  tools, `platform-tools`, `platforms;android-35`, and
  `build-tools;35.0.0`, then writes an untracked `local.properties`.
- `/dev/kvm` exists, so an emulator may boot, but it is a headless
  4-core VM rather than Avi's desktop. Connected suite timing and
  stability will differ.
- Every PowerShell command in the existing plans must be read as its
  POSIX equivalent. The commands are equivalent; only the shell differs.

If `connectedDebugAndroidTest` cannot run here, it is recorded as an
environmental gap with the exact command and failure, and it is handed
to Avi's hardware. It is never reported as passing.

## Immediate Sequence

### Step 1: Finish Task 12 validation

Branch `cursor/task-12-validation-95a8` from `ff0d8f8`. Never continue
from Codex's uncommitted worktree.

1. Run `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and
   `assembleDebugAndroidTest`, recording exact counts and exit codes.
2. Attempt the full `connectedDebugAndroidTest` suite on a locally
   created AVD. Record the result or the environmental gap.
3. Run `git diff --check`.

### Step 2: Review Codex's Task 12 diff

Review `80ca9a8..ff0d8f8` as inherited code, not as trusted code.
Focus on the areas the plan calls out:

- The two-stage atomic supplement and settings save survived the
  `MainActivity.kt` extraction. That file lost 213 lines and gained a
  new 333-line composable, so the persistence orchestration is the
  highest-risk part of the change.
- Account readiness and account-switch checks still guard every stage.
- Permission denial does not clear configured switches or times.
- WorkManager unique names, Room schema, defaults, REST, and MCP
  contracts are unchanged.
- Times remain stored as minutes after midnight.

Any defect found is fixed test-first on the same branch.

### Step 3: Integrate Task 12

Update the live handover with the commit range, counts, device, review
findings, and any deferred manual checks. Open a pull request into
`main`. Merge only when Avi asks.

### Step 4: Land the coordination docs

PR #8 carries the takeover channel, the Tasks 12-19 roadmap, and this
plan. It is documentation only and can merge independently of Task 12.

## Tasks 13-19

Order and scope stay exactly as the roadmap defines:
`13 -> 14 -> 15 -> 16 -> 17 -> 18 -> 19`. The sequence is mandatory
because every task eventually touches `MainActivity.kt` or shared
state.

Cursor changes only the working method, not the plan:

- Each task starts from the integrated, remotely verified head.
- Each task gets its own `cursor/task-NN-...-95a8` branch and its own
  pull request.
- Each task keeps the TDD RED/GREEN discipline and records evidence
  with exit codes.
- The live handover is updated before each pause.

Two boundaries carry the most risk and are called out early:

- Task 17 is the only Room migration, version 6 to 7. Tasks 18 and 19
  must not branch from a head lacking the validated migration.
- Task 19 is the release gate and runs `assembleRelease`. Missing
  signing configuration is recorded as a blocker, never bypassed.

Issues [#5](https://github.com/Avitoohband/NutRun/issues/5) and
[#7](https://github.com/Avitoohband/NutRun/issues/7) stay open
throughout. Neither is closed by engineering delivery.

## What Cursor Needs From Avi

- Approval to start Step 1. Cursor holds before running the gate.
- A decision on merging PR #8, which is safe to merge now.
- Confirmation on whether device-only checks that this VM cannot
  perform should be batched for one manual pass on Avi's emulator at
  the end of Task 12, or deferred to customer acceptance.

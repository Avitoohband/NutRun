# GTG Implementation Handover - 2026-09-16

Status: IMPLEMENTED AND AUTOMATED GATES GREEN. PHYSICAL/CLIENT ACCEPTANCE PENDING.

## Repository state

- Repository: `C:\Avi\Studies\Projects\FitnessApp`
- Branch: `codex/issue-15-gtg`
- Baseline HEAD: `4ab7d01`
- Worktree: implementation and documentation are uncommitted and unpushed.
- Do not use or delete this worktree until the diff is committed or deliberately discarded.

## Delivered

- Correct incomplete-workout review when exercises are skipped; skipped targets remain separate in history.
- Correct Progress unit ownership for metric and imperial summaries/data rows.
- Deterministic connected-test login, scrolling, unit, profile, and walk-back behavior.
- GTG schema-v4 domain, validation, codec, analytics, account-scoped persistence, rollback, and backward compatibility.
- GTG plan creation/edit/archive with catalog/custom exercises, reps/hold mode, daily goal, weekdays, and optional external load.
- GTG set logging/edit/delete with RIR and notes; Today shows scheduled plans and daily set progress.
- Progress range summaries keep reps and seconds separate and open plan -> day -> log details for edit/delete.
- Backend schema-downgrade rejection and matching 900 KiB UTF-8 training-payload guard locally and server-side.

## Validation evidence

- JVM: 348 passed, 0 failed, 0 skipped.
- Backend: 14 passed, 0 failed.
- Lint: exit 0, no errors.
- `assembleDebug`: passed.
- `assembleDebugAndroidTest`: passed.
- Connected Android suite: 140 passed, 0 failed, 0 skipped.
- Device: Pixel_10 AVD, `emulator-5554`, Android 17.
- Focused GTG tests were rerun after the final Progress editor interaction and passed.
- `git diff --check`: passed before final documentation edits; rerun before commit.

Gradle on this PC requires:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\NutRun-UnixSockets-Unavailable'
```

## Pending acceptance

Avi explicitly asked to document phone-only checks as pending:

1. Rest timer audible/vibration behavior while backgrounded and locked, including exactly one completion alert.
2. Notification tap returning to the active workout and no stale/duplicate alert after finish, cancel, logout, or account switch.
3. Water, training, and supplement reminders on a real phone, including permission denial and battery-management delay.
4. TalkBack and large-font walkthrough for active workout controls, sticky timer, and primary GTG forms.
5. Client walkthrough for issue #15 and final decision on issues #5, #7, and #15.

## Known scope notes

- WorkManager reminders remain best-effort; no exact-alarm permission or foreground service was added.
- GTG logs capture the current timestamp when created and preserve it when edited; the UI does not currently expose arbitrary historical timestamp editing.
- Training payloads remain one JSON record. The 900 KiB guard fails safely before Firestore/backend limits and never truncates history. If real usage approaches that ceiling, migrate GTG logs to event storage in a separate plan.

## Next safe action

1. Run `git diff --check` and inspect `git status`.
2. Commit this branch when authorized, then push `codex/issue-15-gtg`.
3. Perform the pending phone/client checklist.
4. Close #5 after unit display acceptance, close #7 only after physical timer acceptance, and close #15 only after the full client walkthrough.

One writer at a time. Do not edit `TrainingViewModel.kt`, `TrainingStateCodec.kt`, or GTG UI files concurrently until this branch is committed or handed off.

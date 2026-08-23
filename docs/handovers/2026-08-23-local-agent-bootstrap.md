# Local Cursor Agent Bootstrap (from Cloud Agent)

**Created:** 2026-08-23  
**Purpose:** Hand off NutRun work from the Cursor **cloud agent** to a **local Cursor IDE agent** on Avi's Windows machine.

## How Avi activates the local agent

1. Open **Cursor Desktop** on Windows.
2. **File → Open Folder** → canonical checkout (see below).
3. Pull latest `main`: `git checkout main && git pull origin main`
4. Open Chat (`Ctrl+L`) → mode **Agent** (not Ask).
5. Paste the **Bootstrap prompt** at the bottom of this file, or say:
   > Read `@docs/handovers/2026-08-23-local-agent-bootstrap.md` and execute the bootstrap steps.

## Canonical checkout

Prefer:

```text
C:\Users\Avi_OP_PC\Documents\FitnessApp
```

Verify before editing:

```powershell
git remote -v
git log main -1 --oneline
Test-Path .\local.properties
adb devices
```

Expected after sync:

- Remote: `https://github.com/Avitoohband/NutRun`
- `main` at `bf1dae5` or newer (Task 12 merged)
- `emulator-5554` shows `device` when Android Studio AVD is running

Do **not** assume `C:\Avi\Studies\Projects\FitnessApp` is current until Avi confirms both paths match `git log main -1`.

## Agent roles (2026-08-23)

| Agent | Runtime | Status | Role |
| --- | --- | --- | --- |
| Cursor cloud | Remote Linux VM | **STANDBY** | PRs, JVM/lint/build in cloud, docs when Avi is away |
| Cursor local (IDE) | Avi's Windows PC | **ACTIVE when Avi opens Agent** | Emulator, connected tests, Task 13+ implementation |
| Codex | Local (when credits) | **STANDBY** | Former writer; do not edit without new transfer |
| Cloud run URL | https://cursor.com/agents/bc-8a35620a-665a-4d2d-af94-ae2dbc4795a8 | — | Historical context only |

**One writer at a time.** When the local agent is active, the cloud agent must not edit production files.

## Published repo state

- **`origin/main`:** `bf1dae5` — Task 12 merged (PR #9 + doc update PR #10)
- **Task 12 merge:** `7d2c42a` (code), validation doc `b0f9782`
- **Open PR (do not merge):** [#8](https://github.com/Avitoohband/NutRun/pull/8) — coordination/handover docs only; Avi explicitly wants this **unmerged for now**
- **Merged PRs:** [#9](https://github.com/Avitoohband/NutRun/pull/9) Task 12, [#10](https://github.com/Avitoohband/NutRun/pull/10) Task 12 completion docs
- **Keep open:** GitHub issues [#5](https://github.com/Avitoohband/NutRun/issues/5) and [#7](https://github.com/Avitoohband/NutRun/issues/7)

Extra coordination docs (takeover channel, Tasks 12–19 roadmap) live **only on PR #8 branch** — read without merging:

```powershell
git fetch origin cursor/codex-takeover-handshake-95a8
git show origin/cursor/codex-takeover-handshake-95a8:docs/handovers/2026-08-22-cursor-codex-takeover.md
git show origin/cursor/codex-takeover-handshake-95a8:docs/handovers/2026-08-22-tasks-12-19-execution-handover.md
```

## Task completion status

| Task | Status | Notes |
| --- | --- | --- |
| 10–11 | Complete on `main` | Task 11 had full connected 56/56 locally |
| 12 | **Merged to `main`** | JVM/lint/build verified in cloud (256 unit tests) |
| 13–19 | Planned | **Do not start without Avi approval per task** |

### Task 12 testing gap (local agent should close first)

Cloud agent could not run `connectedDebugAndroidTest` (emulator offline on VM).

Avi's earlier local run hit Espresso/API 35 error:

```text
NoSuchMethodException: android.hardware.input.InputManager.getInstance []
```

**Local agent first job:** run full connected suite on Avi's machine and report results. If API 35 fails, try API 34 AVD or note for Espresso fix — do not conflate with Task 12 logic without evidence.

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

## Mandatory task order

```text
12 (done) → 13 → 14 → 15 → 16 → 17 → 18 → 19
```

**Next task:** Task 13 — Form Components and Validation  
Plan: `docs/superpowers/plans/2026-08-21-task-13-form-components-validation.md`

## Required reading (in order)

1. This file
2. `docs/handovers/2026-08-22-task-12-live-handover.md`
3. `docs/handovers/2026-08-21-ux-ui-tasks-10-19-handover.md`
4. `docs/superpowers/plans/2026-08-21-ux-ui-task-index.md`
5. Task-specific plan when Avi approves that task

## Non-negotiable constraints

- One writer at a time; consult Avi before starting each new task (13, 14, …)
- Do not merge PR #8 unless Avi explicitly asks
- Do not close issues #5 or #7
- No Room migration except Task 17 (6→7)
- Do not edit `MainActivity.kt` concurrently across agents
- Use `.\gradlew.bat` on Windows
- Never commit `local.properties`
- Update handover docs before/after each checkpoint
- Task 19 release gate includes `assembleRelease`

## Full validation gate (when Avi asks for GREEN)

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

## Transfer record

```text
Status: LOCAL_AGENT_ACTIVE (when Avi pastes bootstrap in Cursor IDE)
Previous writer: Cursor cloud agent (STANDBY)
Branch: main @ bf1dae5 or newer
Task 12: merged; connected tests pending local verification
Next exact step: run connectedDebugAndroidTest locally; await Avi before Task 13
PR #8: open, do not merge
Updated at: 2026-08-23T13:15Z
```

---

## Bootstrap prompt (copy everything below into Cursor Agent)

```text
You are the LOCAL NutRun agent on my Windows PC (Cursor IDE Agent mode).

Read these files first:
- docs/handovers/2026-08-23-local-agent-bootstrap.md
- docs/handovers/2026-08-22-task-12-live-handover.md
- docs/superpowers/plans/2026-08-21-ux-ui-task-index.md

Context:
- Repo: https://github.com/Avitoohband/NutRun
- Task 12 is MERGED to main (PR #9). Cloud agent validated JVM/lint/build but NOT connected tests.
- PR #8 is handover-only — DO NOT merge it.
- Issues #5 and #7 stay open.
- Cloud agent is STANDBY; you are the active writer on my machine.
- One task at a time; ask me before starting Task 13.

Step 1 — Verify environment (report output):
  git checkout main
  git pull origin main
  git log -1 --oneline
  adb devices
  .\gradlew.bat --version

Step 2 — Close Task 12 test gap:
  .\gradlew.bat connectedDebugAndroidTest --console=plain
  Report pass/fail/skip counts. If InputManager.getInstance failures on API 35, say so clearly.

Step 3 — Stop and ask me:
  - whether Task 12 is fully green
  - whether to start Task 13.1 (ValidatedInputs.kt TDD)

Do not edit production files in Step 1–2 unless tests prove an app bug.
Update docs/handovers/2026-08-23-local-agent-bootstrap.md Transfer Record with your results before stopping.
```

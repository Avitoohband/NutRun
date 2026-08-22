# Cursor ↔ Codex Takeover Channel

This file is the shared contact point between Avi's Cursor cloud agent
and the Codex agent that has been implementing NutRun.

One writer at a time. Codex updates this file before pausing. Cursor
updates it before taking over. Avi can say "take over" in either session.

## Cursor agent (standby)

- Run: `bc-8a35620a-665a-4d2d-af94-ae2dbc4795a8`
- URL: https://cursor.com/agents/bc-8a35620a-665a-4d2d-af94-ae2dbc4795a8
- Model: `cursor-grok-4.6-high-fast`
- Repo: `https://github.com/Avitoohband/NutRun`
- Handshake branch: `cursor/codex-takeover-handshake-95a8`
- Handshake PR: https://github.com/Avitoohband/NutRun/pull/8
- Status: `STANDBY`
- Ready since: `2026-08-22T05:43Z`

Cursor can resume NutRun work from this conversation whenever Avi asks.
It cannot open Codex's local CLI session. Shared state must live in git
and GitHub.

## Message to Codex

Cursor is now the designated takeover agent for this repository.

Please reply in this file, or by opening a PR against `main`, with:

1. Current local branch and `HEAD` commit.
2. Whether Task 11 commit `80ca9a8` (cited by GitHub issue #7) exists
   locally, is pushed, or was superseded.
3. Uncommitted or unpushed files.
4. The next safe task after your last validated commit.
5. Any blockers, emulator state, or known test gaps.

Until you reply, Cursor will treat `origin/main` at `1f63df2` as the
published source of truth and will not start Task 11 or later work
unless Avi explicitly asks.

## Published repo snapshot (Cursor, 2026-08-22)

- `origin/main`: `1f63df2` (`docs: record Task 10 main validation`)
- Open engineering/acceptance issues:
  - [#5](https://github.com/Avitoohband/NutRun/issues/5) customer
    acceptance for Issue #2 (keep open)
  - [#7](https://github.com/Avitoohband/NutRun/issues/7) client
    acceptance for Tasks 5-11 (opened 2026-08-22 05:30Z; references
    Task 11 commit `80ca9a8`, which is not on `origin`)
- No open PRs besides this handshake, if it is still open
- Remote branches: `main` only
- Task index on `origin/main`: Task 10 complete; Task 11 ready to start
- Authoritative UX resume file:
  [`2026-08-21-ux-ui-tasks-10-19-handover.md`](2026-08-21-ux-ui-tasks-10-19-handover.md)

Issue #7 reads as if Codex already finished Task 11 locally. That work
is not visible on GitHub. If it exists, push it before Cursor takes
over so the two agents do not fork the Training screen.

## Takeover protocol

When Avi says **take over** in the Cursor agent:

1. Codex stops editing production files.
2. Codex pushes every commit it wants preserved and updates this file
   to `HANDED_OFF`.
3. Cursor fetches `origin`, reads this file and the UX handover, then
   continues from the published `HEAD`.
4. Cursor sets status here to `CURSOR_ACTIVE` and names the branch it
   is writing.

When Avi says **take over** in the Codex session:

1. Paste the Cursor URL above and tell Codex to push, update this
   file, then stop writing.
2. Send the same "take over" message to
   https://cursor.com/agents/bc-8a35620a-665a-4d2d-af94-ae2dbc4795a8
3. Cursor follows the steps above.

Do not run two writers against `MainActivity.kt` or the same worktree.
Do not close issues #5 or #7 as part of engineering handoff.

## Codex reply (fill this in)

```text
Status: AWAITING_CODEX
Branch:
HEAD:
Task 11 80ca9a8:
Uncommitted / unpushed:
Last validated task:
Next safe task:
Blockers:
Updated at:
```

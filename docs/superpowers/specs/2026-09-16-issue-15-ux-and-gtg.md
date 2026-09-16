# Issue 15: flexible training and Grease the Groove

Source: [YossiTuch's UX issue #15](https://github.com/Avitoohband/NutRun/issues/15).
Baseline reviewed: `4ab7d01`, 2026-09-16. Status: proposed follow-up design, not implemented.

## Requirements translated from the issue

1. Reorder exercises in a program without deleting and adding them again.
2. Add exercises and mark other exercises not relevant during a workout. Optionally start a workout without a program and add exercises while training.
3. Sticky rest timer and notification timer.
4. Track GTG progression. Avi clarified GTG means **Grease the Groove**.

## Existing implementation

Commit `4ab7d01` implements requirements 1-3: editor move controls, independent active-workout snapshots, add/skip/undo, Quick workout, explicit post-finish template saving, sticky countdown, notification chronometer, and a WorkManager completion fallback. Its predecessor plan is [the flexible-workout plan](../plans/2026-08-27-flexible-workouts-and-rest-timer.md).

These features require acceptance and regression work, not a second implementation. The September audit is [recorded separately](../../testing/2026-09-16-client-acceptance.md). A green unit suite alone does not close this issue.

GTG has no existing model or UI in the baseline. The decisions below are a proposed MVP, not additional requirements attributed to Yossi.

## Proposed GTG experience

- Training gets a third mode, **GTG**, alongside Schedule and Workouts. Keep the existing modes and state restoration.
- A GTG plan references one catalog/custom exercise, a user-entered daily set goal, selected weekdays, and either reps or hold duration. Optional external load follows Profile units. Do not infer a training prescription or automatically increase targets.
- Start with an empty GTG collection. Creation chooses an exercise and user-entered targets; do not seed fake GTG history.
- Logging a practice set does not start/finish a normal workout or interrupt an existing workout/rest timer. Show a short log form, prefilled from the plan, with explicit Save and optional effort/notes. No automatic rest timer or GTG reminders in this first increment.
- Today shows scheduled GTG plans with sets completed/goal and a Log set action. Training also allows an off-schedule practice entry without changing weekday assignments.
- A GTG history shows timestamp, reps or seconds, external load, optional RIR, and notes. Permit edit and confirmed deletion. Archive plans without deleting logs; use Undo after deleting a single log when feasible.
- Progress gets a GTG section with exercise selection and existing 7/30/90/all ranges: daily sets, total reps or hold seconds, days practiced, and historical goal attainment. Keep rep and hold metrics separate. Show lists/accessible summaries when charts have insufficient data.
- GTG entries do not count as completed workouts, normal workout volume, personal-record recommendations, or progressive-overload suggestions. This prevents frequent practice sets from distorting existing progress.

## Data and consistency

- Extend the account-owned training JSON from schema 3 to schema 4 with `gtgPlans` and `gtgLogs`; keep the Room database at version 7. Existing REST/MCP request contracts remain unchanged because the training payload already carries JSON.
- Keep parsing, validation, and analytics in dedicated files. `TrainingViewModel` remains the single writer for the combined training payload; do not create another independent whole-payload writer.
- Use UUID-backed plan/log IDs and snapshot the exercise name and goal on each log. Editing a plan must not rewrite historical measurements or goals.
- Store kilograms canonically. Reps: 1-1,000; hold seconds: 1-3,600; daily set goal: 1-100; optional external load: finite 0-1,000 kg; optional RIR: integer 0-10; notes: at most 1,000 characters. Exactly one of reps/hold seconds is present, matching the plan mode. These are input guards, not exercise recommendations.
- Persist event time, timezone ID, and performed local date. Changing timezone later must not silently move historical practice to another day. Validate edited dates/times against the current instant; explicitly resolve ambiguous DST times.
- Multiple real sets on the same day are valid. A repeated save of the same log ID is idempotent. Prevent double submissions while a save is pending.
- Existing schema 1/2/3 payloads decode with empty GTG collections, preserving all workouts/supplements/active timers. Future unsupported schemas must not be decoded and rewritten as older versions.
- Include GTG in restore/reset/rollback/sync/export/delete-account paths. Demo remains local. Never drop oldest logs to fit a payload limit. Establish payload-size behavior and backward-client safety before rollout; old binaries cannot safely preserve fields they do not understand.

## Acceptance and non-goals

- Issue #15 remains open until requirements 1-4 are verified, including GTG persistence and account isolation.
- Existing client acceptance issues can close only when their entire technical checklist has evidence. Do not mark physical or subjective checks as passed based on code inspection.
- Avi selected **document physical-phone checks as pending** on 2026-09-16.
- Not in scope: automatic GTG prescriptions, GTG notifications, social features, automatic PR recommendations, or unrelated release/billing work.
- No production implementation, commit, or push is requested in this audit. The next agent should implement the linked plan only after Avi asks to proceed.

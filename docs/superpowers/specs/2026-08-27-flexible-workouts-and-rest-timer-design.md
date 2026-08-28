# Flexible Workouts and Rest Timer — Design

Approved English requirements:

- Reorder exercises in saved workouts without delete/re-add.
- During an active workout, add exercises and mark others as skipped (session-local until explicit save).
- Start a Quick workout without a program and add exercises while training.
- Rest timer stays visible while scrolling and appears as a live Android notification.

## Architecture

- **Schema v3** stores active state in a single `activeWorkout` snapshot (`ActiveWorkoutSession`), migrated from schema v2 legacy fields.
- Active mutations never modify source `WorkoutTemplate` rows until the user saves after finish.
- **Sticky timer** reads `restTimerEndAtMillis` from the snapshot; **RestTimerNotificationCoordinator** posts a chronometer notification and schedules **RestTimerCompletionWorker** as a validated fallback.

See `docs/superpowers/plans/2026-08-27-flexible-workouts-and-rest-timer.md` for the full implementation plan.

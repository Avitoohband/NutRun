# Supplement Reminder Recovery Redesign

## Goal

Replace the fragile account-wide recovery state machine with bounded,
best-effort WorkManager recovery that cannot lose one reminder system behind
another or reset an active retry budget.

## Recovery Model

Use one unique work chain for each `(userId, ReminderSystem)` pair:

- `reminder-reschedule-recovery:<userId>:HYDRATION`
- `reminder-reschedule-recovery:<userId>:TRAINING`
- `reminder-reschedule-recovery:<userId>:SUPPLEMENTS`

Each request contains one nonblank user ID and one valid reminder-system name.
It uses `ExistingWorkPolicy.KEEP`, exponential backoff starting at 15 seconds,
and at most three executions (`runAttemptCount` 0, 1, and 2). Failure on the
third execution is terminal. A later app launch, settings change, reboot, or
timezone change may create a fresh chain after the old chain is terminal.

The immediate scheduling attempt made by the triggering event is separate
from the three recovery executions. Events arriving while the same chain is
unfinished are intentionally coalesced by `KEEP` and do not reset backoff or
attempt count. Different reminder systems and accounts use different names and
cannot suppress one another.

## State And Errors

WorkManager is the only recovery state store. Remove active recovery systems,
`closing`, begin/complete phases, continuations, and handoffs from
`AppPreferences` and production code. Legacy preference keys remain inert and
may be deleted during account cleanup.

Each recovery execution checks that its input account is still authenticated,
then invokes only its input reminder system. A successful reschedule returns
success. An ordinary exception or reported failure returns retry on attempts
0 and 1 and failure on attempt 2. `CancellationException` always propagates.
Malformed input fails without reading account data or invoking a scheduler.

Sign-out cancels all three recovery names for that account. No Room schema
change is required. The existing notification-delivery claim protocol remains
unchanged because it protects the non-idempotent notification post; recovery
only repeats idempotent scheduling.

## Receiver Validation

Receiver action filtering covers unrelated actions, boot, and timezone
changes. Framework lifecycle coverage must deliver an app-owned test broadcast
through Android so the production `goAsync()` receives a real
`PendingResult` and demonstrably calls `finish()`.

A separate production-composition instrumentation test may invoke the receiver
with a controlled runtime that does not call `goAsync()`. It uses real
preferences, Room, and WorkManager, forces one concrete scheduler to fail,
verifies only that system receives recovery work, and verifies supplement
delivery work is still scheduled. Tests clean receivers, observers, account
rows, preferences, and all touched unique work in `finally` blocks.

## Acceptance Criteria

- No application-owned active recovery state or phase remains.
- A failure set of N systems creates N account/system-scoped `KEEP` chains.
- Same-key scheduling never uses `REPLACE`; different systems cannot suppress
  one another.
- Each chain runs only its input system and stops after its third failed
  execution.
- Ordinary exceptions follow the same cap and cancellation propagates.
- Sign-out can cancel all recovery work for one account without touching
  another account.
- Supplement recovery still schedules the real account-scoped supplement
  delivery worker.
- Receiver tests use framework delivery for production `goAsync()` and perform
  complete cleanup.
- Existing delivery-claim and Room migration tests remain passing.

## Scope

Recovery remains best-effort. A same-system event coalesced while an unfinished
chain reaches terminal failure does not receive a separate retry budget; the
next external event after terminal completion starts a fresh chain. A durable
outbox and exact alarms remain outside this change.

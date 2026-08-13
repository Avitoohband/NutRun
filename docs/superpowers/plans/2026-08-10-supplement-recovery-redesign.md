# Supplement Reminder Recovery Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the blocked account-wide recovery protocol with independent, bounded WorkManager chains and valid receiver coverage.

**Architecture:** WorkManager owns all recovery state. Each authenticated account and reminder system has one immutable `KEEP` chain with exponential backoff and three executions; no DataStore queue, closing phase, continuation, or handoff remains.

**Tech Stack:** Kotlin, WorkManager, Coroutines, Preferences DataStore, Room, JUnit 4, AndroidX instrumentation tests.

## Global Constraints

- Recovery is best-effort and stops after three failed WorkManager executions.
- Unique names are `reminder-reschedule-recovery:<userId>:<SYSTEM>`.
- Recovery requests use `ExistingWorkPolicy.KEEP` and 15-second exponential backoff.
- Each request and worker execution contains exactly one `ReminderSystem`.
- Different systems and accounts cannot replace or suppress one another.
- `CancellationException` propagates and malformed input performs no account work.
- The Room delivery-claim protocol and supplement-delivery scheduler remain unchanged.
- Receiver tests never directly call a default `goAsync()` runtime without a framework-attached `PendingResult`.
- Existing user data and Room schema version 6 remain unchanged.

---

### Task 1: Replace Recovery State With Per-System Work

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/reminders/ReminderRescheduleReceiver.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/AppPreferences.kt`
- Test: `app/src/test/java/com/avitoohband/nutrun/SupplementReminderWorkerTest.kt`

**Interfaces:**
- Produces: `ReminderRescheduleRecoveryScheduler.schedule(userId, systems)` that fans out one `KEEP` request per system.
- Produces: `ReminderRescheduleRecoveryScheduler.cancel(userId)` that cancels the three account/system work names.
- Produces: stateless single-system recovery execution used by `ReminderRescheduleRecoveryWorker`.

- [ ] **Step 1: Replace closing-state tests with failing per-system tests**

Add tests named:

```kotlin
recoverySchedulerEnqueuesOneKeepChainPerFailedSystem
recoveryNamesIsolateAccountsAndSystems
repeatedSameSystemSchedulingNeverUsesReplace
recoveryWorkerScopesEachAttemptToItsInputSystem
recoveryFailureRetriesTwiceAndStopsOnThirdExecution
ordinaryRecoveryExceptionUsesTheSameCap
recoveryCancellationPropagates
malformedRecoveryInputIsTerminalAndDoesNotReschedule
accountMismatchStopsWithoutRescheduling
recoveryCancelCancelsAllAccountSystemNamesOnly
```

Assert exact names, singular system input, `KEEP`, `BackoffPolicy.EXPONENTIAL`,
15-second backoff, results for attempts 0/1/2, and no scheduler call for invalid
or stale-account input. Delete tests that model `closing`, handoffs, completion
capture, or a fake durable recovery queue.

- [ ] **Step 2: Run the focused test and verify red**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.SupplementReminderWorkerTest
```

Expected: failures because the existing scheduler still uses account-wide
state and continuation behavior.

- [ ] **Step 3: Remove application-owned recovery state**

Delete recovery-state DTOs and active merge/begin/complete methods from
`AppPreferences`. Retain only legacy-key removal inside account cleanup so data
written by intermediate builds is harmless.

Delete `ReminderRecoveryStateStore`, preference adapters, closing/completion
actions, continuation scheduling, and handoff logic from the receiver module.

- [ ] **Step 4: Implement immutable per-system requests**

Use singular worker input:

```kotlin
const val KEY_USER_ID = "userId"
const val KEY_SYSTEM = "system"

fun recoveryWorkName(userId: String, system: ReminderSystem): String =
    "reminder-reschedule-recovery:$userId:${system.name}"
```

For every failed system, enqueue one `OneTimeWorkRequest` under its exact name
with `ExistingWorkPolicy.KEEP` and 15-second exponential backoff. Implement
`cancel(userId)` by cancelling all three calculated names.

- [ ] **Step 5: Implement stateless bounded execution**

Parse exactly one enum value. Check the authenticated account before invoking:

```kotlin
rescheduleReminderSystemsForUser(context, userId, setOf(system))
```

Return success when the system recovers or the account changed. Return retry
for a failure/ordinary exception at attempts 0 and 1, and failure at attempt 2.
Rethrow `CancellationException`. Legacy account-wide requests with only
`KEY_SYSTEMS` are malformed and terminate without writes.

- [ ] **Step 6: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.SupplementReminderWorkerTest assembleDebug
git add app/src/main/java/com/avitoohband/nutrun/reminders/ReminderRescheduleReceiver.kt app/src/main/java/com/avitoohband/nutrun/data/AppPreferences.kt app/src/test/java/com/avitoohband/nutrun/SupplementReminderWorkerTest.kt
git commit -m "fix: simplify reminder recovery"
```

Expected: focused tests pass and the debug APK builds.

---

### Task 2: Validate Receiver Lifecycle And Production Composition

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/reminders/ReminderRescheduleReceiver.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ReminderRescheduleReceiverProductionTest.kt`
- Test: `app/src/test/java/com/avitoohband/nutrun/SupplementReminderWorkerTest.kt`

**Interfaces:**
- Consumes: per-system recovery names and scheduler from Task 1.
- Produces: framework-valid async receiver coverage and isolated real-dependency composition coverage.

- [ ] **Step 1: Add failing action-gate and framework-lifecycle coverage**

Keep a JVM test proving unrelated actions are ignored and both
`Intent.ACTION_BOOT_COMPLETED` and `Intent.ACTION_TIMEZONE_CHANGED` dispatch.

Add `frameworkDeliveredReceiverFinishesAsyncWork`: dynamically register a
receiver for an app-owned test action, send an ordered broadcast, block the
injected dispatcher, verify the terminal receiver waits, release the
dispatcher, then verify ordered completion. Unregister every receiver in
`finally`.

- [ ] **Step 2: Add a failing production-composition test**

Construct the default dispatcher factory with a controlled runtime that does
not call `goAsync()`. Sign in a UUID account, seed valid supplement state and
settings, and force the real training scheduler to fail using an invalid
persisted minute. Invoke timezone handling and assert:

```text
supplement settings timezone == ZoneId.systemDefault().id
supplement-reminder:<userId> exists
reminder-reschedule-recovery:<userId>:TRAINING exists
HYDRATION and SUPPLEMENTS recovery names do not exist
```

Use polling/futures rather than a forever LiveData observer.

- [ ] **Step 3: Make receiver dependencies testable without weakening production**

Retain the public no-argument receiver constructor. Keep production defaults
for action filtering, `goAsync()`, `Dispatchers.IO`, and `PendingResult.finish()`.
Allow tests to supply an app-owned action and controlled runtime only through
instance-local constructor seams; introduce no mutable global test state.

- [ ] **Step 4: Guarantee cleanup**

In `finally`, unregister receivers, cancel and await all account recovery,
supplement-delivery, training-reminder, and global hydration work touched by
the test, clear account Room rows, and clear account preferences.

- [ ] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.SupplementReminderWorkerTest assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
git add app/src/main/java/com/avitoohband/nutrun/reminders/ReminderRescheduleReceiver.kt app/src/androidTest/java/com/avitoohband/nutrun/ReminderRescheduleReceiverProductionTest.kt app/src/test/java/com/avitoohband/nutrun/SupplementReminderWorkerTest.kt
git commit -m "test: validate reminder receiver recovery"
```

Expected: unit tests and Android test APK pass. Run connected tests when a
device is available; otherwise record that runtime validation remains pending.

---

### Task 3: Regression Validation And Handoff

**Files:**
- Verify: all files changed by Tasks 1-2.

**Interfaces:**
- Produces: a reviewed recovery foundation for the original supplement-reminder plan Tasks 4-6.

- [ ] **Step 1: Run focused regression validation**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.SupplementReminderWorkerTest --tests com.avitoohband.nutrun.SupplementReminderRulesTest --tests com.avitoohband.nutrun.SupplementReminderTest lintDebug assembleDebug assembleDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Review the redesign diff**

Confirm no recovery DataStore state, `closing`, continuation, handoff, or
recovery `REPLACE` remains. Confirm Room schema and delivery-claim behavior are
unchanged and the worktree contains no unrelated staged changes.

- [ ] **Step 3: Resume the supplement-reminder plan**

Mark the recovery redesign complete, then resume Task 4 in
`docs/superpowers/plans/2026-08-09-supplement-reminders.md` using the new
per-system recovery scheduler for settings/mutation failures and sign-out
cancellation.

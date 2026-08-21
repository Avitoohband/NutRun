# Walk Recording Confidence and Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make walk recording status, GPS quality, permissions, completion, and recovery clear without risking accidental route loss.

**Architecture:** Extract Walk UI and add a lifecycle-aware `WalkLocationMonitor` for pre-start/current GPS readiness. Continue using `WalkRecordingService` and Room as recording authority. Add an explicit discard action that deletes only the active account-owned session and points after confirmation; normal Finish remains history-preserving.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Fused Location Provider, foreground service, Room transactions, Hilt, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 18.

## Global Constraints

- Preserve foreground-service recovery, account isolation, route filtering, canonical meters, and completed-walk history.
- Do not request permissions until the user starts the walk flow and has seen the rationale.
- Location denial must not block access to prior walk history.
- Discard is destructive and always requires explicit confirmation naming route and set of data removed.
- Finish must remain idempotent and reset the active map after Room reports completion.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 18.1: GPS Readiness Monitor

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/walk/WalkLocationMonitor.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/di/AppModule.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/NutRunViewModel.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/WalkReadinessTest.kt`

**Interfaces:**
- Produces: `sealed interface WalkGpsState { PermissionRequired; Acquiring; Ready(accuracyMeters); Weak(accuracyMeters); Unavailable(reason) }`.
- Produces: `WalkLocationMonitor.state: StateFlow<WalkGpsState>` plus `start()` and `stop()`.

- [ ] **Step 1: Write failing state tests**

Cover permission missing, provider unavailable, no fix timeout, accuracy thresholds (`<=25 m` Ready, `>25 m` Weak), stale fixes, and monitor stop/restart.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.WalkReadinessTest --console=plain
```

- [ ] **Step 3: Implement the monitor**

Request high-accuracy updates only while Walk is visible and permission is granted. Do not persist monitor fixes; the service remains the only route writer.

- [ ] **Step 4: Verify and commit**

Commit monitor, DI, ViewModel exposure, and tests.

### Task 18.2: Permission Rationale and Active Recording UI

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/WalkContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/WalkRecordingContentTest.kt`

- [ ] **Step 1: Write failing permission and status tests**

Assert Start first shows why location/activity data are needed, permission denial leaves History available, notification permission is requested only when starting the foreground flow on required Android versions, and GPS states have distinct labels/actions.

- [ ] **Step 2: Implement extracted Walk content**

Before recording, show map/history plus GPS status near Start. During recording, use a stable active header and persistent bottom controls for elapsed time, distance, steps, GPS state, Pause/Resume, Finish, and overflow Discard.

- [ ] **Step 3: Add active process-recovery tests**

Recreate the Activity with an active/paused Room session and assert controls, elapsed duration, route, and account ownership restore without starting a second session.

- [ ] **Step 4: Verify and commit**

Run focused connected tests and the existing `WalkPresentationTest` and camera-framing tests.

### Task 18.3: Confirmed Finish and Discard

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/walk/WalkRecordingService.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/NutRunDao.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/NutRunRepository.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/WalkContent.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/AccountIsolationDatabaseTest.kt`

**Interfaces:**
- Produces: `WalkRecordingService.ACTION_DISCARD`.
- Produces: transactional `discardActiveWalk(userId: String, sessionId: String): Boolean` deleting points then the matching unfinished session.

- [ ] **Step 1: Write failing transaction/idempotency tests**

Assert Finish twice creates one finished walk, Discard removes only the named unfinished session and points, cross-account IDs are rejected, completed sessions cannot be discarded, and service recovery after discard finds no active walk.

- [ ] **Step 2: Implement finish/discard confirmations**

Finish dialog summarizes current distance/time and offers Keep walking/Finish. Discard dialog states that route, distance, time, and steps will be permanently removed and offers Keep walk/Discard walk.

- [ ] **Step 3: Run full validation and update handover**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

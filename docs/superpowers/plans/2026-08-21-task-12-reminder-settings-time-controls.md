# Reminder Settings and Time Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give water, training, and supplement reminders one understandable time-input pattern and prevent users from losing changes in a long settings screen.

**Architecture:** Extract Notification Settings into focused content that reuses `ReminderTimeInput` for every time. Keep the existing atomic two-stage supplement/settings persistence and account-change validation. Use a persistent bottom Save action, explicit dirty state, and a navigation confirmation instead of introducing background autosave races.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 time picker, Hilt ViewModel, WorkManager scheduling APIs, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 12.

## Global Constraints

- Keep the three independent master switches and every individual supplement switch/time.
- Do not change reminder delivery rules, WorkManager unique names, Room schema, or defaults.
- Clock pickers must respect the device 12/24-hour format while storage remains minutes after midnight.
- A denied Android notification permission must not erase configured switches or times.
- Keep account-readiness and account-switch safeguards from `NotificationSettingsSaveButton`.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 12.1: Shared Reminder Draft and Summaries

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/NotificationSettingsContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/ReminderTimeInput.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/NotificationSettingsDraftTest.kt`

**Interfaces:**
- Produces: `NotificationSettingsDraft` containing water, training, supplement master, and per-supplement drafts.
- Produces: `notificationSettingsDirty(saved, draft): Boolean`.
- Produces: `nextReminderSummary(draft, supplements, now): String`.

- [ ] **Step 1: Write failing draft tests**

Cover unchanged state, each switch/time becoming dirty, disabled sections retaining values, next water/training/supplement summaries, no configured supplements, already-passed times, and invalid typed times.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.NotificationSettingsDraftTest --tests com.avitoohband.nutrun.ReminderTimeInputTest --console=plain
```

- [ ] **Step 3: Implement immutable draft helpers**

Keep parsing delegated to existing minute helpers. Summary calculation is display-only and must not schedule work or write persistence.

- [ ] **Step 4: Verify GREEN and commit**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/NotificationSettingsContent.kt app/src/main/java/com/avitoohband/nutrun/ReminderTimeInput.kt app/src/test/java/com/avitoohband/nutrun/NotificationSettingsDraftTest.kt
git commit -m "refactor: model notification settings draft"
```

### Task 12.2: Unified Clock Inputs and Persistent Save

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/NotificationSettingsContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/SupplementReminderSettingsCard.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/NotificationSettingsContentTest.kt`

- [ ] **Step 1: Write failing Compose interaction tests**

Assert all four water/training fields expose clock actions, labels contain no `HH:mm`, disabled cards collapse to a summary, supplement master and Toggle all remain independent, Save stays displayed while scrolling a 20-supplement list, and dirty Back opens `Discard notification changes?`.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.NotificationSettingsContentTest' --console=plain
```

- [ ] **Step 3: Implement the screen extraction**

Use a `Scaffold` with top back action, weighted settings `LazyColumn`, and bottom `NotificationSettingsSaveButton`. Replace water/training `OutlinedTextField` instances with `ReminderTimeInput`. Use labels `First reminder`, `Last reminder`, `Day-before reminder`, and `Training-day reminder`.

- [ ] **Step 4: Implement dirty navigation handling**

Use `BackHandler` and the top back action to show the same confirmation. `Keep editing` dismisses it; `Discard` returns without persistence. A successful save clears dirty state and returns through the existing callback.

- [ ] **Step 5: Verify GREEN and commit**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/NotificationSettingsContent.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/main/java/com/avitoohband/nutrun/SupplementReminderSettingsCard.kt app/src/androidTest/java/com/avitoohband/nutrun/NotificationSettingsContentTest.kt
git commit -m "feat: unify reminder time settings"
```

### Task 12.3: Persistence and Scheduling Regression Gate

- [ ] **Step 1: Extend existing account and save tests**

Add cases for picker values, typed values, permission denial, account change before each persistence stage, retained disabled values, and exactly one reschedule after a successful save.

- [ ] **Step 2: Run full validation**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

- [ ] **Step 3: Update the UX handover**

Record test counts, reminder worker behavior verified, and any device permission limitations before Task 13.

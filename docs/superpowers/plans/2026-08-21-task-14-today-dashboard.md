# Today Dashboard Actions and Empty States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Today a dependable launch surface where every summary clearly leads to the corresponding daily action or detail.

**Architecture:** Extract Today into `TodayContent.kt`, keep all domain data in the existing two ViewModels, and expand `MainApp.navigateTo` callbacks for nutrition, walk, training, and supplement destinations. Introduce semantics-aware shared summary/action components without changing persistence.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Hilt ViewModel, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 14.

## Global Constraints

- Preserve the consumable navigation request IDs used by notification taps.
- Water navigation must continue focusing the Nutrition water section.
- Today supplements must continue showing only items due on the local date and completed items last.
- Do not add network calls, schema changes, or a configurable dashboard in this task.
- Every action needs a visible affordance and complete TalkBack label.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 14.1: Actionable Summary Semantics and Navigation

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/TodayContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/TodayDashboardTest.kt`

**Interfaces:**
- Produces: `TodayScreen(state, training, onTrainingClick, onNutritionClick, onWaterClick, onWalkClick, onManageSupplements, ...)`.
- Produces: `DashboardMetric(value, label, icon, actionLabel, onClick)`.

- [ ] **Step 1: Write failing navigation and semantics tests**

Assert water focuses Nutrition/Water, protein opens Nutrition without forced water focus, last walk opens Walk, training opens Training, repeated taps work, and each metric exposes labels such as `Open Nutrition, 0 grams protein` rather than an unlabeled clickable container.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.TodayDashboardTest' --console=plain
```

- [ ] **Step 3: Extract Today and extend callbacks**

Move only Today-related composables from `MainActivity.kt`. Use icon plus value/label and a trailing navigation affordance for actionable metrics. If no completed walk exists, Walk still opens the recording screen and the label reads `No completed walks`.

- [ ] **Step 4: Verify GREEN and commit**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/TodayContent.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/androidTest/java/com/avitoohband/nutrun/TodayDashboardTest.kt
git commit -m "feat: make today metrics actionable"
```

### Task 14.2: Visible Quick Actions and Supplement Empty State

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/TodayContent.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/TodayDashboardTest.kt`

- [ ] **Step 1: Add failing state tests**

Cover no supplements configured, supplements configured but none due, due incomplete/completed sorting, large lists, reached hydration goal, no workout today with next workout, and no training plan.

- [ ] **Step 2: Implement concise empty states**

Use `No supplements due today` plus Manage, and `Add your supplements` plus Add when none exist. Preserve checkbox interactivity and gray completed presentation.

- [ ] **Step 3: Make water amount selection discoverable**

Add a visible menu/chevron action labeled `Choose water amount`; keep the main quick-add action logging the saved serving. Do not require long press, though it may remain as a shortcut.

- [ ] **Step 4: Add compact Food and Workout quick actions**

Food opens Nutrition at logging/search; Workout opens today's session details or Training planning when no session exists. Avoid adding another card row that pushes supplements further down.

- [ ] **Step 5: Verify large text and bottom inset**

At 200% font scale, all values wrap without clipping, the supplement section can scroll fully above navigation, and no button text is truncated.

### Task 14.3: Full Gate and Handover

- [ ] **Step 1: Run validation**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

- [ ] **Step 2: Record completion**

Update the UX handover with the exact connected test count, navigation behaviors verified, and the next task recommendation.

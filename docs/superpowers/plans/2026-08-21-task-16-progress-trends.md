# Progress Trends and Drill-Down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn existing health, workout, walking, nutrition, hydration, and weight records into understandable time-based progress insights.

**Architecture:** Add pure analytics reducers that transform existing records into dated series and accessible summaries. Render charts with focused Compose Canvas components to avoid introducing a chart dependency; every chart also exposes a textual summary and data list. Progress remains read-only except for existing history edit/delete actions.

**Tech Stack:** Kotlin, Jetpack Compose Canvas/Material 3, `java.time`, Hilt ViewModels, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 16.

## Global Constraints

- Do not change stored records or create derived-data tables.
- Use device-local dates/timezone and profile-owned units.
- Historical values must remain stable when the current unit preference changes.
- Charts need textual accessibility summaries and useful empty/single-point states.
- Do not present BMI, BMR, TDEE, or trends as medical diagnosis.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 16.1: Analytics Domain

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ProgressAnalytics.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/ProgressAnalyticsTest.kt`

**Interfaces:**
- Produces: `enum class ProgressRange { DAYS_7, DAYS_30, DAYS_90, ALL }`.
- Produces: `DatedValue(date: LocalDate, value: Double)` and `ProgressSeries(label, unit, points)`.
- Produces: `weightSeries`, `workoutFrequencySeries`, `trainingVolumeSeries`, `walkingDistanceSeries`, `calorieAdherenceSeries`, `hydrationAdherenceSeries`, and `exerciseProgressSeries`.

- [ ] **Step 1: Write failing reducer tests**

Cover range boundaries, timezone-local dates, empty data, duplicate same-day records, incomplete workouts, bodyweight/no-weight sets, distance totals, zero goals, future records, and deterministic ordering.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ProgressAnalyticsTest --console=plain
```

- [ ] **Step 3: Implement pure reducers**

Aggregate only from values passed into each function. Return empty series rather than throwing for missing data, and clamp adherence percentages to a documented display maximum of 200%.

- [ ] **Step 4: Verify GREEN and commit**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/ProgressAnalytics.kt app/src/test/java/com/avitoohband/nutrun/ProgressAnalyticsTest.kt
git commit -m "feat: add progress analytics series"
```

### Task 16.2: Accessible Charts and Progress Sections

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ProgressContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/ProgressContentTest.kt`

**Interfaces:**
- Produces: `AccessibleLineChart(series, range, valueFormatter)`.
- Produces: `ProgressScreen(app, state, training)` extracted from `MainActivity.kt`.

- [ ] **Step 1: Write failing range/chart tests**

Assert range selection, chart title/unit/summary semantics, weight and training trends, empty-state navigation actions, consistent localized dates, and imperial display conversion without source mutation.

- [ ] **Step 2: Implement charts and sections**

Use stable chart dimensions, axis labels at start/end, point markers for sparse data, and one highlighted latest value. Provide `View data` to expose a simple dated list for accessibility and exact inspection.

- [ ] **Step 3: Reorder Progress hierarchy**

Place range control and trend overview first, then training/exercise drill-down, weight, walking, nutrition/hydration, recent history, and finally a compact Health Connect status section.

- [ ] **Step 4: Verify and commit**

Run `ProgressAnalyticsTest` and `ProgressContentTest`, then commit the extracted screen and tests.

### Task 16.3: Exercise Drill-Down and Full Gate

- [ ] **Step 1: Add exercise-selection tests**

Select an exercise from personal records/history and verify weight, repetitions, volume, and estimated 1RM series use only completed sets and the correct unit.

- [ ] **Step 2: Implement drill-down content**

Use a searchable exercise menu limited to exercises with history. Preserve selected range and return position when leaving details.

- [ ] **Step 3: Run full validation and update handover**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

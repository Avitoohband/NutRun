# Form Components and Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fragile date and numeric text entry with reusable inputs that give precise errors and never save silent fallback values.

**Architecture:** Add pure validators and Compose fields in `ValidatedInputs.kt`. Adopt them incrementally in health/profile, workout dates, nutrition, hydration, weight, and timer forms while preserving canonical units and existing repository operations. Each form owns a typed draft and converts to domain values only after validation succeeds.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 `DatePickerDialog`, keyboard options/actions, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 13.

## Global Constraints

- Store dates as existing `LocalDate`/epoch values and measurements in canonical units.
- Do not add a Room migration or change REST/MCP contracts.
- Accept device-locale decimal input but normalize to a dot before Kotlin parsing.
- Reject invalid input; do not silently substitute prior/default values.
- Preserve custom calorie targets unless `Use recommended target` is selected.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 13.1: Reusable Date and Numeric Inputs

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ValidatedInputs.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/ValidatedInputsTest.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/ValidatedInputsComposeTest.kt`

**Interfaces:**
- Produces: `DecimalRule(label, minInclusive, maxInclusive, required)`.
- Produces: `validateDecimalInput(value, rule): ValidatedDecimal`.
- Produces: `ValidatedDateField(value: LocalDate?, onValueChange, label, allowedRange, error)`.
- Produces: `ValidatedNumberField(value, onValueChange, rule, integerOnly, error)`.

- [ ] **Step 1: Write failing validator tests**

Cover empty required/optional values, comma and period decimals, boundaries, NaN/infinity rejection, negative values, implausible dates, future birth dates, and leap days.

```kotlin
assertEquals(75.5, validateDecimalInput("75,5", weightRule).value)
assertEquals("Weight must be between 20 and 500 kg.", validateDecimalInput("501", weightRule).error)
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ValidatedInputsTest --console=plain
```

- [ ] **Step 3: Implement validators and Compose fields**

The date field displays a localized date, opens a Material date picker, and exposes a clear accessibility label. Numeric fields choose Number or Decimal keyboard, show supporting errors, and do not call domain save operations.

- [ ] **Step 4: Run JVM and Compose tests and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ValidatedInputsTest --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.ValidatedInputsComposeTest' --console=plain
git add app/src/main/java/com/avitoohband/nutrun/ValidatedInputs.kt app/src/test/java/com/avitoohband/nutrun/ValidatedInputsTest.kt app/src/androidTest/java/com/avitoohband/nutrun/ValidatedInputsComposeTest.kt
git commit -m "feat: add validated date and number inputs"
```

### Task 13.2: Health, Weight, and Workout Date Adoption

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/MeasurementConversionTest.kt`

- [ ] **Step 1: Add failing adoption tests**

Cover onboarding birth-date picker, health-edit date picker, read-only email presentation, metric/imperial conversions, Add Weight bounds, workout-history date picker, reschedule date picker, and unsaved-change confirmation.

- [ ] **Step 2: Replace raw fields**

Use a typed `LocalDate?` draft for dates and validated numeric drafts for height, weight, and calorie target. Set reasonable explicit ranges in one constants object: birth date from 120 years ago through today, height `50..280 cm`, weight `20..500 kg`, and calorie target `500..10000 kcal`.

- [ ] **Step 3: Verify conversion behavior**

Switching units converts the current valid measurement exactly once and never reinterprets an entered value. Saving an unchanged weight creates no extra history row.

- [ ] **Step 4: Run focused tests and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.MeasurementConversionTest --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.ProductionFlowTest' --console=plain
git add app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt app/src/test/java/com/avitoohband/nutrun/MeasurementConversionTest.kt
git commit -m "feat: validate health and workout date forms"
```

### Task 13.3: Nutrition, Hydration, and Timer Adoption

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

- [ ] **Step 1: Write failing invalid-save tests**

Assert food rejects blank/negative serving, calories, and macros; hydration rejects goal outside `250..10000 mL` and serving outside `50..2000 mL`; rest timer retains `15..600 seconds`; invalid values leave repository and screen state unchanged.

- [ ] **Step 2: Replace silent fallback conversion**

Remove `?: initialValue`, `?: 100.0`, and `?: 0` from Save paths. Build domain objects only from successful validated values and keep the dialog open on errors.

- [ ] **Step 3: Add unsaved-change protection**

Use one shared `ConfirmDiscardChangesDialog` from `ValidatedInputs.kt` for forms whose drafts differ from their initial values.

- [ ] **Step 4: Run full validation and update handover**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

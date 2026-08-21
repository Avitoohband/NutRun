# Nutrition Logging Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make daily food and water logging faster, safer, and more informative while preserving existing logs, templates, and account isolation.

**Architecture:** Extract Nutrition UI, add explicit search state with ViewModel debounce, and add account-scoped nutrition targets in Room. Reuse Task 13 validators. Implement deletion undo by delaying the destructive repository call until the Snackbar window expires; navigation or process loss commits the pending deletion rather than resurrecting data unpredictably.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt ViewModel, Room migration 6 to 7, coroutines/StateFlow, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 17.

## Global Constraints

- Preserve all food, meal-template, favorite, hydration, and account-scoped data.
- Store grams and milliliters canonically; macro targets are grams per day.
- Default macro targets are editable estimates derived from calorie target at 25% protein, 45% carbohydrate, and 30% fat; label them as general guidance.
- Keep REST and MCP contracts unchanged; nutrition-target preferences remain device-local until those contracts are separately versioned.
- Never save negative or silently defaulted serving/calorie/macro values.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 17.1: Nutrition Targets and Migration

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/Entities.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/NutRunDao.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/NutRunDatabase.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/data/NutRunRepository.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/NutRunViewModel.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/DatabaseMigrationTest.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/NutritionTargetsTest.kt`

**Interfaces:**
- Produces: `NutritionTargetEntity(userId, proteinGrams, carbohydrateGrams, fatGrams, custom)`.
- Produces: `recommendedNutritionTargets(calorieTarget): NutritionTargets`.
- Produces: repository observe/save operations and `NutRunUiState.nutritionTargets`.

- [ ] **Step 1: Write failing target and migration tests**

Assert calorie conversion with 4 kcal/g protein/carbohydrate and 9 kcal/g fat, positive rounded targets, per-account isolation, custom persistence, version-6 data survival, and no surprise row until an account's default is requested.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.NutritionTargetsTest --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.DatabaseMigrationTest' --console=plain
```

- [ ] **Step 3: Add migration 6 to 7**

Create `nutrition_targets` with unique `userId`, include it in `NutRunDatabase`, and add `MIGRATION_6_7` to the shared builder. Do not modify existing tables.

- [ ] **Step 4: Verify GREEN and commit**

Commit entity, DAO, database, repository, ViewModel state, and focused tests together.

### Task 17.2: Debounced Search and Logging Layout

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/NutritionContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/NutRunViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/FoodSearchStateTest.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/NutritionContentTest.kt`

**Interfaces:**
- Produces: `sealed interface FoodSearchUiState { Idle; Loading(query); Results(query, items); Empty(query); Error(query, message) }`.
- Changes: `NutRunViewModel.searchFood` cancels the prior job and waits 300 ms before querying.

- [ ] **Step 1: Write failing search-state tests**

Cover blank query, rapid query replacement, stale result suppression, offline error, empty results, retry, and account/sign-out cancellation.

- [ ] **Step 2: Implement debounce and state ownership**

Only the latest normalized query may update state. Clearing query cancels work and returns Idle. Do not retain results across account changes.

- [ ] **Step 3: Build compact Nutrition content**

Show calorie and macro target progress, Water, search, collapsible Quick add groups, then meal sections. Preserve query until a result is logged or the user explicitly clears it.

- [ ] **Step 4: Adopt Task 13 validation**

Food Save remains disabled until every required value is valid; all invalid fields show supporting errors. Add a discoverable amount-menu icon beside the water quick-add button.

- [ ] **Step 5: Verify and commit**

Run focused JVM/connected tests before committing the extraction.

### Task 17.3: Undoable Deletion

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/NutRunViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/NutritionContent.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/NutritionContentTest.kt`

**Interfaces:**
- Produces: `PendingNutritionDeletion(id, label, kind)` and `pendingNutritionDeletion: StateFlow<...?>`.
- Produces: `requestNutritionDeletion`, `undoNutritionDeletion`, and `commitNutritionDeletion`.

- [ ] **Step 1: Write failing deletion timing tests**

Assert request hides the row without repository deletion, Undo restores it, timeout commits exactly once, a second delete commits the first before replacing it, sign-out commits/cancels safely for the owning account, and no account can undo another account's item.

- [ ] **Step 2: Implement pending deletion and Snackbar**

Use one ViewModel-owned pending deletion and a 5-second job. UI observes it and shows `Deleted <name>` with Undo. Repository deletion receives the captured owner ID.

- [ ] **Step 3: Run full migration and regression gates**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

- [ ] **Step 4: Update handover**

Record migration validation, search cancellation evidence, connected test count, and rollback instructions.

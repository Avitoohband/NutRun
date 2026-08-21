# Training Information Architecture and Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate weekly planning from reusable workout management and replace catalog-sized dialogs with scalable full-screen workflows.

**Architecture:** Keep `WorkoutTemplate` and `WeeklyDayPlan` as canonical data. Add a saveable Training mode, compact schedule content, searchable assignment content, and a full-screen workout editor. Add workout duplication and assignment reordering as validated `TrainingViewModel` mutations persisted through the existing JSON payload.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt ViewModel, Room-backed training flow, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 11.

**Status:** Completed and validated on `codex/task-11-training-information-architecture` from `1f63df2`.

## Global Constraints

- Preserve training JSON schema version 2 and every stable workout/exercise ID.
- Do not overwrite customized plans or delete history when editing or deleting templates.
- Rest Day and assignments remain mutually exclusive; assigned workout IDs remain unique and ordered.
- Profile remains the only unit preference owner.
- Preserve active-workout behavior delivered by Task 10.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 11.1: Planning Mode and Compact Schedule

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingPlanningContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/TrainingInformationArchitectureTest.kt`

**Interfaces:**
- Produces: `enum class TrainingPlanningMode { SCHEDULE, WORKOUTS }`.
- Produces: `TrainingPlanningContent(model, mode, onModeChange, onOpenTemplate, onAssignDay)`.

- [x] **Step 1: Write failing mode and compact-layout tests**

Assert Schedule and Workouts appear in a segmented control, only the selected section is composed, the mode survives recreation, Today has a direct scroll/focus action, and seven day rows fit without embedding the workout library below them.

- [x] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.TrainingInformationArchitectureTest' --console=plain
```

- [x] **Step 3: Implement saveable mode and compact day rows**

Use `SingleChoiceSegmentedButtonRow` when available in the current Material dependency. Otherwise use two equal-width `FilterChip` controls with selected semantics. Day rows show day, Today/Rest/Unplanned status, assignment names, and one overflow/action control; expand a row for Assign and Rest Day actions.

- [x] **Step 4: Run the focused test and commit**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/TrainingPlanningContent.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/androidTest/java/com/avitoohband/nutrun/TrainingInformationArchitectureTest.kt
git commit -m "feat: separate training schedule and workouts"
```

### Task 11.2: Searchable Ordered Assignment

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/WorkoutAssignmentContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/WorkoutPlanning.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/WorkoutPlanningTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/TrainingInformationArchitectureTest.kt`

**Interfaces:**
- Produces: `moveAssignedWorkout(ids: List<String>, fromIndex: Int, toIndex: Int): List<String>`.
- Produces: `filterWorkoutTemplates(templates, query): List<WorkoutTemplate>`.
- Produces: `WorkoutAssignmentContent(day, templates, selectedIds, onSave, onCancel)`.

- [x] **Step 1: Write failing domain and UI tests**

Cover case-insensitive name search, stable catalog order, selected count, a 20-workout list, duplicate-free selection, move-up/move-down ordering, Save replacing Rest Day, and Cancel leaving state unchanged.

- [x] **Step 2: Run tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.WorkoutPlanningTest --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.TrainingInformationArchitectureTest' --console=plain
```

- [x] **Step 3: Implement full-height assignment content**

Use a top app bar with day name and selected count, search field, `LazyColumn`, checkboxes, and explicit reorder icons for selected items. Save once through `replaceAssignments(day, orderedIds)`.

- [x] **Step 4: Verify GREEN and commit**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/WorkoutAssignmentContent.kt app/src/main/java/com/avitoohband/nutrun/WorkoutPlanning.kt app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt app/src/test/java/com/avitoohband/nutrun/WorkoutPlanningTest.kt app/src/androidTest/java/com/avitoohband/nutrun/TrainingInformationArchitectureTest.kt
git commit -m "feat: add scalable workout assignment"
```

### Task 11.3: Full-Screen Workout Editor and Explicit Actions

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/WorkoutEditorContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingPlanningContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingViewModel.kt`
- Modify: `app/src/test/java/com/avitoohband/nutrun/TrainingViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Produces: `TrainingViewModel.duplicateWorkout(templateId: String): TrainingMutationResult`.
- Changes: `WorkoutEditorContent` becomes full-screen content with `onBack` and `onSaved` callbacks instead of an `AlertDialog`.

- [x] **Step 1: Write failing duplication and editor tests**

Assert duplication creates a new UUID-backed ID, appends `Copy` with collision-safe numbering, copies guidance/targets, does not copy history/active state, and persists. Assert editor search/filter state survives multiple additions and unsaved Back asks for confirmation.

- [x] **Step 2: Implement duplication atomically**

Perform one serialized ViewModel mutation and one persistence attempt. Roll back the new template if encoding or repository save fails, following existing mutation snapshot behavior.

- [x] **Step 3: Convert the editor to full screen**

Use current-exercise and Add-exercise sections, sticky Save, inline workout-name validation, category filters, quick custom creation, and explicit Start/Edit/Duplicate/Delete menu actions on library cards.

- [x] **Step 4: Remove unreachable legacy planner code**

Delete the commented block after the unconditional `return` in `TrainingScreen` only after new tests cover every retained action.

- [x] **Step 5: Run full validation and update handover**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

Record final test counts and commit range in the UX handover before starting Task 12.

## Completion Record

Implementation commits, oldest first:

```text
974e615 docs: start Task 11 training architecture
e9d8a3f feat: separate training schedule and workouts
084c91b feat: add scalable workout assignment
9f5d6c0 feat: add full-screen workout editor
7b6caed fix: clarify workout library actions
```

Delivered:

- Saveable Schedule/Workouts planning modes, compact expandable weekday rows, and a Today shortcut.
- Full-screen searchable assignment for 20+ workouts with selected count, unique ordered selection, reordering, atomic Save, and non-mutating Cancel.
- Full-screen local-draft workout editor with sticky Save, custom exercises, set editing, removal confirmation, persistent search/filter state, and unsaved-change confirmation.
- Collision-safe workout duplication with fresh workout and target UUIDs, copied guidance/targets, persistence rollback support, and no history or active-workout copying.
- Explicit labeled Start, Edit, Duplicate, and Delete workout-library actions in stable two-row controls; deletion still preserves completed history.
- Removal of the unreachable legacy Training planner while preserving the Task 10 active-workout boundary and schema version 2.

Validation on Pixel 10 AVD, Android 17:

- Focused planning and editor Compose suite: 19/19 passed.
- Repaired full-suite mode regressions: 3/3 passed.
- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: passed.
- Final `connectedDebugAndroidTest`: 56/56 passed, zero skipped, zero failed, in 6 minutes 3 seconds.
- `git diff --check`: passed.

Review found and corrected three stale tests that expected workout cards in the default Schedule mode. No Room, REST, MCP, or training JSON schema changes were introduced. Customer acceptance issue #5 remains open.

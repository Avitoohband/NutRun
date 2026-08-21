# Accessibility and Responsive UI Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give NutRun consistent UI primitives and make all primary workflows usable with TalkBack, 200% font scale, compact widths, landscape, and dark theme.

**Architecture:** Introduce a small NutRun theme/token layer and reusable screen, metric, empty, and feedback components. Adopt them in screens already extracted by Tasks 10-14, then audit remaining screens. Keep responsive decisions local to components using constraints rather than global viewport-scaled typography.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose semantics, `BoxWithConstraints`, AndroidX Compose UI Test, Android accessibility checks where supported.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 15.

## Global Constraints

- Do not alter domain behavior, persistence, REST, MCP, billing, or notification scheduling.
- Maintain minimum 48 dp touch targets and WCAG AA text contrast in light/dark themes.
- Do not rely on color alone for Today, completion, error, selected, or disabled states.
- Do not scale font sizes with viewport width.
- Support portrait widths down to 320 dp, landscape, and Android font scale 2.0.
- Preserve stable test tags while adding user-facing semantics.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 15.1: Theme Tokens and Shared Components

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/NutRunTheme.kt`
- Create: `app/src/main/java/com/avitoohband/nutrun/NutRunComponents.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/NutRunComponentsTest.kt`

**Interfaces:**
- Produces: `NutRunTheme(darkTheme, content)`.
- Produces: `NutRunScreen(title, onBack?, actions, bottomBar?, content)`.
- Produces: `NutRunMetric`, `NutRunEmptyState`, `NutRunInlineMessage`, and `NutRunLoadingState`.

- [ ] **Step 1: Write failing component tests**

Assert headings are announced once, back actions have labels, metrics merge value/label/action into one node, error/loading states expose live-region semantics, and long labels do not clip at 200% font scale.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.avitoohband.nutrun.NutRunComponentsTest' --console=plain
```

- [ ] **Step 3: Implement the theme and components**

Use a balanced palette anchored by deep teal `#0B6E69`, neutral surfaces, blue information, green success, amber warning, and Material error colors. Define light/dark schemes, typography roles, 4/8/12/16/24/32 dp spacing tokens, and shapes no larger than 8 dp for cards.

- [ ] **Step 4: Replace root MaterialTheme and duplicate headers**

Use `NutRunTheme` in `NutRunRoot`. `NutRunScreen` owns the single visible screen title and back navigation; remove repeated headings directly below the app top bar as each screen adopts it.

- [ ] **Step 5: Verify and commit**

```powershell
git add app/src/main/java/com/avitoohband/nutrun/NutRunTheme.kt app/src/main/java/com/avitoohband/nutrun/NutRunComponents.kt app/src/main/java/com/avitoohband/nutrun/MainActivity.kt app/src/androidTest/java/com/avitoohband/nutrun/NutRunComponentsTest.kt
git commit -m "feat: add accessible NutRun UI foundation"
```

### Task 15.2: Semantics and Compact Layout Audit

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/ActiveWorkoutContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TrainingPlanningContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/WorkoutEditorContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/NotificationSettingsContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/TodayContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/SupplementReminderSettingsCard.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/AccessibilityResponsiveTest.kt`

- [ ] **Step 1: Add failing semantics tests**

Cover selected bottom navigation, completed supplement state, reminder switch state, clickable workout cards, set-completion controls, timer actions, delete/edit icons, and disabled Start explanations.

- [ ] **Step 2: Add failing compact-width tests**

At 320 dp width and font scale 2.0, assert set inputs stack instead of overlap, filter chips scroll, summary metrics wrap to two columns or a list, and persistent actions remain reachable.

- [ ] **Step 3: Implement explicit semantics and constraint-based layouts**

Use `semantics(mergeDescendants = true)`, `Role.Button`/`Role.Switch`, `stateDescription`, and content descriptions that name the object and action. Use `BoxWithConstraints` or adaptive grid tracks; do not hide required actions behind inaccessible gestures.

- [ ] **Step 4: Verify and commit**

Run the focused connected class and `lintDebug`, then commit only the audited components and tests.

### Task 15.3: Full-App Manual Accessibility Gate

- [ ] **Step 1: Run full automated validation**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
git diff --check
```

- [ ] **Step 2: Execute and record the manual matrix**

Test login/onboarding, Today, schedule, workout editor, active workout, Nutrition, Walk, Progress, Profile, and Notifications in light/dark, font scale 1.0/2.0, compact portrait, landscape, and TalkBack. Record every screen and result in `docs/testing/task-15-accessibility-validation.md`.

- [ ] **Step 3: Update handover**

Link the validation matrix and list any device-only checks still requiring customer hardware.

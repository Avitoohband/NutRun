# Authentication, Profile, and Product Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish NutRun's first-run, account, profile, and visual experience without weakening authentication or destructive-action safety.

**Architecture:** Extract authentication/onboarding/profile content, extend the existing authentication gateway with password reset, and reuse Task 13 inputs plus Task 15 theme/screen primitives. Keep debug demo bypass isolated behind both UI and ViewModel `BuildConfig.DEBUG` checks. Account deletion retains current backend-first behavior and adds typed identity confirmation.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Firebase Authentication, Hilt, DataStore, Billing, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/plans/04-ux-ui-improvement-backlog.md`, Task 19.

## Global Constraints

- Release builds must contain no usable demo authentication bypass.
- Never log, persist, prefill, or expose passwords.
- Password reset sends only through Firebase Authentication and returns non-enumerating user-facing copy.
- Account deletion still requires successful configured backend deletion outside debug and remains account scoped.
- Profile owns units; onboarding and health editing store centimeters/kilograms canonically.
- Do not change billing product IDs or entitlement rules.
- Use TDD and keep customer acceptance issue #5 open.

---

### Task 19.1: Authentication State and Password Reset

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/auth/AuthenticationGateway.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/NutRunViewModel.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/AuthenticationUiStateTest.kt`

**Interfaces:**
- Produces: `AuthenticationGateway.sendPasswordReset(email: String): Result<Unit>`.
- Produces: `AuthenticationUiState(mode, busy, fieldErrors, message)` and ViewModel `authenticate`/`sendPasswordReset` state transitions.

- [ ] **Step 1: Write failing state tests**

Cover invalid email, short/blank password, busy duplicate submission, success/failure, reset with invalid email, reset success copy that does not reveal account existence, sign-out clearing state, and debug demo never calling the gateway.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.AuthenticationUiStateTest --console=plain
```

- [ ] **Step 3: Implement gateway and ViewModel state**

Use `FirebaseAuth.sendPasswordResetEmail`. Map provider exceptions to stable generic UI messages while retaining diagnostic causes only in debug logging that contains no credentials.

- [ ] **Step 4: Verify and commit**

Commit gateway, ViewModel, and tests as one authentication-domain slice.

### Task 19.2: Authentication and Stepped Onboarding UI

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/AuthenticationContent.kt`
- Create: `app/src/main/java/com/avitoohband/nutrun/OnboardingContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/AuthenticationOnboardingTest.kt`

- [ ] **Step 1: Write failing authentication tests**

Assert password visibility, Next/Done IME actions, inline email/password errors, disabled busy controls, reset flow, create/sign-in mode clarity, trial copy only in create mode, and debug-only demo button.

- [ ] **Step 2: Write failing onboarding tests**

Assert three short steps: Basics (birth date/sex), Measurements (units/height/weight), and Goal (activity/goal/calorie target). Verify progress, Back without data loss, health-estimate preview, recommended-target action, rotation restoration, and final save once.

- [ ] **Step 3: Implement extracted content**

Reuse `ValidatedDateField`/`ValidatedNumberField` and `NutRunScreen`. Keep all onboarding draft data in `rememberSaveable` keyed to authenticated account ID; final conversion uses existing `calculateHealthEstimate`.

- [ ] **Step 4: Verify and commit**

Run focused connected tests and existing demo production-flow tests before committing.

### Task 19.3: Structured Profile and Destructive Safety

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/ProfileContent.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Create: `app/src/androidTest/java/com/avitoohband/nutrun/ProfileContentTest.kt`

- [ ] **Step 1: Write failing profile tests**

Assert Account, Health, Notifications, Appearance, Subscription, and Data headings; entire preference rows are clickable; Sign out is separate from Data; Delete account requires typing the signed-in email; mismatch keeps Delete disabled; demo account copy clearly states local-only deletion.

- [ ] **Step 2: Implement structured profile**

Use list rows with icons, primary/supporting text, and trailing value/chevron. Put Delete account in an error-colored Data card at the end, and never place it adjacent to routine settings.

- [ ] **Step 3: Preserve deletion ordering**

Do not clear local records until `AuthenticationGateway.deleteAccount` succeeds, except the existing explicit debug behavior. Keep the confirmation open and show an actionable error on failure.

- [ ] **Step 4: Verify and commit**

Run account-isolation and connected profile tests, then commit the extracted screen.

### Task 19.4: Product Polish and Final Release Gate

- [ ] **Step 1: Audit screen titles and feedback**

Remove duplicate app/screen titles, standardize Back behavior, replace generic `NutRun` message dialogs with inline/Snackbar feedback where no blocking decision is required, and retain dialogs for destructive or completion decisions.

- [ ] **Step 2: Run release/debug bypass tests**

Build debug and release test variants where configured. Inspect the release UI and ViewModel paths to prove `demo`/`123456` and Enter demo cannot authenticate.

- [ ] **Step 3: Run full validation**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\gradlew.bat assembleRelease --console=plain
git diff --check
```

If release signing/configuration prevents `assembleRelease`, record the exact configuration blocker rather than weakening the release build.

- [ ] **Step 4: Update final UX handover**

Record Tasks 10-19 commit ranges, migrations, full test counts, unresolved customer/device checks, and the recommended release branch action.

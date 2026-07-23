# NutRun Stage 1 Remediation Plan

## Objective

Bring the current Android prototype into compliance with the Stage 1 missions and
prototype acceptance criteria in `PLAN.md`.

This work remains a design-validation prototype. Firebase, Room, billing, real
advertising, production notifications, and backend synchronization remain Stage 2
work.

## Phase 1: Restore Buildability

1. Add a Gradle 8.9 wrapper:
   - `gradlew`
   - `gradlew.bat`
   - `gradle/wrapper/gradle-wrapper.jar`
   - `gradle/wrapper/gradle-wrapper.properties`
2. Add a `.gitignore` covering Gradle output, Android Studio metadata, local SDK
   configuration, generated APKs, and signing material.
3. Confirm the project builds from the command line.
4. Initialize or restore Git history so future reviews have a baseline and a
   meaningful diff.

### Acceptance

- A fresh checkout can run `gradlew.bat assembleDebug`.
- Gradle uses version 8.9, as required by Android Gradle Plugin 8.7.3.
- Generated files and local secrets are not tracked by Git.

## Phase 2: Introduce Prototype State

1. Move sample data and application state out of `MainActivity.kt`.
2. Add prototype models for:
   - Account and onboarding state.
   - Trial and plan state.
   - Supplements and recurrence schedules.
   - Programs, sessions, and scheduled workouts.
   - Active-workout progress and completed-workout summaries.
   - Progression suggestions and decisions.
3. Keep data in memory while preserving it across navigation and dialog dismissal.
4. Use `rememberSaveable` or an activity-scoped state holder for state that should
   survive configuration changes.

### Acceptance

- User actions visibly update the relevant screens.
- Closing and reopening a dialog does not discard saved prototype actions.
- Navigation and device rotation do not unexpectedly reset an active journey.

## Phase 3: Complete Core Journeys

Implement every flow listed under Mission 3 in `PLAN.md`:

1. Add clickable Google and email registration simulations.
2. Add trial onboarding and notification-permission simulations.
3. Allow users to add and check supplements.
4. Support daily, every-N-days, and selected-weekday supplement schedules.
5. Let users create a weekly training program with named sessions and weekdays.
6. Make exercise search functional.
7. Let users select exercises and configure relevant sets, reps, weight, duration,
   or distance.
8. Represent receiving and opening tomorrow's workout reminder.
9. Implement starting, pausing, resuming, and completing a workout.
10. Show exercise instructions and affected muscles.
11. Let users receive and accept a progression suggestion.
12. Add a trial-expiry simulation that moves the account to the ad-supported free
    plan without removing data.

### Acceptance

- Every Mission 3 journey is clickable from beginning to end.
- Creating a program changes the displayed weekly schedule.
- "Save for later" preserves active-workout progress.
- Finishing a workout creates a summary and history entry.
- Exercise search and "Add to session" update real prototype state.

## Phase 4: Correct Product-State Behavior

1. Hide advertisements during the ad-free trial.
2. Show the ad preview only after simulated trial expiry.
3. Keep the profile plan, trial countdown, and advertisement state consistent.
4. Make the program floating action button open program creation.
5. Make subscription and notification controls open their prototype flows.
6. Remove or disable any remaining controls that have no action.

### Acceptance

- Trial users never see ads.
- Free-plan users see the ad preview.
- Visible plan information and application behavior always agree.
- Every enabled control produces a clear result.

## Phase 5: Accessibility and Responsive QA

1. Add meaningful semantics and labels to interactive controls.
2. Verify minimum touch-target sizes.
3. Check color contrast in light and dark themes.
4. Test compact, normal, and large Android screen sizes.
5. Test increased font and display scaling.
6. Fix clipping or overflow in navigation, metrics, cards, and dialogs.

### Acceptance

- Core screens remain readable and operable in light and dark themes.
- Core journeys work at common Android screen sizes.
- Important content remains usable at 200% font scaling.
- Automated accessibility checks report no critical issues.

## Phase 6: Tests and Final Validation

1. Add unit tests for trial transitions and supplement recurrence.
2. Add Compose UI tests for:
   - Registration and onboarding.
   - Program creation.
   - Supplement creation and completion.
   - Active-workout pause and resume.
   - Workout completion and history.
   - Progression suggestion acceptance.
   - Trial-to-free-plan transition.
   - Light and dark themes.
3. Run:
   - `gradlew.bat assembleDebug`
   - `gradlew.bat test`
   - `gradlew.bat lint`
   - Connected Compose UI tests on an emulator.
4. Complete a manual Mission 3 acceptance checklist on compact and standard
   emulator profiles.

### Acceptance

- Build, unit tests, UI tests, and lint pass.
- Every prototype acceptance criterion in `PLAN.md` has recorded validation.
- No enabled placeholder controls remain.

## Recommended Implementation Order

1. Build tooling and Git baseline.
2. Shared prototype models and state holder.
3. Authentication, onboarding, and trial state.
4. Supplements.
5. Program builder and exercise selection.
6. Active workout, summary, and history.
7. Progression and trial-expiry flows.
8. Accessibility, responsive testing, and final acceptance validation.

## Completion Definition

The remediation is complete when the project builds reproducibly and every Stage 1
core journey can be demonstrated from beginning to end in both light and dark
themes at common Android screen sizes.

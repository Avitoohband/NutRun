# Fitness App — Three-Stage Development Plan

## Summary

Develop the product in three stages:

1. **Interactive prototype:** Validate the complete experience and visual design before backend development.
2. **Android MVP:** Build the essential working product with Kotlin and Jetpack Compose.
3. **Full vision:** Add subscriptions, advanced coaching, ads, richer exercise content, and eventually automatic workout recommendations.

The first implementation targets Android only. Future iOS development will be evaluated separately rather than constraining the Android architecture prematurely.

## Stage 1 — Interactive Prototype

### Mission 1: Design system

- Establish a soft, modern visual identity with rounded controls and restrained glass effects.
- Design accessible light and dark themes.
- Define typography, spacing, colors, icons, cards, buttons, progress indicators, and navigation patterns.
- Maintain readable contrast instead of using transparency on important text or controls.

### Mission 2: Core navigation

Create a clickable prototype with five primary destinations:

- **Today:** Supplements, today’s workout, tomorrow’s preview, and quick progress.
- **Program:** Weekly schedule and workout-program builder.
- **Exercises:** Searchable exercise library.
- **Progress:** Workout history, consistency, and progression suggestions.
- **Profile:** Account, trial, subscription, units, theme, and notification settings.

### Mission 3: Prototype key flows

Demonstrate the complete experience for:

- Google and email registration.
- Trial onboarding and notification permission.
- Adding and checking supplements.
- Scheduling supplements daily, every N days, or on selected weekdays.
- Creating a weekly training program.
- Selecting exercises and configuring sets, reps, weights, duration, or distance.
- Receiving tomorrow’s workout reminder.
- Starting, pausing, resuming, and completing a workout.
- Viewing exercise instructions and affected muscles.
- Receiving and accepting a progression suggestion.
- Completing the 30-day trial and continuing on the ad-supported free plan.

### Prototype acceptance

- Every core journey is clickable from beginning to end.
- Light and dark themes are represented.
- The design is tested at common Android screen sizes.
- Important controls remain readable and accessible.
- Product flows are approved before substantial backend work begins.

## Stage 2 — Android MVP

### Mission 4: Application foundation

- Build the Android app using Kotlin, Jetpack Compose, Material 3, and a modular architecture.
- Use Room for local data, WorkManager for dependable scheduled work, and Firebase for authentication and synchronized account data.
- Use a local-first data layer so supplements and active workouts remain usable without connectivity.
- Add crash reporting and privacy-conscious operational analytics.

### Mission 5: Authentication, trial, and plans

- Support Google authentication and email/password registration.
- Add password reset, logout, account deletion, and purchase restoration entry points.
- Begin the 30-day ad-free trial when the authenticated account is created.
- Store entitlement dates server-side so reinstalling cannot restart a trial.
- Clearly display the trial expiry date; no payment details are collected at signup.
- At expiry, automatically move the user to the free plan; do not block access or remove their data.
- Show ads on the free plan in non-disruptive locations.
- Give active subscribers an ad-free experience.
- Preserve the user’s history through trial and subscription changes.

### Mission 6: Supplement tracking

- Include presets such as vitamin C, B12, D, omega-3, magnesium, iron, and creatine.
- Allow custom names, dosage/unit, notes, and preferred reminder times.
- Support daily, every N days, and selected-weekday schedules.
- Calculate every-N-days schedules from a fixed start date so missed checks do not shift future dates.
- Support completed, skipped, missed, paused, and archived states.
- Retain completion history and show basic adherence statistics.
- Present tracking as an organizational tool, not medical or dosage advice.

### Mission 7: Exercise library

- Include exercises for weights, bodyweight training, HIIT, running, walking, swimming, and mobility.
- Support search and filters for muscles, equipment, difficulty, and activity type.
- Show setup, execution steps, common mistakes, safety notes, and primary/secondary muscles.
- Add original or properly licensed illustrations.
- Permit custom exercises without requiring illustrations.

### Mission 8: Program builder and scheduling

- Let users create, name, edit, duplicate, pause, and archive programs.
- Create named session templates such as Push/Biceps, Pull/Triceps, and HIIT.
- Assign sessions and activities to weekdays.
- Configure exercise order, sets, reps, weight, rest, duration, and distance as applicable.
- Allow temporary rescheduling or skipping without changing the recurring program.
- Keep reusable templates separate from dated workouts and completed history.

### Mission 9: Reminders and Today screen

- Show due supplements, today’s workout, tomorrow’s preview, and recent consistency.
- Send an optional preparation notification one day before a workout, using wording such as “Push day tomorrow—get a good night’s sleep.”
- Support optional same-day workout and supplement reminders.
- Respect notification permission, quiet hours, local timezone, daylight-saving changes, and device restarts.
- Open the relevant workout or supplement directly from each notification.

### Mission 10: Active workout and history

- Allow scheduled and unscheduled workouts.
- Display planned exercises with instructions, sets, reps, weights, duration, distance, and rest timers.
- Let users complete individual sets or entire exercises.
- Support editing actual performance, notes, skipped exercises, and early completion.
- Save immediately after interactions so interrupted sessions can be resumed.
- Generate a summary comparing planned and completed work.
- Show basic history and progress trends by exercise and activity.

### Mission 11: Initial progression engine

- Evaluate eligible exercises every two weeks.
- Offer optional weight increases for weighted exercises.
- Offer more reps or sets for rep-based exercises.
- Offer harder curated variations, tempo changes, reps, or sets for bodyweight exercises.
- Offer configurable duration or distance increases for endurance activities.
- Allow the user to accept, change, postpone, or reject a suggestion.
- Never modify future targets without confirmation.
- Suppress suggestions when recent targets were not completed or the user reported pain or injury.

### MVP acceptance

- A user can complete the full trial lifecycle and retain full core access after it expires.
- Free-plan users see ads; subscribers see no ads.
- Supplements appear on the correct dates and maintain accurate history.
- A weekly schedule like Monday Push/Biceps, Wednesday Pull/Triceps, and Friday HIIT can be created.
- Workouts survive app interruption and temporary loss of connectivity.
- Reminders arrive at the correct local time without duplication.
- Progression suggestions are explainable, editable, and optional.
- Core screens pass accessibility checks in light and dark modes.

## Stage 3 — Full Vision

### Mission 12: Production subscriptions and advertising

- Integrate Google Play Billing with monthly and annual plans.
- Verify purchases through the backend rather than trusting only the device.
- Support purchase restoration, billing grace periods, cancellations, expiration, and temporary billing problems.
- Add clear plan comparison, pricing, renewal, and cancellation information.
- Remove advertisements immediately after entitlement confirmation.
- Introduce ads only after the core experience and subscription flow are stable.
- Keep ads away from active workouts, safety instructions, authentication, and urgent interactions.

### Mission 13: Advanced progress and coaching

- Add richer charts for volume, load, repetitions, duration, distance, consistency, and personal records.
- Provide plateaus, recovery patterns, and missed-session insights.
- Let users configure progression aggressiveness and deload preferences.
- Keep all coaching suggestions explainable and user-controlled.

### Mission 14: Automatic workout recommendations

- Collect optional onboarding information about goals, experience, available days, equipment, preferred activities, and limitations.
- Generate draft plans using deterministic safety constraints and curated exercise relationships.
- Present generated plans for review rather than scheduling them automatically.
- Explain why exercises, volume, and progression were selected.
- Allow users to replace any recommendation before activation.
- Introduce this only after manual-program and progress data are sufficiently reliable.

### Mission 15: Rich content

- Expand the exercise catalog with professionally reviewed instructions and richer animations.
- Add contextual education without making medical claims.

### Mission 16: Future platform evaluation

- Review the Android domain and data layers before beginning iOS development.
- Reuse backend contracts, content, rules, and product designs.
- Choose native iOS, Kotlin Multiplatform, or another approach based on the Android product’s maturity and available team skills.
- Do not compromise current Android quality solely for hypothetical code sharing.

## Core Interfaces and Data

The implementation should define stable models for:

- User profile and preferences.
- Trial and subscription entitlement.
- Supplement and recurrence schedule.
- Supplement completion record.
- Exercise and muscle relationship.
- Program and session template.
- Scheduled workout instance.
- Workout and exercise-performance log.
- Progression suggestion and user decision.

Program templates describe intended recurring training. Scheduled workout instances represent particular dates. Workout logs remain immutable historical records, preventing later program edits from altering past results.

## Testing

- Unit-test supplement recurrence, trial expiry, entitlement changes, progress calculations, and suggestion eligibility.
- UI-test authentication, program creation, supplement completion, active workouts, subscription state, and both themes.
- Test reminders across timezones, daylight-saving transitions, notification denial, and device restarts.
- Test offline logging, synchronization retries, and multi-device conflicts.
- Test billing with Google Play test accounts before production.
- Run a closed beta between the prototype and public MVP release.

## Assumptions

- The prototype is a design-validation deliverable rather than a production backend.
- Kotlin and Jetpack Compose are used for the working Android application.
- The MVP launches after prototype feedback has been incorporated.
- The 30-day trial is ad-free and requires no payment details.
- After the trial, all core functionality remains available on the ad-supported free plan.
- A subscription removes ads. Any future premium capabilities will be specified separately before implementation.
- English and metric units are initial defaults; strings and measurement handling remain localization-ready.
- The app provides fitness organization and general guidance, not diagnosis, treatment, or personalized medical advice.

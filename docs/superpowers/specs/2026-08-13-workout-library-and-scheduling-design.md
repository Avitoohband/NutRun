# Workout Library and Weekly Scheduling Design

Date: 2026-08-13
Issue: GitHub #2
Status: Approved

## Objective

Turn the Training tab into two related tools:

1. A weekly schedule that assigns reusable workouts to weekdays or marks a day as rest.
2. A workout library where users create, edit, start, and delete reusable workouts.

Also expand the built-in exercise catalog to at least 220 useful exercises, support reusable custom exercises, make exercise set counts editable per workout, and make Profile Settings the only owner of Metric/Imperial preferences.

## Scope

This change includes:

- At least 220 stable built-in exercises across strength, machines, cables, bodyweight, calisthenics, cardio, mobility, rehabilitation, and home training.
- Required additions such as Pike Push-up, Pull-up variants, and Planche progressions.
- Account-scoped custom exercises with a required name and optional metadata.
- Reusable workout templates independent of weekdays.
- A weekly schedule that supports multiple workouts per day or an explicit Rest Day.
- Per-workout exercise targets, including editable set counts.
- Profile-owned weight units throughout training, progress, and history.
- Backward-compatible decoding of existing training payloads.
- Updated training reminders based on the new weekly schedule.

This change does not include:

- Sharing workouts between accounts.
- Cloud catalog publishing or community workouts.
- Multiple named schedules, rotating multi-week programs, or calendar-period programming.
- Deleting built-in exercises.
- A separate exercise-management screen outside the workout editor.

## Domain Model

### Exercise Catalog

The exercise catalog is the union of:

- Immutable built-in exercises with stable predefined IDs.
- Account-scoped custom exercises with UUID IDs.

A custom exercise requires only `name`. The following fields are optional:

- `category`
- `primaryMuscles`
- `secondaryMuscles`
- `instructions`
- `safetyNote`
- `defaultSets`
- `defaultReps`
- default weight, duration, or distance where applicable

Blank optional fields use neutral display defaults and remain searchable when populated. Custom names must be unique within the user's custom catalog after trimming and case folding. A custom exercise can be reused by any workout.

The built-in catalog must contain at least 220 meaningful exercises. IDs must be unique and existing IDs must not be removed or changed. The catalog must cover free weights, machines, cables, bodyweight, calisthenics skills and progressions, cardio, mobility, rehabilitation/prehabilitation, and home training without padding the count with duplicate naming variants.

### Workout Templates

`WorkoutTemplate` contains:

- Stable `id`
- User-editable `name`
- Ordered exercise targets
- Optional guidance lines

An exercise target remains a workout-specific snapshot. Its set count, rep range, weight, duration, distance, intensity guidance, and alternative group do not change the catalog exercise or targets in other workouts.

Set counts are editable from `1` through `20`. Existing rep, weight, duration, and distance validation remains in force. The repository rejects duplicate exercise IDs within one template.

An empty workout can be saved but cannot be started.

### Weekly Day Plans

`WeeklyDayPlan` contains:

- A `DayOfWeek`
- An ordered list of unique workout-template IDs
- `isRestDay`

Each weekday can contain multiple workouts. Rest Day is mutually exclusive with workout assignments:

- Enabling Rest Day clears all workout assignments for that weekday.
- Assigning any workout turns Rest Day off.
- Removing the final workout leaves the day unplanned rather than automatically marking it as rest.

A workout may be assigned to multiple weekdays. Assignment order is preserved and controls display and grouped reminder order.

Date-specific move and skip overrides continue to reference a workout-template ID and original date. They apply to one occurrence and do not mutate the recurring weekly plan.

### Deletion Rules

Deleting a workout template:

- Requires confirmation.
- Is blocked while that workout is active.
- Removes the template from all recurring weekday assignments.
- Removes future date-specific overrides for that template.
- Preserves completed workout history and the exercise snapshots stored in that history.

Removing an exercise from a workout affects only that workout. Custom catalog deletion is outside this change.

## Persistence and Compatibility

The existing Room `training_state` entity remains the persistence boundary. REST and MCP contracts remain unchanged because the new fields travel inside the existing training JSON payload.

The new payload is versioned and stores:

- `workoutTemplates`
- `weeklyDayPlans`
- `customExercises`
- Existing supplements, history, workout history, active workout state, overrides, timer settings, and progression state

The decoder supports both shapes:

- New payloads decode directly.
- Legacy `sessions` payloads become workout templates with one weekday assignment each.

Legacy migration rules:

- Preserve every session ID, exercise target ID, name, exercise configuration, guidance line, and active-workout reference.
- Preserve all workout history and date-specific overrides.
- Collapse duplicate assignments while preserving their first order.
- Discard dangling day-plan IDs without deleting valid templates or history.
- Decode legacy `usesMetricUnits` only as a fallback when no profile unit preference is available.
- Save the new shape through the normal account-scoped persistence path after a user mutation; no destructive Room migration is required.

For new accounts, the default plan uses one reusable `Walk or Swim` template assigned to Sunday, Tuesday, and Thursday. The strength templates are assigned to Monday, Wednesday, and Friday. Saturday is explicitly a Rest Day.

## Unit Ownership

The profile's preferred unit system is the single source of truth.

- Measurements remain stored canonically in kilograms, centimeters, kilometers, and milliliters.
- Metric/Imperial selectors are removed from Training, active workout, Progress, workout details, and workout-history editing.
- Saving a unit change in Profile updates all training and progress displays on the next observed profile emission.
- Changing units converts display and input values without reinterpreting or rewriting canonical measurements.
- Progression increments use the current profile unit system.
- The legacy training-level unit field remains decode-only for compatibility and is no longer independently persisted or editable.

The account-scoped training runtime observes the active profile's preferred units. During account transitions, training mutations remain gated until both the training payload and profile unit preference belong to the active account.

## Training Screen

### Weekly Schedule

The upper section is `Weekly schedule` and displays seven weekday rows.

Each row:

- Accents the current local weekday.
- Lists assigned workouts in order.
- Shows `Rest Day` when explicitly selected.
- Shows `Unplanned` when it has neither workouts nor Rest Day.
- Provides an assign action that opens a multi-select workout picker.
- Allows an assigned workout to be removed from that day without deleting it.
- Opens workout details and Start when an assigned workout is selected.

The multi-select picker lists reusable workouts from the library. Saving replaces that day's assignment list atomically. Selecting at least one workout disables Rest Day.

### Workout Library

The lower section is `Workout library`.

Each workout card shows:

- Workout name
- Exercise count
- Assigned weekdays
- Start, Edit, and Delete actions

The section header contains `Create workout`. New workouts start empty and open directly in the editor. Delete requires confirmation and follows the deletion rules above.

### Workout Editor

The editor displays the current workout exercises first. Each target supports:

- Removal with confirmation
- Set-count input or stepper from 1 to 20
- Existing rep, weight, duration, distance, and guidance controls where applicable

Below the current list, the exercise catalog supports case-insensitive search and category filtering across built-in and custom exercises. Exercises already in the workout are disabled.

`Create custom exercise` opens a quick form:

- Name is required and visible immediately.
- Optional details are expandable.
- Saving validates uniqueness, adds the exercise to the account catalog, and immediately adds it to the current workout.

## Reminder Behavior

Training reminders resolve workouts through weekly day plans rather than a weekday stored on each template.

- Previous-day and same-day reminders include every workout assigned to the target weekday.
- Explicit Rest Days and unplanned days produce no training reminder.
- Date-specific moves and skips retain their current precedence.
- Grouped notification order follows the weekly assignment order.
- Editing templates, changing assignments, setting Rest Day, deleting a workout, changing timezone, or changing reminder settings reschedules both training jobs.

## Error Handling and Data Safety

- Reject blank workout names and blank custom-exercise names.
- Reject case-insensitive duplicate custom-exercise names.
- Reject duplicate exercise targets within one workout.
- Clamp or reject invalid set counts outside `1..20` at UI and domain boundaries.
- Gate mutations until account-scoped training and profile-unit restoration completes.
- Persist template and schedule mutations through one account-safe payload update.
- Preserve the prior in-memory state and show an error if durable persistence fails.
- Never delete workout history when a template or recurring assignment is removed.

## Validation

Unit tests must cover:

- At least 220 unique stable built-in IDs and balanced category coverage.
- Required new exercises and search metadata.
- Custom exercise validation, reuse, account isolation, and JSON round trips.
- Legacy session decoding and migration into templates and day plans.
- Multi-workout weekdays, assignment ordering, Rest Day exclusivity, and unplanned days.
- Template deletion, active-workout protection, override cleanup, and history preservation.
- Per-workout set counts and isolation between templates.
- Profile-owned units, canonical measurement preservation, and progression increments.
- Reminder grouping, Rest Day suppression, and date-specific overrides.

Compose and instrumentation tests must cover:

- Weekly schedule and workout library separation.
- Today's schedule marker.
- Multi-select assignment and removal.
- Rest Day selection and replacement.
- Workout creation, editing, starting, and confirmed deletion.
- Set-count controls and validation.
- Catalog search, category filters, required new exercises, and custom exercise creation.
- Absence of unit selectors outside Profile and immediate display updates after a profile unit change.
- Backward-compatible restoration of an existing account.

Final validation runs:

- `testDebugUnitTest`
- Room migration and connected instrumentation tests
- `lintDebug`
- `assembleDebug`
- `assembleDebugAndroidTest`
- `connectedDebugAndroidTest`

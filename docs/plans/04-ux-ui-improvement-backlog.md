# NutRun UX/UI Improvement Backlog

**Status:** Detailed implementation plans complete after emulator and code audit on 2026-08-21. Tasks 10, 11, and 12 are implemented and validated; Tasks 13-19 remain planned.

**Relationship to customer testing:** GitHub issue #5 remains the acceptance tracker for issue #2. These tasks are follow-up improvements and should not replace or close that customer test.

## Audit Method

- Installed the latest debug build on `emulator-5554` (`Pixel_10(AVD)`, 1080 x 2424).
- Entered the fixed debug demo account and inspected Today, Training, an active workout, Nutrition, Walk, Progress, Profile, Health Details, Notification Settings, and Manage Supplements.
- Captured Android UI hierarchies and checked the matching Compose implementation and existing instrumentation coverage.
- Direct Windows screenshot automation was unavailable because the desktop helper failed sandbox initialization. Android UI hierarchy inspection and connected emulator interaction remained available.

## Main Findings

### Critical Workflows

1. An active six-exercise workout expands to roughly ten screens of set rows. Cancel and Finish are only available after scrolling to the bottom, and all exercises are expanded at once.
2. Weekly scheduling and the reusable workout library share one long list. Users must scroll past seven large weekday cards to reach workout management.
3. Workout assignment and editing use dialogs that do not scale well as the workout and exercise catalogs grow.

### Consistency and Discoverability

4. Water and training reminder times are plain `HH:mm` fields, while supplement times have a clock picker. The Save action is below every supplement row and can be several screens away.
5. Birth date, workout date, and reschedule date use raw `YYYY-MM-DD` text entry instead of a date picker.
6. Today makes water actionable, but visually similar protein and walk summary cards do nothing. The long-press water amount menu is also undiscoverable.
7. Progress mainly presents totals and lists; it does not yet help users understand trends over time.

### Quality and Accessibility

8. Several clickable cards and icon controls have incomplete merged semantics. TalkBack users may hear an unlabeled clickable container or lose the relationship between a value and its action.
9. Set logging places weight, repetitions, RPE, and completion in one dense row without numeric keyboard configuration or inline field errors.
10. The app uses default Material color schemes and repeated page headings, so screen hierarchy and NutRun identity still feel prototype-like.

## Development Tasks
Detailed execution plans:

| Task | Implementation plan |
| --- | --- |
| 10 | [`Active Workout Focus Mode`](../superpowers/plans/2026-08-21-task-10-active-workout-focus-mode.md) |
| 11 | [`Training Information Architecture`](../superpowers/plans/2026-08-21-task-11-training-information-architecture.md) |
| 12 | [`Reminder Settings and Time Controls`](../superpowers/plans/2026-08-21-task-12-reminder-settings-time-controls.md) |
| 13 | [`Form Components and Validation`](../superpowers/plans/2026-08-21-task-13-form-components-validation.md) |
| 14 | [`Today Dashboard`](../superpowers/plans/2026-08-21-task-14-today-dashboard.md) |
| 15 | [`Accessibility and Responsive Foundation`](../superpowers/plans/2026-08-21-task-15-accessibility-responsive-foundation.md) |
| 16 | [`Progress Trends`](../superpowers/plans/2026-08-21-task-16-progress-trends.md) |
| 17 | [`Nutrition Logging Refinement`](../superpowers/plans/2026-08-21-task-17-nutrition-logging-refinement.md) |
| 18 | [`Walk Recording Confidence`](../superpowers/plans/2026-08-21-task-18-walk-recording-confidence.md) |
| 19 | [`Authentication, Profile, and Product Polish`](../superpowers/plans/2026-08-21-task-19-auth-profile-product-polish.md) |

### Task 10: Active Workout Focus Mode

**Priority:** P0
**Estimate:** 4-6 development days
**Status:** Completed and validated on 2026-08-21.

Replace the fully expanded workout log with a focused, low-friction training experience.

Acceptance criteria:

- Keep workout progress, elapsed time, rest status, Cancel, and Finish visible without scrolling to the end.
- Show one expanded exercise at a time, or collapsible exercise sections with completed-state summaries.
- Provide Previous and Next exercise controls and preserve partially entered values while navigating.
- Use numeric keyboards and inline validation for weight, repetitions, duration, and RPE.
- Make set completion a large labeled action and clearly indicate when it starts the rest timer.
- Show previous-set values beside each input with a one-tap copy option.
- Confirm Finish when targets are incomplete and summarize what will be saved.
- Add Compose tests for long workouts, focus movement, set validation, persistent bottom actions, and incomplete completion.

### Task 11: Training Information Architecture and Full-Screen Editor

**Priority:** P0
**Estimate:** 5-7 development days
**Status:** Completed and validated on 2026-08-21.

Make schedule planning and reusable workout management independently easy to reach.

Acceptance criteria:

- Add a segmented control or tabs for `Schedule` and `Workouts` instead of one long combined list.
- Open on the most recently used view and provide a direct Today shortcut in Schedule.
- Use compact weekday rows that expand for assignments; retain the Today marker and Rest Day state.
- Make the assignment chooser scrollable and searchable, show selected count, and support reordering multiple assigned workouts.
- Replace the workout editor dialog with a full-screen editor.
- Separate current exercises from Add exercises; keep search/filter state while adding multiple exercises.
- Make Start, Edit, Duplicate, and Delete actions explicit without three cramped trailing icons.
- Add tests with at least 20 workouts and the full exercise catalog to prove dialogs and lists remain usable.

### Task 12: Reminder Settings and Time Controls

**Priority:** P1
**Estimate:** 2-3 development days

Unify reminder interactions and prevent unsaved changes from being hidden at the bottom.

Acceptance criteria:

- Reuse `ReminderTimeInput` for water and training times so every time supports typing and the device-format clock picker.
- Remove `HH:mm` from user-facing labels; use `First reminder`, `Last reminder`, `Day-before reminder`, and `Training-day reminder`.
- Collapse disabled reminder sections while retaining their saved values.
- Show each section's next scheduled reminder in its collapsed summary.
- Use autosave with clear saved/error feedback, or keep a sticky Save action visible while scrolling.
- Keep supplement master and per-item toggles independent and explain this relationship in one short supporting line.
- Add tests for unsaved-change navigation, picker and typed input parity, and long supplement lists.

### Task 13: Form Components and Validation

**Priority:** P1
**Estimate:** 3-4 development days

Replace fragile free-form inputs with reusable, validated components.

Acceptance criteria:

- Add a reusable date field with a Material date picker for birth date, workout history, and training rescheduling.
- Configure decimal or integer keyboards for every numeric field.
- Validate health, weight, food, water, timer, and workout inputs with field-specific inline errors and documented bounds.
- Never silently replace invalid values with defaults when saving food or hydration settings.
- Style read-only email as read-only information rather than an editable-looking input.
- Warn before leaving a form with unsaved changes.
- Add unit tests for bounds and Compose tests for error placement, date selection, and keyboard actions.

### Task 14: Today Dashboard Actions and Empty States

**Priority:** P1
**Estimate:** 2-3 development days

Make Today a reliable launch point rather than a passive summary.

Acceptance criteria:

- Make protein navigate to Nutrition and last walk navigate to Walk or its latest details.
- Give every actionable summary card a visible affordance and a complete accessibility label.
- Add direct quick actions for water, food, and today's workout without relying on hidden long press.
- Show a useful empty state when no supplements are due today, with an action to Manage Supplements.
- Keep completed supplements compact and visually secondary while preserving interactivity.
- Ensure the supplements section is not obscured by bottom navigation at large font sizes.
- Add navigation, empty-state, and 200% font-scale Compose tests.

### Task 15: Accessibility and Responsive UI Foundation

**Priority:** P1
**Estimate:** 3-5 development days

Create shared UI primitives and make the complete app usable with accessibility services and varied displays.

Acceptance criteria:

- Add semantic roles, state descriptions, and combined labels for clickable cards, switches, set rows, and icon actions.
- Verify TalkBack traversal follows visual task order and does not announce duplicate headings.
- Meet 48 dp touch targets and WCAG AA contrast in light and dark themes.
- Support 200% font scale without clipped text, overlapping controls, or unreachable actions.
- Add compact-width and landscape layouts for set rows, filter chips, and summary metrics.
- Centralize spacing, typography, colors, screen headers, empty states, and feedback components.
- Add automated accessibility checks where supported and a documented TalkBack/manual checklist.

### Task 16: Progress Trends and Drill-Down

**Priority:** P2
**Estimate:** 5-8 development days

Turn Progress from a collection of totals into an analysis surface.

Acceptance criteria:

- Add 7-day, 30-day, 90-day, and all-time range controls.
- Graph weight, workout frequency, training volume, walking distance, and hydration/calorie adherence.
- Allow exercise-level progression drill-down for weight, repetitions, estimated 1RM, and volume.
- Use profile units everywhere and format dates consistently with the rest of the app.
- Separate Health Connect setup/status from the primary progress insights.
- Add useful first-use empty states that link to the relevant logging screen.
- Include chart accessibility summaries and deterministic chart-data unit tests.

### Task 17: Nutrition Logging Refinement

**Priority:** P2
**Estimate:** 4-6 development days

Reduce the number of steps and errors involved in everyday food logging.

Acceptance criteria:

- Show calorie and macro goal progress, not only consumed totals.
- Debounce food search, distinguish remote/loading/offline/no-result states, and preserve the current query when appropriate.
- Provide recent foods, favorites, and saved meals as compact quick-add groups that can collapse.
- Add clear per-field validation for serving size, calories, and macros; reject negative or unrealistic values.
- Add Undo feedback after deleting food, favorites, or saved meals.
- Make the water amount menu discoverable with a menu/chevron while preserving one-tap quick logging.
- Add tests for invalid nutrition entries, deletion undo, search states, and quick logging.

### Task 18: Walk Recording Confidence and Safety

**Priority:** P2
**Estimate:** 4-6 development days

Make users confident that a walk is actually being recorded and will be saved correctly.

Acceptance criteria:

- Explain location and activity permissions before requesting them; request notification permission only when required by the recording flow.
- Show GPS readiness and accuracy before Start, plus a clear acquiring-signal state.
- Keep elapsed time, distance, steps, GPS status, Pause, and Finish visible during recording.
- Confirm Finish and provide a separate discard action with explicit data-loss language.
- Show route-loading, no-GPS, permission-denied, and map-configuration states distinctly.
- Keep the completed route reset behavior and make saved-history navigation obvious.
- Add service-state, permission, process-recovery, and active-screen interaction tests.

### Task 19: Authentication, Profile, and Product Polish

**Priority:** P2
**Estimate:** 3-5 development days

Finish the first-run and settings experience while establishing a recognizable NutRun visual language.

Acceptance criteria:

- Add password visibility, inline authentication errors, loading state, keyboard actions, and forgot-password flow where supported.
- Split onboarding into short steps with progress, date picker, measurement validation, and a health-estimate preview before confirmation.
- Group Profile into Account, Health, Notifications, Appearance, Subscription, and Data sections.
- Visually separate Sign out from destructive Delete account and require explicit account identity confirmation before deletion.
- Remove duplicated screen headings beneath the top app bar and use one consistent back-navigation pattern.
- Define NutRun light/dark colors, typography, icon treatment, and reusable feedback states without changing domain behavior.
- Add first-run, rotation/process-recreation, and destructive-action Compose tests.

## Recommended Execution Order

1. Task 10 - Active Workout Focus Mode.
2. Task 11 - Training Information Architecture and Full-Screen Editor.
3. Task 12 - Reminder Settings and Time Controls.
4. Task 13 - Form Components and Validation.
5. Task 15 - Accessibility and Responsive UI Foundation, applied alongside Tasks 10-14 and completed as a dedicated audit.
6. Task 14 - Today Dashboard Actions and Empty States.
7. Tasks 16-19 can proceed after the core daily workflows are stable.

## Quick-Wins Slice

A small first sprint can deliver visible improvement in approximately 2-4 development days:

- Add clock pickers to water and training reminder fields.
- Keep Notification Settings Save visible or autosave each section.
- Add date pickers and numeric keyboard types to existing forms.
- Make Today protein and walk cards navigable and label all clickable cards for accessibility.
- Add a no-supplements-due empty state.
- Format weight-history dates consistently.

## Validation for Every Task

- Add focused ViewModel/domain tests for new state and validation rules.
- Add Compose interaction tests for every primary, empty, loading, error, and destructive state.
- Run `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`.
- Run focused connected tests during development and the full `connectedDebugAndroidTest` before merging.
- Manually verify light/dark themes, 200% font scale, compact portrait, landscape, TalkBack, and process recreation.

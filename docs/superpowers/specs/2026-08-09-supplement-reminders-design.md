# Supplement Reminders Design

## Goal

Add supplement reminders to Notification Settings. Users can pause all supplement reminders globally, configure each supplement independently, choose one reminder time per supplement, and enable or disable all individual reminders in one action.

## User Experience

Notification Settings gains a **Supplement reminders** section containing:

- A master switch that pauses or resumes supplement notifications without changing individual switches or saved times.
- An **Enable all / Disable all** control above the supplement list. This updates every supplement's individual reminder switch while preserving each saved time.
- One row per supplement from Manage Supplements, showing its name, dose, individual reminder switch, and reminder time.
- A time field and Material clock picker for each supplement. Times respect the device's 12/24-hour preference.
- A **Manage supplements** action that opens the existing management screen.

Existing supplements upgrade with individual reminders disabled and a saved default time of `08:00`, preventing surprise notifications. Newly created supplements default to an enabled individual reminder at `08:00`. Add and Edit Supplement also expose the individual switch and time controls.

The master switch is only a global delivery gate. Turning it off retains every individual switch and time. The bulk action changes individual switches but never changes the master switch or any saved time.

## Persistence

Extend the persisted `Supplement` model with:

- `reminderEnabled: Boolean`
- `reminderMinute: Int`, measured after midnight and constrained to `0..1439`

These fields remain inside the existing per-user training-state JSON. Backward decoding assigns `reminderEnabled = false` and `reminderMinute = 480`.

Add a per-user Room entity for the device-local master setting and timezone. Add a migration that preserves all current profile, nutrition, hydration, training, walking, and reminder data. The global setting defaults to disabled for existing accounts.

Repository operations update one supplement or all individual reminder switches atomically. REST and MCP contracts remain unchanged because individual fields travel inside the existing training payload and the master setting is device-local.

## Scheduling And Delivery

Use one unique one-time WorkManager dispatcher per account. It calculates the earliest future reminder from supplements that are individually enabled, due on a selected weekday, and allowed by the master switch.

When work runs, it:

1. Rechecks the authenticated account, notification permission, master switch, local date, selected weekdays, individual switch, saved time, and completion status.
2. Groups all due and untaken supplements sharing the same scheduled minute into one notification listing names and doses.
3. Records delivery by account, local date, and scheduled minute to suppress duplicates.
4. Schedules the next eligible occurrence.

Delayed work may notify only on its intended local date. It never sends catch-up notifications after midnight. Work is rescheduled after supplement add, edit, removal, completion changes, bulk changes, master changes, login, reboot, or timezone change. It is cancelled on logout or while the master switch is off.

Tapping a supplement notification opens Today and focuses the supplements section using the existing consumable navigation request.

## Permission And Errors

Enabling the master switch, enabling an individual reminder, or enabling all requests notification permission on Android 13 and newer. A denied permission retains all settings and displays the existing permission-required state with an action to open Android notification settings.

Invalid typed times show inline validation and cannot be saved. The clock picker always returns a valid minute. With no supplements, the section shows an empty state and the Manage Supplements action.

## Testing

- Unit-test backward JSON decoding, default values, bulk switching, next-reminder calculation, weekday selection, passed times, midnight, timezone changes, grouping, completion suppression, and duplicate prevention.
- Add a Room migration test proving existing records survive and the master setting defaults safely.
- Add Compose tests for the master switch, Enable all/Disable all, individual switches, typed times, clock picker, empty state, permission denial, and Manage Supplements navigation.
- Run unit tests, migration tests, connected instrumentation tests when a device is available, lint, `assembleDebug`, and `assembleDebugAndroidTest`.

## Scope

Each supplement supports one reminder time for all of its selected weekdays. Exact alarms and notification action buttons are outside this change; WorkManager delivery remains best-effort.

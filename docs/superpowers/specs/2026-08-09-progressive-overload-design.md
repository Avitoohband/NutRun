# Progressive Overload Suggestions Design

## Goal

Give users conservative, explainable next-load suggestions from their recorded sets without automatically changing their training program.

## Architecture

A pure progression engine consumes one `ExerciseTarget`, recent `WorkoutRecord` values, and the selected unit system. It returns an optional typed suggestion containing the action, current load, suggested load, and explanation. `TrainingViewModel` exposes derived suggestions and does not persist them because workout history is already the source of truth.

Legacy `suggestionDecision` and `suggestedWeightKg` fields remain decode-compatible, but the new engine does not use the old hard-coded lat-pulldown update path. Suggestions recalculate after workout completion, history edits, history deletion, or unit changes.

## Rules

- Only weighted repetition targets are eligible.
- Fewer than the target number of logged sets, missing completed set data, or missing RPE data produces no suggestion.
- If every required set reaches the target's maximum repetition count and every RPE is at most 8, suggest increasing by 2.5 kg in metric mode or 5 lb in imperial mode.
- If the latest required sets meet the minimum repetitions but any RPE is at least 9, suggest keeping the current load.
- If each of the two newest attempts has an incomplete required set or a completed set below the minimum repetitions, suggest reducing the latest working load by 5 percent.
- Otherwise suggest keeping the current load so the advice never overreacts to one ordinary session.
- Suggestions are advisory text only and never alter exercise targets or prefilled set values.

## User Experience

Program session cards show concise suggestions before a workout starts. The active workout repeats the relevant suggestion beside each exercise so it remains visible while logging. Weight labels follow the current kg/lb setting.

## Testing

Unit tests cover increase, high-RPE hold, ordinary hold, two-attempt reduction, insufficient data, bodyweight/cardio exclusion, record edits/deletions through derived recalculation, and exact kg/lb display increments. Compose tests verify suggestion copy appears before and during a workout. Existing unit tests, instrumentation tests, lint, and debug assembly remain required.


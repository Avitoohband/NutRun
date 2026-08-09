# Task 1 Report: Pure Progression Engine

## Scope

Created the pure advisory engine in `app/src/main/java/com/avitoohband/nutrun/ProgressionSuggestions.kt` and its unit tests in `app/src/test/java/com/avitoohband/nutrun/ProgressionSuggestionsTest.kt`.

The engine returns typed `INCREASE`, `KEEP`, or `REDUCE` suggestions without changing the supplied target or workout history.

## Requirement Evidence

- Eligibility is limited to weighted repetition targets; bodyweight and duration/cardio targets return no suggestion.
- Relevant workouts are sorted by descending `finishedAtMillis`, then descending workout ID for deterministic timestamp ties. Caller order is never used to decide the latest attempt.
- Required set logs must be present once for every required set number and have a positive, consistent working weight plus valid reps and RPE. Missing or inconsistent data returns no suggestion.
- An increase requires all required sets at the target maximum reps and RPE at or below 8.
- A completed attempt at minimum reps with RPE 9 or higher, and ordinary completed attempts, produce `KEEP`.
- Two newest failed attempts, whether incomplete or below minimum reps, produce `REDUCE`.
- Metric increases are exactly 2.5 kg. Imperial increases add exactly 5 lb and convert the increment back to canonical kilograms. Five-percent reductions round to the nearest 2.5 kg or 5 lb respectively.
- The tests assert that the target and history values are unchanged after a suggestion.

## TDD Evidence

1. Added the branch tests before creating the production API.
2. Focused red command:
   `./gradlew.bat testDebugUnitTest --tests "com.avitoohband.nutrun.ProgressionSuggestionsTest"`
   failed as expected at test compilation with unresolved `progressionSuggestion` and `ProgressionAction` references.
3. Implemented the engine, then corrected a Kotlin `Sequence.map` non-local return compile error after confirming the exact compiler diagnostic. The next run exposed an inconsistent reduction-test timestamp fixture; the fixture was corrected so the two newest attempts were the intended misses.
4. Focused green command completed successfully with 11/11 progression tests passing.
5. Full unit command:
   `./gradlew.bat testDebugUnitTest`
   completed successfully. XML results report 58 tests, 0 failures, 0 errors, and 0 skipped.

## Self-Review

- Reviewed the engine and test files against the Task 1 brief and approved design.
- Confirmed no mutation operations are performed on `target` or `history`.
- Confirmed `git diff --check` has no whitespace errors.
- Confirmed the only implementation changes are the two Task 1 Kotlin files; this report is the requested Task 1 evidence artifact.

## Fix Round 1/5: Strictly Lower Reductions

The prior reduction rounding could return the current load or a heavier load when a five-percent reduction was rounded to the nearest 2.5 kg or 5 lb increment. This fix changes only reduction rounding:

- Metric reductions round `current * 0.95` to the nearest 0.5 kg.
- Imperial reductions round in pounds to the nearest 1 lb, then convert the result back to canonical kilograms.
- A rounded result is used only when it is positive and strictly below the current load. Otherwise the engine subtracts one resolution; if that is not positive, it returns the exact positive five-percent reduction.

Focused regression tests were added before the implementation change for metric and imperial 10 kg reductions plus sub-resolution light loads. The focused red run completed 15 tests with the five expected reduction failures. After the helper change, the focused suite passed 15/15. The full `./gradlew.bat testDebugUnitTest` suite also passed.

The reviewer-noted one-miss `KEEP` and equal-timestamp tie-break tests remain explicitly deferred and were not added in this scope.

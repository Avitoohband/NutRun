# Task 3 Report: Expand the Built-in Exercise Catalog

## Result
- Built-in catalog: 225 unique exercises.
- Legacy IDs: all 81 IDs from `origin/main` are asserted by `ExerciseCatalogCoverageTest`.
- Duplicate IDs: rejected by a catalog construction `require` check.

## Category Counts
- Free weights: 49
- Machine: 38
- Cable: 33
- Bodyweight: 16
- Calisthenics: 25
- Cardio: 10
- Mobility: 10
- Rehabilitation: 25
- Home: 19

## TDD Evidence
- RED command: `./gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ExerciseCatalogCoverageTest --tests com.avitoohband.nutrun.ProductionDomainTest --console=plain`
- RED result: three intended contract failures: minimum count, approved-category coverage, and required calisthenics search coverage.
- GREEN command: same command.
- GREEN result: `BUILD SUCCESSFUL`; 15 selected tests completed.

## Verification
- Focused catalog/domain/default-program command: `./gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ExerciseCatalogCoverageTest --tests com.avitoohband.nutrun.ProductionDomainTest --tests com.avitoohband.nutrun.DefaultTrainingProgramTest --console=plain`
- Result: `BUILD SUCCESSFUL`.
- Full JVM command (run once): `./gradlew.bat testDebugUnitTest --console=plain`
- Result: `BUILD SUCCESSFUL`.
- `git diff --check`: clean.

## Files Changed
- `app/src/main/java/com/avitoohband/nutrun/ExerciseCatalog.kt`
- `app/src/main/java/com/avitoohband/nutrun/ExerciseCatalogSeeds.kt`
- `app/src/test/java/com/avitoohband/nutrun/ProductionDomainTest.kt`
- `app/src/test/java/com/avitoohband/nutrun/ExerciseCatalogCoverageTest.kt`
- `.superpowers/sdd/2026-08-13-workout-library-and-scheduling/task-3-report.md`

## Self-review
- Confirmed the contract test uses the literal set of all 81 legacy IDs.
- Confirmed named calisthenics entries include `pike-push-up`, `pull-up`, `wide-grip-pull-up`, `neutral-grip-pull-up`, and the four required planche entries.
- Confirmed default-program lookup remains green.

## Concerns
- None remaining from automated verification.
## Fix Round 1

### Review Findings Addressed
- Replaced name-derived seeds with 144 literal `ExerciseSeed` records. Each record carries a fixed ID, display name, approved category, primary and secondary muscles, concise instructions, and a safety note.
- Made `ExerciseSeed` private and expose only converted `Exercise` values to the catalog.
- Added a complete literal `addedStableIds` manifest in `ExerciseCatalogCoverageTest`; the catalog's new IDs must equal it exactly, independently of display text.
- Added practical, case-insensitive search checks for every approved category, including `lats` -> `pull-up` and metadata searches against primary and secondary muscle fields.

### Metadata Audit
- Added records: 144; total built-in catalog: 225.
- Categories: Free weights 49, Machine 38, Cable 33, Bodyweight 16, Calisthenics 25, Cardio 10, Mobility 10, Rehabilitation 25, Home 19.
- Audited seed examples: `dumbbell-pullover` (Lats; Biceps, upper back), `pull-up` (Lats; Biceps, upper back), `band-external-rotation` (Rotator cuff, shoulders; Upper back), and `backpack-deadlift` (Hamstrings, glutes; Lower back, core).

### TDD and Verification
- RED: `./gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ExerciseCatalogCoverageTest --console=plain` failed at `practicalSearchFindsExercisesAcrossAllApprovedCategories` before metadata rewrite.
- GREEN: same command passed after literal seed rewrite.
- Focused: `./gradlew.bat testDebugUnitTest --tests com.avitoohband.nutrun.ExerciseCatalogCoverageTest --tests com.avitoohband.nutrun.ProductionDomainTest --tests com.avitoohband.nutrun.DefaultTrainingProgramTest --console=plain` passed.
- Full JVM: `./gradlew.bat testDebugUnitTest --console=plain` passed.
- Self-review: `git diff --check` passed; duplicate-ID fail-fast and default-program coverage retained.

### Changed Files
- `app/src/main/java/com/avitoohband/nutrun/ExerciseCatalog.kt`
- `app/src/main/java/com/avitoohband/nutrun/ExerciseCatalogSeeds.kt`
- `app/src/test/java/com/avitoohband/nutrun/ExerciseCatalogCoverageTest.kt`
- `.superpowers/sdd/2026-08-13-workout-library-and-scheduling/task-3-report.md`

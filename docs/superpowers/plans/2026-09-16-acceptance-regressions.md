# Acceptance Regression Remediation Plan

> For agentic workers: execute this before GTG work in the Issue 15 plan. Track every checkbox and preserve fresh test evidence.

**Goal:** Restore a trustworthy connected-test gate and fix the two acceptance defects reproduced on the Pixel 10 emulator.

**Baseline:** main at 4ab7d01. Evidence: ../../testing/2026-09-16-client-acceptance.md.

**Implementation status (2026-09-16):** Tasks 1-3 are complete on `codex/issue-15-gtg`. Task 4 remains pending by product-owner decision because emulator evidence cannot prove physical alerts or accessibility behavior on a phone.

## Task 1: Fix skipped and incomplete workout completion

**Files:** ActiveWorkoutSession.kt, ActiveWorkoutContent.kt, TrainingViewModel.kt; tests ActiveWorkoutSessionTest.kt and ActiveWorkoutContentTest.kt.

- [x] Add a failing test: create six independent targets, skip three, complete none, and request Finish. The incomplete review must appear for the remaining three.
- [x] Align the predicates. The denominator excludes skipped targets, so the review compares completed non-skipped logical targets with remaining non-skipped logical targets. Do not add skipped targets to the completed count.
- [x] Cover all-skipped, mixed complete/skipped/incomplete, and alternative groups.
- [x] Verify Finish anyway records skipped exercises separately and does not mark them complete.
- [x] Re-run the focused JVM/UI tests and repeat the emulator regression through the connected suite.

## Task 2: Fix duplicate Progress units

**Files:** ProgressContent.kt and ProgressAnalytics.kt; tests ProgressContentTest.kt and ProgressAnalyticsTest.kt.

- [x] Add exact metric and imperial assertions using nonzero weight and volume fixtures.
- [x] Reproduce Profile -> Imperial -> Progress. Baseline reads Training volume: 0 lb kg and Weight: 165.3 lb kg.
- [x] Assign unit formatting to one layer; formatters now return the complete display value.
- [x] Apply the rule to accessible summaries and expanded rows for weight, volume, distance, and percentages.
- [x] Confirm the change is display-only; canonical kg/cm storage and weight-history behavior are unchanged.

## Task 3: Repair deterministic instrumentation

**Files:** ProductionFlowTest.kt, ProfileContentTest.kt, AccessibilityResponsiveTest.kt, BottomNavAccessibilityTest.kt.

- [x] Wait for resolved login/home state before entering demo and wait for sign-out completion.
- [x] Scroll before asserting compact active-workout controls and lower Profile sections.
- [x] Make unit assertions independent of persisted emulator preference.
- [x] Diagnose the walk-details Back failure and use the activity back dispatcher for this Compose navigation path.
- [x] Run affected classes, then the complete connected suite. Final result: 140 passed, 0 failed, 0 skipped.

## Task 4: Physical checks

- [ ] On a phone, verify rest countdown while locked/backgrounded, one noticeable completion alert, and notification tap returning to the active workout.
- [ ] Verify permission-denied behavior and water/training/supplement reminder suppression.
- [ ] Check active-workout controls and sticky timer with TalkBack and large font.

Do not close #5 or #7 until Tasks 1-4 pass. Keep #15 open until GTG is implemented and accepted.

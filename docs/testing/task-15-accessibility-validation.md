# Task 15 — Accessibility and responsive validation

Date: 2026-08-23  
Branch: `main` (Task 15)  
Automated gate: `testDebugUnitTest`, `lintDebug`, `connectedDebugAndroidTest`

## Automated coverage

| Area | Tests |
|------|-------|
| Shared components | `NutRunComponentsTest` (6) |
| Semantics + compact layouts | `AccessibilityResponsiveTest` (7) |
| Bottom navigation selection | `BottomNavAccessibilityTest` (1) |
| Today dashboard (regression) | `TodayDashboardTest` (5) |
| Active workout compact | `ActiveWorkoutContentTest` |

## Manual matrix (emulator, font scale 1.0 and 2.0)

| Screen | Portrait 320dp | Landscape | Dark | TalkBack spot-check | Result |
|--------|----------------|-----------|------|---------------------|--------|
| Login / demo | Pass | Pass | Pass | Sign-in fields labeled | Pass |
| Onboarding | Pass | Pass | Pass | Form fields reachable | Pass |
| Today | Pass | Pass | Pass | Metrics announce value + action | Pass |
| Training schedule | Pass | Pass | Pass | Day cards and actions labeled | Pass |
| Workout library | Pass | Pass | Pass | Start disabled reason announced | Pass |
| Workout editor | Pass | Pass | Pass | Delete/remove icons labeled | Pass |
| Active workout | Pass | Pass | Pass | Set completion row state | Pass |
| Nutrition | Pass | Pass | Pass | Water/food actions reachable | Pass |
| Walk | Pass | Pass | Pass | Record controls ≥48dp | Pass |
| Progress | Pass | Pass | Pass | Charts have text context | Pass |
| Profile | Pass | Pass | Pass | Menu items labeled | Pass |
| Notifications | Pass | Pass | Pass | Reminder switches On/Off | Pass |

## Theme foundation

- Primary teal `#0B6E69` with light/dark Material 3 schemes
- Spacing tokens: 4/8/12/16/24/32 dp
- Card shapes capped at 8 dp radius
- Shared components: `NutRunScreen`, `NutRunMetric`, `NutRunEmptyState`, `NutRunInlineMessage`, `NutRunLoadingState`

## Device-only follow-ups

- Physical device TalkBack on Samsung/Pixel with system font scale 2.0
- Haptic feedback on timer skip (optional polish, not blocking)
- Customer hardware for walk GPS accuracy (Task 18 scope)

## Sign-off

Engineering validation complete for Task 15. Client acceptance tracked separately.

# Walk History Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the recorder map after finishing and provide complete, tappable walk-history details with the saved route.

**Architecture:** Split active and selected-history route flows in `NutRunViewModel`. Keep list/detail state in the Walk tab and reuse persisted `WalkSessionEntity` and `WalkPointEntity` data without a Room migration.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, Room, Google Maps Compose, JUnit, Android Compose Test.

## Global Constraints

- Preserve existing walk sessions and route points.
- Use device-local date and time.
- Do not change recording service behavior or sync contracts.
- Keep the offline route fallback working when Maps is not configured.

---

### Task 1: Walk Presentation Rules

**Files:**
- Create: `app/src/main/java/com/avitoohband/nutrun/WalkPresentation.kt`
- Create: `app/src/test/java/com/avitoohband/nutrun/WalkPresentationTest.kt`

**Interfaces:**
- Produces: `activeRouteSessionId(WalkSessionEntity?): String?`
- Produces: `formatWalkDate(Long): String`, `formatWalkTimeRange(WalkSessionEntity): String`
- Produces: `formatWalkDuration(Long): String`, `averageWalkPaceMinutesPerKm(WalkSessionEntity): Double?`

- [ ] **Step 1: Write failing presentation tests**

```kotlin
assertNull(activeRouteSessionId(null))
assertEquals("walk-1", activeRouteSessionId(walk))
assertEquals("Sunday, August 9, 2026", formatWalkDate(startedAt))
assertNull(averageWalkPaceMinutesPerKm(walk.copy(distanceMeters = 0.0)))
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.avitoohband.nutrun.WalkPresentationTest"`

- [ ] **Step 3: Implement the pure helpers**

```kotlin
fun activeRouteSessionId(activeWalk: WalkSessionEntity?): String? = activeWalk?.id

fun averageWalkPaceMinutesPerKm(walk: WalkSessionEntity): Double? =
    (walk.distanceMeters / 1_000.0).takeIf { it > 0.0 }
        ?.let { distanceKm -> walk.accumulatedDurationMillis / 60_000.0 / distanceKm }
```

- [ ] **Step 4: Run the focused test and confirm it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.avitoohband.nutrun.WalkPresentationTest"`

### Task 2: Independent Route State and Detail UI

**Files:**
- Modify: `app/src/main/java/com/avitoohband/nutrun/NutRunViewModel.kt`
- Modify: `app/src/main/java/com/avitoohband/nutrun/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/avitoohband/nutrun/ProductionFlowTest.kt`

**Interfaces:**
- Consumes: Task 1 formatting and pace helpers.
- Produces: `selectedWalkRoutePoints: StateFlow<List<WalkPointEntity>>`
- Produces: `selectCompletedWalk(String)` and `clearSelectedWalk()`.

- [ ] **Step 1: Add a failing UI assertion for actionable walk-history details**

```kotlin
composeRule.onAllNodesWithTag("walk-history-card")[0].performClick()
composeRule.onNodeWithTag("walk-details-heading").assertIsDisplayed()
composeRule.onNodeWithText("Average pace").assertIsDisplayed()
```

- [ ] **Step 2: Make the active route flow emit empty when there is no active walk**

```kotlin
val routePoints = repository.activeWalk
    .map(::activeRouteSessionId)
    .flatMapLatest { id -> id?.let(repository::walkPoints) ?: flowOf(emptyList()) }
```

- [ ] **Step 3: Add selected-history route state**

```kotlin
private val selectedWalkId = MutableStateFlow<String?>(null)
val selectedWalkRoutePoints = selectedWalkId
    .flatMapLatest { id -> id?.let(repository::walkPoints) ?: flowOf(emptyList()) }
```

- [ ] **Step 4: Build history rows and the full-screen detail view**

Use `ActionCard(onClick = ...)`, a Back icon button, `RouteMap`, and stable test tags. Show date, time range, distance, duration, steps, and average pace. Center configured Google Maps on the supplied route points.

- [ ] **Step 5: Run walk tests and Android validation**

Run: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`


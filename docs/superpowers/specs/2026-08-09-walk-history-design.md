# Walk History Detail Design

## Goal

Make the live walking map empty after a walk finishes while keeping every completed route available from walk history.

## Architecture

`NutRunViewModel` exposes two independent route streams. `routePoints` follows only `repository.activeWalk`; when the active session becomes null it emits an empty list. A selected-history-walk ID drives `selectedWalkRoutePoints`, so viewing an old route cannot repopulate the live recorder map.

The existing Walk tab owns list/detail navigation. Selecting a finished walk replaces the list with a full-screen detail view, and Back clears the selected ID. No database change is required because walk sessions and ordered route points are already persisted by session ID.

## User Experience

- The live map clears as soon as the finished session is no longer active.
- Finished walks are ordered newest first and show the local date, distance, duration, and steps.
- Tapping a finished walk opens a full-screen view with a Back action, route map, date, start/end time, distance, duration, steps, and average pace.
- Zero-distance walks display pace as unavailable instead of dividing by zero.
- A route with fewer than two points continues to use the existing empty-route presentation.
- Configured Google Maps centers on the saved route; the existing offline canvas remains the fallback.

## Testing

Pure presentation tests cover active-route selection, local date/time formatting, duration formatting, and pace calculation including zero distance. Compose coverage verifies that history rows are actionable and the detail screen exposes the expected route and metrics. Existing unit tests, instrumentation tests, lint, and debug assembly remain required.


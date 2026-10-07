package com.techvibedev.triptrace.trip

// Shared by the phone's ActiveTripScreen and the Android Auto navigation
// screen, so both follow a trip in progress the same way.

// How often, while a trip is in progress, we (a) upload any GPS points Room
// has recorded since the last tick and (b) refresh the live ETA, turn-by-
// turn steps, and stop progress from the API. Chosen as a balance:
// frequent enough that the screen feels live, infrequent enough not to
// hammer Google Routes (each recalculate-eta is a billable-ish call, see
// routing_service.py) or the device's radio/battery. The turn card does
// NOT depend on this interval: each response already carries every step to
// the destination, and the current one is advanced locally from GPS (see
// matchRouteStep). Leaving the route triggers an early refresh instead of
// waiting out the interval.
internal const val POLL_INTERVAL_MS = 30_000L

// Beyond this distance (plus the fix's own reported accuracy, capped by
// MAX_ACCURACY_ALLOWANCE_METERS) from the current/upcoming steps' road, the
// car is treated as off the suggested route.
internal const val OFF_ROUTE_THRESHOLD_METERS = 40.0
internal const val MAX_ACCURACY_ALLOWANCE_METERS = 30.0

// Consecutive off-route GPS points (one every ~2-3s) needed before asking
// for a new route, so a single stray fix doesn't trigger a reroute.
internal const val OFF_ROUTE_CONFIRM_POINTS = 2

// Minimum gap between two recalculate-eta calls, whatever triggered them.
// Caps the worst case (e.g. driving in circles off route) at 6 calls/min
// instead of one per GPS fix.
internal const val MIN_REFRESH_GAP_MS = 10_000L

// How many steps past the current one are considered when matching the
// car's position to a step. Small on purpose: a later step that happens to
// run close by (a U-turn, a parallel street) shouldn't be able to steal the
// match from the one actually being driven.
internal const val STEP_LOOKAHEAD = 3

// Same 100m radius the API already uses server-side to mark a stop
// reached, for consistency between what the server considers "arrived" and
// what the trip screens prompt about.
internal const val ARRIVAL_THRESHOLD_METERS = 100.0

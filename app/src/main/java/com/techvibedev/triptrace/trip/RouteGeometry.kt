package com.techvibedev.triptrace.trip

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Standard great-circle distance — no equivalent existed yet in the Android
// app itself (server-side stop detection does its own version in Python).
internal fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

// Nearest point on a polyline to a given position, used to trim the
// suggested-route line to "from here forward" as the car moves (instead of
// always drawing the whole thing from where it was at the last poll).
// Checks every segment and keeps the closest projection — routes from
// recalculate-eta only cover a single ~30s window, at most a few km, so a
// full scan is cheap; no need for anything smarter here.
internal fun trimRouteBehindPosition(routePoints: List<LatLng>, position: LatLng): List<LatLng> {
    if (routePoints.size < 2) return routePoints

    var bestSegmentIndex = 0
    var bestProjection = routePoints[0]
    var bestDistanceMeters = Double.MAX_VALUE

    for (i in 0 until routePoints.size - 1) {
        val projection = projectPointOntoSegment(position, routePoints[i], routePoints[i + 1])
        val distanceMeters = haversineMeters(
            position.latitude,
            position.longitude,
            projection.latitude,
            projection.longitude,
        )
        if (distanceMeters < bestDistanceMeters) {
            bestDistanceMeters = distanceMeters
            bestSegmentIndex = i
            bestProjection = projection
        }
    }

    return listOf(bestProjection) + routePoints.drop(bestSegmentIndex + 1)
}

internal data class RouteStepMatch(val stepIndex: Int, val distanceMeters: Double)

// Which of the current and next few steps the position lies closest to,
// and how far from that step's road it is. Null only if none of them has
// any geometry. On a tie (right at a maneuver point, where one step's
// polyline ends and the next begins) the earlier step wins, so the card
// only moves on once the car is actually past the turn.
internal fun matchRouteStep(stepPolylines: List<List<LatLng>>, fromIndex: Int, position: LatLng): RouteStepMatch? {
    var best: RouteStepMatch? = null
    val lastIndex = minOf(fromIndex + STEP_LOOKAHEAD, stepPolylines.size - 1)
    for (index in fromIndex..lastIndex) {
        val distanceMeters = distanceToPolylineMeters(stepPolylines[index], position) ?: continue
        if (best == null || distanceMeters < best.distanceMeters) {
            best = RouteStepMatch(index, distanceMeters)
        }
    }
    return best
}

internal fun distanceToPolylineMeters(points: List<LatLng>, position: LatLng): Double? {
    if (points.isEmpty()) return null
    if (points.size == 1) {
        return haversineMeters(position.latitude, position.longitude, points[0].latitude, points[0].longitude)
    }
    var bestDistanceMeters = Double.MAX_VALUE
    for (i in 0 until points.size - 1) {
        val projection = projectPointOntoSegment(position, points[i], points[i + 1])
        val distanceMeters = haversineMeters(
            position.latitude,
            position.longitude,
            projection.latitude,
            projection.longitude,
        )
        if (distanceMeters < bestDistanceMeters) bestDistanceMeters = distanceMeters
    }
    return bestDistanceMeters
}

// Treats lat/lng as flat Cartesian coordinates to find the closest point on
// segment a-b to the given point — a standard simplification for a segment
// this short (part of a single route step, well under a km), same one
// already used by the haversine-based distance checks elsewhere in this
// file. Not accurate enough for anything spanning a meaningful fraction
// of the globe, but that's not what this is for.
internal fun projectPointOntoSegment(point: LatLng, a: LatLng, b: LatLng): LatLng {
    val abx = b.longitude - a.longitude
    val aby = b.latitude - a.latitude
    val lengthSquared = abx * abx + aby * aby
    if (lengthSquared == 0.0) return a

    val t = (((point.longitude - a.longitude) * abx) + ((point.latitude - a.latitude) * aby)) / lengthSquared
    val clampedT = t.coerceIn(0.0, 1.0)
    return LatLng(a.latitude + aby * clampedT, a.longitude + abx * clampedT)
}

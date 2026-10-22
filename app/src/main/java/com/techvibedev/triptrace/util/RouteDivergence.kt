package com.techvibedev.triptrace.util

import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sqrt

// Checking every point of a long route against every point of another is
// wasted work: a few hundred evenly spread samples per route already find
// the spot where they are furthest apart.
private const val MAX_SAMPLES = 300

// The point of `alternative` that lies furthest from `main`. Used to turn a
// Google alternative into a single via point: forcing the route through the
// spot where the two differ most makes Google keep following the
// alternative during live rerouting, instead of switching back to its
// fastest route. Null when either route is empty.
fun furthestPointFrom(main: List<LatLng>, alternative: List<LatLng>): LatLng? {
    if (main.isEmpty() || alternative.isEmpty()) return null
    val mainSamples = sample(main)
    return sample(alternative).maxByOrNull { candidate ->
        mainSamples.minOf { approxDistanceMeters(candidate, it) }
    }
}

private fun sample(points: List<LatLng>): List<LatLng> {
    if (points.size <= MAX_SAMPLES) return points
    val step = points.size.toDouble() / MAX_SAMPLES
    return List(MAX_SAMPLES) { points[(it * step).toInt()] }
}

// Equirectangular approximation: accurate enough at the scale of a city or
// a region, and much cheaper than haversine for the many comparisons above.
private fun approxDistanceMeters(a: LatLng, b: LatLng): Double {
    val metersPerDegree = 111_320.0
    val dLat = (a.latitude - b.latitude) * metersPerDegree
    val dLng = (a.longitude - b.longitude) * metersPerDegree * cos(Math.toRadians(a.latitude))
    return sqrt(dLat * dLat + dLng * dLng)
}

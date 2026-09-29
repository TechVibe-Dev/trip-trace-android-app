package com.techvibedev.triptrace.util

import com.google.android.gms.maps.model.LatLng

// Decodes Google's encoded polyline algorithm format (the same one
// trip-trace-api stores/returns for planned_route_polyline and, since
// android#110, recalculate-eta's route_polyline and each step's own
// polyline) into a list of LatLng points. Standard reference
// implementation, unchanged for years — not tied to any Google library or
// API key, works with any encoded polyline string. Ported from the web
// frontend's identical src/utils/polyline.ts, kept algorithmically
// identical to it on purpose.
fun decodePolyline(encoded: String): List<LatLng> {
    val points = mutableListOf<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0

    while (index < encoded.length) {
        var result = 0
        var shift = 0
        var byte: Int
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        val deltaLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += deltaLat

        result = 0
        shift = 0
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        val deltaLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += deltaLng

        points.add(LatLng(lat / 1e5, lng / 1e5))
    }

    return points
}

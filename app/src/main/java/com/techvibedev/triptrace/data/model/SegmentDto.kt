package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

// Mirrors the API's TripSegmentRead (GET /trips/{id}/segments) — slow/
// normal/fast stretches of a completed trip, classified server-side
// relative to the trip's own average speed (see trip_stats_service.py).
// Same shape the frontend uses (trip-trace-frontend#6) to color the real
// route on its trip detail map — this is that same idea on Android.
data class SegmentResponse(
    @SerializedName("segment_type") val segmentType: String,
    @SerializedName("start_lat") val startLat: Double,
    @SerializedName("start_lng") val startLng: Double,
    @SerializedName("end_lat") val endLat: Double,
    @SerializedName("end_lng") val endLng: Double,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("avg_speed") val avgSpeed: Double,
)

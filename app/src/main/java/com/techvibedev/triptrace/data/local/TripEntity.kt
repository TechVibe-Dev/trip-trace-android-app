package com.techvibedev.triptrace.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Mirrors the API's Trip model, plus syncedAt (nullable) to track whether
// this trip's own fields (status, timestamps, metrics) have been pushed to
// the API yet. Null means not synced.
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val originName: String,
    val originLat: Double,
    val originLng: Double,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val plannedRoutePolyline: String?,
    val status: String,
    val plannedDepartureAt: String?,
    val desiredArrivalAt: String?,
    val calculatedArrivalAt: String?,
    val startedAt: String?,
    val endedAt: String?,
    val distanceKm: Double?,
    val maxSpeed: Double?,
    val minSpeed: Double?,
    val avgSpeed: Double?,
    val createdAt: String,
    val updatedAt: String,
    val syncedAt: String?,
)

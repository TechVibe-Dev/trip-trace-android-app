package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

data class GpsPointCreateRequest(
    val lat: Double,
    val lng: Double,
    val speed: Double?,
    val accuracy: Double?,
    val bearing: Double?,
    @SerializedName("recorded_at") val recordedAt: String,
)

data class GpsPointResponse(
    val id: Long,
    @SerializedName("trip_id") val tripId: String,
    val lat: Double,
    val lng: Double,
    val speed: Double?,
    val accuracy: Double?,
    val bearing: Double?,
    @SerializedName("recorded_at") val recordedAt: String,
)

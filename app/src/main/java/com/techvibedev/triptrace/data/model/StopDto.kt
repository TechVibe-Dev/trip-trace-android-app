package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

data class StopCreateRequest(
    val type: String,
    val name: String?,
    val lat: Double,
    val lng: Double,
    @SerializedName("planned_arrival_at") val plannedArrivalAt: String? = null,
    val sequence: Int,
)

data class StopResponse(
    val id: String,
    @SerializedName("trip_id") val tripId: String,
    val type: String,
    val name: String?,
    val lat: Double,
    val lng: Double,
    @SerializedName("planned_arrival_at") val plannedArrivalAt: String?,
    @SerializedName("actual_arrival_at") val actualArrivalAt: String?,
    @SerializedName("departure_at") val departureAt: String?,
    val sequence: Int,
)

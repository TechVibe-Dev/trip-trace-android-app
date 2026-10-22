package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

data class TripCreateRequest(
    @SerializedName("origin_name") val originName: String,
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double,
    @SerializedName("destination_name") val destinationName: String,
    @SerializedName("destination_lat") val destinationLat: Double,
    @SerializedName("destination_lng") val destinationLng: Double,
    @SerializedName("planned_departure_at") val plannedDepartureAt: String? = null,
    @SerializedName("desired_arrival_at") val desiredArrivalAt: String? = null,
    // Null (left out of the JSON) = let Google choose the route.
    @SerializedName("via_points") val viaPoints: List<ViaPointDto>? = null,
    @SerializedName("route_name") val routeName: String? = null,
    @SerializedName("saved_route_id") val savedRouteId: String? = null,
)

data class TripUpdateRequest(
    val status: String? = null,
    // An empty list (not null, which Gson leaves out) drops the chosen
    // route: the API goes back to Google's fastest from then on.
    @SerializedName("via_points") val viaPoints: List<ViaPointDto>? = null,
    @SerializedName("planned_departure_at") val plannedDepartureAt: String? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("ended_at") val endedAt: String? = null,
)

// Full shape of the API's TripRead — expanded from just the fields the UI
// needed, because we now also use this to populate Room's TripEntity
// (needed to satisfy GpsPointEntity's foreign key before the tracking
// service can insert any point).
data class TripResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("origin_name") val originName: String,
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double,
    @SerializedName("destination_name") val destinationName: String,
    @SerializedName("destination_lat") val destinationLat: Double,
    @SerializedName("destination_lng") val destinationLng: Double,
    @SerializedName("planned_route_polyline") val plannedRoutePolyline: String?,
    @SerializedName("via_points") val viaPoints: List<ViaPointDto>?,
    @SerializedName("route_name") val routeName: String?,
    @SerializedName("saved_route_id") val savedRouteId: String?,
    val status: String,
    @SerializedName("planned_departure_at") val plannedDepartureAt: String?,
    @SerializedName("desired_arrival_at") val desiredArrivalAt: String?,
    @SerializedName("calculated_arrival_at") val calculatedArrivalAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("distance_km") val distanceKm: Double?,
    @SerializedName("max_speed") val maxSpeed: Double?,
    @SerializedName("min_speed") val minSpeed: Double?,
    @SerializedName("avg_speed") val avgSpeed: Double?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

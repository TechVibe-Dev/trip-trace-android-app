package com.techvibedev.triptrace.data.network

import com.techvibedev.triptrace.data.model.EtaRecalculationResponse
import com.techvibedev.triptrace.data.model.GpsPointCreateRequest
import com.techvibedev.triptrace.data.model.GpsPointResponse
import com.techvibedev.triptrace.data.model.SegmentResponse
import com.techvibedev.triptrace.data.model.StopCreateRequest
import com.techvibedev.triptrace.data.model.StopResponse
import com.techvibedev.triptrace.data.model.TripCreateRequest
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.model.TripUpdateRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TripApiService {

    @POST("api/v1/trips")
    suspend fun createTrip(
        @Header("Authorization") bearerToken: String,
        @Body request: TripCreateRequest,
    ): TripResponse

    @GET("api/v1/trips")
    suspend fun listTrips(
        @Header("Authorization") bearerToken: String,
        @Query("status_filter") statusFilter: String? = null,
    ): List<TripResponse>

    @GET("api/v1/trips/{tripId}")
    suspend fun getTrip(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
    ): TripResponse

    @PATCH("api/v1/trips/{tripId}")
    suspend fun updateTrip(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
        @Body request: TripUpdateRequest,
    ): TripResponse

    @POST("api/v1/trips/{tripId}/calculate-route")
    suspend fun calculateRoute(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
    ): TripResponse

    @POST("api/v1/trips/{tripId}/recalculate-eta")
    suspend fun recalculateEta(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
    ): EtaRecalculationResponse

    @POST("api/v1/trips/{tripId}/gps-points")
    suspend fun uploadGpsPoints(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
        @Body points: List<GpsPointCreateRequest>,
    ): List<GpsPointResponse>

    @GET("api/v1/trips/{tripId}/gps-points")
    suspend fun listGpsPoints(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
    ): List<GpsPointResponse>

    @POST("api/v1/trips/{tripId}/finalize")
    suspend fun finalizeTrip(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
    ): TripResponse

    @POST("api/v1/trips/{tripId}/stops")
    suspend fun createStop(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
        @Body request: StopCreateRequest,
    ): StopResponse

    @GET("api/v1/trips/{tripId}/stops")
    suspend fun listStops(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
    ): List<StopResponse>

    // Same segments already used by the web frontend (trip-trace-frontend#6)
    // to color a completed trip's route — SLOW/NORMAL/FAST stretches,
    // classified server-side.
    @GET("api/v1/trips/{tripId}/segments")
    suspend fun listSegments(
        @Header("Authorization") bearerToken: String,
        @Path("tripId") tripId: String,
    ): List<SegmentResponse>
}

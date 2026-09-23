package com.techvibedev.triptrace.data.network

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
}

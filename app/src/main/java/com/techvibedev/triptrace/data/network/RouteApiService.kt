package com.techvibedev.triptrace.data.network

import com.techvibedev.triptrace.data.model.RoutePreviewRequest
import com.techvibedev.triptrace.data.model.RoutePreviewResponse
import com.techvibedev.triptrace.data.model.SavedRouteCreateRequest
import com.techvibedev.triptrace.data.model.SavedRouteResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RouteApiService {

    @POST("api/v1/routes/preview")
    suspend fun preview(
        @Header("Authorization") bearerToken: String,
        @Body request: RoutePreviewRequest,
    ): List<RoutePreviewResponse>

    @POST("api/v1/saved-routes")
    suspend fun createSavedRoute(
        @Header("Authorization") bearerToken: String,
        @Body request: SavedRouteCreateRequest,
    ): SavedRouteResponse

    // With all four coordinates, only the routes that start and end near
    // them; with none, every saved route of the user.
    @GET("api/v1/saved-routes")
    suspend fun listSavedRoutes(
        @Header("Authorization") bearerToken: String,
        @Query("origin_lat") originLat: Double? = null,
        @Query("origin_lng") originLng: Double? = null,
        @Query("destination_lat") destinationLat: Double? = null,
        @Query("destination_lng") destinationLng: Double? = null,
    ): List<SavedRouteResponse>

    @DELETE("api/v1/saved-routes/{id}")
    suspend fun deleteSavedRoute(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    )
}

package com.techvibedev.triptrace.data.network

import com.techvibedev.triptrace.data.model.FavoritePlaceCreateRequest
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface FavoritePlaceApiService {

    @POST("api/v1/favorite-places")
    suspend fun create(
        @Header("Authorization") bearerToken: String,
        @Body request: FavoritePlaceCreateRequest,
    ): FavoritePlaceResponse

    @GET("api/v1/favorite-places")
    suspend fun list(@Header("Authorization") bearerToken: String): List<FavoritePlaceResponse>

    @DELETE("api/v1/favorite-places/{id}")
    suspend fun delete(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    )
}

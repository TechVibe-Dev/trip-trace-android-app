package com.techvibedev.triptrace.data.network

import com.techvibedev.triptrace.data.model.RegisterRequest
import com.techvibedev.triptrace.data.model.TokenResponse
import com.techvibedev.triptrace.data.model.UserResponse
import com.techvibedev.triptrace.data.model.UserUpdateRequest
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): UserResponse

    // /login expects OAuth2PasswordRequestForm (form-urlencoded), not JSON —
    // "username" is the field name FastAPI uses even though we send the
    // email as its value.
    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username") email: String,
        @Field("password") password: String,
    ): TokenResponse

    @GET("api/v1/auth/me")
    suspend fun getMe(@Header("Authorization") bearerToken: String): UserResponse

    @PUT("api/v1/auth/me")
    suspend fun updateMe(
        @Header("Authorization") bearerToken: String,
        @Body request: UserUpdateRequest,
    ): UserResponse
}

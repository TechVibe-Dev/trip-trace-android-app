package com.techvibedev.triptrace.data.network

import com.techvibedev.triptrace.data.model.PasswordChangeRequest
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
    // "username" is the field name FastAPI uses per the OAuth2 spec. The API
    // now accepts either the user's actual username or their email there
    // (trip-trace-api#57, android#83), hence "identifier" as the Kotlin
    // param name instead of the old "email".
    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username") identifier: String,
        @Field("password") password: String,
    ): TokenResponse

    @GET("api/v1/auth/me")
    suspend fun getMe(@Header("Authorization") bearerToken: String): UserResponse

    @PUT("api/v1/auth/me")
    suspend fun updateMe(
        @Header("Authorization") bearerToken: String,
        @Body request: UserUpdateRequest,
    ): UserResponse

    // Separate endpoint from updateMe (trip-trace-api#60, android#87) —
    // requires current_password since a valid session alone doesn't prove
    // the caller still knows the password.
    @PUT("api/v1/auth/me/password")
    suspend fun changePassword(
        @Header("Authorization") bearerToken: String,
        @Body request: PasswordChangeRequest,
    )
}

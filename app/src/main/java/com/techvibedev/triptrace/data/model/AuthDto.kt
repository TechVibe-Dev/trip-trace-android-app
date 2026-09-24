package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

data class UserResponse(
    val id: String,
    val email: String,
    val username: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
)

data class UserUpdateRequest(
    val username: String?,
)

package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

data class FavoritePlaceCreateRequest(
    val name: String,
    val lat: Double,
    val lng: Double,
)

data class FavoritePlaceResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

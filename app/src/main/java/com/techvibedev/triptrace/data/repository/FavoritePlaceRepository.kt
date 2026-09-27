package com.techvibedev.triptrace.data.repository

import com.techvibedev.triptrace.data.model.FavoritePlaceCreateRequest
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse
import com.techvibedev.triptrace.data.network.FavoritePlaceApiService
import com.techvibedev.triptrace.data.session.TokenDataStore
import kotlinx.coroutines.flow.first

// One list per user, usable for a trip's origin, destination, or any stop —
// user-curated (android#81), not computed from trip history: origin_name in
// particular can vary slightly between visits to the same real place (GPS
// precision, reverse-geocoding drift), so matching by exact text wouldn't
// reliably detect repeats without real geographic clustering, which this
// first version doesn't attempt.
class FavoritePlaceRepository(
    private val apiService: FavoritePlaceApiService,
    private val tokenDataStore: TokenDataStore,
) {
    private suspend fun authHeader(): String {
        val token = tokenDataStore.tokenFlow.first() ?: error("No auth token available")
        return "Bearer $token"
    }

    suspend fun create(name: String, lat: Double, lng: Double): Result<FavoritePlaceResponse> {
        return try {
            Result.success(
                apiService.create(authHeader(), FavoritePlaceCreateRequest(name, lat, lng)),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun list(): Result<List<FavoritePlaceResponse>> {
        return try {
            Result.success(apiService.list(authHeader()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(id: String): Result<Unit> {
        return try {
            apiService.delete(authHeader(), id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

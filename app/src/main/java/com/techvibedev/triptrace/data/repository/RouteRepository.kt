package com.techvibedev.triptrace.data.repository

import com.techvibedev.triptrace.data.model.RoutePreviewRequest
import com.techvibedev.triptrace.data.model.RoutePreviewResponse
import com.techvibedev.triptrace.data.model.SavedRouteCreateRequest
import com.techvibedev.triptrace.data.model.SavedRouteResponse
import com.techvibedev.triptrace.data.network.RouteApiService
import com.techvibedev.triptrace.data.session.TokenDataStore
import kotlinx.coroutines.flow.first

// Route previews (before a trip exists) and the user's saved routes: named
// ways of driving between two places.
class RouteRepository(
    private val apiService: RouteApiService,
    private val tokenDataStore: TokenDataStore,
) {
    private suspend fun authHeader(): String {
        val token = tokenDataStore.tokenFlow.first() ?: error("No auth token available")
        return "Bearer $token"
    }

    // Each call is a billed Google request on the API side, so callers
    // should only preview what the user is actually looking at.
    suspend fun preview(request: RoutePreviewRequest): Result<List<RoutePreviewResponse>> {
        return try {
            Result.success(apiService.preview(authHeader(), request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSavedRoute(request: SavedRouteCreateRequest): Result<SavedRouteResponse> {
        return try {
            Result.success(apiService.createSavedRoute(authHeader(), request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listSavedRoutes(): Result<List<SavedRouteResponse>> {
        return try {
            Result.success(apiService.listSavedRoutes(authHeader()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listSavedRoutesBetween(
        originLat: Double,
        originLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): Result<List<SavedRouteResponse>> {
        return try {
            Result.success(
                apiService.listSavedRoutes(authHeader(), originLat, originLng, destinationLat, destinationLng),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSavedRoute(id: String): Result<Unit> {
        return try {
            apiService.deleteSavedRoute(authHeader(), id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

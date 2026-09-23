package com.techvibedev.triptrace.data.repository

import com.techvibedev.triptrace.data.model.TripCreateRequest
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.model.TripUpdateRequest
import com.techvibedev.triptrace.data.network.TripApiService
import com.techvibedev.triptrace.data.session.TokenDataStore
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.first

class TripRepository(
    private val apiService: TripApiService,
    private val tokenDataStore: TokenDataStore,
) {
    private suspend fun authHeader(): String {
        val token = tokenDataStore.tokenFlow.first() ?: error("No auth token available")
        return "Bearer $token"
    }

    suspend fun createTrip(request: TripCreateRequest): Result<TripResponse> {
        return try {
            Result.success(apiService.createTrip(authHeader(), request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlannedTrips(): Result<List<TripResponse>> {
        return try {
            Result.success(apiService.listTrips(authHeader(), statusFilter = "PLANNED"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrip(tripId: String): Result<TripResponse> {
        return try {
            Result.success(apiService.getTrip(authHeader(), tripId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startTrip(tripId: String): Result<TripResponse> {
        return try {
            val request = TripUpdateRequest(
                status = "IN_PROGRESS",
                startedAt = OffsetDateTime.now().toString(),
            )
            Result.success(apiService.updateTrip(authHeader(), tripId, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endTrip(tripId: String): Result<TripResponse> {
        return try {
            val request = TripUpdateRequest(
                status = "COMPLETED",
                endedAt = OffsetDateTime.now().toString(),
            )
            Result.success(apiService.updateTrip(authHeader(), tripId, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sets a trip's planned departure to right now — used when the
    // originally planned time already passed and the user wants to start
    // as-is instead of typing a new one.
    suspend fun useCurrentTimeAsDeparture(tripId: String): Result<TripResponse> {
        return try {
            val request = TripUpdateRequest(plannedDepartureAt = OffsetDateTime.now().toString())
            Result.success(apiService.updateTrip(authHeader(), tripId, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Best-effort: asks the API to calculate the route/ETA (Google Routes,
    // traffic-aware) for a trip and persist calculated_arrival_at +
    // planned_route_polyline. Callers should treat failure here as
    // non-fatal — the trip itself is still valid without an ETA.
    suspend fun calculateRoute(tripId: String): Result<TripResponse> {
        return try {
            Result.success(apiService.calculateRoute(authHeader(), tripId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

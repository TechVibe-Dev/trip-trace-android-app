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

    suspend fun getCompletedTrips(): Result<List<TripResponse>> {
        return try {
            Result.success(apiService.listTrips(authHeader(), statusFilter = "COMPLETED"))
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
}

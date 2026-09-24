package com.techvibedev.triptrace.data.repository

import com.techvibedev.triptrace.data.local.GpsPointEntity
import com.techvibedev.triptrace.data.model.GpsPointCreateRequest
import com.techvibedev.triptrace.data.model.GpsPointResponse
import com.techvibedev.triptrace.data.model.StopCreateRequest
import com.techvibedev.triptrace.data.model.StopResponse
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

    // Uploads a batch of locally-recorded GPS points (Room) to the API.
    // Caller is responsible for marking them synced in Room once this
    // succeeds — this function only talks to the network.
    suspend fun uploadGpsPoints(
        tripId: String,
        points: List<GpsPointEntity>,
    ): Result<List<GpsPointResponse>> {
        return try {
            val requests = points.map {
                GpsPointCreateRequest(
                    lat = it.lat,
                    lng = it.lng,
                    speed = it.speed,
                    accuracy = it.accuracy,
                    bearing = it.bearing,
                    recordedAt = it.recordedAt,
                )
            }
            Result.success(apiService.uploadGpsPoints(authHeader(), tripId, requests))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Reads back a trip's synced GPS points — the real recorded path,
    // as opposed to planned_route_polyline (Google's suggested route).
    suspend fun getGpsPoints(tripId: String): Result<List<GpsPointResponse>> {
        return try {
            Result.success(apiService.listGpsPoints(authHeader(), tripId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Best-effort, same reasoning as calculateRoute: computes distance/speed
    // stats server-side from whatever points already made it up for this
    // trip. Call after uploadGpsPoints for real numbers — with no synced
    // points yet, the API just returns nulls.
    suspend fun finalizeTrip(tripId: String): Result<TripResponse> {
        return try {
            Result.success(apiService.finalizeTrip(authHeader(), tripId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createStop(tripId: String, request: StopCreateRequest): Result<StopResponse> {
        return try {
            Result.success(apiService.createStop(authHeader(), tripId, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.techvibedev.triptrace.trip

import android.content.Context
import com.techvibedev.triptrace.data.local.TripEntity
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.service.TripTrackingService
import java.time.OffsetDateTime

// GpsPointEntity has a foreign key on tripId pointing at Room's own trips
// table — but trips are otherwise only known through the API, never
// inserted into Room. Without this, the very first point the tracking
// service tries to save crashes the app with SQLiteConstraintException
// (FOREIGN KEY constraint failed). Fetching and saving the trip here,
// before starting the service, closes that gap.
internal suspend fun ensureTripSavedLocallyAndStartTracking(
    context: Context,
    tripId: String,
    tripRepository: TripRepository,
): Result<TripResponse> {
    val result = tripRepository.getTrip(tripId)
    result.onSuccess { trip ->
        val tripDao = TripTraceDatabase.getInstance(context.applicationContext).tripDao()
        tripDao.insert(trip.toEntity(syncedAt = OffsetDateTime.now().toString()))
        TripTrackingService.start(context, tripId)
    }
    return result
}

private fun TripResponse.toEntity(syncedAt: String): TripEntity {
    return TripEntity(
        id = id,
        userId = userId,
        originName = originName,
        originLat = originLat,
        originLng = originLng,
        destinationName = destinationName,
        destinationLat = destinationLat,
        destinationLng = destinationLng,
        plannedRoutePolyline = plannedRoutePolyline,
        status = status,
        plannedDepartureAt = plannedDepartureAt,
        desiredArrivalAt = desiredArrivalAt,
        calculatedArrivalAt = calculatedArrivalAt,
        startedAt = startedAt,
        endedAt = endedAt,
        distanceKm = distanceKm,
        maxSpeed = maxSpeed,
        minSpeed = minSpeed,
        avgSpeed = avgSpeed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
    )
}

// Uploads whatever GPS points Room has recorded for this trip and not synced
// yet. Best-effort: a failed upload leaves them unsynced in Room, so the
// next call retries them alongside whatever's been recorded since — no data
// is lost, just delayed.
internal suspend fun syncPendingGpsPoints(context: Context, tripId: String, tripRepository: TripRepository) {
    val gpsPointDao = TripTraceDatabase.getInstance(context.applicationContext).gpsPointDao()
    val unsyncedPoints = gpsPointDao.getUnsyncedByTripId(tripId)
    if (unsyncedPoints.isNotEmpty()) {
        tripRepository.uploadGpsPoints(tripId, unsyncedPoints).onSuccess {
            gpsPointDao.markSynced(unsyncedPoints.map { point -> point.id })
        }
    }
}

// Ends a trip in progress, wherever that's triggered from (the phone's
// "Finalizar viaje" or Android Auto). The result is the API's answer to
// marking the trip COMPLETED; tracking stops either way.
internal suspend fun finishTrip(
    context: Context,
    tripId: String,
    tripRepository: TripRepository,
): Result<TripResponse> {
    // Sync (Room -> API) happens here too, right before finalizing:
    // /finalize computes distance/speed stats from whatever GPS points
    // already exist on the server, so without uploading first, those stats
    // always come back null. The periodic sync while the trip runs should
    // have already caught most points, but this makes sure anything from
    // the last partial interval isn't lost.
    syncPendingGpsPoints(context, tripId, tripRepository)

    val endResult = tripRepository.endTrip(tripId)
    if (endResult.isSuccess) {
        // Also best-effort — a failure here leaves the trip correctly
        // COMPLETED with null stats, same degraded state as before Sync
        // existed, not something worth blocking on.
        tripRepository.finalizeTrip(tripId)
    }

    // Local cleanup: once every point for this trip is confirmed synced
    // (whether it already was, or just got uploaded above), Room's copy has
    // served its purpose — History and the web frontend both read from the
    // API, never from here. Leaves TripEntity itself in place (see
    // GpsPointDao.deleteByTripId) so sensor_readings, a separate FK child of
    // it kept for manual review (see HistoryScreen), isn't swept up too. If
    // the upload failed and some points are still unsynced, nothing is
    // deleted — same retry-later posture as the rest of Sync.
    val gpsPointDao = TripTraceDatabase.getInstance(context.applicationContext).gpsPointDao()
    if (gpsPointDao.getUnsyncedByTripId(tripId).isEmpty()) {
        gpsPointDao.deleteByTripId(tripId)
    }

    TripTrackingService.stop(context)
    return endResult
}

package com.techvibedev.triptrace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsPointDao {

    @Insert
    suspend fun insert(point: GpsPointEntity)

    @Insert
    suspend fun insertAll(points: List<GpsPointEntity>)

    @Query("SELECT * FROM gps_points WHERE tripId = :tripId ORDER BY recordedAt")
    suspend fun getByTripId(tripId: String): List<GpsPointEntity>

    @Query("SELECT * FROM gps_points WHERE tripId = :tripId AND synced = 0 ORDER BY recordedAt")
    suspend fun getUnsyncedByTripId(tripId: String): List<GpsPointEntity>

    @Query("UPDATE gps_points SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    // Called once a trip's points are all confirmed synced (see endTrip()
    // in ActiveTripScreen) — the API is the source of truth for everything
    // that reads trip data back (History, the web frontend), so keeping
    // synced points in Room forever serves no purpose, just uses space.
    // Deletes only the points themselves, not the trip's own Room row —
    // TripEntity stays (see TripDao, and why: it's the FK parent of
    // sensor_readings too, which must NOT be swept up by this cleanup).
    @Query("DELETE FROM gps_points WHERE tripId = :tripId")
    suspend fun deleteByTripId(tripId: String)

    // Flow so ActiveTripScreen updates live as the tracking service inserts
    // new points, without polling — Room emits automatically on writes to
    // this table.
    @Query("SELECT * FROM gps_points WHERE tripId = :tripId ORDER BY recordedAt DESC LIMIT 1")
    fun observeLatest(tripId: String): Flow<GpsPointEntity?>

    // Speed can be legitimately absent on any given fix — GPS speed comes
    // from Doppler shift on the satellite signal, which needs a sustained
    // clean read; it drops out in short bursts during turns, braking, or
    // patchy sky visibility, even while position stays fine. Confirmed
    // across a real drive: bursts of a handful of consecutive null-speed
    // points, interspersed with good ones. observeLatest alone would show
    // "--" if the very last point happened to land in one of those bursts,
    // even with a real reading a couple of points earlier — this query
    // skips nulls so the speed shown is always the most recent real one.
    @Query(
        "SELECT * FROM gps_points WHERE tripId = :tripId AND speed IS NOT NULL " +
            "ORDER BY recordedAt DESC LIMIT 1",
    )
    fun observeLatestWithSpeed(tripId: String): Flow<GpsPointEntity?>

    // Same live-update reasoning as observeLatest, but the whole recorded
    // path — for drawing the route-so-far on the live map, sourced straight
    // from Room (recorded every ~3s) rather than the API, which exists to
    // get data to the server, not to redraw the phone's own map.
    @Query("SELECT * FROM gps_points WHERE tripId = :tripId ORDER BY recordedAt")
    fun observeAllByTripId(tripId: String): Flow<List<GpsPointEntity>>
}

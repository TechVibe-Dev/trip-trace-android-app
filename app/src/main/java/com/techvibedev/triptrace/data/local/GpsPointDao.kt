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

    // Flow so ActiveTripScreen updates live as the tracking service inserts
    // new points, without polling — Room emits automatically on writes to
    // this table.
    @Query("SELECT * FROM gps_points WHERE tripId = :tripId ORDER BY recordedAt DESC LIMIT 1")
    fun observeLatest(tripId: String): Flow<GpsPointEntity?>
}

package com.techvibedev.triptrace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

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
}

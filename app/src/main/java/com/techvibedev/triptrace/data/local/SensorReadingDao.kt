package com.techvibedev.triptrace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorReadingDao {

    @Insert
    suspend fun insertAll(readings: List<SensorReadingEntity>)

    @Query("SELECT * FROM sensor_readings WHERE tripId = :tripId ORDER BY recordedAt")
    suspend fun getByTripId(tripId: String): List<SensorReadingEntity>

    // For the delete-sensor-data control in History — lets the UI show how
    // many readings a trip has (or hide the control entirely if none),
    // without fetching every row just to check.
    @Query("SELECT COUNT(*) FROM sensor_readings WHERE tripId = :tripId")
    suspend fun countByTripId(tripId: String): Int

    // Manual, user-triggered cleanup — unlike gps_points, these are never
    // synced anywhere, so nothing auto-deletes them; the user decides when
    // they're done analyzing a trip's sensor data (see HistoryScreen).
    @Query("DELETE FROM sensor_readings WHERE tripId = :tripId")
    suspend fun deleteByTripId(tripId: String)
}

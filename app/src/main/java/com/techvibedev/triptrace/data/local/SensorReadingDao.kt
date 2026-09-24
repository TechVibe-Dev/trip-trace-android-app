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
}

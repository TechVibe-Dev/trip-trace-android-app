package com.techvibedev.triptrace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StopDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stop: StopEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)

    @Query("SELECT * FROM stops WHERE tripId = :tripId ORDER BY sequence")
    suspend fun getByTripId(tripId: String): List<StopEntity>
}

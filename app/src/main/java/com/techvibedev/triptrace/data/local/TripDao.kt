package com.techvibedev.triptrace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity)

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<TripEntity>

    @Query("SELECT * FROM trips WHERE syncedAt IS NULL")
    suspend fun getUnsynced(): List<TripEntity>
}

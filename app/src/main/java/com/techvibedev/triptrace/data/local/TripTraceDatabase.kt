package com.techvibedev.triptrace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// exportSchema = false for now — fine for an early-stage personal project;
// revisit (export + track schema files) once real migrations matter.
@Database(
    entities = [TripEntity::class, StopEntity::class, GpsPointEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TripTraceDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun gpsPointDao(): GpsPointDao

    companion object {
        @Volatile
        private var INSTANCE: TripTraceDatabase? = null

        fun getInstance(context: Context): TripTraceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TripTraceDatabase::class.java,
                    "triptrace.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}

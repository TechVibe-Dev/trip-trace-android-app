package com.techvibedev.triptrace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// exportSchema = false for now — fine for an early-stage personal project;
// revisit (export + track schema files) once real migrations matter.
// fallbackToDestructiveMigration: no real users yet, and version 2 only
// adds a new table (sensor_readings) — wiping local data on upgrade is an
// acceptable tradeoff over writing a migration for this stage.
@Database(
    entities = [TripEntity::class, StopEntity::class, GpsPointEntity::class, SensorReadingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class TripTraceDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun gpsPointDao(): GpsPointDao
    abstract fun sensorReadingDao(): SensorReadingDao

    companion object {
        @Volatile
        private var INSTANCE: TripTraceDatabase? = null

        fun getInstance(context: Context): TripTraceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TripTraceDatabase::class.java,
                    "triptrace.db",
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}

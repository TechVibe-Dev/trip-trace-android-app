package com.techvibedev.triptrace.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Raw accelerometer/gyroscope samples recorded alongside GPS points, purely
// to evaluate whether sensor fusion (e.g. FSensor — see android#76) can
// improve position/speed accuracy during GPS signal gaps. Not synced to the
// API — this is local-only data for offline analysis, not part of the
// trip's shared/recorded data. One table with a type column rather than
// two, since accelerometer and gyroscope readings share the same shape
// (x/y/z + timestamp) and are always analyzed together.
@Entity(
    tableName = "sensor_readings",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    // "ACCELEROMETER" or "GYROSCOPE"
    val sensorType: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val recordedAt: String,
)

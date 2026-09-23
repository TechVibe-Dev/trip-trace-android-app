package com.techvibedev.triptrace.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// `synced` is per-point (not a single timestamp like Trip/Stop) because
// points are created continuously during a trip and uploaded in batches —
// new unsynced points keep appearing after each batch sync.
@Entity(
    tableName = "gps_points",
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
data class GpsPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val lat: Double,
    val lng: Double,
    val speed: Double?,
    val accuracy: Double?,
    val bearing: Double?,
    val recordedAt: String,
    val synced: Boolean = false,
)

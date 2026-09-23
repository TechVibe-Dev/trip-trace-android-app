package com.techvibedev.triptrace.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stops",
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
data class StopEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val type: String,
    val name: String?,
    val lat: Double,
    val lng: Double,
    val plannedArrivalAt: String?,
    val actualArrivalAt: String?,
    val departureAt: String?,
    val sequence: Int,
)

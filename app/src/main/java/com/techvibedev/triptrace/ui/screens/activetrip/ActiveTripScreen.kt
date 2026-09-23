package com.techvibedev.triptrace.ui.screens.activetrip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.techvibedev.triptrace.data.local.TripEntity
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.service.TripTrackingService
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

data class TripStop(
    val label: String,
    val timeLabel: String,
    val reached: Boolean,
)

// Stops and the recalculated ETA are still mock — both need api#7 (progress
// per stop, ETA recalculated from the current position). Current speed and
// departure time below are real, read straight from Room, as of this PR.
private val mockStops = listOf(
    TripStop(label = "Parada: Peaje Ruta 8", timeLabel = "14:10", reached = true),
    TripStop(label = "Destino: Oficina", timeLabel = "14:47", reached = false),
)

// GpsPoint.speed is stored in m/s (Android's Location.getSpeed() unit) —
// same conversion applied server-side in trip-trace-api#41, needed again
// here since this reads Room directly and never goes through the API.
private const val MS_TO_KMH = 3.6

@Composable
fun ActiveTripScreen(
    tripId: String,
    tripRepository: TripRepository,
    onTripEnded: () -> Unit,
) {
    val context = LocalContext.current
    val gpsPointDao = remember {
        TripTraceDatabase.getInstance(context.applicationContext).gpsPointDao()
    }
    val latestPoint by gpsPointDao.observeLatest(tripId).collectAsState(initial = null)

    var startedAt by remember { mutableStateOf<String?>(null) }
    var isEnding by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                val result = ensureTripSavedLocallyAndStartTracking(context, tripId, tripRepository)
                result.fold(
                    onSuccess = { trip -> startedAt = trip.startedAt },
                    onFailure = {
                        errorMessage = "No se pudo cargar el viaje, no se inicio la grabacion."
                    },
                )
            }
        } else {
            errorMessage = "Se necesita permiso de ubicacion para grabar el viaje"
        }
    }

    LaunchedEffect(tripId) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val result = ensureTripSavedLocallyAndStartTracking(context, tripId, tripRepository)
            result.fold(
                onSuccess = { trip -> startedAt = trip.startedAt },
                onFailure = {
                    errorMessage = "No se pudo cargar el viaje, no se inicio la grabacion."
                },
            )
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun endTrip() {
        isEnding = true
        errorMessage = null
        scope.launch {
            val result = tripRepository.endTrip(tripId)
            TripTrackingService.stop(context)
            isEnding = false
            result.fold(
                onSuccess = { onTripEnded() },
                onFailure = { errorMessage = "No se pudo finalizar el viaje." },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        RouteMapPlaceholder()

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Llegada estimada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "14:47",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Planeado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "14:32",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                label = "Velocidad",
                value = formatSpeed(latestPoint?.speed),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Salida",
                value = formatLocalTime(startedAt),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            mockStops.forEachIndexed { index, stop ->
                StopRow(stop = stop)
                if (index != mockStops.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { endTrip() },
            enabled = !isEnding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isEnding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Finalizar viaje")
            }
        }
    }
}

// GpsPointEntity has a foreign key on tripId pointing at Room's own trips
// table — but trips are otherwise only known through the API, never
// inserted into Room. Without this, the very first point the tracking
// service tries to save crashes the app with SQLiteConstraintException
// (FOREIGN KEY constraint failed). Fetching and saving the trip here,
// before starting the service, closes that gap.
private suspend fun ensureTripSavedLocallyAndStartTracking(
    context: Context,
    tripId: String,
    tripRepository: TripRepository,
): Result<TripResponse> {
    val result = tripRepository.getTrip(tripId)
    result.onSuccess { trip ->
        val tripDao = TripTraceDatabase.getInstance(context.applicationContext).tripDao()
        tripDao.insert(trip.toEntity(syncedAt = OffsetDateTime.now().toString()))
        TripTrackingService.start(context, tripId)
    }
    return result
}

private fun TripResponse.toEntity(syncedAt: String): TripEntity {
    return TripEntity(
        id = id,
        userId = userId,
        originName = originName,
        originLat = originLat,
        originLng = originLng,
        destinationName = destinationName,
        destinationLat = destinationLat,
        destinationLng = destinationLng,
        plannedRoutePolyline = plannedRoutePolyline,
        status = status,
        plannedDepartureAt = plannedDepartureAt,
        desiredArrivalAt = desiredArrivalAt,
        calculatedArrivalAt = calculatedArrivalAt,
        startedAt = startedAt,
        endedAt = endedAt,
        distanceKm = distanceKm,
        maxSpeed = maxSpeed,
        minSpeed = minSpeed,
        avgSpeed = avgSpeed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun StopRow(stop: TripStop) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (stop.reached) Icons.Filled.Check else Icons.Filled.Flag,
                contentDescription = null,
                tint = if (stop.reached) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stop.label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stop.timeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Simplified placeholder for the real live map, which needs a maps SDK wired
// up to the recorded GPS points.
@Composable
private fun RouteMapPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Mapa en vivo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSpeed(speedMs: Double?): String {
    if (speedMs == null) return "-- km/h"
    return "${(speedMs * MS_TO_KMH).toInt()} km/h"
}

// The API returns timestamps in UTC — formatting an OffsetDateTime directly
// prints ITS OWN offset's hour, not the phone's local one. Converting to
// the system zone first avoids showing e.g. "20:34" when the phone's real
// local time is 17:34 (UTC-3) — same bug found and fixed in TripsScreen.
private fun formatLocalTime(isoDateTime: String?): String {
    if (isoDateTime == null) return "--:--"
    return try {
        OffsetDateTime.parse(isoDateTime)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "--:--"
    }
}

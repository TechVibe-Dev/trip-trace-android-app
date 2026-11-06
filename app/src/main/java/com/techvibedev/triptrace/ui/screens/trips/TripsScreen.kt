package com.techvibedev.triptrace.ui.screens.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.techvibedev.triptrace.R
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.util.decodePolyline
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TripsScreen(tripRepository: TripRepository, onStartTrip: (String) -> Unit) {
    var trips by remember { mutableStateOf<List<TripResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadTrips() {
        isLoading = true
        val result = tripRepository.getPlannedTrips()
        isLoading = false
        result.fold(
            onSuccess = {
                trips = it
                errorMessage = null
            },
            onFailure = { errorMessage = "No se pudieron cargar los viajes." },
        )
    }

    LaunchedEffect(Unit) { loadTrips() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            trips.isEmpty() -> {
                Text(
                    text = "No tenés viajes planeados todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trips) { trip ->
                        PlannedTripCard(
                            trip = trip,
                            onStartTrip = { tripId ->
                                scope.launch {
                                    val result = tripRepository.startTrip(tripId)
                                    result.fold(
                                        onSuccess = { onStartTrip(tripId) },
                                        onFailure = { errorMessage = "No se pudo iniciar el viaje." },
                                    )
                                }
                            },
                            onUseCurrentTime = { tripId ->
                                scope.launch {
                                    val result = tripRepository.useCurrentTimeAsDeparture(tripId)
                                    result.fold(
                                        onSuccess = { loadTrips() },
                                        onFailure = { errorMessage = "No se pudo actualizar la hora." },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedTripCard(
    trip: TripResponse,
    onStartTrip: (String) -> Unit,
    onUseCurrentTime: (String) -> Unit,
) {
    val isStale = isDepartureStale(trip.plannedDepartureAt)
    val dateLabel = formatDepartureDate(trip.plannedDepartureAt)
    val durationLabel = formatEstimatedDuration(trip.plannedDepartureAt, trip.calculatedArrivalAt)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        PlannedRouteMap(trip = trip)

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${trip.originName} \u2192 ${trip.destinationName}",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (dateLabel != null) {
                    TripDetail(icon = Icons.Filled.CalendarToday, text = dateLabel)
                }
                if (!isStale) {
                    val arrival = trip.calculatedArrivalAt?.let { " \u2192 ${formatLocalTime(it)}" } ?: ""
                    TripDetail(
                        icon = Icons.Filled.Schedule,
                        text = "${formatLocalTime(trip.plannedDepartureAt)}$arrival",
                    )
                }
                if (durationLabel != null) {
                    TripDetail(icon = null, text = "\u2248 $durationLabel")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isStale) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "Hora planeada (${formatLocalTime(trip.plannedDepartureAt)}) ya pasó",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onUseCurrentTime(trip.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Usar hora actual")
                    }
                    Button(
                        onClick = { onStartTrip(trip.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Iniciar")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = { onStartTrip(trip.id) }) {
                        Text("Iniciar")
                    }
                }
            }
        }
    }
}

@Composable
private fun TripDetail(icon: ImageVector?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Static preview of the route Google suggested when the trip was created.
// Lite mode renders a bitmap instead of a live map, which is what keeps
// several maps in a scrolling list cheap. Without a stored route (the
// route calculation failed) it still shows both ends of the trip.
@Composable
private fun PlannedRouteMap(trip: TripResponse) {
    val context = LocalContext.current
    val mapProperties = remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
    }
    val origin = LatLng(trip.originLat, trip.originLng)
    val destination = LatLng(trip.destinationLat, trip.destinationLng)
    val routePoints = remember(trip.plannedRoutePolyline) {
        trip.plannedRoutePolyline
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { decodePolyline(it) }.getOrNull() }
            .orEmpty()
    }
    val cameraPositionState = rememberCameraPositionState()

    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
        ),
        // A lite map opens the Google Maps app on tap unless the click is
        // handled here.
        onMapClick = {},
        onMapLoaded = {
            val bounds = LatLngBounds.Builder().apply {
                include(origin)
                include(destination)
                routePoints.forEach { include(it) }
            }.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 40))
        },
    ) {
        if (routePoints.size > 1) {
            Polyline(points = routePoints, color = MaterialTheme.colorScheme.primary, width = 10f)
        }
        Marker(state = rememberMarkerState(key = "${trip.id}-origin", position = origin))
        Marker(state = rememberMarkerState(key = "${trip.id}-destination", position = destination))
    }
}

// Small buffer so a departure time set to "right now" (via "Usar hora
// actual") doesn't immediately count as stale again on the very next
// recomposition/reload, a few hundred milliseconds later, before the user
// even gets a chance to tap "Iniciar".
private val STALE_GRACE_PERIOD: Duration = Duration.ofSeconds(60)

private fun isDepartureStale(plannedDepartureAt: String?): Boolean {
    if (plannedDepartureAt == null) return false
    return try {
        OffsetDateTime.parse(plannedDepartureAt).isBefore(OffsetDateTime.now().minus(STALE_GRACE_PERIOD))
    } catch (e: Exception) {
        false
    }
}

// The API returns timestamps in UTC — formatting an OffsetDateTime directly
// prints ITS OWN offset's hour, not the phone's local one, so without
// converting first this showed UTC time as if it were local (e.g. showing
// "20:34" while the phone's actual local time was 17:34, in UTC-3).
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

private val SPANISH = Locale.forLanguageTag("es")

// "Hoy, vie 6 nov", "Mañana, sáb 7 nov" or "lun 9 nov", in the phone's
// time zone.
private fun formatDepartureDate(isoDateTime: String?): String? {
    if (isoDateTime == null) return null
    return try {
        val date = OffsetDateTime.parse(isoDateTime).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        // Some Android versions abbreviate with a trailing dot ("vie.").
        val formatted = date.format(DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)).replace(".", "")
        when (date) {
            today -> "Hoy, $formatted"
            today.plusDays(1) -> "Mañana, $formatted"
            else -> formatted
        }
    } catch (e: Exception) {
        null
    }
}

// Duration of Google's estimate at creation time, "53 min" or "1 h 35 min".
private fun formatEstimatedDuration(departureAt: String?, arrivalAt: String?): String? {
    if (departureAt == null || arrivalAt == null) return null
    return try {
        val minutes = Duration.between(OffsetDateTime.parse(departureAt), OffsetDateTime.parse(arrivalAt)).toMinutes()
        when {
            minutes <= 0 -> null
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    } catch (e: Exception) {
        null
    }
}

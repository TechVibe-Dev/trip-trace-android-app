package com.techvibedev.triptrace.ui.screens.trips

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
                    text = "No tenes viajes planeados todavia.",
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

@Composable
private fun PlannedTripCard(
    trip: TripResponse,
    onStartTrip: (String) -> Unit,
    onUseCurrentTime: (String) -> Unit,
) {
    val isStale = isDepartureStale(trip.plannedDepartureAt)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${trip.originName} \u2192 ${trip.destinationName}",
                style = MaterialTheme.typography.titleMedium,
            )

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
                        text = "Hora planeada (${formatLocalTime(trip.plannedDepartureAt)}) ya paso",
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Sale ${formatLocalTime(trip.plannedDepartureAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onStartTrip(trip.id) }) {
                        Text("Iniciar")
                    }
                }
            }
        }
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

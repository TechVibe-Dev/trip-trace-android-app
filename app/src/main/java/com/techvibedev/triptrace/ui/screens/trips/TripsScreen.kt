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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

@Composable
fun TripsScreen(
    tripRepository: TripRepository,
    onOpenTrip: (String) -> Unit,
    onStartTrip: (String) -> Unit,
) {
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
                            onOpenTrip = onOpenTrip,
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
    onOpenTrip: (String) -> Unit,
    onStartTrip: (String) -> Unit,
    onUseCurrentTime: (String) -> Unit,
) {
    Card(
        onClick = { onOpenTrip(trip.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${trip.originName} \u2192 ${trip.destinationName}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = "Ver mapa",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            PlannedTripInfo(trip = trip)
            Spacer(modifier = Modifier.height(8.dp))
            PlannedTripActions(trip = trip, onStartTrip = onStartTrip, onUseCurrentTime = onUseCurrentTime)
        }
    }
}

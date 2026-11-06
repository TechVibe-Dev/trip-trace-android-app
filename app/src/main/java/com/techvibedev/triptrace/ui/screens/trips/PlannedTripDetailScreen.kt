package com.techvibedev.triptrace.ui.screens.trips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
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

@Composable
fun PlannedTripDetailScreen(
    tripId: String,
    tripRepository: TripRepository,
    onBack: () -> Unit,
    onStartTrip: (String) -> Unit,
) {
    var trip by remember(tripId) { mutableStateOf<TripResponse?>(null) }
    var errorMessage by remember(tripId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadTrip() {
        tripRepository.getTrip(tripId).fold(
            onSuccess = {
                trip = it
                errorMessage = null
            },
            onFailure = { errorMessage = "No se pudo cargar el viaje." },
        )
    }

    LaunchedEffect(tripId) { loadTrip() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = trip?.let { "${it.originName} → ${it.destinationName}" } ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }

        val currentTrip = trip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                currentTrip != null -> PlannedRouteMap(trip = currentTrip)
                errorMessage != null -> Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
                else -> CircularProgressIndicator()
            }
        }

        if (currentTrip != null) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    PlannedTripInfo(trip = currentTrip)
                    Spacer(modifier = Modifier.height(12.dp))
                    PlannedTripActions(
                        trip = currentTrip,
                        onStartTrip = { id ->
                            scope.launch {
                                tripRepository.startTrip(id).fold(
                                    onSuccess = { onStartTrip(id) },
                                    onFailure = { errorMessage = "No se pudo iniciar el viaje." },
                                )
                            }
                        },
                        onUseCurrentTime = { id ->
                            scope.launch {
                                tripRepository.useCurrentTimeAsDeparture(id).fold(
                                    onSuccess = { loadTrip() },
                                    onFailure = { errorMessage = "No se pudo actualizar la hora." },
                                )
                            }
                        },
                    )
                    val actionError = errorMessage
                    if (actionError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = actionError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// The route Google suggested when the trip was created. Without a stored
// route (the route calculation failed) it still shows both ends of the trip.
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
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
        ),
        // Bounds-fitting needs the map to have its real size, which is only
        // guaranteed once it has loaded.
        onMapLoaded = {
            val bounds = LatLngBounds.Builder().apply {
                include(origin)
                include(destination)
                routePoints.forEach { include(it) }
            }.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 96))
        },
    ) {
        if (routePoints.size > 1) {
            Polyline(points = routePoints, color = MaterialTheme.colorScheme.primary, width = 12f)
        }
        Marker(state = rememberMarkerState(position = origin), title = trip.originName)
        Marker(state = rememberMarkerState(position = destination), title = trip.destinationName)
    }
}

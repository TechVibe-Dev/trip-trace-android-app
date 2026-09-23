package com.techvibedev.triptrace.ui.screens.createtrip

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.techvibedev.triptrace.data.model.TripCreateRequest
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.location.LocationProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

// Destination (and origin, when the user opts out of current location) still
// use placeholder coordinates — geocoding a typed address isn't wired up
// yet, see issue android#23. Origin now uses real GPS when "Ubicacion
// actual" is selected (this PR).
private const val PLACEHOLDER_LAT = -34.9011
private const val PLACEHOLDER_LNG = -56.1645

@Composable
fun CreateTripScreen(
    tripRepository: TripRepository,
    onTripSaved: () -> Unit,
    onTripStarted: (String) -> Unit,
) {
    val context = LocalContext.current
    val locationProvider = remember { LocationProvider(context.applicationContext) }

    var useCurrentLocation by remember { mutableStateOf(true) }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf("") }
    val stops = remember { mutableStateListOf<String>() }
    var newStop by remember { mutableStateOf("") }
    var departureTime by remember { mutableStateOf("18:30") }
    var desiredArrivalTime by remember { mutableStateOf("19:15") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun fetchCurrentLocation() {
        isLoadingLocation = true
        locationError = null
        val result = locationProvider.getCurrentLocation()
        isLoadingLocation = false
        result.fold(
            onSuccess = { (lat, lng) ->
                currentLat = lat
                currentLng = lng
            },
            onFailure = { exception ->
                // Shown directly on screen (not just Logcat) since testing
                // happens straight on a real phone, without Android Studio
                // attached to read logs.
                val detail = exception.message ?: exception::class.simpleName ?: "error desconocido"
                locationError = "No se pudo obtener tu ubicacion: $detail"
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch { fetchCurrentLocation() }
        } else {
            locationError = "Se necesita permiso de ubicacion"
        }
    }

    fun requestCurrentLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            scope.launch { fetchCurrentLocation() }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        if (useCurrentLocation) {
            requestCurrentLocation()
        }
    }

    fun save(startNow: Boolean) {
        if (destination.isBlank()) {
            errorMessage = "Ingresa un destino"
            return
        }
        if (useCurrentLocation && (currentLat == null || currentLng == null)) {
            errorMessage = "Esperando tu ubicacion, intenta de nuevo en un momento"
            return
        }
        errorMessage = null
        isSaving = true
        scope.launch {
            val originLat = if (useCurrentLocation) currentLat!! else PLACEHOLDER_LAT
            val originLng = if (useCurrentLocation) currentLng!! else PLACEHOLDER_LNG
            val request = TripCreateRequest(
                originName = if (useCurrentLocation) "Ubicacion actual" else "Origen",
                originLat = originLat,
                originLng = originLng,
                destinationName = destination,
                destinationLat = PLACEHOLDER_LAT,
                destinationLng = PLACEHOLDER_LNG,
                plannedDepartureAt = timeTextToIso(departureTime),
                desiredArrivalAt = timeTextToIso(desiredArrivalTime),
            )
            val result = tripRepository.createTrip(request)
            result.fold(
                onSuccess = { trip ->
                    if (startNow) {
                        val startResult = tripRepository.startTrip(trip.id)
                        isSaving = false
                        startResult.fold(
                            onSuccess = { onTripStarted(trip.id) },
                            onFailure = {
                                errorMessage = "El viaje se guardo pero no se pudo iniciar."
                            },
                        )
                    } else {
                        isSaving = false
                        onTripSaved()
                    }
                },
                onFailure = {
                    isSaving = false
                    errorMessage = "No se pudo guardar el viaje."
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Nuevo viaje",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OriginField(
            useCurrentLocation = useCurrentLocation,
            isLoadingLocation = isLoadingLocation,
            locationError = locationError,
            onChangeClick = {
                useCurrentLocation = !useCurrentLocation
                if (useCurrentLocation) {
                    requestCurrentLocation()
                }
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("Destino") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(10.dp))

        stops.forEachIndexed { index, stop ->
            StopRow(label = stop, onRemove = { stops.removeAt(index) })
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newStop,
                onValueChange = { newStop = it },
                label = { Text("Agregar parada") },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (newStop.isNotBlank()) {
                        stops.add(newStop)
                        newStop = ""
                    }
                },
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Agregar parada")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = departureTime,
                onValueChange = { departureTime = it },
                label = { Text("Hora de salida") },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = desiredArrivalTime,
                onValueChange = { desiredArrivalTime = it },
                label = { Text("Quiero llegar") },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { save(startNow = false) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Guardar")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { save(startNow = true) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Guardar e iniciar ahora")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun OriginField(
    useCurrentLocation: Boolean,
    isLoadingLocation: Boolean,
    locationError: String?,
    onChangeClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (useCurrentLocation) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (useCurrentLocation) "Ubicacion actual" else "Origen",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (useCurrentLocation && isLoadingLocation) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            TextButton(onClick = onChangeClick) {
                Text(if (useCurrentLocation) "Cambiar" else "Usar ubicacion actual")
            }
        }
        if (useCurrentLocation && locationError != null) {
            Text(
                text = locationError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun StopRow(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Quitar parada")
        }
    }
}

private fun timeTextToIso(timeText: String): String? {
    return try {
        val time = LocalTime.parse(timeText)
        LocalDateTime.of(LocalDate.now(), time)
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime()
            .toString()
    } catch (e: DateTimeParseException) {
        null
    }
}

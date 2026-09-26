package com.techvibedev.triptrace.ui.screens.createtrip

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocation
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.techvibedev.triptrace.R
import com.techvibedev.triptrace.data.model.StopCreateRequest
import com.techvibedev.triptrace.data.model.TripCreateRequest
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.location.GeocodingProvider
import com.techvibedev.triptrace.location.LocationProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

// Origin still falls back to a placeholder when the user opts out of
// "Ubicacion actual" — there's no text field for typing a custom origin
// address yet, only the current-location toggle. Destination and stops are
// geocoded from whatever text the user types (see GeocodingProvider). Also
// used as the map-confirm dialog's fallback center when geocoding fails
// outright and there's no current GPS location to center on instead.
private const val PLACEHOLDER_LAT = -34.9011
private const val PLACEHOLDER_LNG = -56.1645

private const val LOG_TAG = "CreateTripScreen"

// A stop as entered here — name is always required; confirmedLat/Lng are
// set only if the user opened the map-confirm dialog for this stop and
// dragged the pin (android#73). Both null means "not confirmed yet, resolve
// by geocoding the name at save time" — the same as before this feature
// existed, so typing a stop and saving without ever touching the map still
// works exactly as it did.
private data class StopDraft(
    val name: String,
    val confirmedLat: Double? = null,
    val confirmedLng: Double? = null,
)

// Which field the open map-confirm dialog is for, and the point it should
// start centered on (the just-geocoded position, or a previously confirmed
// one if reopening).
private sealed class ConfirmTarget {
    data object Destination : ConfirmTarget()
    data class Stop(val index: Int) : ConfirmTarget()
}

private data class MapConfirmState(
    val target: ConfirmTarget,
    val label: String,
    val lat: Double,
    val lng: Double,
    // false when geocoding couldn't resolve the typed text at all, and the
    // dialog opened anyway with a fallback center so the user can place the
    // pin themselves — the dialog shows different guidance text in that
    // case, since there's no "found" point to merely adjust.
    val wasGeocoded: Boolean,
)

@Composable
fun CreateTripScreen(
    tripRepository: TripRepository,
    onTripSaved: () -> Unit,
    onTripStarted: (String) -> Unit,
) {
    val context = LocalContext.current
    val locationProvider = remember { LocationProvider(context.applicationContext) }
    val geocodingProvider = remember { GeocodingProvider(context.applicationContext) }

    var useCurrentLocation by remember { mutableStateOf(true) }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf("") }
    // Set once the user confirms (optionally adjusts) the destination pin
    // on the map — used instead of re-geocoding the text at save time.
    // Cleared whenever the destination text changes, so a stale confirmed
    // point can never silently apply to different text.
    var destinationCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val stops = remember { mutableStateListOf<StopDraft>() }
    var newStop by remember { mutableStateOf("") }
    // Departure defaults to right now — the most common case ("Guardar e
    // iniciar ahora"). Desired arrival has no sensible default (we can't
    // guess what time the user wants to arrive), so it starts empty; the
    // field is optional on the API, an empty value just means "not set".
    var departureTime by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var desiredArrivalTime by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mapConfirmState by remember { mutableStateOf<MapConfirmState?>(null) }
    var isResolvingMapConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Center for the map-confirm dialog when geocoding fails outright and
    // there's nothing found to center on instead — the user's current
    // location is a reasonable starting guess (the destination is often
    // somewhere near where the trip starts), falling back to the same
    // fixed placeholder the origin itself uses when GPS isn't available.
    fun fallbackMapCenter(): Pair<Double, Double> =
        (currentLat ?: PLACEHOLDER_LAT) to (currentLng ?: PLACEHOLDER_LNG)

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

    // Geocodes (unless already confirmed on the map) and opens the confirm
    // dialog for the destination — reuses destinationCoords as the starting
    // pin position if the user is reopening it to adjust further. If
    // geocoding fails outright, opens the dialog anyway on a fallback
    // center instead of just leaving the user stuck on an error message —
    // they place the pin themselves.
    fun openMapConfirmForDestination() {
        if (destination.isBlank()) {
            errorMessage = "Ingresa un destino primero"
            return
        }
        scope.launch {
            isResolvingMapConfirm = true
            val coords = destinationCoords ?: geocodingProvider.geocode(destination).getOrNull()
            isResolvingMapConfirm = false
            errorMessage = null
            val (lat, lng) = coords ?: fallbackMapCenter()
            mapConfirmState = MapConfirmState(
                target = ConfirmTarget.Destination,
                label = destination,
                lat = lat,
                lng = lng,
                wasGeocoded = coords != null,
            )
        }
    }

    fun openMapConfirmForStop(index: Int) {
        val stop = stops.getOrNull(index) ?: return
        scope.launch {
            isResolvingMapConfirm = true
            val coords = if (stop.confirmedLat != null && stop.confirmedLng != null) {
                stop.confirmedLat to stop.confirmedLng
            } else {
                geocodingProvider.geocode(stop.name).getOrNull()
            }
            isResolvingMapConfirm = false
            errorMessage = null
            val (lat, lng) = coords ?: fallbackMapCenter()
            mapConfirmState = MapConfirmState(
                target = ConfirmTarget.Stop(index),
                label = stop.name,
                lat = lat,
                lng = lng,
                wasGeocoded = coords != null,
            )
        }
    }

    fun save(startNow: Boolean) {
        // If the user typed a stop but never tapped "+" to add it, commit
        // it now instead of silently losing it — this turned out to be the
        // actual reason stops weren't getting saved despite being typed:
        // the pending text just sat in the field, never entering `stops`.
        if (newStop.isNotBlank()) {
            stops.add(StopDraft(name = newStop))
            newStop = ""
        }

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
            // Use the map-confirmed point if there is one (android#73) —
            // otherwise fall back to geocoding the text, same as before
            // this feature existed. Confirming on the map is optional, not
            // a required step — unless geocoding fails outright, in which
            // case there's no other way to resolve a point, so the map
            // opens automatically instead of just leaving the user stuck.
            val destinationCoordsResolved = destinationCoords
                ?: geocodingProvider.geocode(destination).getOrNull()
            if (destinationCoordsResolved == null) {
                isSaving = false
                val (lat, lng) = fallbackMapCenter()
                mapConfirmState = MapConfirmState(
                    target = ConfirmTarget.Destination,
                    label = destination,
                    lat = lat,
                    lng = lng,
                    wasGeocoded = false,
                )
                errorMessage = "No se encontro \"$destination\" automaticamente — marca el punto en el mapa"
                return@launch
            }

            // Same either/or resolution per stop — geocode only the ones
            // that weren't already confirmed on the map. If any one of them
            // can't be resolved, same treatment as the destination above:
            // open the map for that specific stop instead of just erroring.
            val geocodedStops = mutableListOf<Pair<String, Pair<Double, Double>>>()
            for ((index, stop) in stops.withIndex()) {
                val stopCoords = if (stop.confirmedLat != null && stop.confirmedLng != null) {
                    stop.confirmedLat to stop.confirmedLng
                } else {
                    geocodingProvider.geocode(stop.name).getOrNull()
                }
                if (stopCoords == null) {
                    isSaving = false
                    val (lat, lng) = fallbackMapCenter()
                    mapConfirmState = MapConfirmState(
                        target = ConfirmTarget.Stop(index),
                        label = stop.name,
                        lat = lat,
                        lng = lng,
                        wasGeocoded = false,
                    )
                    errorMessage = "No se encontro \"${stop.name}\" automaticamente — marca el punto en el mapa"
                    return@launch
                }
                geocodedStops.add(stop.name to stopCoords)
            }

            val originLat = if (useCurrentLocation) currentLat!! else PLACEHOLDER_LAT
            val originLng = if (useCurrentLocation) currentLng!! else PLACEHOLDER_LNG
            // Best-effort, unlike destination/stops above — the origin
            // comes from real GPS, not typed text, so a failure here is
            // more likely a transient network/service hiccup than "this
            // place doesn't exist". Not worth blocking a valid save just to
            // give the origin a nicer name (android#69) — falls back to the
            // previous fixed text silently.
            val originName = if (useCurrentLocation) {
                geocodingProvider.reverseGeocode(originLat, originLng).getOrDefault("Ubicacion actual")
            } else {
                "Origen"
            }
            val request = TripCreateRequest(
                originName = originName,
                originLat = originLat,
                originLng = originLng,
                destinationName = destination,
                destinationLat = destinationCoordsResolved.first,
                destinationLng = destinationCoordsResolved.second,
                plannedDepartureAt = timeTextToIso(departureTime),
                desiredArrivalAt = timeTextToIso(desiredArrivalTime),
            )
            val result = tripRepository.createTrip(request)
            result.fold(
                onSuccess = { trip ->
                    // Best-effort from here on — the trip itself already
                    // exists at this point (geocoding already validated
                    // everything above), so a stop or route hiccup
                    // shouldn't trap the user on this screen. Failures are
                    // logged rather than surfaced (we're about to navigate
                    // away, so an on-screen error here would never be
                    // seen) — check Logcat for this tag if a stop seems to
                    // go missing.
                    geocodedStops.forEachIndexed { index, (name, coords) ->
                        val stopRequest = StopCreateRequest(
                            type = "PLANNED",
                            name = name,
                            lat = coords.first,
                            lng = coords.second,
                            sequence = index,
                        )
                        val stopResult = tripRepository.createStop(trip.id, stopRequest)
                        stopResult.onFailure { e ->
                            Log.w(LOG_TAG, "Failed to save stop \"$name\" for trip ${trip.id}", e)
                        }
                    }

                    tripRepository.calculateRoute(trip.id)

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
            onValueChange = {
                destination = it
                // The confirmed pin (if any) belonged to the previous
                // text — it no longer applies once the text changes.
                destinationCoords = null
            },
            label = { Text("Destino") },
            singleLine = true,
            enabled = !isSaving,
            trailingIcon = {
                IconButton(
                    onClick = { openMapConfirmForDestination() },
                    enabled = !isSaving && !isResolvingMapConfirm && destination.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditLocation,
                        contentDescription = "Confirmar destino en el mapa",
                        tint = if (destinationCoords != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(10.dp))

        stops.forEachIndexed { index, stop ->
            StopRow(
                stop = stop,
                onRemove = { stops.removeAt(index) },
                onConfirmOnMap = { openMapConfirmForStop(index) },
                enabled = !isSaving && !isResolvingMapConfirm,
            )
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
                        stops.add(StopDraft(name = newStop))
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
                label = { Text("Quiero llegar (opcional)") },
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

    mapConfirmState?.let { state ->
        LocationConfirmDialog(
            label = state.label,
            initialLat = state.lat,
            initialLng = state.lng,
            wasGeocoded = state.wasGeocoded,
            onConfirm = { lat, lng ->
                when (val target = state.target) {
                    is ConfirmTarget.Destination -> destinationCoords = lat to lng
                    is ConfirmTarget.Stop -> {
                        val current = stops.getOrNull(target.index)
                        if (current != null) {
                            stops[target.index] = current.copy(confirmedLat = lat, confirmedLng = lng)
                        }
                    }
                }
                mapConfirmState = null
            },
            onDismiss = { mapConfirmState = null },
        )
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
private fun StopRow(
    stop: StopDraft,
    onRemove: () -> Unit,
    onConfirmOnMap: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stop.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onConfirmOnMap, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.EditLocation,
                contentDescription = "Confirmar parada en el mapa",
                tint = if (stop.confirmedLat != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onRemove) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Quitar parada")
        }
    }
}

// Lets the user see where a typed address actually geocoded to, and drag
// the pin to correct it if it's off (android#73) — the core gap this issue
// was about: geocoding happened "blind" before, with no way to see or fix
// a wrong result. Confirming here is optional; saving without ever opening
// this dialog still works exactly as before, resolving via geocoding at
// save time. Used for both the destination and any stop, distinguished by
// the caller via `label` and where the confirmed point gets stored.
//
// wasGeocoded = false means geocoding couldn't resolve the typed text at
// all — the dialog still opens, centered on a fallback point, so the user
// has a way to place the pin themselves instead of hitting a dead end.
@Composable
private fun LocationConfirmDialog(
    label: String,
    initialLat: Double,
    initialLng: Double,
    wasGeocoded: Boolean,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Dark map style (android#68), consistent with every other map in the
    // app. Remembered so it's parsed once, not on every recomposition
    // while the marker is being dragged.
    val mapProperties = remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
    }
    // draggable = true on the Marker below means Maps Compose itself keeps
    // this position updated as the user drags — no reactive-reassignment
    // workaround needed here (unlike the live-trip map's current-position
    // marker, which had to fight rememberMarkerState only picking up an
    // initial value — that bug doesn't apply to a user-driven drag, only
    // to programmatically-driven position updates).
    val markerState = rememberMarkerState(position = LatLng(initialLat, initialLng))
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), 16f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                text = "Confirmar: $label",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (wasGeocoded) {
                    "Mantene presionado el pin para arrastrarlo y ajustar la ubicacion."
                } else {
                    "No pudimos encontrar esta direccion automaticamente. Mantene presionado " +
                        "el pin y arrastralo hasta el lugar correcto."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (wasGeocoded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        rotationGesturesEnabled = false,
                        tiltGesturesEnabled = false,
                    ),
                ) {
                    Marker(
                        state = markerState,
                        draggable = true,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(markerState.position.latitude, markerState.position.longitude)
                    },
                ) {
                    Text("Confirmar")
                }
            }
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

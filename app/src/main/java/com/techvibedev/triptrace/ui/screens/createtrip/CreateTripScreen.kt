@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse
import com.techvibedev.triptrace.data.model.StopCreateRequest
import com.techvibedev.triptrace.data.model.TripCreateRequest
import com.techvibedev.triptrace.data.repository.FavoritePlaceRepository
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.location.GeocodingProvider
import com.techvibedev.triptrace.location.LocationProvider
import com.techvibedev.triptrace.ui.components.FavoriteAwareTextField
import com.techvibedev.triptrace.ui.components.LocationConfirmDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

// Fallback center for the map-confirm dialog when geocoding fails outright
// and there's no current GPS location to center on instead (e.g. origin
// typed manually, with location permission denied).
private const val PLACEHOLDER_LAT = -34.9011
private const val PLACEHOLDER_LNG = -56.1645

private const val LOG_TAG = "CreateTripScreen"

// A stop as entered here — name is always required; confirmedLat/Lng are
// set either by dragging the pin in the map-confirm dialog, or directly by
// picking a favorite (which already has known coordinates, skipping
// geocoding entirely). Both null means "not confirmed yet, resolve by
// geocoding the name at save time" — the same as before this feature
// existed, so typing a stop and saving without ever touching the map or
// favorites still works exactly as it did.
private data class StopDraft(
    val name: String,
    val confirmedLat: Double? = null,
    val confirmedLng: Double? = null,
)

// Which field the open map-confirm dialog is for, and the point it should
// start centered on (the just-geocoded position, or a previously confirmed
// one if reopening).
private sealed class ConfirmTarget {
    data object Origin : ConfirmTarget()
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
    favoritePlaceRepository: FavoritePlaceRepository,
    onTripSaved: () -> Unit,
    onTripStarted: (String) -> Unit,
) {
    val context = LocalContext.current
    val locationProvider = remember { LocationProvider(context.applicationContext) }
    val geocodingProvider = remember { GeocodingProvider(context.applicationContext) }

    var favorites by remember { mutableStateOf<List<FavoritePlaceResponse>>(emptyList()) }

    var useCurrentLocation by remember { mutableStateOf(true) }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    // Only used when useCurrentLocation is false — a manual origin, same
    // shape as destination (typed text + optionally-confirmed coords).
    // Preserved across toggling useCurrentLocation back and forth, so
    // switching to GPS and back doesn't lose what was already typed.
    var originText by remember { mutableStateOf("") }
    var originCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var destination by remember { mutableStateOf("") }
    // Set once the user confirms (optionally adjusts) the destination pin
    // on the map, or picks a favorite — used instead of re-geocoding the
    // text at save time. Cleared whenever the destination text changes, so
    // a stale confirmed point can never silently apply to different text.
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
    // somewhere near where the trip starts), falling back to a fixed
    // placeholder when GPS isn't available either.
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
        favoritePlaceRepository.list().onSuccess { favorites = it }
    }

    // Geocodes (unless already confirmed on the map or picked as a
    // favorite) and opens the confirm dialog for the origin — same pattern
    // as destination below. Only reachable when useCurrentLocation is
    // false (the field doesn't exist otherwise).
    fun openMapConfirmForOrigin() {
        if (originText.isBlank()) {
            errorMessage = "Ingresa un origen primero"
            return
        }
        scope.launch {
            isResolvingMapConfirm = true
            val coords = originCoords ?: geocodingProvider.geocode(originText).getOrNull()
            isResolvingMapConfirm = false
            errorMessage = null
            val (lat, lng) = coords ?: fallbackMapCenter()
            mapConfirmState = MapConfirmState(
                target = ConfirmTarget.Origin,
                label = originText,
                lat = lat,
                lng = lng,
                wasGeocoded = coords != null,
            )
        }
    }

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
        if (!useCurrentLocation && originText.isBlank()) {
            errorMessage = "Ingresa un origen"
            return
        }
        errorMessage = null
        isSaving = true
        scope.launch {
            val originCoordsResolved = if (useCurrentLocation) {
                currentLat!! to currentLng!!
            } else {
                originCoords ?: geocodingProvider.geocode(originText).getOrNull()
            }
            if (originCoordsResolved == null) {
                isSaving = false
                val (lat, lng) = fallbackMapCenter()
                mapConfirmState = MapConfirmState(
                    target = ConfirmTarget.Origin,
                    label = originText,
                    lat = lat,
                    lng = lng,
                    wasGeocoded = false,
                )
                errorMessage = "No se encontro \"$originText\" automaticamente — marca el punto en el mapa"
                return@launch
            }

            // Use the map-confirmed/favorite point if there is one —
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
            // that weren't already confirmed on the map or picked as a
            // favorite. If any one of them can't be resolved, same
            // treatment as origin/destination above: open the map for that
            // specific stop instead of just erroring.
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

            // origin_name: a real typed/favorite name when entered
            // manually; reverse-geocoded from GPS (best-effort, falls back
            // to the old fixed text on failure) when using current location.
            val originName = if (useCurrentLocation) {
                geocodingProvider
                    .reverseGeocode(originCoordsResolved.first, originCoordsResolved.second)
                    .getOrDefault("Ubicacion actual")
            } else {
                originText
            }
            val request = TripCreateRequest(
                originName = originName,
                originLat = originCoordsResolved.first,
                originLng = originCoordsResolved.second,
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
            originText = originText,
            onOriginTextChange = {
                originText = it
                originCoords = null
            },
            favorites = favorites,
            onFavoriteSelected = { favorite ->
                originText = favorite.name
                originCoords = favorite.lat to favorite.lng
            },
            onConfirmOnMap = { openMapConfirmForOrigin() },
            hasConfirmedCoords = originCoords != null,
            enabled = !isSaving && !isResolvingMapConfirm,
            onChangeClick = {
                useCurrentLocation = !useCurrentLocation
                if (useCurrentLocation) {
                    requestCurrentLocation()
                }
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        FavoriteAwareTextField(
            label = "Destino",
            value = destination,
            onValueChange = {
                destination = it
                // The confirmed pin (if any) belonged to the previous
                // text — it no longer applies once the text changes.
                destinationCoords = null
            },
            favorites = favorites,
            onFavoriteSelected = { favorite ->
                destination = favorite.name
                destinationCoords = favorite.lat to favorite.lng
            },
            enabled = !isSaving,
            extraTrailingIcon = {
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
            FavoriteAwareTextField(
                label = "Agregar parada",
                value = newStop,
                onValueChange = { newStop = it },
                favorites = favorites,
                // A favorite already has known coordinates — add it as a
                // confirmed stop directly, instead of just filling the text
                // and waiting for "+" (which would geocode it again,
                // pointlessly, for a point we already know exactly).
                onFavoriteSelected = { favorite ->
                    stops.add(
                        StopDraft(
                            name = favorite.name,
                            confirmedLat = favorite.lat,
                            confirmedLng = favorite.lng,
                        ),
                    )
                    newStop = ""
                },
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

        // These were plain free-text fields ("18:30" typed by hand), easy
        // to mistype with no feedback until save silently dropped an
        // unparseable value. A native time picker removes that failure
        // mode entirely — every value it can produce is valid.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimePickerField(
                label = "Hora de salida",
                timeText = departureTime,
                onTimeSelected = { departureTime = it },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
            TimePickerField(
                label = "Quiero llegar (opcional)",
                timeText = desiredArrivalTime,
                onTimeSelected = { desiredArrivalTime = it },
                onClear = { desiredArrivalTime = "" },
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
                    is ConfirmTarget.Origin -> originCoords = lat to lng
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

// Toggles between "Ubicacion actual" (GPS, unchanged from before) and a
// manual FavoriteAwareTextField — the manual path didn't exist before
// android#81, origin had no text entry at all.
@Composable
private fun OriginField(
    useCurrentLocation: Boolean,
    isLoadingLocation: Boolean,
    locationError: String?,
    originText: String,
    onOriginTextChange: (String) -> Unit,
    favorites: List<FavoritePlaceResponse>,
    onFavoriteSelected: (FavoritePlaceResponse) -> Unit,
    onConfirmOnMap: () -> Unit,
    hasConfirmedCoords: Boolean,
    enabled: Boolean,
    onChangeClick: () -> Unit,
) {
    Column {
        if (useCurrentLocation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ubicacion actual",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isLoadingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onChangeClick, enabled = enabled) {
                    Text("Cambiar")
                }
            }
            if (locationError != null) {
                Text(
                    text = locationError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        } else {
            FavoriteAwareTextField(
                label = "Origen",
                value = originText,
                onValueChange = onOriginTextChange,
                favorites = favorites,
                onFavoriteSelected = onFavoriteSelected,
                enabled = enabled,
                extraTrailingIcon = {
                    IconButton(
                        onClick = onConfirmOnMap,
                        enabled = enabled && originText.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditLocation,
                            contentDescription = "Confirmar origen en el mapa",
                            tint = if (hasConfirmedCoords) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onChangeClick, enabled = enabled) {
                Text("Usar ubicacion actual")
            }
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

// A read-only field that opens a native TimePicker dialog on tap, instead
// of accepting freeform text — every value it can produce is already a
// valid "HH:mm", so timeTextToIso() below never has to reject a typo.
// onClear is only passed for the optional field (desired arrival); the
// departure field is always required, so it has nothing to clear to.
@Composable
private fun TimePickerField(
    label: String,
    timeText: String,
    onTimeSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.OutlinedTextField(
        value = timeText,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        trailingIcon = {
            Row {
                if (onClear != null && timeText.isNotEmpty()) {
                    IconButton(onClick = onClear, enabled = enabled) {
                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Borrar hora")
                    }
                }
                IconButton(onClick = { showDialog = true }, enabled = enabled) {
                    Icon(imageVector = Icons.Filled.AccessTime, contentDescription = "Elegir hora")
                }
            }
        },
        modifier = modifier,
    )

    if (showDialog) {
        val initial = parseTimeOrNull(timeText) ?: LocalTime.now()
        val timePickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismiss = { showDialog = false },
            onConfirm = {
                onTimeSelected("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                showDialog = false
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}

private fun parseTimeOrNull(timeText: String): LocalTime? {
    return try {
        LocalTime.parse(timeText)
    } catch (e: DateTimeParseException) {
        null
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

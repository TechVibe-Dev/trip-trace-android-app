@file:OptIn(ExperimentalMaterial3Api::class)

package com.techvibedev.triptrace.ui.screens.createtrip

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse
import com.techvibedev.triptrace.data.model.StopCreateRequest
import com.techvibedev.triptrace.data.model.TripCreateRequest
import com.techvibedev.triptrace.data.model.ViaPointDto
import com.techvibedev.triptrace.data.repository.FavoritePlaceRepository
import com.techvibedev.triptrace.data.repository.RouteRepository
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.location.GeocodingProvider
import com.techvibedev.triptrace.location.LocationProvider
import com.techvibedev.triptrace.ui.components.FavoriteAwareTextField
import com.techvibedev.triptrace.ui.components.LocationConfirmDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    routeRepository: RouteRepository,
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
    // The day the trip leaves. Defaults to today; desired arrival is
    // assumed to fall on the same day, or the next one when its time is
    // earlier than departure.
    var departureDate by remember { mutableStateOf(LocalDate.now()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mapConfirmState by remember { mutableStateOf<MapConfirmState?>(null) }
    var isResolvingMapConfirm by remember { mutableStateOf(false) }
    // Null = let Google choose (its fastest route). Cleared whenever origin
    // or destination changes, since a route only makes sense between the
    // two places it was picked for.
    var routeChoice by remember { mutableStateOf<RouteChoice?>(null) }
    // Origin and destination as resolved when the route picker opened; the
    // picker is shown while this is set.
    var routePickerEndpoints by remember {
        mutableStateOf<Pair<Pair<Double, Double>, Pair<Double, Double>>?>(null)
    }
    var routePickerOriginName by remember { mutableStateOf("") }
    // The stops as resolved when the picker opened, so its previews go
    // through them like the saved trip's route will.
    var routePickerStops by remember { mutableStateOf<List<ViaPointDto>>(emptyList()) }
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
                locationError = "No se pudo obtener tu ubicación: $detail"
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch { fetchCurrentLocation() }
        } else {
            locationError = "Se necesita permiso de ubicación"
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
            errorMessage = "Ingresá un origen primero"
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
            errorMessage = "Ingresá un destino primero"
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

    // The picker needs real coordinates for both ends, so they're resolved
    // here the same way save() does. Geocoded results are kept as the
    // confirmed points, so the trip is saved between the same two places
    // the route was picked for.
    fun openRoutePicker() {
        if (destination.isBlank()) {
            errorMessage = "Ingresá un destino primero"
            return
        }
        if (useCurrentLocation && (currentLat == null || currentLng == null)) {
            errorMessage = "Esperando tu ubicación, intentá de nuevo en un momento"
            return
        }
        if (!useCurrentLocation && originText.isBlank()) {
            errorMessage = "Ingresá un origen primero"
            return
        }
        scope.launch {
            isResolvingMapConfirm = true
            val originResolved = if (useCurrentLocation) {
                currentLat!! to currentLng!!
            } else {
                originCoords ?: geocodingProvider.geocode(originText).getOrNull()?.also { originCoords = it }
            }
            val destinationResolved = destinationCoords
                ?: geocodingProvider.geocode(destination).getOrNull()?.also { destinationCoords = it }
            isResolvingMapConfirm = false
            if (originResolved == null || destinationResolved == null) {
                errorMessage = "No se encontró el origen o el destino — confirmalo en el mapa primero"
                return@launch
            }
            // Names a saved route's start in Perfil; "Ubicación actual"
            // alone wouldn't say where that was.
            routePickerOriginName = if (useCurrentLocation) {
                geocodingProvider
                    .reverseGeocode(originResolved.first, originResolved.second)
                    .getOrDefault("Ubicación actual")
            } else {
                originText
            }
            errorMessage = null
            // Stops not confirmed on the map yet are geocoded here and kept
            // as confirmed, the same as origin and destination above.
            isResolvingMapConfirm = true
            val resolvedStops = mutableListOf<ViaPointDto>()
            for ((index, stop) in stops.withIndex()) {
                val coords = if (stop.confirmedLat != null && stop.confirmedLng != null) {
                    stop.confirmedLat to stop.confirmedLng
                } else {
                    geocodingProvider.geocode(stop.name).getOrNull()?.also { (lat, lng) ->
                        stops[index] = stop.copy(confirmedLat = lat, confirmedLng = lng)
                    }
                }
                if (coords == null) {
                    isResolvingMapConfirm = false
                    errorMessage = "No se encontró la parada \"${stop.name}\" — confirmala en el mapa primero"
                    return@launch
                }
                resolvedStops.add(ViaPointDto(name = stop.name, lat = coords.first, lng = coords.second))
            }
            isResolvingMapConfirm = false
            routePickerStops = resolvedStops
            routePickerEndpoints = originResolved to destinationResolved
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
            errorMessage = "Ingresá un destino"
            return
        }
        if (useCurrentLocation && (currentLat == null || currentLng == null)) {
            errorMessage = "Esperando tu ubicación, intentá de nuevo en un momento"
            return
        }
        if (!useCurrentLocation && originText.isBlank()) {
            errorMessage = "Ingresá un origen"
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
                errorMessage = "No se encontró \"$originText\" automáticamente — marcá el punto en el mapa"
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
                errorMessage = "No se encontró \"$destination\" automáticamente — marcá el punto en el mapa"
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
                    errorMessage = "No se encontró \"${stop.name}\" automáticamente — marcá el punto en el mapa"
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
                    .getOrDefault("Ubicación actual")
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
                plannedDepartureAt = toIsoDateTime(departureDate, departureTime),
                desiredArrivalAt = arrivalIsoDateTime(departureDate, departureTime, desiredArrivalTime),
                viaPoints = routeChoice?.viaPoints,
                routeName = routeChoice?.name,
                savedRouteId = routeChoice?.savedRouteId,
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
                                errorMessage = "El viaje se guardó pero no se pudo iniciar."
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

    val today = LocalDate.now()
    val isToday = departureDate == today
    var showDeparturePicker by remember { mutableStateOf(false) }
    var showArrivalPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Nuevo viaje",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Planificá a dónde vas y cuándo salís",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard {
                TimelineRow(marker = TimelineMarker.Origin, lineAbove = false, lineBelow = true) {
                    OriginField(
                        useCurrentLocation = useCurrentLocation,
                        isLoadingLocation = isLoadingLocation,
                        locationError = locationError,
                        originText = originText,
                        onOriginTextChange = {
                            originText = it
                            originCoords = null
                            routeChoice = null
                        },
                        favorites = favorites,
                        onFavoriteSelected = { favorite ->
                            originText = favorite.name
                            originCoords = favorite.lat to favorite.lng
                            routeChoice = null
                        },
                        onConfirmOnMap = { openMapConfirmForOrigin() },
                        hasConfirmedCoords = originCoords != null,
                        enabled = !isSaving && !isResolvingMapConfirm,
                        onChangeClick = {
                            useCurrentLocation = !useCurrentLocation
                            routeChoice = null
                            if (useCurrentLocation) {
                                requestCurrentLocation()
                            }
                        },
                    )
                }

                stops.forEachIndexed { index, stop ->
                    TimelineRow(marker = TimelineMarker.Stop, lineAbove = true, lineBelow = true) {
                        StopRow(
                            label = "Parada ${index + 1}",
                            stop = stop,
                            onRemove = { stops.removeAt(index) },
                            onConfirmOnMap = { openMapConfirmForStop(index) },
                            enabled = !isSaving && !isResolvingMapConfirm,
                        )
                    }
                }

                TimelineRow(marker = TimelineMarker.AddStop, lineAbove = true, lineBelow = true) {
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
                        underlined = true,
                        extraTrailingIcon = {
                            IconButton(
                                onClick = {
                                    if (newStop.isNotBlank()) {
                                        stops.add(StopDraft(name = newStop))
                                        newStop = ""
                                    }
                                },
                                enabled = !isSaving && newStop.isNotBlank(),
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = "Agregar parada")
                            }
                        },
                    )
                }

                TimelineRow(marker = TimelineMarker.Destination, lineAbove = true, lineBelow = false) {
                    FavoriteAwareTextField(
                        label = "Destino",
                        value = destination,
                        onValueChange = {
                            destination = it
                            // The confirmed pin (if any) belonged to the previous
                            // text — it no longer applies once the text changes.
                            destinationCoords = null
                            routeChoice = null
                        },
                        favorites = favorites,
                        onFavoriteSelected = { favorite ->
                            destination = favorite.name
                            destinationCoords = favorite.lat to favorite.lng
                            routeChoice = null
                        },
                        enabled = !isSaving,
                        underlined = true,
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
                }

                Spacer(modifier = Modifier.height(10.dp))

                RouteField(
                    routeChoice = routeChoice,
                    onPickClick = { openRoutePicker() },
                    onClear = { routeChoice = null },
                    enabled = !isSaving && !isResolvingMapConfirm,
                )
            }

            SectionCard {
                WhenSection(
                    date = departureDate,
                    today = today,
                    onDateChange = { departureDate = it },
                    departureTime = departureTime,
                    arrivalTime = desiredArrivalTime,
                    onDepartureClick = { showDeparturePicker = true },
                    onArrivalClick = { showArrivalPicker = true },
                    onArrivalClear = { desiredArrivalTime = "" },
                    enabled = !isSaving,
                )
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Starting right away only makes sense for a trip that leaves
        // today; any other day just gets saved for later.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isToday) {
                OutlinedButton(
                    onClick = { save(startNow = false) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Text("Guardar")
                }
                Button(
                    onClick = { save(startNow = true) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(52.dp),
                ) {
                    if (isSaving) {
                        SavingIndicator()
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar e iniciar")
                    }
                }
            } else {
                Button(
                    onClick = { save(startNow = false) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    if (isSaving) {
                        SavingIndicator()
                    } else {
                        Icon(
                            imageVector = Icons.Filled.EventAvailable,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Programar viaje")
                    }
                }
            }
        }
    }

    if (showDeparturePicker) {
        TimePickerDialog(
            initial = parseTimeOrNull(departureTime) ?: LocalTime.now(),
            onDismiss = { showDeparturePicker = false },
            onConfirm = {
                departureTime = it
                showDeparturePicker = false
            },
        )
    }

    if (showArrivalPicker) {
        TimePickerDialog(
            initial = parseTimeOrNull(desiredArrivalTime) ?: LocalTime.now(),
            onDismiss = { showArrivalPicker = false },
            onConfirm = {
                desiredArrivalTime = it
                showArrivalPicker = false
            },
        )
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

    routePickerEndpoints?.let { (originResolved, destinationResolved) ->
        RoutePickerDialog(
            routeRepository = routeRepository,
            originName = routePickerOriginName,
            stops = routePickerStops,
            origin = originResolved,
            destinationName = destination,
            destination = destinationResolved,
            plannedDepartureAt = toIsoDateTime(departureDate, departureTime),
            initialChoice = routeChoice,
            onConfirm = { choice ->
                routeChoice = choice
                routePickerEndpoints = null
            },
            onDismiss = { routePickerEndpoints = null },
        )
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
            content = content,
        )
    }
}

private enum class TimelineMarker(val size: Dp) {
    Origin(20.dp),
    Stop(10.dp),
    AddStop(20.dp),
    Destination(22.dp),
}

// Center of a field's first line, where each marker sits so it lines up
// with the text next to it.
private val TIMELINE_ANCHOR = 28.dp

// One stop of the itinerary: a marker on the left, joined to the ones
// above and below by a vertical line, and the field on the right.
@Composable
private fun TimelineRow(
    marker: TimelineMarker,
    lineAbove: Boolean,
    lineBelow: Boolean,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimelineLine(
                visible = lineAbove,
                modifier = Modifier.height(TIMELINE_ANCHOR - marker.size / 2),
            )
            TimelineMarkerIcon(marker)
            TimelineLine(visible = lineBelow, modifier = Modifier.weight(1f))
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun TimelineLine(visible: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .width(2.dp)
            .background(
                if (visible) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else Color.Transparent,
            ),
    )
}

@Composable
private fun TimelineMarkerIcon(marker: TimelineMarker) {
    val colors = MaterialTheme.colorScheme
    when (marker) {
        TimelineMarker.Origin -> Box(
            modifier = Modifier
                .size(marker.size)
                .background(colors.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(colors.tertiary, CircleShape),
            )
        }
        TimelineMarker.Stop -> Box(
            modifier = Modifier
                .size(marker.size)
                .border(2.dp, colors.onSurfaceVariant, CircleShape),
        )
        TimelineMarker.AddStop -> Box(
            modifier = Modifier
                .size(marker.size)
                .border(1.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        TimelineMarker.Destination -> Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(marker.size),
        )
    }
}

// Which way to drive: Google's fastest by default, or a route picked in
// RoutePickerDialog (a saved one, a Google alternative, or custom "pasar
// por" points).
@Composable
private fun RouteField(
    routeChoice: RouteChoice?,
    onPickClick: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ruta",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val description = if (routeChoice == null) {
                "Ruta más rápida"
            } else {
                val count = routeChoice.viaPoints.size
                val points = if (count == 1) "pasa por 1 punto" else "pasa por $count puntos"
                "${routeChoice.name} · $points"
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (routeChoice != null) {
            IconButton(onClick = onClear, enabled = enabled) {
                Icon(imageVector = Icons.Filled.Clear, contentDescription = "Volver a la ruta más rápida")
            }
        }
        TextButton(onClick = onPickClick, enabled = enabled) {
            Text(if (routeChoice == null) "Elegir" else "Cambiar")
        }
    }
}

// Either the current GPS location or a typed origin (with favorites and
// map confirmation, same as destination), with a way to switch between them.
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
                    .height(56.dp)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Origen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Ubicación actual",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
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
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
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
                underlined = true,
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
            AssistChip(
                onClick = onChangeClick,
                enabled = enabled,
                label = { Text("Usar mi ubicación") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun StopRow(
    label: String,
    stop: StopDraft,
    onRemove: () -> Unit,
    onConfirmOnMap: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Quitar parada")
        }
    }
}

// Date chips (today, tomorrow, any later day from a calendar), the
// departure and desired arrival times, and a one-line summary of both.
@Composable
private fun WhenSection(
    date: LocalDate,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    departureTime: String,
    arrivalTime: String,
    onDepartureClick: () -> Unit,
    onArrivalClick: () -> Unit,
    onArrivalClear: () -> Unit,
    enabled: Boolean,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val tomorrow = today.plusDays(1)
    val isOtherDay = date != today && date != tomorrow
    val arrivalDate = arrivalDate(date, departureTime, arrivalTime)

    Column(
        modifier = Modifier.padding(end = 8.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cuándo",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateChip(
                label = "Hoy",
                selected = date == today,
                onClick = { onDateChange(today) },
                enabled = enabled,
            )
            DateChip(
                label = "Mañana",
                selected = date == tomorrow,
                onClick = { onDateChange(tomorrow) },
                enabled = enabled,
            )
            DateChip(
                label = if (isOtherDay) shortDate(date) else "Elegir fecha",
                selected = isOtherDay,
                onClick = { showDatePicker = true },
                enabled = enabled,
                leadingIcon = Icons.Filled.CalendarToday,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeTile(
                label = "Salida",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                iconTint = MaterialTheme.colorScheme.tertiary,
                time = departureTime.ifEmpty { null },
                caption = relativeDate(date, today),
                highlighted = true,
                onClick = onDepartureClick,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            TimeTile(
                label = "Quiero llegar",
                icon = Icons.Filled.Flag,
                iconTint = MaterialTheme.colorScheme.secondary,
                time = arrivalTime.ifEmpty { null },
                caption = if (arrivalDate == null) "Opcional" else relativeDate(arrivalDate, today),
                highlighted = false,
                onClick = onArrivalClick,
                onClear = if (arrivalTime.isNotEmpty()) onArrivalClear else null,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = departureSummary(date, today, departureTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker) {
        TripDatePickerDialog(
            initial = date,
            today = today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                onDateChange(it)
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun DateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

// A tappable block with a big time, opening the time picker. onClear is
// only passed for the optional desired arrival once it has a value.
@Composable
private fun TimeTile(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    time: String?,
    caption: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.background else Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onClear != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled, onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Borrar hora",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(
                text = time ?: "--:--",
                style = MaterialTheme.typography.headlineMedium,
                color = if (time == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavingIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        strokeWidth = 2.dp,
    )
}

// Material's date picker works in UTC milliseconds, so dates are converted
// at UTC midnight both ways to avoid landing on the previous or next day.
@Composable
private fun TripDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !utcMillisToDate(utcTimeMillis).isBefore(today)

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    val colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(utcMillisToDate(it)) } ?: onDismiss() },
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        colors = colors,
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = "Fecha del viaje",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            showModeToggle = false,
            colors = colors,
        )
    }
}

private fun utcMillisToDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

// A native 24-hour time picker in a dialog — every value it can produce is
// already a valid "HH:mm", so nothing typed by hand has to be validated.
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = timePickerState)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onConfirm("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                        },
                    ) { Text("Aceptar") }
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

private val WEEKDAYS_SHORT = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val WEEKDAYS_LONG = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val MONTHS_LONG = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

// Spelled out by hand so the text doesn't depend on the device locale.
private fun shortDate(date: LocalDate): String {
    val weekday = WEEKDAYS_SHORT[date.dayOfWeek.value - 1].replaceFirstChar { it.uppercase() }
    return "$weekday ${date.dayOfMonth} ${MONTHS_SHORT[date.monthValue - 1]}"
}

private fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy, ${shortDate(date).replaceFirstChar { it.lowercase() }}"
    today.plusDays(1) -> "Mañana, ${shortDate(date).replaceFirstChar { it.lowercase() }}"
    else -> shortDate(date)
}

private fun departureSummary(date: LocalDate, today: LocalDate, timeText: String): String {
    val at = if (timeText.isEmpty()) "" else " a las $timeText"
    val weekday = WEEKDAYS_LONG[date.dayOfWeek.value - 1]
    return when (date) {
        today -> "Salís hoy$at"
        today.plusDays(1) -> "Salís mañana $weekday ${date.dayOfMonth}$at"
        else -> "Salís el $weekday ${date.dayOfMonth} de ${MONTHS_LONG[date.monthValue - 1]}$at"
    }
}

private fun toIsoDateTime(date: LocalDate, timeText: String): String? {
    val time = parseTimeOrNull(timeText) ?: return null
    return LocalDateTime.of(date, time)
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime()
        .toString()
}

// Desired arrival only has a time, so it lands on the departure day, or the
// next day when it's earlier than departure (a trip that crosses midnight).
private fun arrivalDate(departureDate: LocalDate, departureText: String, arrivalText: String): LocalDate? {
    val arrival = parseTimeOrNull(arrivalText) ?: return null
    val departure = parseTimeOrNull(departureText)
    return if (departure != null && arrival.isBefore(departure)) departureDate.plusDays(1) else departureDate
}

private fun arrivalIsoDateTime(departureDate: LocalDate, departureText: String, arrivalText: String): String? {
    val date = arrivalDate(departureDate, departureText, arrivalText) ?: return null
    return toIsoDateTime(date, arrivalText)
}

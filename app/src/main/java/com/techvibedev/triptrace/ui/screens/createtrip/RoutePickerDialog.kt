@file:OptIn(ExperimentalMaterial3Api::class)

package com.techvibedev.triptrace.ui.screens.createtrip

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
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
import com.techvibedev.triptrace.data.model.RoutePreviewRequest
import com.techvibedev.triptrace.data.model.RoutePreviewResponse
import com.techvibedev.triptrace.data.model.SavedRouteCreateRequest
import com.techvibedev.triptrace.data.model.SavedRouteResponse
import com.techvibedev.triptrace.data.model.ViaPointDto
import com.techvibedev.triptrace.data.repository.RouteRepository
import com.techvibedev.triptrace.location.GeocodingProvider
import com.techvibedev.triptrace.util.decodePolyline
import com.techvibedev.triptrace.util.furthestPointFrom
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Same cap as the API: 11 or more via points are billed at a higher rate.
private const val MAX_VIA_POINTS = 10

// Wait for the user to stop adding or removing points before asking for a
// new preview, so placing three points in a row costs one Google call, not
// three.
private const val CUSTOM_PREVIEW_DEBOUNCE_MS = 700L

private val OtherRouteColor = Color(0xFF7A7A7A)

// The route picked for a trip. A null RouteChoice (not this class) means
// "let Google choose", which is also what every trip did before this
// feature existed.
data class RouteChoice(
    val name: String,
    val viaPoints: List<ViaPointDto>,
    val savedRouteId: String?,
)

private sealed class RouteOption {
    data object Fastest : RouteOption()
    data class Alternative(val index: Int) : RouteOption()
    data class Saved(val id: String) : RouteOption()
    data object Custom : RouteOption()
}

// Full-screen picker shown from Create trip once origin and destination are
// known. Offers Google's fastest route, its alternatives, the user's saved
// routes between these two places, and a custom route built by long-pressing
// the map to add "pasar por" points. A custom route (or a Google
// alternative) can be saved with a name for next time.
@Composable
fun RoutePickerDialog(
    routeRepository: RouteRepository,
    originName: String,
    origin: Pair<Double, Double>,
    destinationName: String,
    destination: Pair<Double, Double>,
    plannedDepartureAt: String?,
    initialChoice: RouteChoice?,
    onConfirm: (RouteChoice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val geocodingProvider = remember { GeocodingProvider(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val mapProperties = remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
    }
    val originLatLng = LatLng(origin.first, origin.second)
    val destinationLatLng = LatLng(destination.first, destination.second)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(originLatLng, 11f)
    }

    // googleRoutes[0] is Google's fastest; any further ones are alternatives.
    var googleRoutes by remember { mutableStateOf<List<RoutePreviewResponse>>(emptyList()) }
    var savedRoutes by remember { mutableStateOf<List<SavedRouteResponse>>(emptyList()) }
    val savedPreviews = remember { mutableStateMapOf<String, RoutePreviewResponse>() }
    val customVia = remember { mutableStateListOf<ViaPointDto>() }
    var customPreview by remember { mutableStateOf<RoutePreviewResponse?>(null) }
    var customName by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<RouteOption>(RouteOption.Fastest) }
    var isLoading by remember { mutableStateOf(true) }
    var isPreviewing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    fun previewRequest(viaPoints: List<ViaPointDto>, alternatives: Boolean = false) = RoutePreviewRequest(
        originLat = origin.first,
        originLng = origin.second,
        destinationLat = destination.first,
        destinationLng = destination.second,
        viaPoints = viaPoints,
        plannedDepartureAt = plannedDepartureAt,
        alternatives = alternatives,
    )

    LaunchedEffect(Unit) {
        routeRepository.preview(previewRequest(emptyList(), alternatives = true)).fold(
            onSuccess = { googleRoutes = it },
            onFailure = { errorMessage = "No se pudieron cargar las rutas." },
        )
        routeRepository.listSavedRoutesBetween(
            origin.first,
            origin.second,
            destination.first,
            destination.second,
        ).onSuccess { savedRoutes = it }
        // Reopening the picker shows what was chosen before.
        if (initialChoice != null) {
            val saved = savedRoutes.firstOrNull { it.id == initialChoice.savedRouteId }
            if (saved != null) {
                selected = RouteOption.Saved(saved.id)
            } else if (initialChoice.viaPoints.isNotEmpty()) {
                customVia.addAll(initialChoice.viaPoints)
                customName = initialChoice.name
                selected = RouteOption.Custom
            }
        }
        isLoading = false
    }

    LaunchedEffect(customVia.toList()) {
        if (customVia.isEmpty()) {
            customPreview = null
            return@LaunchedEffect
        }
        delay(CUSTOM_PREVIEW_DEBOUNCE_MS)
        isPreviewing = true
        routeRepository.preview(previewRequest(customVia.toList())).fold(
            onSuccess = {
                customPreview = it.firstOrNull()
                errorMessage = null
            },
            onFailure = { errorMessage = "No se pudo calcular la ruta con esos puntos." },
        )
        isPreviewing = false
    }

    LaunchedEffect(selected) {
        val option = selected as? RouteOption.Saved ?: return@LaunchedEffect
        if (savedPreviews.containsKey(option.id)) return@LaunchedEffect
        val route = savedRoutes.firstOrNull { it.id == option.id } ?: return@LaunchedEffect
        isPreviewing = true
        routeRepository.preview(previewRequest(route.viaPoints)).fold(
            onSuccess = { previews -> previews.firstOrNull()?.let { savedPreviews[option.id] = it } },
            onFailure = { errorMessage = "No se pudo calcular \"${route.name}\"." },
        )
        isPreviewing = false
    }

    fun previewFor(option: RouteOption): RoutePreviewResponse? = when (option) {
        RouteOption.Fastest -> googleRoutes.getOrNull(0)
        is RouteOption.Alternative -> googleRoutes.getOrNull(option.index)
        is RouteOption.Saved -> savedPreviews[option.id]
        RouteOption.Custom -> customPreview
    }

    // A Google alternative has no via points of its own: the spot where it
    // differs most from the fastest route becomes its single via point, so
    // live rerouting keeps following it.
    fun alternativeViaPoints(index: Int): List<ViaPointDto> {
        val main = googleRoutes.getOrNull(0) ?: return emptyList()
        val alternative = googleRoutes.getOrNull(index) ?: return emptyList()
        val point = furthestPointFrom(decodePolyline(main.routePolyline), decodePolyline(alternative.routePolyline))
            ?: return emptyList()
        return listOf(ViaPointDto(name = alternative.description.ifBlank { null }, lat = point.latitude, lng = point.longitude))
    }

    fun viaPointsFor(option: RouteOption): List<ViaPointDto> = when (option) {
        RouteOption.Fastest -> emptyList()
        is RouteOption.Alternative -> alternativeViaPoints(option.index)
        is RouteOption.Saved -> savedRoutes.firstOrNull { it.id == option.id }?.viaPoints ?: emptyList()
        RouteOption.Custom -> customVia.toList()
    }

    fun alternativeLabel(index: Int): String {
        val description = googleRoutes.getOrNull(index)?.description.orEmpty()
        return if (description.isNotBlank()) "Por $description" else "Alternativa $index"
    }

    fun choiceFor(option: RouteOption): RouteChoice? = when (option) {
        RouteOption.Fastest -> null
        is RouteOption.Alternative -> RouteChoice(alternativeLabel(option.index), viaPointsFor(option), null)
        is RouteOption.Saved -> savedRoutes.firstOrNull { it.id == option.id }
            ?.let { RouteChoice(it.name, it.viaPoints, it.id) }
        RouteOption.Custom -> if (customVia.isEmpty()) {
            null
        } else {
            RouteChoice(customName ?: "Personalizada", customVia.toList(), null)
        }
    }

    fun addViaPoint(latLng: LatLng) {
        if (selected != RouteOption.Custom) {
            // Long-pressing while looking at another route starts a custom
            // route from that one's points, so it can be adjusted instead of
            // rebuilt from scratch.
            val base = viaPointsFor(selected)
            customVia.clear()
            customVia.addAll(base)
            customName = null
            selected = RouteOption.Custom
        }
        if (customVia.size >= MAX_VIA_POINTS) {
            errorMessage = "Podés agregar hasta $MAX_VIA_POINTS puntos de paso."
            return
        }
        val point = ViaPointDto(name = null, lat = latLng.latitude, lng = latLng.longitude)
        customVia.add(point)
        scope.launch {
            // Best-effort label for the list; the point works the same
            // without it.
            geocodingProvider.reverseGeocode(latLng.latitude, latLng.longitude).onSuccess { name ->
                val index = customVia.indexOf(point)
                if (index >= 0) customVia[index] = point.copy(name = name)
            }
        }
    }

    val selectedPreview = previewFor(selected)
    val selectedPoints = remember(selectedPreview) {
        selectedPreview?.let { decodePolyline(it.routePolyline) } ?: emptyList()
    }
    val googlePoints = remember(googleRoutes) { googleRoutes.map { decodePolyline(it.routePolyline) } }
    val selectedViaPoints = viaPointsFor(selected)

    LaunchedEffect(selectedPoints, mapLoaded) {
        if (!mapLoaded || selectedPoints.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().apply {
            selectedPoints.forEach { include(it) }
            include(originLatLng)
            include(destinationLatLng)
        }.build()
        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                    Text(
                        text = "Elegí la ruta",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (isPreviewing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
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
                        onMapLoaded = { mapLoaded = true },
                        onMapLongClick = { addViaPoint(it) },
                    ) {
                        // Google's other routes stay visible in grey, so it's
                        // clear what the selected one is being compared to.
                        googlePoints.forEach { points ->
                            if (points != selectedPoints) {
                                Polyline(points = points, color = OtherRouteColor, width = 8f)
                            }
                        }
                        if (selectedPoints.isNotEmpty()) {
                            Polyline(points = selectedPoints, color = MaterialTheme.colorScheme.primary, width = 12f)
                        }
                        Marker(
                            state = rememberMarkerState(position = originLatLng),
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        )
                        Marker(
                            state = rememberMarkerState(position = destinationLatLng),
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                        )
                        selectedViaPoints.forEach { via ->
                            key(via.lat, via.lng) {
                                Marker(
                                    state = rememberMarkerState(position = LatLng(via.lat, via.lng)),
                                    title = via.name,
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Mantené presionado el mapa para agregar un punto por donde pasar.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        RouteOptionRow(
                            label = "Más rápida",
                            preview = googleRoutes.getOrNull(0),
                            selected = selected == RouteOption.Fastest,
                            onClick = { selected = RouteOption.Fastest },
                        )
                        for (index in 1 until googleRoutes.size) {
                            RouteOptionRow(
                                label = alternativeLabel(index),
                                preview = googleRoutes[index],
                                selected = selected == RouteOption.Alternative(index),
                                onClick = { selected = RouteOption.Alternative(index) },
                            )
                        }
                        savedRoutes.forEach { route ->
                            RouteOptionRow(
                                label = route.name,
                                preview = savedPreviews[route.id],
                                selected = selected == RouteOption.Saved(route.id),
                                onClick = { selected = RouteOption.Saved(route.id) },
                            )
                        }
                        if (customVia.isNotEmpty()) {
                            RouteOptionRow(
                                label = customName ?: "Personalizada",
                                preview = customPreview,
                                selected = selected == RouteOption.Custom,
                                onClick = { selected = RouteOption.Custom },
                            )
                        }
                    }

                    if (selected == RouteOption.Custom && customVia.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Pasar por", style = MaterialTheme.typography.labelLarge)
                        customVia.forEachIndexed { index, via ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}. ${via.name ?: "Punto en el mapa"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { customVia.removeAt(index) }) {
                                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Quitar punto")
                                }
                            }
                        }
                    }

                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    val canSave = selected is RouteOption.Alternative ||
                        (selected == RouteOption.Custom && customVia.isNotEmpty())
                    if (canSave) {
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Guardar como ruta")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { onConfirm(choiceFor(selected)) },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Usar esta ruta")
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveRouteDialog(
            initialName = (selected as? RouteOption.Alternative)?.let { alternativeLabel(it.index) } ?: customName.orEmpty(),
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                val viaPoints = viaPointsFor(selected)
                val preview = previewFor(selected)
                scope.launch {
                    val request = SavedRouteCreateRequest(
                        name = name,
                        originName = originName,
                        originLat = origin.first,
                        originLng = origin.second,
                        destinationName = destinationName,
                        destinationLat = destination.first,
                        destinationLng = destination.second,
                        viaPoints = viaPoints,
                    )
                    routeRepository.createSavedRoute(request).fold(
                        onSuccess = { saved ->
                            savedRoutes = savedRoutes + saved
                            if (preview != null) savedPreviews[saved.id] = preview
                            selected = RouteOption.Saved(saved.id)
                            showSaveDialog = false
                        },
                        onFailure = {
                            errorMessage = "No se pudo guardar la ruta."
                            showSaveDialog = false
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun RouteOptionRow(
    label: String,
    preview: RoutePreviewResponse?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (preview != null) {
                Text(
                    text = formatRouteSummary(preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SaveRouteDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar ruta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "La vas a poder elegir en tus próximos viajes entre estos dos lugares.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre, ej. Por Rambla y Ruta 101") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

private fun formatRouteSummary(preview: RoutePreviewResponse): String {
    val minutes = (preview.durationSeconds + 30) / 60
    val duration = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    val km = preview.distanceMeters / 1000.0
    return "$duration · ${"%.1f".format(km)} km"
}

package com.techvibedev.triptrace.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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

// Lets the user see where a typed address (or a favorite, or the device's
// current location) actually resolved to, and drag the pin to correct it if
// it's off — the core gap this dialog exists to close: a geocoded point
// used to be applied "blind", with no way to see or fix a wrong result.
// Shared between Create trip (destination/stops/origin) and the Perfil
// "Agregar favorito" flow — same confirm-a-point need, different callers.
//
// wasGeocoded = false means the point wasn't resolved from typed text at
// all (a total geocoding failure, or there simply wasn't any text to
// geocode) — the dialog still opens, centered on a fallback point, so the
// user has a way to place the pin themselves instead of hitting a dead end.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationConfirmDialog(
    label: String,
    initialLat: Double,
    initialLng: Double,
    wasGeocoded: Boolean,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Dark map style, consistent with every other map in the app.
    // Remembered so it's parsed once, not on every recomposition while the
    // marker is being dragged.
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
                    "Mantener presionado el pin para arrastrarlo y ajustar la ubicación."
                } else {
                    "No pudimos encontrar esta dirección automáticamente. Mantener presionado " +
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

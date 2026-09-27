package com.techvibedev.triptrace.ui.screens.user

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse
import com.techvibedev.triptrace.data.model.UserResponse
import com.techvibedev.triptrace.data.repository.AuthRepository
import com.techvibedev.triptrace.data.repository.FavoritePlaceRepository
import com.techvibedev.triptrace.data.session.SettingsDataStore
import com.techvibedev.triptrace.location.GeocodingProvider
import com.techvibedev.triptrace.location.LocationProvider
import com.techvibedev.triptrace.ui.components.LocationConfirmDialog
import kotlinx.coroutines.launch
import retrofit2.HttpException

// Profile display + logout. The sensor-recording toggle and favorites
// management live here too, as separate sections.
@Composable
fun UserScreen(
    authRepository: AuthRepository,
    favoritePlaceRepository: FavoritePlaceRepository,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context.applicationContext) }

    var user by remember { mutableStateOf<UserResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf<List<FavoritePlaceResponse>>(emptyList()) }
    var isLoadingFavorites by remember { mutableStateOf(true) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reloadFavorites() {
        isLoadingFavorites = true
        favoritePlaceRepository.list().onSuccess { favorites = it }
        isLoadingFavorites = false
    }

    LaunchedEffect(Unit) {
        isLoading = true
        authRepository.getMe().fold(
            onSuccess = {
                user = it
                errorMessage = null
            },
            onFailure = { errorMessage = "No se pudo cargar tu perfil." },
        )
        isLoading = false
        reloadFavorites()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Perfil",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            user != null -> {
                ProfileCard(user = user!!)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FavoritesCard(
            favorites = favorites,
            isLoading = isLoadingFavorites,
            onAddClick = { showAddFavoriteDialog = true },
            onDeleteClick = { favorite ->
                scope.launch {
                    favoritePlaceRepository.delete(favorite.id)
                    reloadFavorites()
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        SensorRecordingCard(settingsDataStore = settingsDataStore)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showChangePasswordDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cambiar contraseña")
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                isLoggingOut = true
                scope.launch {
                    authRepository.logout()
                    isLoggingOut = false
                    onLoggedOut()
                }
            },
            enabled = !isLoggingOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cerrar sesión")
        }
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            authRepository = authRepository,
            onDismiss = { showChangePasswordDialog = false },
            // Changing the password invalidates every existing token for
            // this account, including the one this very session is using
            // (trip-trace-api's change-password endpoint has no per-device
            // concept to spare it) — so the local token is now stale too.
            // Clearing it and navigating to Login here, right after a
            // successful change, avoids the alternative: staying on a
            // now-broken session where every screen fails with a generic
            // error until the user finds "Cerrar sesion" themselves.
            onChanged = {
                showChangePasswordDialog = false
                authRepository.logout()
                onLoggedOut()
            },
        )
    }

    if (showAddFavoriteDialog) {
        AddFavoriteDialog(
            favoritePlaceRepository = favoritePlaceRepository,
            onDismiss = { showAddFavoriteDialog = false },
            onSaved = {
                showAddFavoriteDialog = false
                scope.launch { reloadFavorites() }
            },
        )
    }
}

@Composable
private fun ProfileCard(user: UserResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileRow(label = "Usuario", value = user.username)
            ProfileRow(label = "Email", value = user.email)
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// User-curated places (android#81) — one list, usable for a trip's origin,
// destination, or any stop (see CreateTripScreen). Management (add/delete)
// lives here in Perfil; picking one happens on Create trip.
@Composable
private fun FavoritesCard(
    favorites: List<FavoritePlaceResponse>,
    isLoading: Boolean,
    onAddClick: () -> Unit,
    onDeleteClick: (FavoritePlaceResponse) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Favoritos",
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(onClick = onAddClick) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Agregar favorito")
                }
            }
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                }
                favorites.isEmpty() -> {
                    Text(
                        text = "Todavia no tenes favoritos guardados.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    favorites.forEach { favorite ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = favorite.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDeleteClick(favorite) }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Borrar favorito \"${favorite.name}\"",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Two ways to set the favorite's point, same as Create trip: type an
// address (geocode → confirm/adjust the pin), or use the current GPS
// location — both funnel into the same LocationConfirmDialog, so the user
// always gets a chance to adjust before saving. "Casa"/"Trabajo" are
// pre-defined name suggestions, not fixed slots — tapping one just fills
// the name field, same as typing it by hand.
//
// Mutually exclusive with the location-confirm step, rather than stacked:
// showing both AlertDialog and LocationConfirmDialog at once would be two
// separate dialog surfaces overlapping. Canceling the confirm step returns
// to this form with name/address preserved.
@Composable
private fun AddFavoriteDialog(
    favoritePlaceRepository: FavoritePlaceRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val geocodingProvider = remember { GeocodingProvider(context.applicationContext) }
    val locationProvider = remember { LocationProvider(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var addressText by remember { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    fun openConfirmFromAddress() {
        if (name.isBlank()) {
            errorMessage = "Ingresa un nombre primero"
            return
        }
        if (addressText.isBlank()) {
            errorMessage = "Ingresa una direccion"
            return
        }
        scope.launch {
            isResolving = true
            val coords = geocodingProvider.geocode(addressText).getOrNull()
            isResolving = false
            if (coords == null) {
                errorMessage = "No se encontro esa direccion"
            } else {
                errorMessage = null
                confirmCoords = coords
            }
        }
    }

    fun openConfirmFromCurrentLocation() {
        if (name.isBlank()) {
            errorMessage = "Ingresa un nombre primero"
            return
        }
        scope.launch {
            isResolving = true
            val result = locationProvider.getCurrentLocation()
            isResolving = false
            result.fold(
                onSuccess = { (lat, lng) ->
                    errorMessage = null
                    confirmCoords = lat to lng
                },
                onFailure = { errorMessage = "No se pudo obtener tu ubicacion" },
            )
        }
    }

    if (confirmCoords == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Agregar favorito") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        enabled = !isResolving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { name = "Casa" }, enabled = !isResolving) {
                            Text("Casa")
                        }
                        TextButton(onClick = { name = "Trabajo" }, enabled = !isResolving) {
                            Text("Trabajo")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = { addressText = it },
                        label = { Text("Direccion") },
                        singleLine = true,
                        enabled = !isResolving,
                        trailingIcon = {
                            IconButton(
                                onClick = { openConfirmFromAddress() },
                                enabled = !isResolving && addressText.isNotBlank(),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditLocation,
                                    contentDescription = "Elegir en el mapa",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { openConfirmFromCurrentLocation() }, enabled = !isResolving) {
                        Text("Usar mi ubicacion actual")
                    }
                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (isResolving) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !isResolving) {
                    Text("Cancelar")
                }
            },
        )
    } else {
        val (lat, lng) = confirmCoords!!
        LocationConfirmDialog(
            label = name,
            initialLat = lat,
            initialLng = lng,
            wasGeocoded = true,
            onConfirm = { confirmedLat, confirmedLng ->
                scope.launch {
                    isSaving = true
                    val result = favoritePlaceRepository.create(name, confirmedLat, confirmedLng)
                    isSaving = false
                    result.fold(
                        onSuccess = { onSaved() },
                        onFailure = {
                            errorMessage = "No se pudo guardar el favorito"
                            confirmCoords = null
                        },
                    )
                }
            },
            onDismiss = { confirmCoords = null },
        )
    }
}

// Lets the user turn off raw accelerometer/gyroscope recording, which
// TripTrackingService otherwise always did. Checked once when a trip's
// tracking starts (see that service), so toggling here only takes effect on
// the NEXT trip, not one already running.
@Composable
private fun SensorRecordingCard(settingsDataStore: SettingsDataStore) {
    val scope = rememberCoroutineScope()
    val sensorRecordingEnabled by settingsDataStore.sensorRecordingEnabledFlow.collectAsState(initial = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Grabar datos de sensores",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Acelerómetro y giroscopio durante el viaje, para evaluar mejoras " +
                        "futuras. No se envían al servidor.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = sensorRecordingEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setSensorRecordingEnabled(enabled) }
                },
            )
        }
    }
}

// current_password is required by the API itself (see that endpoint's own
// reasoning) — this dialog just collects it alongside the new password,
// with the new/confirm match checked locally before ever calling the
// network. onChanged is suspend so it can log the (now-stale) local session
// out as part of the same coroutine, right after the API call succeeds.
@Composable
private fun ChangePasswordDialog(
    authRepository: AuthRepository,
    onDismiss: () -> Unit,
    onChanged: suspend () -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (newPassword != confirmPassword) {
            errorMessage = "Las contraseñas nuevas no coinciden"
            return
        }
        if (currentPassword.isBlank() || newPassword.isBlank()) {
            errorMessage = "Completa todos los campos"
            return
        }
        errorMessage = null
        isSubmitting = true
        scope.launch {
            val result = authRepository.changePassword(currentPassword, newPassword)
            isSubmitting = false
            result.fold(
                onSuccess = { onChanged() },
                onFailure = { exception ->
                    // 400 from the API specifically means current_password
                    // didn't match — anything else (network, 5xx) gets a
                    // generic message instead of implying the password
                    // itself was wrong.
                    errorMessage = if (exception is HttpException && exception.code() == 400) {
                        "La contraseña actual es incorrecta"
                    } else {
                        "No se pudo cambiar la contraseña, intenta de nuevo"
                    }
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Contraseña actual") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Contraseña nueva") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar contraseña nueva") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancelar")
            }
        },
    )
}

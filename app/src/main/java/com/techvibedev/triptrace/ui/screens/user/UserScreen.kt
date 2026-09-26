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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.techvibedev.triptrace.data.model.UserResponse
import com.techvibedev.triptrace.data.repository.AuthRepository
import com.techvibedev.triptrace.data.session.SettingsDataStore
import kotlinx.coroutines.launch
import retrofit2.HttpException

// Three parts of android#87: profile display + logout (this file), password
// change (ChangePasswordDialog below), and the sensor-recording toggle
// (SensorRecordingToggle below).
@Composable
fun UserScreen(
    authRepository: AuthRepository,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context.applicationContext) }

    var user by remember { mutableStateOf<UserResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var passwordChangedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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

        SensorRecordingCard(settingsDataStore = settingsDataStore)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                passwordChangedMessage = null
                showChangePasswordDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cambiar contrasena")
        }

        passwordChangedMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
            Text("Cerrar sesion")
        }
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            authRepository = authRepository,
            onDismiss = { showChangePasswordDialog = false },
            onChanged = {
                showChangePasswordDialog = false
                passwordChangedMessage = "Contrasena actualizada."
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

// android#87 part 3 — lets the user turn off raw accelerometer/gyroscope
// recording (android#77), which TripTrackingService otherwise always did.
// Checked once when a trip's tracking starts (see that service), so
// toggling here only takes effect on the NEXT trip, not one already running.
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
                    text = "Acelerometro y giroscopio durante el viaje, para evaluar mejoras " +
                        "futuras. No se envian al servidor.",
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

// trip-trace-api#60 / android#87 part 2. current_password is required by
// the API itself (see that PR's reasoning) — this dialog just collects it
// alongside the new password, with the new/confirm match checked locally
// before ever calling the network.
@Composable
private fun ChangePasswordDialog(
    authRepository: AuthRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (newPassword != confirmPassword) {
            errorMessage = "Las contrasenas nuevas no coinciden"
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
                    // didn't match (see trip-trace-api#60) — anything else
                    // (network, 5xx) gets a generic message instead of
                    // implying the password itself was wrong.
                    errorMessage = if (exception is HttpException && exception.code() == 400) {
                        "La contrasena actual es incorrecta"
                    } else {
                        "No se pudo cambiar la contrasena, intenta de nuevo"
                    }
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contrasena") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Contrasena actual") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Contrasena nueva") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar contrasena nueva") },
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

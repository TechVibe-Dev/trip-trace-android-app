package com.techvibedev.triptrace.ui.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.model.UserResponse
import com.techvibedev.triptrace.data.repository.AuthRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

// Profile display + logout. The sensor-recording toggle lands here too, as
// a separate section, once its own groundwork (a local DataStore setting)
// is in place.
@Composable
fun UserScreen(
    authRepository: AuthRepository,
    onLoggedOut: () -> Unit,
) {
    var user by remember { mutableStateOf<UserResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
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
            // (the API's change-password endpoint has no per-device concept
            // to spare it) — so the local token is now stale too. Clearing
            // it and navigating to Login here, right after a successful
            // change, avoids the alternative: staying on a now-broken
            // session where every screen fails with a generic error until
            // the user finds "Cerrar sesion" themselves.
            onChanged = {
                showChangePasswordDialog = false
                authRepository.logout()
                onLoggedOut()
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

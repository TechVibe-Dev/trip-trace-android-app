package com.techvibedev.triptrace.ui.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.model.UserResponse
import com.techvibedev.triptrace.data.repository.AuthRepository
import kotlinx.coroutines.launch

// First piece of android#87 — profile display + logout. Password change and
// the sensor-recording toggle land here too, as separate sections, once
// their own groundwork (an API password-change endpoint, a local DataStore
// setting) is in place.
@Composable
fun UserScreen(
    authRepository: AuthRepository,
    onLoggedOut: () -> Unit,
) {
    var user by remember { mutableStateOf<UserResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }
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
            text = "Usuario",
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

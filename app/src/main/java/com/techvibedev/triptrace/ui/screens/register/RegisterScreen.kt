package com.techvibedev.triptrace.ui.screens.register

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.repository.AuthRepository
import com.techvibedev.triptrace.ui.screens.login.registerErrorMessage
import kotlinx.coroutines.launch

// bcrypt only hashes the first 72 bytes of a password, and the API rejects
// anything longer with a 422 (see PasswordChange/UserCreate in trip-trace-api)
// — checked here too so the user hears about it before a round trip.
private const val MAX_PASSWORD_BYTES = 72

// Returns the first problem found with what was typed, or null if it's fine
// to send. Only what the API can't tell us cheaply (or would answer with an
// opaque 422) is checked here — the server stays the source of truth for the
// rest.
private fun validateRegistration(
    email: String,
    username: String,
    password: String,
    confirmPassword: String,
): String? {
    if (email.isBlank() || username.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
        return "Completá todos los campos."
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
        return "El email no parece válido."
    }
    if (password != confirmPassword) {
        return "Las contraseñas no coinciden."
    }
    if (password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES) {
        return "La contraseña es demasiado larga (máximo $MAX_PASSWORD_BYTES bytes)."
    }
    return null
}

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val validationError = validateRegistration(email, username, password, confirmPassword)
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        errorMessage = null
        isLoading = true
        scope.launch {
            val registerError = authRepository.register(email.trim(), username.trim(), password).exceptionOrNull()
            if (registerError != null) {
                isLoading = false
                errorMessage = registerErrorMessage(registerError)
                return@launch
            }

            // The account exists now — log in straight away with the same
            // credentials, so registering doesn't end at a login screen asking
            // for what was just typed. The username works as the identifier
            // (the API accepts email or username).
            val loginResult = authRepository.login(username.trim(), password)
            isLoading = false
            if (loginResult.isSuccess) {
                onRegistered()
            } else {
                errorMessage = "Tu cuenta se creó, pero no pudimos iniciar sesión automáticamente. " +
                    "Volvé al inicio e iniciá sesión con tus datos."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Crear cuenta",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario") },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirmar contraseña") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { submit() },
            enabled = !isLoading &&
                email.isNotBlank() &&
                username.isNotBlank() &&
                password.isNotBlank() &&
                confirmPassword.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Crear cuenta")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Text(
                text = "¿Ya tenés cuenta? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Iniciá sesión",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(enabled = !isLoading, onClick = onBackToLogin),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

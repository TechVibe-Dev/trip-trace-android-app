package com.techvibedev.triptrace.ui.screens.login

import com.techvibedev.triptrace.data.network.ApiFailure
import com.techvibedev.triptrace.data.network.toApiFailure

// "45 segundos", "1 minuto", "2 minutos", "1 hora" — rounds up, so the
// message never promises a wait shorter than the real one.
internal fun formatWait(seconds: Int): String {
    if (seconds < 60) {
        return "$seconds ${if (seconds == 1) "segundo" else "segundos"}"
    }
    val minutes = (seconds + 59) / 60
    if (minutes < 60) {
        return "$minutes ${if (minutes == 1) "minuto" else "minutos"}"
    }
    val hours = (minutes + 59) / 60
    return "$hours ${if (hours == 1) "hora" else "horas"}"
}

// Shared by every auth screen that can hit the API's rate limiter (login
// now, registration too).
internal fun rateLimitMessage(retryAfterSeconds: Int?): String {
    return if (retryAfterSeconds != null && retryAfterSeconds > 0) {
        "Demasiados intentos. Probá de nuevo en ${formatWait(retryAfterSeconds)}."
    } else {
        "Demasiados intentos. Probá de nuevo en unos minutos."
    }
}

internal fun loginErrorMessage(error: Throwable): String {
    return when (val failure = error.toApiFailure()) {
        is ApiFailure.RateLimited -> rateLimitMessage(failure.retryAfterSeconds)
        // The API answers 400 ("Invalid credentials") for a wrong
        // email/username or password — same message for both on purpose, so
        // it doesn't reveal which accounts exist.
        is ApiFailure.Http -> if (failure.code == 400) {
            "No se pudo iniciar sesión. Revisá tu email/usuario y contraseña."
        } else {
            "Algo salió mal en el servidor. Probá de nuevo en un rato."
        }
        ApiFailure.NoConnection -> "No se pudo conectar. Revisá tu conexión y probá de nuevo."
        ApiFailure.Unknown -> "No se pudo iniciar sesión. Probá de nuevo."
    }
}

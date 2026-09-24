package com.techvibedev.triptrace.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// TripTrace always renders in dark mode — it's the app's fixed identity
// (night navigation aesthetic), not something that should follow the
// system theme or the device's dynamic (Material You) palette.
private val TripTraceDarkColorScheme = darkColorScheme(
    primary = TripTraceBlue,
    onPrimary = TripTraceTextPrimary,
    primaryContainer = TripTraceBlueContainer,
    onPrimaryContainer = TripTraceTextPrimary,

    secondary = TripTraceAmber,
    onSecondary = TripTraceBackground,
    secondaryContainer = TripTraceAmberContainer,
    onSecondaryContainer = TripTraceTextPrimary,

    tertiary = TripTraceTeal,
    onTertiary = TripTraceTextPrimary,
    tertiaryContainer = TripTraceTealContainer,
    onTertiaryContainer = TripTraceTextPrimary,

    background = TripTraceBackground,
    onBackground = TripTraceTextPrimary,

    surface = TripTraceSurface,
    onSurface = TripTraceTextPrimary,
    surfaceVariant = TripTraceSurfaceVariant,
    onSurfaceVariant = TripTraceTextSecondary,

    outline = TripTraceOutline,

    error = TripTraceError,
    onError = TripTraceTextPrimary,
    errorContainer = TripTraceErrorContainer,
    onErrorContainer = TripTraceTextPrimary,
)

@Composable
fun TripTraceTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TripTraceDarkColorScheme,
        typography = Typography,
        content = content,
    )
}

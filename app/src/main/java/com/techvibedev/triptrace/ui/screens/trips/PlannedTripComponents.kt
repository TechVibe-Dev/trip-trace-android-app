package com.techvibedev.triptrace.ui.screens.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.model.TripResponse
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Date, times and estimated duration of a planned trip, plus the warning
// when its departure time already passed. Shared by the list and the
// detail screen.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlannedTripInfo(trip: TripResponse) {
    val isStale = isDepartureStale(trip.plannedDepartureAt)
    val dateLabel = formatDepartureDate(trip.plannedDepartureAt)
    val durationLabel = formatEstimatedDuration(trip.plannedDepartureAt, trip.calculatedArrivalAt)

    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (dateLabel != null) {
                TripDetail(icon = Icons.Filled.CalendarToday, text = dateLabel)
            }
            if (!isStale) {
                val arrival = trip.calculatedArrivalAt?.let { " \u2192 ${formatLocalTime(it)}" } ?: ""
                TripDetail(
                    icon = Icons.Filled.Schedule,
                    text = "${formatLocalTime(trip.plannedDepartureAt)}$arrival",
                )
            }
            if (durationLabel != null) {
                TripDetail(icon = null, text = "\u2248 $durationLabel")
            }
        }

        if (isStale) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "Hora planeada (${formatLocalTime(trip.plannedDepartureAt)}) ya pasó",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
internal fun PlannedTripActions(
    trip: TripResponse,
    onStartTrip: (String) -> Unit,
    onUseCurrentTime: (String) -> Unit,
) {
    if (isDepartureStale(trip.plannedDepartureAt)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onUseCurrentTime(trip.id) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Usar hora actual")
            }
            Button(
                onClick = { onStartTrip(trip.id) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Iniciar")
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { onStartTrip(trip.id) }) {
                Text("Iniciar")
            }
        }
    }
}

@Composable
private fun TripDetail(icon: ImageVector?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Small buffer so a departure time set to "right now" (via "Usar hora
// actual") doesn't immediately count as stale again on the very next
// recomposition/reload, a few hundred milliseconds later, before the user
// even gets a chance to tap "Iniciar".
private val STALE_GRACE_PERIOD: Duration = Duration.ofSeconds(60)

internal fun isDepartureStale(plannedDepartureAt: String?): Boolean {
    if (plannedDepartureAt == null) return false
    return try {
        OffsetDateTime.parse(plannedDepartureAt).isBefore(OffsetDateTime.now().minus(STALE_GRACE_PERIOD))
    } catch (e: Exception) {
        false
    }
}

// The API returns timestamps in UTC — formatting an OffsetDateTime directly
// prints ITS OWN offset's hour, not the phone's local one, so without
// converting first this showed UTC time as if it were local (e.g. showing
// "20:34" while the phone's actual local time was 17:34, in UTC-3).
internal fun formatLocalTime(isoDateTime: String?): String {
    if (isoDateTime == null) return "--:--"
    return try {
        OffsetDateTime.parse(isoDateTime)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "--:--"
    }
}

private val SPANISH = Locale.forLanguageTag("es")

// "Hoy, vie 6 nov", "Mañana, sáb 7 nov" or "lun 9 nov", in the phone's
// time zone.
internal fun formatDepartureDate(isoDateTime: String?): String? {
    if (isoDateTime == null) return null
    return try {
        val date = OffsetDateTime.parse(isoDateTime).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        // Some Android versions abbreviate with a trailing dot ("vie.").
        val formatted = date.format(DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)).replace(".", "")
        when (date) {
            today -> "Hoy, $formatted"
            today.plusDays(1) -> "Mañana, $formatted"
            else -> formatted
        }
    } catch (e: Exception) {
        null
    }
}

// Duration of Google's estimate at creation time, "53 min" or "1 h 35 min".
internal fun formatEstimatedDuration(departureAt: String?, arrivalAt: String?): String? {
    if (departureAt == null || arrivalAt == null) return null
    return try {
        val minutes = Duration.between(OffsetDateTime.parse(departureAt), OffsetDateTime.parse(arrivalAt)).toMinutes()
        when {
            minutes <= 0 -> null
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    } catch (e: Exception) {
        null
    }
}

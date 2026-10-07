package com.techvibedev.triptrace.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Distance
import androidx.core.content.ContextCompat
import com.techvibedev.triptrace.data.model.TripResponse
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Used when the host is too old to report its own list limit (car API 1).
// Six is the smallest limit hosts are known to enforce.
private const val DEFAULT_LIST_LIMIT = 6

internal fun listItemLimit(carContext: CarContext): Int {
    if (carContext.carAppApiLevel < 2) return DEFAULT_LIST_LIMIT
    return carContext.getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
}

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

internal fun TripResponse.routeLabel(): String = "$originName → $destinationName"

// The API returns timestamps in UTC; shown in the phone's local time, same
// as the phone screens do.
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

// Metric only, rounded the way a driver reads it: tens of meters up close,
// one decimal in km under 10 km, whole km beyond that.
internal fun carDistance(meters: Double): Distance {
    return when {
        meters >= 10_000 -> Distance.create((meters / 1000).roundToInt().toDouble(), Distance.UNIT_KILOMETERS)
        meters >= 1_000 -> Distance.create(((meters / 100).roundToInt() / 10.0), Distance.UNIT_KILOMETERS_P1)
        meters >= 100 -> Distance.create(((meters / 10).roundToInt() * 10).toDouble(), Distance.UNIT_METERS)
        else -> Distance.create(meters.roundToInt().coerceAtLeast(0).toDouble(), Distance.UNIT_METERS)
    }
}

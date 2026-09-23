package com.techvibedev.triptrace.location

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Resolves a typed address ("Solis", "Av. 18 de Julio 1234") to lat/lng
// using Android's built-in Geocoder — free, no API key, no quota to
// track, unlike Google's Routes API (see trip-trace-api's routing_service.py).
// Good enough for this app's needs: a rough point for the destination, not
// autocomplete-grade precision.
class GeocodingProvider(private val context: Context) {

    suspend fun geocode(address: String): Result<Pair<Double, Double>> =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) {
                return@withContext Result.failure(
                    IllegalStateException("Geocoding no disponible en este dispositivo"),
                )
            }
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                // The synchronous overload is deprecated in favor of a
                // callback-based one added in API 33 — but it's still fully
                // supported (not removed), and since we already dispatch
                // this to Dispatchers.IO ourselves, the "don't block the
                // main thread" concern behind that deprecation doesn't
                // apply here. Using it avoids branching on API level just
                // to support minSdk 26-32.
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(address, 1)
                val match = results?.firstOrNull()
                if (match == null) {
                    Result.failure(NoSuchElementException("No se encontro esa direccion"))
                } else {
                    Result.success(match.latitude to match.longitude)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

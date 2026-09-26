package com.techvibedev.triptrace.location

import android.content.Context
import android.location.Address
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

    // The other direction: lat/lng -> a readable address, used to name the
    // origin when it comes from GPS instead of typed text (android#69).
    // Prefers "calle y numero" (getThoroughfare + getSubThoroughfare) since
    // that's a more natural short label than Android's full formatted
    // address line, falling back to that full line when the shorter pieces
    // aren't available (not every geocoder response has a street number,
    // e.g. rural areas or some non-US-style address formats).
    suspend fun reverseGeocode(lat: Double, lng: Double): Result<String> =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) {
                return@withContext Result.failure(
                    IllegalStateException("Geocoding no disponible en este dispositivo"),
                )
            }
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(lat, lng, 1)
                val match = results?.firstOrNull()
                if (match == null) {
                    Result.failure(NoSuchElementException("No se pudo resolver la direccion"))
                } else {
                    Result.success(formatAddress(match))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun formatAddress(address: Address): String {
        val street = address.thoroughfare
        val number = address.subThoroughfare
        return when {
            street != null && number != null -> "$street $number"
            street != null -> street
            else -> address.getAddressLine(0) ?: "Ubicacion actual"
        }
    }
}

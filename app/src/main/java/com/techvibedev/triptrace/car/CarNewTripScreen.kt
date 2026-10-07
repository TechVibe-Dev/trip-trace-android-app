package com.techvibedev.triptrace.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.techvibedev.triptrace.data.model.TripCreateRequest
import java.time.OffsetDateTime
import kotlinx.coroutines.launch

// Last step of a new trip from the car, once the destination is picked.
// Simpler than the phone's Create trip on purpose: the origin is always the
// current location, departure is now, and there are no stops or desired
// arrival time (all of that needs typing, which isn't safe while driving).
internal class CarNewTripScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val destinationName: String,
    private val destinationLat: Double,
    private val destinationLng: Double,
) : Screen(carContext) {

    private var isSaving = false
    private var errorMessage: String? = null

    private fun save(startNow: Boolean) {
        if (!hasLocationPermission(carContext)) {
            // Shown on the phone's screen: the car display can't host
            // Android's permission dialog itself.
            carContext.requestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION)) { granted, _ ->
                if (Manifest.permission.ACCESS_FINE_LOCATION in granted) {
                    save(startNow)
                } else {
                    errorMessage = "Se necesita permiso de ubicación para crear el viaje."
                    invalidate()
                }
            }
            return
        }

        isSaving = true
        errorMessage = null
        invalidate()
        lifecycleScope.launch {
            val origin = deps.locationProvider.getCurrentLocation().getOrNull()
            if (origin == null) {
                fail("No se pudo obtener tu ubicación.")
                return@launch
            }
            val (originLat, originLng) = origin
            val originName = deps.geocodingProvider
                .reverseGeocode(originLat, originLng)
                .getOrDefault("Ubicación actual")
            val request = TripCreateRequest(
                originName = originName,
                originLat = originLat,
                originLng = originLng,
                destinationName = destinationName,
                destinationLat = destinationLat,
                destinationLng = destinationLng,
                plannedDepartureAt = OffsetDateTime.now().toString(),
            )
            val trip = deps.tripRepository.createTrip(request).getOrNull()
            if (trip == null) {
                fail("No se pudo guardar el viaje.")
                return@launch
            }
            // Best-effort, same as on the phone: the trip is valid without
            // a precalculated route.
            deps.tripRepository.calculateRoute(trip.id)

            if (!startNow) {
                CarToast.makeText(carContext, "Viaje guardado", CarToast.LENGTH_SHORT).show()
                screenManager.popToRoot()
                return@launch
            }
            deps.tripRepository.startTrip(trip.id).fold(
                onSuccess = {
                    screenManager.popToRoot()
                    screenManager.push(CarActiveTripScreen(carContext, deps, trip.id))
                },
                onFailure = { fail("El viaje se guardó pero no se pudo iniciar.") },
            )
        }
    }

    private fun fail(message: String) {
        isSaving = false
        errorMessage = message
        invalidate()
    }

    override fun onGetTemplate(): Template {
        if (isSaving) {
            val builder = MessageTemplate.Builder("Creando el viaje...")
                .setTitle("Nuevo viaje")
                .setHeaderAction(Action.BACK)
            if (carContext.carAppApiLevel >= 2) builder.setLoading(true)
            return builder.build()
        }

        val message = buildString {
            append("Ir a $destinationName desde tu ubicación actual.")
            errorMessage?.let { append("\n\n$it") }
        }
        return MessageTemplate.Builder(message)
            .setTitle("Nuevo viaje")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Iniciar viaje")
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener { save(startNow = true) }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle("Guardar")
                    .setOnClickListener { save(startNow = false) }
                    .build(),
            )
            .build()
    }
}

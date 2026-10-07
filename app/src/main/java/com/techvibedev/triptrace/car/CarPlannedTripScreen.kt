package com.techvibedev.triptrace.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.techvibedev.triptrace.data.model.TripResponse
import kotlinx.coroutines.launch

// A planned trip's summary with the button to start it. Starting here is the
// same as "Iniciar" on the phone's Viajes tab: the trip becomes
// IN_PROGRESS from now, whatever its planned departure was.
internal class CarPlannedTripScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val trip: TripResponse,
) : Screen(carContext) {

    private var isStarting = false
    private var errorMessage: String? = null

    private fun startTrip() {
        isStarting = true
        errorMessage = null
        invalidate()
        lifecycleScope.launch {
            deps.tripRepository.startTrip(trip.id).fold(
                onSuccess = {
                    screenManager.popToRoot()
                    screenManager.push(CarActiveTripScreen(carContext, deps, trip.id))
                },
                onFailure = {
                    isStarting = false
                    errorMessage = "No se pudo iniciar el viaje."
                    invalidate()
                },
            )
        }
    }

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
        if (isStarting) {
            pane.setLoading(true)
        } else {
            pane.addRow(Row.Builder().setTitle("Destino").addText(trip.destinationName).build())
            pane.addRow(Row.Builder().setTitle("Desde").addText(trip.originName).build())
            pane.addRow(
                Row.Builder()
                    .setTitle("Salida planeada")
                    .addText(formatLocalTime(trip.plannedDepartureAt))
                    .build(),
            )
            errorMessage?.let { message ->
                pane.addRow(Row.Builder().setTitle(message).build())
            }
            pane.addAction(
                Action.Builder()
                    .setTitle("Iniciar viaje")
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener { startTrip() }
                    .build(),
            )
        }
        return PaneTemplate.Builder(pane.build())
            .setTitle(trip.routeLabel())
            .setHeaderAction(Action.BACK)
            .build()
    }
}

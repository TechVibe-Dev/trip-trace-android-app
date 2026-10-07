package com.techvibedev.triptrace.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.techvibedev.triptrace.trip.finishTrip
import kotlinx.coroutines.launch

// Confirmation before ending the trip, either from the "Finalizar" button
// or prompted automatically on getting close to the destination (arrived).
// Same wording and behavior as the phone's two dialogs.
internal class CarEndTripScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val tripId: String,
    private val arrived: Boolean,
) : Screen(carContext) {

    private var isEnding = false
    private var errorMessage: String? = null

    private fun endTrip() {
        isEnding = true
        errorMessage = null
        invalidate()
        lifecycleScope.launch {
            finishTrip(carContext, tripId, deps.tripRepository).fold(
                onSuccess = {
                    CarToast.makeText(carContext, "Viaje finalizado", CarToast.LENGTH_SHORT).show()
                    screenManager.popToRoot()
                },
                onFailure = {
                    isEnding = false
                    errorMessage = "No se pudo finalizar el viaje."
                    invalidate()
                },
            )
        }
    }

    override fun onGetTemplate(): Template {
        val title = if (arrived) "Llegaste a destino" else "Finalizar viaje"
        if (isEnding) {
            val builder = MessageTemplate.Builder("Finalizando el viaje...").setTitle(title)
            if (carContext.carAppApiLevel >= 2) builder.setLoading(true)
            return builder.build()
        }

        val question = if (arrived) "¿Querés finalizar el viaje?" else "¿Seguro que querés finalizar el viaje?"
        val message = errorMessage?.let { "$it\n\n$question" } ?: question
        return MessageTemplate.Builder(message)
            .setTitle(title)
            .addAction(
                Action.Builder()
                    .setTitle("Finalizar")
                    .setOnClickListener { endTrip() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (arrived) "Seguir viaje" else "Cancelar")
                    .setOnClickListener { finish() }
                    .build(),
            )
            .build()
    }
}

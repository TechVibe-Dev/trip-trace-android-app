package com.techvibedev.triptrace.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.techvibedev.triptrace.data.model.TripResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// First screen on the car display: the trip in progress (if any), a way to
// start a new trip, and the planned ones ready to start. Reloads every time
// it comes back into view, so a trip started or finished from another
// screen (or from the phone) is reflected right away.
internal class CarHomeScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data object LoggedOut : State
        data object Error : State
        data class Loaded(val inProgress: List<TripResponse>, val planned: List<TripResponse>) : State
    }

    private var state: State = State.Loading

    // Opening Android Auto mid-trip should land straight on the navigation
    // view, but only on the first load: coming back here on purpose (back
    // button) must not bounce the driver into it again.
    private var hasAutoOpenedActiveTrip = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                load()
            }
        })
    }

    private fun load() {
        lifecycleScope.launch {
            if (deps.tokenDataStore.tokenFlow.first() == null) {
                state = State.LoggedOut
                invalidate()
                return@launch
            }
            val inProgressResult = deps.tripRepository.getInProgressTrips()
            val plannedResult = deps.tripRepository.getPlannedTrips()
            state = if (inProgressResult.isFailure && plannedResult.isFailure) {
                State.Error
            } else {
                State.Loaded(
                    inProgress = inProgressResult.getOrDefault(emptyList()),
                    planned = plannedResult.getOrDefault(emptyList()),
                )
            }
            invalidate()

            val loaded = state as? State.Loaded ?: return@launch
            if (!hasAutoOpenedActiveTrip && loaded.inProgress.size == 1) {
                hasAutoOpenedActiveTrip = true
                openActiveTrip(loaded.inProgress.first().id)
            }
            hasAutoOpenedActiveTrip = true
        }
    }

    private fun openActiveTrip(tripId: String) {
        screenManager.push(CarActiveTripScreen(carContext, deps, tripId))
    }

    override fun onGetTemplate(): Template {
        return when (val current = state) {
            State.Loading -> ListTemplate.Builder()
                .setTitle(TITLE)
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
            State.LoggedOut -> messageWithRetry(
                "Iniciá sesión en Trip Trace desde el teléfono para usarla en el auto.",
            )
            State.Error -> messageWithRetry("No se pudieron cargar los viajes.")
            is State.Loaded -> buildList(current)
        }
    }

    private fun messageWithRetry(message: String): Template {
        return MessageTemplate.Builder(message)
            .setTitle(TITLE)
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle("Reintentar")
                    .setOnClickListener {
                        state = State.Loading
                        invalidate()
                        load()
                    }
                    .build(),
            )
            .build()
    }

    private fun buildList(loaded: State.Loaded): Template {
        val builder = ListTemplate.Builder()
            .setTitle(TITLE)
            .setHeaderAction(Action.APP_ICON)
        // The host enforces a cap on rows across all sections; the "new
        // trip" row always fits, the in-progress trip comes next, and
        // planned trips fill whatever room is left.
        var remaining = listItemLimit(carContext) - 1

        val inProgress = loaded.inProgress.take(remaining.coerceAtLeast(0))
        remaining -= inProgress.size
        if (inProgress.isNotEmpty()) {
            val items = ItemList.Builder()
            inProgress.forEach { trip ->
                items.addItem(
                    Row.Builder()
                        .setTitle(trip.routeLabel())
                        .addText("Iniciado ${formatLocalTime(trip.startedAt)}")
                        .setBrowsable(true)
                        .setOnClickListener { openActiveTrip(trip.id) }
                        .build(),
                )
            }
            builder.addSectionedList(SectionedItemList.create(items.build(), "En curso"))
        }

        val newTripItems = ItemList.Builder().addItem(
            Row.Builder()
                .setTitle("Nuevo viaje")
                .addText("Desde tu ubicación actual")
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(CarDestinationScreen(carContext, deps)) }
                .build(),
        )
        builder.addSectionedList(SectionedItemList.create(newTripItems.build(), "Nuevo"))

        val planned = loaded.planned.take(remaining.coerceAtLeast(0))
        if (planned.isNotEmpty()) {
            val items = ItemList.Builder()
            planned.forEach { trip ->
                items.addItem(
                    Row.Builder()
                        .setTitle(trip.routeLabel())
                        .addText("Sale ${formatLocalTime(trip.plannedDepartureAt)}")
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(CarPlannedTripScreen(carContext, deps, trip))
                        }
                        .build(),
                )
            }
            builder.addSectionedList(SectionedItemList.create(items.build(), "Planeados"))
        }

        return builder.build()
    }

    private companion object {
        const val TITLE = "Trip Trace"
    }
}

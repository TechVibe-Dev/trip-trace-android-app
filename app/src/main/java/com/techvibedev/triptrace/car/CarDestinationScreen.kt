package com.techvibedev.triptrace.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse
import kotlinx.coroutines.launch

// First step of a new trip from the car: pick the destination. Favorites
// come first since they need no typing at all (the only safe option while
// driving); searching an address is there too, and the host itself decides
// whether the keyboard is usable at that moment.
internal class CarDestinationScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
) : Screen(carContext) {

    private var isLoading = true
    private var favorites: List<FavoritePlaceResponse> = emptyList()
    private var favoritesFailed = false

    init {
        lifecycleScope.launch {
            deps.favoritePlaceRepository.list().fold(
                onSuccess = { favorites = it },
                onFailure = { favoritesFailed = true },
            )
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle("Nuevo viaje")
            .setHeaderAction(Action.BACK)
        if (isLoading) return builder.setLoading(true).build()

        val items = ItemList.Builder()
        items.addItem(
            Row.Builder()
                .setTitle("Buscar dirección")
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(CarAddressSearchScreen(carContext, deps)) }
                .build(),
        )
        favorites.take(listItemLimit(carContext) - 1).forEach { favorite ->
            items.addItem(
                Row.Builder()
                    .setTitle(favorite.name)
                    .addText("Favorito")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(
                            CarNewTripScreen(carContext, deps, favorite.name, favorite.lat, favorite.lng),
                        )
                    }
                    .build(),
            )
        }
        if (favoritesFailed) {
            items.addItem(Row.Builder().setTitle("No se pudieron cargar los favoritos").build())
        }
        return builder.setSingleList(items.build()).build()
    }
}

package com.techvibedev.triptrace.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.techvibedev.triptrace.location.GeocodedPlace
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Destination search by address, resolved with the same free Android
// Geocoder the phone's Create trip screen uses. Only runs on submit, not
// per keystroke, to keep geocoder calls to one per search.
internal class CarAddressSearchScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
) : Screen(carContext) {

    private var isSearching = false
    private var lastQuery: String? = null
    private var results: List<GeocodedPlace> = emptyList()
    private var searchFailed = false
    private var searchJob: Job? = null

    private fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        lastQuery = trimmed
        isSearching = true
        invalidate()
        searchJob = lifecycleScope.launch {
            val result = deps.geocodingProvider.search(trimmed, maxResults = listItemLimit(carContext))
            results = result.getOrDefault(emptyList())
            searchFailed = result.isFailure
            isSearching = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                search(searchText)
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Dirección de destino")
            .setShowKeyboardByDefault(lastQuery == null)
        lastQuery?.let { builder.setInitialSearchText(it) }

        if (isSearching) return builder.setLoading(true).build()
        if (lastQuery == null) return builder.build()

        val items = ItemList.Builder()
        if (results.isEmpty()) {
            items.setNoItemsMessage(
                if (searchFailed) "No se pudo buscar la dirección" else "No se encontró esa dirección",
            )
        }
        results.forEach { place ->
            val row = Row.Builder()
                .setTitle(place.name)
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(CarNewTripScreen(carContext, deps, place.name, place.lat, place.lng))
                }
            place.detail?.let { row.addText(it) }
            items.addItem(row.build())
        }
        return builder.setItemList(items.build()).build()
    }
}

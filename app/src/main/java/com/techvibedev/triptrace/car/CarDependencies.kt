package com.techvibedev.triptrace.car

import android.content.Context
import com.techvibedev.triptrace.data.network.RetrofitClient
import com.techvibedev.triptrace.data.repository.FavoritePlaceRepository
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.data.session.TokenDataStore
import com.techvibedev.triptrace.location.GeocodingProvider
import com.techvibedev.triptrace.location.LocationProvider

// Same objects the phone UI builds in TripTraceNavHost, created once per car
// session and handed to every car screen. The session token is shared with
// the phone (same DataStore), so the car is logged in whenever the phone is.
internal class CarDependencies(context: Context) {
    private val appContext = context.applicationContext

    val tokenDataStore = TokenDataStore(appContext)
    val tripRepository = TripRepository(RetrofitClient.tripApiService, tokenDataStore)
    val favoritePlaceRepository = FavoritePlaceRepository(RetrofitClient.favoritePlaceApiService, tokenDataStore)
    val locationProvider = LocationProvider(appContext)
    val geocodingProvider = GeocodingProvider(appContext)
}

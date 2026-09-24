package com.techvibedev.triptrace

import android.app.Application
import com.google.android.gms.maps.MapsInitializer

class TripTraceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensures the Maps SDK is ready before any BitmapDescriptorFactory
        // call anywhere in the app — calling that class before a map has
        // been created/initialized throws IllegalStateException. This was
        // the root cause of a crash on "Iniciar viaje" (LiveRouteMap built
        // a custom marker icon before any GoogleMap existed yet). The real
        // fix is not calling BitmapDescriptorFactory before a map exists
        // (see LiveRouteMap), but initializing early here removes this
        // whole class of ordering bug for good, everywhere in the app.
        MapsInitializer.initialize(this)
    }
}

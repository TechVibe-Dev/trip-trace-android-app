package com.techvibedev.triptrace.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

// Android Auto only covers the trip itself: starting a new one, starting a
// planned one, and following the one in progress. Login, profile, settings
// and history stay phone-only.
class TripTraceCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return CarHomeScreen(carContext, CarDependencies(carContext))
    }
}

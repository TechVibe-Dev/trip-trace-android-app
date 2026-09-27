package com.techvibedev.triptrace.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class TripTraceCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return PlaceholderScreen(carContext)
    }
}

// Phase 1 placeholder (android#93) — replaced by a real trip list once the
// plumbing is confirmed working on a real head unit / the emulator.
private class PlaceholderScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("Trip Trace")
            .addAction(
                Action.Builder()
                    .setTitle("OK")
                    .setOnClickListener {}
                    .build(),
            )
            .build()
    }
}

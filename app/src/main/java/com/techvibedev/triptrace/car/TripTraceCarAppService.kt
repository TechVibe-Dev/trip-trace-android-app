package com.techvibedev.triptrace.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

// Entry point Android Auto binds to. Phase 1 (android#93) — proves the
// plumbing works (the app shows up on the car screen, a placeholder renders)
// before building anything that reads real trip data.
class TripTraceCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // ALLOW_ALL_HOSTS_VALIDATOR is appropriate for personal testing —
        // this app is only ever sideloaded via Android Auto's own "unknown
        // sources" developer setting, never distributed through Play. A
        // properly restricted validator only matters for an app meant for
        // other people to install.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return TripTraceCarSession()
    }
}

package com.techvibedev.triptrace.data.network

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor

// Tracks when the oldest API call still waiting for a response started, so
// the UI can tell the user the server is waking up (Render's free plan
// sleeps after 15 minutes idle and takes about a minute to come back)
// instead of showing a spinner that looks stuck.
object SlowRequestTracker {

    private var nextId = 0L
    private val startTimesById = mutableMapOf<Long, Long>()

    // elapsedRealtime() at which the oldest in-flight call started, or null
    // when nothing is waiting on the API.
    private val _oldestStartMs = MutableStateFlow<Long?>(null)
    val oldestStartMs: StateFlow<Long?> = _oldestStartMs.asStateFlow()

    val interceptor = Interceptor { chain ->
        val id = begin()
        try {
            chain.proceed(chain.request())
        } finally {
            end(id)
        }
    }

    @Synchronized
    private fun begin(): Long {
        val id = nextId++
        startTimesById[id] = SystemClock.elapsedRealtime()
        _oldestStartMs.value = startTimesById.values.min()
        return id
    }

    @Synchronized
    private fun end(id: Long) {
        startTimesById.remove(id)
        _oldestStartMs.value = startTimesById.values.minOrNull()
    }
}

package com.techvibedev.triptrace.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.techvibedev.triptrace.MainActivity
import com.techvibedev.triptrace.R
import com.techvibedev.triptrace.data.local.GpsPointEntity
import com.techvibedev.triptrace.data.local.SensorReadingEntity
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import com.techvibedev.triptrace.data.session.SettingsDataStore
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Foreground service that keeps recording GPS points to Room while a trip is
// IN_PROGRESS, even with the screen off or the app backgrounded. Only needs
// ACCESS_FINE_LOCATION (already requested elsewhere) — a foreground service
// started while the app is in the foreground keeps location access alive in
// the background on its own, so ACCESS_BACKGROUND_LOCATION isn't needed for
// this flow (see PR discussion).
class TripTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager

    // Which trip raw sensor readings belong to right now — read by
    // sensorEventListener (registered once, in onCreate) rather than
    // captured per-trip like locationCallback, since sensors don't need
    // per-trip LocationRequest-style configuration and can just stay
    // registered across trips, switching which tripId they tag.
    private var activeTripId: String? = null
    private val sensorReadingBuffer = mutableListOf<SensorReadingEntity>()
    private val sensorReadingBufferLock = Any()

    // Raw accelerometer/gyroscope samples, recorded purely to evaluate
    // sensor fusion (android#76) for bridging GPS gaps — not used for
    // anything in the app yet. Buffered in memory and flushed periodically
    // (see startSensorFlushLoop) rather than inserted one row at a time,
    // since these arrive far more often than GPS points.
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val tripId = activeTripId ?: return
            val type = when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> "ACCELEROMETER"
                Sensor.TYPE_GYROSCOPE -> "GYROSCOPE"
                else -> return
            }
            val reading = SensorReadingEntity(
                tripId = tripId,
                sensorType = type,
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                recordedAt = OffsetDateTime.now().toString(),
            )
            synchronized(sensorReadingBufferLock) {
                sensorReadingBuffer.add(reading)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()

        // Checked once, here — not reactively for the rest of the service's
        // life (android#87 part 3). Whatever the setting is when a trip's
        // tracking starts is what applies for that whole trip; toggling it
        // mid-trip only takes effect on the next one. Simpler than
        // continuously collecting the Flow and dynamically registering/
        // unregistering listeners, and matches how this setting is actually
        // meant to be used — deciding ahead of time whether you want this
        // trip's data for evaluation, not flipping it while driving.
        serviceScope.launch {
            val sensorRecordingEnabled = SettingsDataStore(applicationContext)
                .sensorRecordingEnabledFlow
                .first()
            if (sensorRecordingEnabled) {
                registerSensorListeners()
                startSensorFlushLoop()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentTripId = intent?.getStringExtra(EXTRA_TRIP_ID)
        if (currentTripId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        activeTripId = currentTripId

        // A started Service is reused across calls — Android does not spin
        // up a new instance just because start() was called again. Without
        // this, a leftover callback from a previous trip (e.g. the app left
        // ActiveTripScreen without hitting "Finalizar viaje") stays
        // registered forever, so every location update gets written twice:
        // once per trip. Tearing down any previous callback first guarantees
        // at most one active tracking session per service instance.
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val point = GpsPointEntity(
                    tripId = currentTripId,
                    lat = location.latitude,
                    lng = location.longitude,
                    speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                    bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
                    recordedAt = OffsetDateTime.now().toString(),
                )
                serviceScope.launch {
                    TripTraceDatabase.getInstance(applicationContext).gpsPointDao().insert(point)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        sensorManager.unregisterListener(sensorEventListener)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSensorListeners() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { accelerometer ->
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { gyroscope ->
            sensorManager.registerListener(sensorEventListener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun startSensorFlushLoop() {
        serviceScope.launch {
            while (true) {
                delay(SENSOR_FLUSH_INTERVAL_MS)
                val toFlush = synchronized(sensorReadingBufferLock) {
                    val copy = sensorReadingBuffer.toList()
                    sensorReadingBuffer.clear()
                    copy
                }
                if (toFlush.isNotEmpty()) {
                    TripTraceDatabase.getInstance(applicationContext).sensorReadingDao().insertAll(toFlush)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Viaje en curso",
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Was a hardcoded "TripTrace" literal — read from the same
            // resource the launcher name itself uses, so a rename (like
            // "TripTrace" -> "Trip Trace") doesn't need a second edit here.
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Grabando tu viaje...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
        private const val CHANNEL_ID = "trip_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        // Lowered from 10s/5s after a real driving test: the live map felt
        // laggy (points, speed, and the route polyline all only updated
        // every ~10s) and the polyline visibly cut corners between distant
        // points instead of following the street. 3s/1.5s keeps roughly the
        // same 2:1 ratio between requested and minimum interval, gives ~3x
        // point density, and is still well within what Room/the 30s API
        // sync comfortably handles for a normal trip length.
        private const val LOCATION_INTERVAL_MS = 3_000L
        private const val LOCATION_MIN_INTERVAL_MS = 1_500L
        private const val SENSOR_FLUSH_INTERVAL_MS = 2_000L

        fun start(context: Context, tripId: String) {
            val intent = Intent(context, TripTrackingService::class.java).apply {
                putExtra(EXTRA_TRIP_ID, tripId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripTrackingService::class.java))
        }
    }
}

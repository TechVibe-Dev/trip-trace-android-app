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
import com.techvibedev.triptrace.data.local.GpsPointEntity
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentTripId = intent?.getStringExtra(EXTRA_TRIP_ID)
        if (currentTripId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

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
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            .setContentTitle("TripTrace")
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

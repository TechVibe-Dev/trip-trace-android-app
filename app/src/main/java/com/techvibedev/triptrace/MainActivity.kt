package com.techvibedev.triptrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.techvibedev.triptrace.data.network.RetrofitClient
import com.techvibedev.triptrace.ui.navigation.TripTraceNavHost
import com.techvibedev.triptrace.ui.theme.TripTraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripTraceTheme {
                TripTraceNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Every time the app comes to the foreground: wakes the API if it
        // went to sleep while the app was closed (see RetrofitClient).
        RetrofitClient.warmUp()
    }
}

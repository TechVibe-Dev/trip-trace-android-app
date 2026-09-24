package com.techvibedev.triptrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
}

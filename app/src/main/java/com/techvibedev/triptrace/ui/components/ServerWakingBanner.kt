package com.techvibedev.triptrace.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.network.SlowRequestTracker
import kotlinx.coroutines.delay

// A normal API call answers in well under this; one still waiting past it
// is almost always the server booting after sleeping.
private const val SLOW_REQUEST_THRESHOLD_MS = 10_000L

// Shown while any API call has been waiting longer than
// SLOW_REQUEST_THRESHOLD_MS, so a cold start reads as "wait a bit" rather
// than as the app being frozen.
@Composable
fun ServerWakingBanner(modifier: Modifier = Modifier) {
    val oldestStartMs by SlowRequestTracker.oldestStartMs.collectAsState()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(oldestStartMs) {
        val startMs = oldestStartMs
        if (startMs == null) {
            isVisible = false
            return@LaunchedEffect
        }
        val waitedMs = SystemClock.elapsedRealtime() - startMs
        if (waitedMs < SLOW_REQUEST_THRESHOLD_MS) {
            isVisible = false
            delay(SLOW_REQUEST_THRESHOLD_MS - waitedMs)
        }
        isVisible = true
    }

    AnimatedVisibility(visible = isVisible, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = "El servidor se está despertando, puede tardar hasta un minuto.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

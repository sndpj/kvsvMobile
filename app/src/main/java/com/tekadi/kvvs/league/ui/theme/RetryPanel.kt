package com.tekadi.kvvs.league.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * KvsvRequest1.1 #4: "Handle slow network check issue and retry option in mobile application
 * screen for connection timeout error." The app already retries transient failures under the
 * hood (see network/RetryInterceptor.kt) and uses generous timeouts (RetrofitClient.kt), but
 * once every automatic attempt is exhausted — or the failure is a genuine timeout rather than a
 * transient 502/503/504 — a screen had nothing but a static error message with no way to try
 * again short of leaving and reopening it. This is the one shared "load failed, tap to retry"
 * panel, same role as PageLoader.kt is for loading: a screen shows this instead of its content
 * whenever a load attempt has failed and nothing has loaded yet, and `onRetry` re-runs whatever
 * load function got it there in the first place.
 */
@Composable
fun RetryPanel(
    message: String,
    accentColor: Color,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = textColor, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onRetry, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accentColor)) {
            Text("Retry")
        }
    }
}

package com.tekadi.kvvs.league.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Feature request: "Android app every loader should be display in center right showing in
 * left." A bare CircularProgressIndicator placed directly inside a Column/LazyColumn item
 * renders at Alignment.Start (left) by default — that's what every full-page "this screen is
 * loading" spinner in the app was doing. This wraps it in a full-width Box centered
 * horizontally, and is the one place that centering now lives, so no future screen can
 * reintroduce this by copy-pasting the old bare-indicator pattern.
 *
 * Deliberately NOT used for small inline spinners (a photo-upload overlay, a spinner inside a
 * button's own content row) — those are already correctly positioned within their own small
 * container and centering them against the *screen* width would be wrong.
 */
@Composable
fun PageLoader(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = color)
    }
}

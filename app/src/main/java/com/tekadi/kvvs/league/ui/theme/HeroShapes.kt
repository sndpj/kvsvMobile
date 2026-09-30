package com.tekadi.kvvs.league.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The old theme's signature shape was a 45°-cut "armor plate" panel — the
 * one recurring silhouette that made the dark HUD look read as a whole. The
 * "Tekadi cricket" mockup's signature is the opposite instinct: plain
 * rounded cards (12px), a hairline border, nothing angular anywhere. Rather
 * than touch the ~24 call sites across every screen that call
 * heroPanelShape()/heroBadgeShape() for "the card shape", these are
 * repurposed in place — same names and parameter shapes, new geometry — so
 * the whole app picks up rounded cards automatically.
 */

/** The app's standard card corner radius. Was a 14dp diagonal cut; now a 12dp round to match the mockup. */
fun heroPanelShape(cut: Dp = 12.dp): Shape = RoundedCornerShape(cut)

/** Smaller radius for chips/badges. */
fun heroBadgeShape(cut: Dp = 6.dp): Shape = RoundedCornerShape(cut)

/** A plain hairline border — was a translucent teal "glow"; the mockup's cards use a simple 0.5px border instead. */
fun glowBorder(color: Color = HairlineBorder, width: Dp = 0.5.dp) = BorderStroke(width, color)

/** Vertical fade used behind banner images so overlaid light text stays legible over any photo. */
val bannerScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, TekadiGreenDim.copy(alpha = 0.85f)),
)

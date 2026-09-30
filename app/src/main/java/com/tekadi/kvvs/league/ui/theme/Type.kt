package com.tekadi.kvvs.league.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// A single clean sans family throughout, per the mockup — the old theme's
// monospace "HUD readout" digits for the score didn't fit a warm cricket-club
// brand, so the live score now shares the same face as everything else, just
// bolder. System font (no bundled font assets / no network font-fetch risk).
val TekadiSans = FontFamily.SansSerif

val CricketTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = TekadiSans, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = TekadiSans, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = TekadiSans, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(
        fontFamily = TekadiSans, fontWeight = FontWeight.Medium, fontSize = 11.sp,
        letterSpacing = 0.4.sp, textAlign = TextAlign.Start,
    ),
)

package com.tekadi.kvvs.league.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TekadiCricketScheme = lightColorScheme(
    primary = TekadiGreen,
    onPrimary = TextOnDark,
    primaryContainer = TekadiGreenDim,
    onPrimaryContainer = TextOnDark,
    secondary = AccentOrangeDim,
    onSecondary = TextOnDark,
    tertiary = InfoBlueColor,
    background = AppBackground,
    onBackground = TextPrimaryColor,
    surface = CardSurface,
    onSurface = TextPrimaryColor,
    surfaceVariant = CardSurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    error = DangerRed,
    onError = TextOnDark,
)

@Composable
fun CricketScorerTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    // "Tekadi cricket" theme: a warm, single-mode light theme (forest green +
    // cream + orange, per tekadi_cricket_app_theme_preview.html), replacing
    // the previous dark HUD look. `darkTheme` is intentionally ignored and
    // defaults to false — the brief's mockup is light-only, same call the
    // previous dark-only theme made in the other direction (see git history
    // for the old "hero command center" rationale). If a real dark variant
    // is ever wanted, it should be designed deliberately from this palette's
    // dark-mode-safe ramps, not derived automatically.
    MaterialTheme(colorScheme = TekadiCricketScheme, typography = CricketTypography, content = content)
}

package com.tekadi.kvvs.league

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tekadi.kvvs.league.ui.nav.AppNavHost
import com.tekadi.kvvs.league.ui.theme.CricketScorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Explicit `light(...)` rather than the default `auto()`: auto() picks status/nav bar
        // icon color from the *system's* dark-mode setting, but CricketScorerTheme always
        // renders light (see Theme.kt) regardless of system setting — on a device in system
        // dark mode, auto() would render light icons over this app's light background and they'd
        // disappear. Forcing dark (visible) icons matches what the app actually looks like.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            CricketScorerTheme {
                AppNavHost()
            }
        }
    }
}

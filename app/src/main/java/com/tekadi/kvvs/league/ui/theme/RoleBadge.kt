package com.tekadi.kvvs.league.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tekadi.kvvs.league.data.CurrentUser

/**
 * Feature request: "role based theme" — one brand identity everywhere, plus this small
 * always-visible color+label so you always know which mode you're in. Renders nothing for
 * VIEWER (CurrentUser.roleLabel is null there) — the unmarked default state.
 *
 * Deliberately NOT a full re-theme per role: see the design-brief conversation for why — brand
 * dilution and a multiplied QA/maintenance surface weren't worth it for the clarity gained.
 */
@Composable
fun RoleBadge(modifier: Modifier = Modifier) {
    val label = CurrentUser.roleLabel ?: return
    val color = when (CurrentUser.role) {
        "SUPER_ADMIN" -> RoleColorSuperAdmin
        "TOURNAMENT_ADMIN" -> RoleColorTournamentAdmin
        "SCORER" -> RoleColorScorer
        "TEAM_MANAGER" -> RoleColorTeamManager
        "UMPIRE" -> RoleColorUmpire
        else -> return
    }
    Surface(color = color.copy(alpha = 0.14f), shape = heroBadgeShape(), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(label.uppercase(), color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

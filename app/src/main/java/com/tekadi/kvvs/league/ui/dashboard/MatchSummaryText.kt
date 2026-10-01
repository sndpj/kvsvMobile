package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.network.PlayerResponse

/**
 * KvsvRequest1.3 #7 — a feed's message, with a match summary's Player of the Match on its own
 * line and the name in bold. Announcements render exactly as before. The award line is outside
 * [maxLines] so a long result summary can't push it off a 2-line card.
 */
@Composable
fun FeedMessageText(feed: FeedResponse, color: Color, style: TextStyle, maxLines: Int = Int.MAX_VALUE) {
    if (feed.type != "MATCH_SUMMARY") {
        Text(feed.message, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val parts = splitPlayerOfMatch(feed.message, feed.playerOfMatchName)
    Column {
        Text(parts.body, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        parts.playerOfMatch?.let { PlayerOfMatchLine(it, color, style) }
    }
}

@Composable
fun PlayerOfMatchLine(name: String, color: Color, style: TextStyle) {
    Text(
        buildAnnotatedString {
            append(PLAYER_OF_MATCH_LABEL)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
        },
        color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/**
 * KvsvRequest1.3 #7 — Super/Tournament Admin picks a different Player of the Match. Shared by
 * the result step of the live screen and the match history screen.
 */
@Composable
fun PlayerOfMatchPicker(candidates: List<PlayerResponse>, currentId: Long?, enabled: Boolean, onPick: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && candidates.isNotEmpty()) {
            Text(if (currentId == null) "Choose Player of the Match" else "Change Player of the Match")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { p ->
                DropdownMenuItem(
                    text = { Text(if (p.id == currentId) "✓ ${p.name}" else p.name) },
                    onClick = { expanded = false; if (p.id != currentId) onPick(p.id) },
                )
            }
        }
    }
}

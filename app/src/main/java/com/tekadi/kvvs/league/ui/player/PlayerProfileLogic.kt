package com.tekadi.kvvs.league.ui.player

import com.tekadi.kvvs.league.network.CareerBatting
import com.tekadi.kvvs.league.network.CreatePlayerRequest
import com.tekadi.kvvs.league.network.PlayerProfileResponse
import java.util.Locale

/**
 * Player profile (KvsvRequest1.3) — display formatting and the Super Admin edit form's checks,
 * kept Android-free so they're unit-tested (PlayerProfileLogicTest). Same pattern as
 * UserAdminLogic.kt / FeedPostLogic.kt.
 */

const val BIO_MAX = 500 // player.bio VARCHAR(500)

fun roleLabel(role: String?): String = when (role) {
    "BATTER" -> "Batter"
    "BOWLER" -> "Bowler"
    "WICKET_KEEPER" -> "Wicket-keeper"
    "ALL_ROUNDER" -> "All-rounder"
    null, "" -> "Player"
    else -> role.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
}

fun battingStyleLabel(style: String?): String? = when (style) {
    "RIGHT_HAND" -> "Right-hand bat"
    "LEFT_HAND" -> "Left-hand bat"
    null, "" -> null
    else -> style.lowercase(Locale.ROOT).replace('_', ' ')
}

fun bowlingStyleLabel(style: String?): String? = when (style) {
    "RIGHT_ARM_FAST" -> "Right-arm fast"
    "MEDIUM" -> "Medium pace"
    "SPIN" -> "Spin"
    "LEFT_ARM_SPIN" -> "Left-arm spin"
    null, "" -> null
    else -> style.lowercase(Locale.ROOT).replace('_', ' ')
}

/** e.g. "All-rounder · #7 · Age 24 · Right-hand bat · Spin" — skips whatever isn't known. */
fun profileSubtitle(p: PlayerProfileResponse): String = listOfNotNull(
    roleLabel(p.role),
    p.jerseyNumber?.let { "#$it" },
    p.age?.let { "Age $it" },
    battingStyleLabel(p.battingStyle),
    bowlingStyleLabel(p.bowlingStyle),
).joinToString(" · ")

/** "3 matches for 2 teams", "1 match for Titans", "No matches yet". */
fun matchesSummary(p: PlayerProfileResponse): String {
    if (p.matchesPlayed == 0) return "No matches yet"
    val played = p.teams.filter { it.matches > 0 }
    val matchWord = if (p.matchesPlayed == 1) "match" else "matches"
    return when (played.size) {
        0 -> "${p.matchesPlayed} $matchWord"
        1 -> "${p.matchesPlayed} $matchWord for ${played[0].teamName}"
        else -> "${p.matchesPlayed} $matchWord for ${played.size} teams"
    }
}

/** Cricket shows "-" for an average that doesn't exist yet (never dismissed / no wickets). */
fun formatAverage(average: Double?): String = average?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "-"

fun formatRate(rate: Double): String = String.format(Locale.ROOT, "%.2f", rate)

/** e.g. "165 runs in 3 inn · HS 102". */
fun battingHeadline(b: CareerBatting): String =
    "${b.runs} runs in ${b.innings} inn" + if (b.innings == 0) "" else " · HS ${b.highestScore}"

// ---------------- Super Admin edit (KvsvRequest1.3 #4) ----------------

data class PlayerEditForm(
    val name: String, val jerseyNumber: String, val age: String, val role: String?,
    val battingStyle: String?, val bowlingStyle: String?, val bio: String,
    // Not editable here, but must be sent back: the backend overwrites photoUrl with whatever the
    // request carries, so leaving it out would delete the player's photo.
    val photoUrl: String? = null,
)

fun PlayerProfileResponse.toEditForm() = PlayerEditForm(
    name = name, jerseyNumber = jerseyNumber?.toString().orEmpty(), age = age?.toString().orEmpty(),
    role = role, battingStyle = battingStyle, bowlingStyle = bowlingStyle, bio = bio.orEmpty(),
    photoUrl = photoUrl,
)

/** null when the form can be saved. */
fun editFormError(f: PlayerEditForm): String? {
    if (f.name.isBlank()) return "Name is required"
    if (f.jerseyNumber.isNotBlank() && (f.jerseyNumber.trim().toIntOrNull() ?: -1) !in 0..999) return "Jersey number must be 0–999"
    if (f.age.isNotBlank() && (f.age.trim().toIntOrNull() ?: -1) !in 5..100) return "Age must be between 5 and 100"
    if (f.bio.trim().length > BIO_MAX) return "Keep the description to $BIO_MAX characters or fewer"
    return null
}

/**
 * The PUT body. teamId, mobileNumber and email are left null on purpose: the backend treats null
 * as "unchanged" for those, so editing from the profile never moves the player or wipes their
 * contact details (which the profile doesn't show).
 */
fun PlayerEditForm.toRequest() = CreatePlayerRequest(
    teamId = null,
    name = name.trim().replace(Regex("\\s+"), " "),
    photoUrl = photoUrl,
    jerseyNumber = jerseyNumber.trim().toIntOrNull(),
    age = age.trim().toIntOrNull(),
    role = role,
    battingStyle = battingStyle,
    bowlingStyle = bowlingStyle,
    bio = bio.trim().ifEmpty { null },
)

// Same values the backend and the team/pool forms use.
val PLAYER_ROLE_OPTIONS = listOf("BATTER", "BOWLER", "WICKET_KEEPER", "ALL_ROUNDER")
val BATTING_STYLE_OPTIONS = listOf("RIGHT_HAND", "LEFT_HAND")
val BOWLING_STYLE_OPTIONS = listOf("RIGHT_ARM_FAST", "MEDIUM", "SPIN", "LEFT_ARM_SPIN")

package com.tekadi.kvvs.league.ui.team

import java.util.Locale

/**
 * KvsvRequest1.3 #2 "No duplicate player entry" — same normalisation as the backend's
 * PlayerDedupCheck.normaliseName (case-insensitive, extra spaces ignored), so the add-player
 * form can say "already on this team" immediately; the server still enforces it. Android-free —
 * see RosterLogicTest.
 */
fun normalisePlayerName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

/** The existing roster name [name] would duplicate, or null if it's new to this team. */
fun duplicateRosterName(name: String, rosterNames: List<String>): String? {
    val key = normalisePlayerName(name)
    if (key.isEmpty()) return null
    return rosterNames.firstOrNull { normalisePlayerName(it) == key }
}

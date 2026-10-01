package com.tekadi.kvvs.league.ui.match

import com.tekadi.kvvs.league.data.maxOversPerBowler

/**
 * KvsvRequest1.3 #1 — "over per bowler restriction configurable, like a toggle; if not required
 * disable it, or it can be configured by the creator." Mirrors the backend's
 * BowlerOverLimitPolicy.validate so a bad value is caught inline instead of after a round-trip
 * (the server still re-checks). Android-free — see MatchFormLogicTest.
 */
data class BowlerLimitCheck(val error: String? = null, val maxOversPerBowler: Int? = null)

/**
 * @param customText what the creator typed; blank = use the default cap.
 * @return the value to send as maxOversPerBowler (null = default / restriction off), or an error.
 */
fun checkBowlerLimit(overs: Int, enabled: Boolean, customText: String): BowlerLimitCheck {
    if (!enabled || customText.isBlank()) return BowlerLimitCheck()
    val custom = customText.trim().toIntOrNull() ?: return BowlerLimitCheck(error = "Max overs per bowler must be a number")
    if (custom < 1) return BowlerLimitCheck(error = "Max overs per bowler must be at least 1")
    if (custom > overs) return BowlerLimitCheck(error = "Max overs per bowler can't be more than the match's $overs overs")
    return BowlerLimitCheck(maxOversPerBowler = custom)
}

/** Hint shown next to the toggle, e.g. "Default: 2 overs each" or "No limit — any bowler, any number of overs". */
fun bowlerLimitHint(overs: Int?, enabled: Boolean): String = when {
    !enabled -> "No limit — a bowler just can't bowl two overs in a row"
    overs == null || overs <= 0 -> "Leave blank for the default (overs ÷ 5, rounded up)"
    else -> "Leave blank for the default: ${maxOversPerBowler(overs)} over${if (maxOversPerBowler(overs) == 1) "" else "s"} each"
}

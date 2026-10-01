package com.tekadi.kvvs.league.ui.dashboard

/**
 * KvsvRequest1.3 #7: "show the [Player of the Match] name in match summary feeds in bold."
 *
 * The backend still writes "Player of the Match: <name>" as the last line of a summary feed's
 * message (so older app versions keep showing it), and now also sends the name as data
 * (FeedResponse.playerOfMatchName). This splits that line out so the UI can render it on its
 * own with the name bolded, instead of showing it twice. Android-free so it's unit-tested —
 * see PlayerOfMatchLogicTest.
 */
const val PLAYER_OF_MATCH_LABEL = "Player of the Match: "

data class MatchSummaryParts(val body: String, val playerOfMatch: String?)

/**
 * @param structuredName FeedResponse.playerOfMatchName — preferred when present (it follows an
 * admin override even if the message text were stale); otherwise the name parsed from the text,
 * which is all a feed posted before this change has.
 */
fun splitPlayerOfMatch(message: String, structuredName: String?): MatchSummaryParts {
    val lines = message.lines()
    val labelIndex = lines.indexOfLast { it.trimStart().startsWith(PLAYER_OF_MATCH_LABEL.trimEnd()) }
    val parsedName = if (labelIndex >= 0) lines[labelIndex].substringAfter(':').trim().ifEmpty { null } else null
    val body = if (labelIndex >= 0) lines.filterIndexed { i, _ -> i != labelIndex } else lines
    return MatchSummaryParts(
        body = body.joinToString("\n").trim(),
        playerOfMatch = structuredName?.trim()?.ifEmpty { null } ?: parsedName,
    )
}

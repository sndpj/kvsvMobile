package com.tekadi.kvvs.league.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KvsvRequest1.3 — the app's DTOs against the JSON the backend actually sends/expects, using the
 * same Json settings as RetrofitClient. Guards both directions of the contract: new backend
 * fields decode, and an older backend response (without them) still decodes.
 */
class KvsvRequest13DtoContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aPlayerProfileFromTheBackendDecodes() {
        val body = """
            {"playerId":5,"name":"Arjun Rao","photoUrl":null,"jerseyNumber":7,"age":24,"role":"ALL_ROUNDER",
             "battingStyle":"RIGHT_HAND","bowlingStyle":"SPIN","bio":"Opener",
             "matchesPlayed":3,
             "teams":[{"teamId":1,"teamName":"Titans","tournamentId":1,"tournamentName":"Summer Cup","matches":2}],
             "playerOfMatchCount":1,
             "playerOfMatchAwards":[{"matchId":9,"matchDate":"2026-09-03","teamAName":"Titans","teamBName":"Warriors","resultSummary":"Titans won"}],
             "batting":{"innings":3,"notOuts":1,"runs":165,"ballsFaced":120,"highestScore":102,"fours":17,"sixes":6,
                        "fifties":1,"hundreds":1,"average":82.5,"strikeRate":137.5},
             "bowling":{"innings":2,"ballsBowled":48,"overs":"8.0","runsConceded":40,"wickets":0,"maidens":0,
                        "average":null,"economy":5.0,"bestFigures":"0/18"}}
        """.trimIndent()

        val p = json.decodeFromString<PlayerProfileResponse>(body)

        assertEquals(5L, p.playerId)
        assertEquals("Summer Cup", p.teams.single().tournamentName)
        assertEquals(9L, p.playerOfMatchAwards.single().matchId)
        assertEquals(82.5, p.batting.average!!, 0.0)
        assertNull(p.bowling.average)
    }

    @Test
    fun aMatchResponseFromBeforeThisChangeStillDecodesWithDefaults() {
        val old = """{"id":1,"tournamentId":1,"oversLimit":20,"teamAId":1,"teamAName":"A","teamBId":2,"teamBName":"B","status":"LIVE"}"""
        val m = json.decodeFromString<MatchResponse>(old)
        assertTrue(m.bowlerOverLimitEnabled)
        assertNull(m.maxOversPerBowler)
        assertNull(m.playerOfMatchName)
    }

    @Test
    fun theNewMatchResponseFieldsDecode() {
        val body = """{"id":1,"tournamentId":1,"oversLimit":20,"teamAId":1,"teamAName":"A","teamBId":2,"teamBName":"B",
            "status":"COMPLETED","bowlerOverLimitEnabled":false,"maxOversPerBowler":null,"playerOfMatchId":55,"playerOfMatchName":"Arjun Rao"}"""
        val m = json.decodeFromString<MatchResponse>(body)
        assertFalse(m.bowlerOverLimitEnabled)
        assertEquals(55L, m.playerOfMatchId)
    }

    @Test
    fun createMatchSendsTheBowlerSettingsTheBackendReads() {
        val off = json.encodeToString(CreateMatchRequest(1, null, "2026-10-04", null, 10, 1, 2,
            bowlerOverLimitEnabled = false, maxOversPerBowler = null))
        assertTrue(off, off.contains("\"bowlerOverLimitEnabled\":false"))

        val custom = json.encodeToString(CreateMatchRequest(1, null, "2026-10-04", null, 10, 1, 2,
            bowlerOverLimitEnabled = true, maxOversPerBowler = 3))
        assertTrue(custom, custom.contains("\"maxOversPerBowler\":3"))
        // enabled=true is the default and may be omitted — the backend treats a missing value as true.
    }

    @Test
    fun aPlayerEditOmitsTeamAndContactSoTheBackendKeepsThem() {
        val body = json.encodeToString(CreatePlayerRequest(teamId = null, name = "Arjun", bio = "Opener"))
        assertFalse(body, body.contains("teamId"))
        assertFalse(body, body.contains("mobileNumber"))
        assertFalse(body, body.contains("email"))
        assertTrue(body, body.contains("\"bio\":\"Opener\""))
    }

    @Test
    fun aSummaryFeedCarriesThePlayerOfTheMatch() {
        val body = """{"id":3,"type":"MATCH_SUMMARY","message":"A vs B","createdAt":"2026-10-01T10:00:00Z",
            "playerOfMatchId":55,"playerOfMatchName":"Arjun Rao"}"""
        assertEquals("Arjun Rao", json.decodeFromString<FeedResponse>(body).playerOfMatchName)
    }
}

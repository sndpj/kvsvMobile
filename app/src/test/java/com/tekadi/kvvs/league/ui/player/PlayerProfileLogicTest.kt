package com.tekadi.kvvs.league.ui.player

import com.tekadi.kvvs.league.network.CareerBatting
import com.tekadi.kvvs.league.network.CareerBowling
import com.tekadi.kvvs.league.network.PlayerProfileResponse
import com.tekadi.kvvs.league.network.TeamAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Player profile (KvsvRequest1.3) display formatting, and the Super Admin edit form (#4). */
class PlayerProfileLogicTest {

    private val noBatting = CareerBatting(0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0.0)
    private val noBowling = CareerBowling(0, 0, "0.0", 0, 0, 0, null, 0.0, null)

    private fun profile(
        matches: Int = 0, teams: List<TeamAppearance> = emptyList(),
        role: String? = "ALL_ROUNDER", jersey: Int? = 7, age: Int? = 24,
        batting: String? = "RIGHT_HAND", bowling: String? = "SPIN", bio: String? = null, photo: String? = null,
    ) = PlayerProfileResponse(
        playerId = 5, name = "Arjun Rao", photoUrl = photo, jerseyNumber = jersey, age = age, role = role,
        battingStyle = batting, bowlingStyle = bowling, bio = bio,
        matchesPlayed = matches, teams = teams, playerOfMatchCount = 0,
        batting = noBatting, bowling = noBowling,
    )

    private fun team(name: String, matches: Int) = TeamAppearance(teamId = name.length.toLong(), teamName = name, matches = matches)

    // ---------------- display ----------------

    @Test
    fun subtitleListsWhateverIsKnown() {
        assertEquals("All-rounder · #7 · Age 24 · Right-hand bat · Spin", profileSubtitle(profile()))
        assertEquals("Player", profileSubtitle(profile(role = null, jersey = null, age = null, batting = null, bowling = null)))
    }

    @Test
    fun unknownCodesStillReadAsWords() {
        assertEquals("Opening batter", roleLabel("OPENING_BATTER"))
        assertEquals("Wicket-keeper", roleLabel("WICKET_KEEPER"))
        assertEquals("Left-arm spin", bowlingStyleLabel("LEFT_ARM_SPIN"))
        assertNull(battingStyleLabel(null))
    }

    @Test
    fun matchesSummaryNamesTheTeamWhenThereIsOnlyOne() {
        assertEquals("No matches yet", matchesSummary(profile()))
        assertEquals("1 match for Titans", matchesSummary(profile(1, listOf(team("Titans", 1)))))
        assertEquals("3 matches for Titans", matchesSummary(profile(3, listOf(team("Titans", 3), team("Warriors", 0)))))
        assertEquals("5 matches for 2 teams", matchesSummary(profile(5, listOf(team("Titans", 3), team("Warriors", 2)))))
    }

    @Test
    fun averagesThatDoNotExistYetShowADash() {
        assertEquals("-", formatAverage(null))
        assertEquals("82.50", formatAverage(82.5))
        assertEquals("137.50", formatRate(137.5))
    }

    @Test
    fun battingHeadlineSkipsHighScoreBeforeAnyInnings() {
        assertEquals("0 runs in 0 inn", battingHeadline(noBatting))
        assertEquals("165 runs in 3 inn · HS 102", battingHeadline(noBatting.copy(innings = 3, runs = 165, highestScore = 102)))
    }

    // ---------------- #4 edit form ----------------

    @Test
    fun editFormStartsFromTheProfile() {
        val form = profile(bio = "Opener", photo = "https://cdn/a.jpg").toEditForm()
        assertEquals("Arjun Rao", form.name)
        assertEquals("7", form.jerseyNumber)
        assertEquals("Opener", form.bio)
        assertEquals("https://cdn/a.jpg", form.photoUrl)
    }

    @Test
    fun aValidFormHasNoError() {
        assertNull(editFormError(profile().toEditForm()))
    }

    @Test
    fun editFormChecks() {
        val ok = profile().toEditForm()
        assertEquals("Name is required", editFormError(ok.copy(name = " ")))
        assertEquals("Jersey number must be 0–999", editFormError(ok.copy(jerseyNumber = "1000")))
        assertEquals("Age must be between 5 and 100", editFormError(ok.copy(age = "3")))
        assertEquals("Keep the description to 500 characters or fewer", editFormError(ok.copy(bio = "x".repeat(501))))
        assertNull(editFormError(ok.copy(jerseyNumber = "", age = "")))
    }

    @Test
    fun theSaveRequestKeepsThePhotoAndLeavesTeamAndContactUntouched() {
        val req = profile(photo = "https://cdn/a.jpg").toEditForm()
            .copy(name = "  Arjun   R  ", jerseyNumber = "", bio = "  ").toRequest()
        assertEquals("Arjun R", req.name)
        assertEquals("https://cdn/a.jpg", req.photoUrl) // would be wiped by the backend if sent as null
        assertNull(req.teamId)
        assertNull(req.mobileNumber)
        assertNull(req.email)
        assertNull(req.jerseyNumber)
        assertNull(req.bio)
        assertEquals(24, req.age)
    }
}

package com.tekadi.kvvs.league.ui.dashboard

import com.tekadi.kvvs.league.network.FeedResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KvsvRequest1.2 (Feeds fixes) acceptance criteria #2: "Feeds features should be tested, write
 * test-cases." Covers the pure logic extracted into FeedPostLogic.kt — same "extract the logic,
 * test that" approach as LiveMatchLogicTest, since FeedPostViewModel itself (AndroidViewModel +
 * live network calls) has no test harness in this project yet.
 */
class FeedPostLogicTest {

    private fun feed(id: Long, createdByUserId: Long? = 1L, message: String = "msg") =
        FeedResponse(id, "ANNOUNCEMENT", message, null, emptyList(), null, null, createdByUserId, "Some Admin", "2026-09-01T10:00:00Z")

    // ---------------- feedIsOwnedBy ----------------

    @Test
    fun ownerMatchesReturnsTrue() {
        assertTrue(feedIsOwnedBy(feed(1, createdByUserId = 42L), currentUserId = 42L))
    }

    @Test
    fun gapCheck_differentUserReturnsFalse() {
        assertFalse(feedIsOwnedBy(feed(1, createdByUserId = 42L), currentUserId = 7L))
    }

    @Test
    fun gapCheck_noLoggedInUserReturnsFalse() {
        assertFalse(feedIsOwnedBy(feed(1, createdByUserId = 42L), currentUserId = null))
    }

    @Test
    fun gapCheck_feedWithNoOwnerOnRecordReturnsFalse() {
        // System-generated match-summary feeds have no createdByUserId at all.
        assertFalse(feedIsOwnedBy(feed(1, createdByUserId = null), currentUserId = 42L))
    }

    // ---------------- validateFeedForm ----------------

    @Test
    fun blankMessageIsRejected() {
        assertEquals("Enter a message", validateFeedForm("", pendingUploadCount = 0))
        assertEquals("Enter a message", validateFeedForm("   ", pendingUploadCount = 0))
    }

    @Test
    fun stillUploadingPhotosBlocksSubmission() {
        assertEquals("Wait for photos to finish uploading", validateFeedForm("Hello", pendingUploadCount = 2))
    }

    @Test
    fun blankMessageIsCheckedBeforePendingUploads() {
        // Both problems present at once — the message error is the more actionable one to show first.
        assertEquals("Enter a message", validateFeedForm("", pendingUploadCount = 3))
    }

    @Test
    fun aValidFormPassesWithNoError() {
        assertNull(validateFeedForm("Great win today!", pendingUploadCount = 0))
    }

    // ---------------- feedsAfterCreate ----------------

    @Test
    fun newlyCreatedFeedGoesToTheTop() {
        val existing = listOf(feed(1), feed(2))
        val created = feed(3)

        val result = feedsAfterCreate(existing, created)

        assertEquals(listOf(3L, 1L, 2L), result.map { it.id })
    }

    @Test
    fun creatingIntoAnEmptyListWorks() {
        assertEquals(listOf(5L), feedsAfterCreate(emptyList(), feed(5)).map { it.id })
    }

    // ---------------- feedsAfterUpdate ----------------

    @Test
    fun updateReplacesOnlyTheMatchingEntryInPlace() {
        val existing = listOf(feed(1, message = "old"), feed(2, message = "untouched"), feed(3, message = "also untouched"))
        val updated = feed(2, message = "new")

        val result = feedsAfterUpdate(existing, updated)

        assertEquals(listOf(1L, 2L, 3L), result.map { it.id }) // order preserved
        assertEquals("new", result.first { it.id == 2L }.message)
        assertEquals("old", result.first { it.id == 1L }.message)
    }

    @Test
    fun gapCheck_updatingAnIdNotInTheListChangesNothing() {
        val existing = listOf(feed(1), feed(2))
        val result = feedsAfterUpdate(existing, feed(999, message = "phantom"))
        assertEquals(existing.map { it.id }, result.map { it.id })
    }

    // ---------------- feedsAfterDelete ----------------

    @Test
    fun deleteRemovesExactlyTheMatchingEntry() {
        val existing = listOf(feed(1), feed(2), feed(3))
        val result = feedsAfterDelete(existing, deletedId = 2)
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun gapCheck_deletingAnIdNotInTheListIsANoOp() {
        val existing = listOf(feed(1), feed(2))
        val result = feedsAfterDelete(existing, deletedId = 999)
        assertEquals(existing.map { it.id }, result.map { it.id })
    }
}

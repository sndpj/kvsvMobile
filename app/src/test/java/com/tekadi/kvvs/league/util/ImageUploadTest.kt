package com.tekadi.kvvs.league.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BUG FIX ("feed image loading still exists on test environment"): covers
 * ImageUpload.resolveUploadedUrl, extracted specifically so this is testable without mocking
 * Retrofit/ContentResolver. Root cause was a real production bug — see the function's own doc
 * comment for the full story — so this locks in both the old (still-supported) local-disk case
 * and the new absolute-GCS-URL case that was silently producing a broken doubled-up URL before
 * this fix.
 */
class ImageUploadTest {

    private val baseUrl = "https://kvsv-backend-service-test-965304126046.us-central1.run.app/"

    @Test
    fun aServerRelativePathFromLocalDiskStorageGetsTheApiBaseUrlPrepended() {
        assertEquals(
            "https://kvsv-backend-service-test-965304126046.us-central1.run.app/uploads/abc.jpg",
            ImageUpload.resolveUploadedUrl(baseUrl, "/uploads/abc.jpg"),
        )
    }

    @Test
    fun gapCheck_anAlreadyAbsoluteGcsUrlIsReturnedAsIsNotDoublePrefixed() {
        val gcsUrl = "https://storage.googleapis.com/kvvs-assets-project-9b9fec0b-90f0-4967-bc5/abc.jpg"
        assertEquals(gcsUrl, ImageUpload.resolveUploadedUrl(baseUrl, gcsUrl))
    }

    @Test
    fun gapCheck_anAbsoluteHttpUrlIsAlsoLeftAsIs() {
        // Not everything on GCS or elsewhere is necessarily https in every environment/test double.
        val httpUrl = "http://localhost:4443/bucket/abc.jpg"
        assertEquals(httpUrl, ImageUpload.resolveUploadedUrl(baseUrl, httpUrl))
    }

    @Test
    fun baseUrlWithoutATrailingSlashStillJoinsCleanlyWithARelativePath() {
        assertEquals(
            "https://example.com/uploads/abc.jpg",
            ImageUpload.resolveUploadedUrl("https://example.com", "/uploads/abc.jpg"),
        )
    }
}

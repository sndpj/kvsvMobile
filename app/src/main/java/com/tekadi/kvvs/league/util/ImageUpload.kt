package com.tekadi.kvvs.league.util

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tekadi.kvvs.league.BuildConfig
import com.tekadi.kvvs.league.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Feature request: "put image picker instead of Image Url in all over application." Wraps the
 * Android Photo Picker (via ActivityResultContracts.PickVisualMedia/PickMultipleVisualMedia —
 * available since androidx.activity 1.7, works down to minSdk 26 through the Jetpack backport,
 * not just on OS versions with the picker built in natively) plus the upload round-trip, so
 * every screen that needs an image just calls one of the two composables below instead of
 * reimplementing picking + uploading + error handling each time.
 */
object ImageUpload {

    /**
     * BUG FIX ("feed image loading still exists on test environment"): this used to
     * unconditionally do `BuildConfig.API_BASE_URL.trimEnd('/') + res.body()!!.url`. That's
     * correct for LocalDiskFileStorageService, which returns a server-relative path
     * ("/uploads/xxx.jpg") — but once the backend's app.storage.mode=gcs fix is actually live
     * (see application-test.yml), GcsFileStorageService returns an already-ABSOLUTE URL
     * ("https://storage.googleapis.com/..."). Prepending the API base URL to an absolute URL
     * produces a malformed, doubled-up string like
     * "https://kvsv-backend-....run.app/https://storage.googleapis.com/..." — which Coil's
     * AsyncImage can't load, so the image silently never appears. resolveUploadedUrl below is
     * the guard: only prefix when the server's response is genuinely relative.
     */
    internal fun resolveUploadedUrl(baseUrl: String, rawUrl: String): String {
        return if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
            rawUrl
        } else {
            baseUrl.trimEnd('/') + rawUrl
        }
    }

    /** Reads a content:// Uri into bytes and uploads it, returning the server's full image URL (base URL + relative path, or the server's own absolute URL as-is). */
    suspend fun uploadUri(contentResolver: ContentResolver, uri: Uri): Result<String> {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Couldn't read the selected image"))
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "upload.$extension", requestBody)

            val res = RetrofitClient.api.uploadImage(part)
            if (res.isSuccessful && res.body() != null) {
                Result.success(resolveUploadedUrl(BuildConfig.API_BASE_URL, res.body()!!.url))
            } else {
                Result.failure(IllegalStateException("Upload failed (HTTP ${res.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/** Single-image picker — feature request 3a, "profile pick single image selection." */
@Composable
fun SingleImagePickerButton(
    label: String = "Pick photo",
    onPicked: (localUri: Uri) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    OutlinedButton(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
        Text(label)
    }
}

/** Multi-image picker — feature request 3b, "for feeds user can select multiple images." */
@Composable
fun MultiImagePickerButton(
    label: String = "Add photos",
    maxItems: Int = 6,
    onPicked: (localUris: List<Uri>) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    OutlinedButton(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
        Text(label)
    }
}

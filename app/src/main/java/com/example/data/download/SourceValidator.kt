package com.example.data.download

import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class SourceValidationResult {
    data class Success(
        val isDirectMedia: Boolean,
        val isHlsStream: Boolean,
        val contentLength: Long,
        val contentType: String?,
        val supportsRange: Boolean,
        val resolvedUrl: String
    ) : SourceValidationResult()

    data class Error(val message: String) : SourceValidationResult()
}

object SourceValidator {
    private const val TAG = "SourceValidator"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isUrlValid(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.trim().lowercase()
        return clean.startsWith("http://") || clean.startsWith("https://")
    }

    suspend fun validate(url: String): SourceValidationResult = withContext(Dispatchers.IO) {
        if (!isUrlValid(url)) {
            return@withContext SourceValidationResult.Error("URL vazia ou com formato inválido.")
        }

        val cleanUrl = url.trim()
        val lower = cleanUrl.lowercase()

        // Check obvious extensions first
        val isM3u8 = lower.contains(".m3u8")
        val isExplicitDirect = lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm")

        try {
            val headRequest = Request.Builder()
                .url(cleanUrl)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .build()

            val response = try {
                client.newCall(headRequest).execute()
            } catch (e: Exception) {
                // Fallback: Range 0-0 GET if server rejects HEAD
                val rangeReq = Request.Builder()
                    .url(cleanUrl)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    .build()
                client.newCall(rangeReq).execute()
            }

            response.use { resp ->
                val finalUrl = resp.request.url.toString()
                val contentType = resp.header("Content-Type")?.lowercase() ?: ""
                val acceptRanges = resp.header("Accept-Ranges")?.lowercase() ?: ""
                val contentRange = resp.header("Content-Range") ?: ""
                val supportsRange = acceptRanges.contains("bytes") || contentRange.contains("bytes")

                val lengthHeader = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val totalLength = if (lengthHeader > 0) {
                    lengthHeader
                } else if (contentRange.contains("/")) {
                    contentRange.substringAfterLast("/").toLongOrNull() ?: -1L
                } else {
                    -1L
                }

                val directMedia = isExplicitDirect ||
                        contentType.contains("video/") ||
                        contentType.contains("application/octet-stream")

                val hlsStream = isM3u8 || contentType.contains("mpegurl") || contentType.contains("hls")

                Log.d(TAG, "Validated: direct=$directMedia, hls=$hlsStream, size=$totalLength, mime=$contentType")

                return@withContext SourceValidationResult.Success(
                    isDirectMedia = directMedia && !hlsStream,
                    isHlsStream = hlsStream,
                    contentLength = totalLength,
                    contentType = contentType.ifBlank { if (directMedia) "video/mp4" else null },
                    supportsRange = supportsRange,
                    resolvedUrl = finalUrl
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network validation fallback: ${e.message}")
            // Graceful heuristic fallback if server blocked HEAD probe
            return@withContext SourceValidationResult.Success(
                isDirectMedia = isExplicitDirect && !isM3u8,
                isHlsStream = isM3u8,
                contentLength = -1L,
                contentType = if (isExplicitDirect) "video/mp4" else if (isM3u8) "application/x-mpegURL" else null,
                supportsRange = true,
                resolvedUrl = cleanUrl
            )
        }
    }

    fun hasAvailableDiskSpace(directory: File, requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) return true
        return try {
            if (!directory.exists()) directory.mkdirs()
            val targetPath = if (directory.exists()) directory.path else android.os.Environment.getDataDirectory().path
            val stat = StatFs(targetPath)
            val available = stat.availableBytes
            available > (requiredBytes + 30 * 1024 * 1024) // 30MB safety buffer
        } catch (e: Exception) {
            true
        }
    }

    fun getAvailableStorageBytes(directory: File): Long {
        return try {
            if (!directory.exists()) directory.mkdirs()
            val targetPath = if (directory.exists()) directory.path else android.os.Environment.getDataDirectory().path
            val stat = StatFs(targetPath)
            stat.availableBytes
        } catch (e: Exception) {
            try {
                val stat = StatFs(android.os.Environment.getDataDirectory().path)
                stat.availableBytes
            } catch (ex: Exception) {
                0L
            }
        }
    }
}

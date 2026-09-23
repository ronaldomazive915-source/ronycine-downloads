package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.local.MediaEntity

/**
 * Robust, professional Content Sharing System for RONYCINE.
 * Handles Deep Links, Native Android Sharesheet, WhatsApp/Telegram shortcuts, and Clipboard copying.
 */
object ContentShareHelper {

    const val DOMAIN = "https://ronycine.app"
    const val RONYCINE_APK_URL = "https://github.com/ronaldomazive915-source/Rony-Skies-/releases/download/v1.1.0/RONYCINE.apk"

    data class ContentShareData(
        val tmdbId: Int,
        val mediaType: String, // "movie" or "tv"
        val title: String,
        val releaseYear: String = "",
        val durationOrSeasons: String = "",
        val genres: String = "",
        val rating: Double = 0.0,
        val restricted18: Boolean = false,
        val posterPath: String? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val episodeTitle: String? = null,
        val contentUrl: String,
        val appDownloadUrl: String = RONYCINE_APK_URL,
        val shareMessage: String
    )

    /**
     * Builds standard ContentShareData from MediaEntity and optional episode metadata.
     */
    fun buildShareData(
        media: MediaEntity,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        episodeTitle: String? = null
    ): ContentShareData {
        val tmdbId = media.tmdbId
        val type = if (media.mediaType.equals("tv", ignoreCase = true) || media.mediaType.equals("series", ignoreCase = true)) "tv" else "movie"
        val contentUrl = generateContentUrl(type, tmdbId, seasonNumber, episodeNumber)
        
        val durationOrSeasons = if (type == "tv") {
            if (seasonNumber != null && episodeNumber != null) {
                "Temporada $seasonNumber • Episódio $episodeNumber"
            } else {
                val count = if (media.seasonsCount > 0) media.seasonsCount else 1
                if (count == 1) "1 temporada" else "$count temporadas"
            }
        } else {
            val mins = if (media.durationMinutes > 0) media.durationMinutes else 118
            val hours = mins / 60
            val remMins = mins % 60
            if (hours > 0) "${hours}h ${remMins}min" else "${mins}min"
        }

        val cleanGenres = media.genres.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" • ")

        val shareMessage = generateShareMessage(
            title = media.title,
            mediaType = type,
            contentUrl = contentUrl,
            apkUrl = RONYCINE_APK_URL,
            season = seasonNumber,
            episode = episodeNumber
        )

        return ContentShareData(
            tmdbId = tmdbId,
            mediaType = type,
            title = media.title,
            releaseYear = media.releaseYear,
            durationOrSeasons = durationOrSeasons,
            genres = cleanGenres,
            rating = media.rating,
            restricted18 = media.restricted18,
            posterPath = media.posterPath,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            contentUrl = contentUrl,
            appDownloadUrl = RONYCINE_APK_URL,
            shareMessage = shareMessage
        )
    }

    /**
     * Generates real, compliant deep link URLs based on RONYCINE routing scheme:
     * - Movie: https://ronycine.app/filme/{tmdbId}
     * - Series: https://ronycine.app/serie/{tmdbId}
     * - Episode: https://ronycine.app/serie/{tmdbId}/temporada/{season}/episodio/{episode}
     */
    fun generateContentUrl(mediaType: String, tmdbId: Int, season: Int? = null, episode: Int? = null): String {
        return if (mediaType == "tv") {
            if (season != null && episode != null) {
                "$DOMAIN/serie/$tmdbId/temporada/$season/episodio/$episode"
            } else {
                "$DOMAIN/serie/$tmdbId"
            }
        } else {
            "$DOMAIN/filme/$tmdbId"
        }
    }

    /**
     * Generates dynamic share text messages formatted according to exact requirements.
     */
    fun generateShareMessage(
        title: String,
        mediaType: String,
        contentUrl: String,
        apkUrl: String = RONYCINE_APK_URL,
        season: Int? = null,
        episode: Int? = null
    ): String {
        return if (mediaType == "tv") {
            if (season != null && episode != null) {
                """
                📺 $title
                Temporada $season • Episódio $episode

                ▶️ Assistir no RONYCINE:
                $contentUrl

                📱 Baixar o RONYCINE:
                $apkUrl
                """.trimIndent()
            } else {
                """
                📺 $title

                ▶️ Assistir no RONYCINE:
                $contentUrl

                📱 Baixar o RONYCINE:
                $apkUrl
                """.trimIndent()
            }
        } else {
            """
            🎬 $title

            ▶️ Assistir no RONYCINE:
            $contentUrl

            📱 Baixar o RONYCINE:
            $apkUrl
            """.trimIndent()
        }
    }

    /**
     * Structured data for an incoming shared content link.
     */
    data class ParsedSharedContent(
        val tmdbId: Int,
        val mediaType: String, // "movie" or "tv"
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val watchRoute: String,
        val detailRoute: String,
        val autoPlay: Boolean = true
    )

    /**
     * Unified central parser for all shared content links and deep links in RONYCINE (Requirement 15).
     * Identifies TMDB ID, mediaType (movie / tv), season, and episode.
     * Validates TMDB ID > 0. Returns null if invalid or not found (Requirement 14).
     */
    fun handleSharedContentLink(uri: Uri?): ParsedSharedContent? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http" && scheme != "ronycine") return null

        val pathSegments = uri.pathSegments
        val host = uri.host?.lowercase()

        // 1. Custom scheme: ronycine://filme/123456 or ronycine://serie/123456/temporada/1/episodio/2
        if (scheme == "ronycine") {
            val typeIndicator = host ?: pathSegments.firstOrNull()?.lowercase()
            val rawId = if (host == "filme" || host == "movie" || host == "serie" || host == "series" || host == "tv" || host == "watch") {
                pathSegments.firstOrNull()
            } else {
                pathSegments.getOrNull(1)
            }
            val tmdbId = rawId?.toIntOrNull()
            if (tmdbId != null && tmdbId > 0) {
                val isTv = typeIndicator == "serie" || typeIndicator == "series" || typeIndicator == "tv" ||
                        uri.getQueryParameter("type") == "tv" || uri.getQueryParameter("type") == "serie"
                val mediaType = if (isTv) "tv" else "movie"

                val (season, episode) = extractSeasonAndEpisode(pathSegments, uri)
                val watchRoute = if (mediaType == "tv" && season != null && episode != null) {
                    "watch/$tmdbId/tv?season=$season&episode=$episode"
                } else if (mediaType == "tv") {
                    "watch/$tmdbId/tv?season=1&episode=1"
                } else {
                    "watch/$tmdbId/movie"
                }
                return ParsedSharedContent(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    seasonNumber = season,
                    episodeNumber = episode,
                    watchRoute = watchRoute,
                    detailRoute = "detail/$tmdbId/$mediaType"
                )
            }
        }

        // 2. HTTPS/HTTP Web link: https://ronycine.app/filme/123456 or https://ronycine.app/serie/123456/temporada/1/episodio/2
        if (pathSegments.isNotEmpty()) {
            val firstSegment = pathSegments[0].lowercase()

            // /filme/{tmdbId} or /movie/{tmdbId}
            if (firstSegment == "filme" || firstSegment == "movie") {
                val tmdbId = pathSegments.getOrNull(1)?.toIntOrNull()
                if (tmdbId != null && tmdbId > 0) {
                    return ParsedSharedContent(
                        tmdbId = tmdbId,
                        mediaType = "movie",
                        seasonNumber = null,
                        episodeNumber = null,
                        watchRoute = "watch/$tmdbId/movie",
                        detailRoute = "detail/$tmdbId/movie"
                    )
                }
            }

            // /serie/{tmdbId} or /series/{tmdbId} or /tv/{tmdbId}
            if (firstSegment == "serie" || firstSegment == "series" || firstSegment == "tv") {
                val tmdbId = pathSegments.getOrNull(1)?.toIntOrNull()
                if (tmdbId != null && tmdbId > 0) {
                    val (season, episode) = extractSeasonAndEpisode(pathSegments, uri)
                    val watchRoute = if (season != null && episode != null) {
                        "watch/$tmdbId/tv?season=$season&episode=$episode"
                    } else {
                        "watch/$tmdbId/tv?season=1&episode=1"
                    }
                    return ParsedSharedContent(
                        tmdbId = tmdbId,
                        mediaType = "tv",
                        seasonNumber = season,
                        episodeNumber = episode,
                        watchRoute = watchRoute,
                        detailRoute = "detail/$tmdbId/tv"
                    )
                }
            }

            // /watch/{tmdbId}/{type}
            if (firstSegment == "watch") {
                val tmdbId = pathSegments.getOrNull(1)?.toIntOrNull()
                val type = pathSegments.getOrNull(2)?.lowercase() ?: "movie"
                val normType = if (type == "tv" || type == "serie" || type == "series") "tv" else "movie"
                if (tmdbId != null && tmdbId > 0) {
                    val (season, episode) = extractSeasonAndEpisode(pathSegments, uri)
                    val watchRoute = if (normType == "tv" && season != null && episode != null) {
                        "watch/$tmdbId/tv?season=$season&episode=$episode"
                    } else if (normType == "tv") {
                        "watch/$tmdbId/tv?season=1&episode=1"
                    } else {
                        "watch/$tmdbId/movie"
                    }
                    return ParsedSharedContent(
                        tmdbId = tmdbId,
                        mediaType = normType,
                        seasonNumber = season,
                        episodeNumber = episode,
                        watchRoute = watchRoute,
                        detailRoute = "detail/$tmdbId/$normType"
                    )
                }
            }

            // /detail/{tmdbId}/{type}
            if (firstSegment == "detail") {
                val tmdbId = pathSegments.getOrNull(1)?.toIntOrNull()
                val type = pathSegments.getOrNull(2)?.lowercase() ?: "movie"
                val normType = if (type == "tv" || type == "serie" || type == "series") "tv" else "movie"
                if (tmdbId != null && tmdbId > 0) {
                    val (season, episode) = extractSeasonAndEpisode(pathSegments, uri)
                    val watchRoute = if (normType == "tv" && season != null && episode != null) {
                        "watch/$tmdbId/tv?season=$season&episode=$episode"
                    } else if (normType == "tv") {
                        "watch/$tmdbId/tv?season=1&episode=1"
                    } else {
                        "watch/$tmdbId/movie"
                    }
                    return ParsedSharedContent(
                        tmdbId = tmdbId,
                        mediaType = normType,
                        seasonNumber = season,
                        episodeNumber = episode,
                        watchRoute = watchRoute,
                        detailRoute = "detail/$tmdbId/$normType"
                    )
                }
            }
        }

        return null
    }

    /**
     * Helper to extract season and episode from path segments or query parameters.
     */
    private fun extractSeasonAndEpisode(pathSegments: List<String>, uri: Uri): Pair<Int?, Int?> {
        val qSeason = uri.getQueryParameter("season")?.toIntOrNull() ?: uri.getQueryParameter("s")?.toIntOrNull()
        val qEpisode = uri.getQueryParameter("episode")?.toIntOrNull() ?: uri.getQueryParameter("e")?.toIntOrNull()
        if (qSeason != null && qEpisode != null) {
            return Pair(qSeason, qEpisode)
        }

        val sIdx = pathSegments.indexOfFirst { it.equals("temporada", true) || it.equals("s", true) || it.equals("season", true) }
        val eIdx = pathSegments.indexOfFirst { it.equals("episodio", true) || it.equals("e", true) || it.equals("episode", true) }

        var season: Int? = null
        var episode: Int? = null

        if (sIdx != -1 && sIdx + 1 < pathSegments.size) {
            season = pathSegments[sIdx + 1].toIntOrNull()
        }
        if (eIdx != -1 && eIdx + 1 < pathSegments.size) {
            episode = pathSegments[eIdx + 1].toIntOrNull()
        }

        return Pair(season ?: qSeason, episode ?: qEpisode)
    }

    /**
     * String overload for handling raw URLs or action string routes.
     */
    fun handleSharedContentLink(rawUrl: String?): ParsedSharedContent? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("ronycine://")) {
            return try {
                handleSharedContentLink(Uri.parse(trimmed))
            } catch (_: Exception) { null }
        }

        return try {
            val fakeUri = Uri.parse("https://ronycine.app/$trimmed")
            handleSharedContentLink(fakeUri)
        } catch (_: Exception) { null }
    }

    /**
     * Native Android Sharesheet using Intent.ACTION_SEND with Intent.createChooser.
     */
    fun shareViaNativeChooser(context: Context, shareData: ContentShareData) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, shareData.title)
            putExtra(Intent.EXTRA_TEXT, shareData.shareMessage)
        }
        val chooser = Intent.createChooser(intent, "Compartilhar via")
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir o compartilhador.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Direct WhatsApp share shortcut with graceful fallback.
     */
    fun shareViaWhatsApp(context: Context, shareData: ContentShareData) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, shareData.shareMessage)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to whatsapp web url or native chooser
            try {
                val encoded = Uri.encode(shareData.shareMessage)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=$encoded"))
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                shareViaNativeChooser(context, shareData)
            }
        }
    }

    /**
     * Direct Telegram share shortcut with graceful fallback.
     */
    fun shareViaTelegram(context: Context, shareData: ContentShareData) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("org.telegram.messenger")
                putExtra(Intent.EXTRA_TEXT, shareData.shareMessage)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val encodedUrl = Uri.encode(shareData.contentUrl)
                val encodedText = Uri.encode(shareData.shareMessage)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/share/url?url=$encodedUrl&text=$encodedText"))
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                shareViaNativeChooser(context, shareData)
            }
        }
    }

    /**
     * Copies content URL to clipboard with user feedback.
     */
    fun copyContentLink(context: Context, url: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Link do Conteúdo", url)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Link copiado!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao copiar link.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Copies APK download URL to clipboard with user feedback.
     */
    fun copyAppLink(context: Context, apkUrl: String = RONYCINE_APK_URL): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Link RONYCINE APK", apkUrl)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Link do aplicativo copiado!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao copiar link do app.", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

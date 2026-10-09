package com.example.util

import com.example.data.local.MediaEntity
import com.example.data.remote.MegaEmbedPlayerConfig
import com.example.data.remote.MegaEmbedPlayerType
import com.example.data.remote.PlayerSource

object PlayerUtils {

    /**
     * Normalizes HEX color string:
     * - Removes leading '#'
     * - Validates hexadecimal character range
     * - Defaults to "fb542b" if null or invalid
     */
    fun normalizeColor(rawColor: String?): String {
        return MegaEmbedPlayerType.normalizeColor(rawColor)
    }

    /**
     * Validates if a generated player URL is complete, non-empty, and free of placeholders or invalid values.
     */
    fun isValidPlayerUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) return false
        if (trimmed.contains("undefined", ignoreCase = true) ||
            trimmed.contains("null", ignoreCase = true) ||
            trimmed.contains("NaN") ||
            trimmed.contains("{") || trimmed.contains("}")
        ) return false
        return true
    }

    /**
     * Centralized URL Generator for RONYCINE Media Players.
     * All views and components MUST use this single source of truth.
     *
     * MegaEmbed URL structure:
     * - Movie TMDB:  https://mgeb.top/embed/{tmdb_id}?player={player}#color:{color}
     * - Series TMDB: https://mgeb.top/embed/{tmdb_id}/{season_number}/{episode_number}?player={player}#color:{color}
     * - Movie IMDb:  https://mgeb.top/embed/{imdb_id}?player={player}#color:{color}
     * - Series IMDb: https://mgeb.top/embed/{imdb_id}/{season_number}/{episode_number}?player={player}#color:{color}
     *                or https://mgeb.top/embed/{imdb_id}-{season_number}-{episode_number}?player={player}#color:{color}
     *
     * CRITICAL: ?player= MUST always appear before #color:
     */
    fun buildPlayerUrl(
        provider: String = "MegaEmbed",
        mediaType: String = "movie",
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        audio: String? = "Dublado",
        player: String? = null,
        color: String? = null,
        dsLang: String? = null,
        imdbSeriesFormat: String = "slash", // "slash" (/season/episode) or "dash" (-season-episode)
        templateMovieUrl: String? = null,
        templateTvUrl: String? = null,
        apiKey: String? = null,
        proEndpoint: String? = null
    ): String {
        val isMovie = mediaType.equals("movie", ignoreCase = true) || mediaType.equals("filme", ignoreCase = true)
        val s = if (season != null && season > 0) season else 1
        val e = if (episode != null && episode > 0) episode else 1

        val cleanColor = normalizeColor(color)
        val cleanPlayer = if (!player.isNullOrBlank()) {
            player.trim().lowercase()
        } else {
            MegaEmbedPlayerType.MEGAPLAY
        }

        val hasValidTmdb = tmdbId != null && tmdbId > 0
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotEmpty() }

        // Helper function for comprehensive template placeholder replacement
        fun applyPlaceholders(template: String): String {
            var res = template.trim()
            if (hasValidTmdb) {
                res = res.replace("{tmdb_id}", tmdbId.toString())
                    .replace("{tmdbId}", tmdbId.toString())
                    .replace("{tmdb}", tmdbId.toString())
                    .replace("{id}", tmdbId.toString())
            }
            if (!cleanImdb.isNullOrEmpty()) {
                res = res.replace("{imdb_id}", cleanImdb)
                    .replace("{imdbId}", cleanImdb)
            }
            if (!isMovie) {
                res = res.replace("{season_number}", s.toString())
                    .replace("{seasonNumber}", s.toString())
                    .replace("{season}", s.toString())
                    .replace("{temporada}", s.toString())
                    .replace("{episode_number}", e.toString())
                    .replace("{episodeNumber}", e.toString())
                    .replace("{episode}", e.toString())
                    .replace("{episodio}", e.toString())
            }
            return res
        }

        // 1. If an official Pro endpoint is configured by the admin
        if (!proEndpoint.isNullOrBlank()) {
            var url = applyPlaceholders(proEndpoint)
            if (!apiKey.isNullOrBlank()) {
                val sep = if (url.contains("?")) "&" else "?"
                url += "${sep}key=${apiKey.trim()}"
            }
            if (isValidPlayerUrl(url)) return url
        }

        // Check if provider is VidSrc or Audio is Legendado (VidSrc is the exclusive Legendado provider)
        val isSubtitledOrVidSrc = audio.equals("Legendado", ignoreCase = true) ||
                audio.equals("subtitled", ignoreCase = true) ||
                provider.contains("vidsrc", ignoreCase = true)

        if (isSubtitledOrVidSrc) {
            val idSegment = if (hasValidTmdb) tmdbId.toString() else (cleanImdb ?: "")
            if (idSegment.isEmpty()) return ""
            val langQuery = if (!dsLang.isNullOrBlank()) "?ds_lang=${dsLang.trim()}" else ""
            val raw = if (isMovie) {
                "https://vidsrc.tw/embed/movie/$idSegment$langQuery"
            } else {
                "https://vidsrc.tw/embed/tv/$idSegment/$s/$e$langQuery"
            }
            return if (isValidPlayerUrl(raw)) raw else ""
        }

        // If custom template URLs are provided (from custom PlayerSource)
        val customTemplate = if (isMovie) templateMovieUrl else templateTvUrl
        if (!customTemplate.isNullOrBlank() && !customTemplate.contains("mgeb.top")) {
            var url = applyPlaceholders(customTemplate)

            // Append query parameters before any existing fragment
            val queryParams = mutableListOf<String>()
            if (cleanPlayer.isNotBlank()) queryParams.add("player=$cleanPlayer")
            if (!apiKey.isNullOrBlank()) queryParams.add("key=${apiKey.trim()}")

            if (queryParams.isNotEmpty()) {
                val separator = if (url.contains("?")) "&" else "?"
                url += separator + queryParams.joinToString("&")
            }
            if (cleanColor.isNotBlank()) {
                url += "#color:$cleanColor"
            }
            if (isValidPlayerUrl(url)) return url
        }

        // MegaEmbed Standard (mgeb.top) for DUBLADO
        val baseDomain = "https://mgeb.top"
        val apiKeyParam = if (!apiKey.isNullOrBlank()) "&key=${apiKey.trim()}" else ""

        val rawFinal = if (hasValidTmdb) {
            if (isMovie) {
                "$baseDomain/embed/$tmdbId?player=$cleanPlayer$apiKeyParam#color:$cleanColor"
            } else {
                "$baseDomain/embed/$tmdbId/$s/$e?player=$cleanPlayer$apiKeyParam#color:$cleanColor"
            }
        } else if (!cleanImdb.isNullOrEmpty()) {
            if (isMovie) {
                "$baseDomain/embed/$cleanImdb?player=$cleanPlayer$apiKeyParam#color:$cleanColor"
            } else {
                if (imdbSeriesFormat.equals("dash", ignoreCase = true)) {
                    "$baseDomain/embed/$cleanImdb-$s-$e?player=$cleanPlayer$apiKeyParam#color:$cleanColor"
                } else {
                    "$baseDomain/embed/$cleanImdb/$s/$e?player=$cleanPlayer$apiKeyParam#color:$cleanColor"
                }
            }
        } else {
            ""
        }

        return if (isValidPlayerUrl(rawFinal)) rawFinal else ""
    }

    /**
     * Overload for calling with PlayerSource and MediaEntity
     */
    fun buildPlayerUrl(
        source: PlayerSource,
        media: MediaEntity,
        season: Int? = null,
        episode: Int? = null,
        megaEmbedConfig: MegaEmbedPlayerConfig? = null
    ): String {
        val isMegaEmbed = source.id.contains("mgeb", ignoreCase = true) ||
                source.name.contains("mega", ignoreCase = true) ||
                source.movieTmdbUrl.contains("mgeb.top") ||
                source.tvTmdbUrl.contains("mgeb.top")

        val effectivePlayer = if (isMegaEmbed && megaEmbedConfig != null && megaEmbedConfig.player.isNotBlank()) {
            megaEmbedConfig.player
        } else if (source.internalPlayer.isNotBlank()) {
            source.internalPlayer
        } else if (megaEmbedConfig != null && megaEmbedConfig.player.isNotBlank()) {
            megaEmbedConfig.player
        } else {
            "megaplay"
        }

        val effectiveColor = if (isMegaEmbed && megaEmbedConfig != null && megaEmbedConfig.color.isNotBlank()) {
            megaEmbedConfig.color
        } else if (source.playerColor.isNotBlank()) {
            source.playerColor
        } else if (megaEmbedConfig != null && megaEmbedConfig.color.isNotBlank()) {
            megaEmbedConfig.color
        } else {
            "fb542b"
        }

        val format = megaEmbedConfig?.imdbSeriesFormat ?: "slash"
        val effectiveApiKey = source.officialApiKey.ifBlank { megaEmbedConfig?.officialApiKey }
        val effectiveProEndpoint = source.officialProEndpoint.ifBlank { megaEmbedConfig?.officialProEndpoint }

        return buildPlayerUrl(
            provider = source.name.ifBlank { "MegaEmbed" },
            mediaType = media.mediaType,
            tmdbId = media.tmdbId,
            imdbId = null,
            season = season,
            episode = episode,
            audio = source.language,
            player = effectivePlayer,
            color = effectiveColor,
            imdbSeriesFormat = format,
            templateMovieUrl = source.movieTmdbUrl.takeIf { it.isNotBlank() },
            templateTvUrl = source.tvTmdbUrl.takeIf { it.isNotBlank() },
            apiKey = effectiveApiKey,
            proEndpoint = effectiveProEndpoint
        )
    }

    /**
     * Overload for calling with PlayerSource, mediaType string and tmdbId directly
     */
    fun buildPlayerUrl(
        source: PlayerSource,
        mediaType: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        megaEmbedConfig: MegaEmbedPlayerConfig? = null,
        dsLang: String? = null
    ): String {
        val isMegaEmbed = source.id.contains("mgeb", ignoreCase = true) ||
                source.name.contains("mega", ignoreCase = true) ||
                source.movieTmdbUrl.contains("mgeb.top") ||
                source.tvTmdbUrl.contains("mgeb.top")

        val effectivePlayer = if (isMegaEmbed && megaEmbedConfig != null && megaEmbedConfig.player.isNotBlank()) {
            megaEmbedConfig.player
        } else if (source.internalPlayer.isNotBlank()) {
            source.internalPlayer
        } else if (megaEmbedConfig != null && megaEmbedConfig.player.isNotBlank()) {
            megaEmbedConfig.player
        } else {
            "megaplay"
        }

        val effectiveColor = if (isMegaEmbed && megaEmbedConfig != null && megaEmbedConfig.color.isNotBlank()) {
            megaEmbedConfig.color
        } else if (source.playerColor.isNotBlank()) {
            source.playerColor
        } else if (megaEmbedConfig != null && megaEmbedConfig.color.isNotBlank()) {
            megaEmbedConfig.color
        } else {
            "fb542b"
        }

        val format = megaEmbedConfig?.imdbSeriesFormat ?: "slash"
        val effectiveApiKey = source.officialApiKey.ifBlank { megaEmbedConfig?.officialApiKey }
        val effectiveProEndpoint = source.officialProEndpoint.ifBlank { megaEmbedConfig?.officialProEndpoint }

        return buildPlayerUrl(
            provider = source.name.ifBlank { "MegaEmbed" },
            mediaType = mediaType,
            tmdbId = tmdbId,
            imdbId = null,
            season = season,
            episode = episode,
            audio = source.language,
            player = effectivePlayer,
            color = effectiveColor,
            dsLang = dsLang,
            imdbSeriesFormat = format,
            templateMovieUrl = source.movieTmdbUrl.takeIf { it.isNotBlank() },
            templateTvUrl = source.tvTmdbUrl.takeIf { it.isNotBlank() },
            apiKey = effectiveApiKey,
            proEndpoint = effectiveProEndpoint
        )
    }

    /**
     * Convenience overload for calling directly with PlayerSource and contentType
     */
    fun buildPlayerUrl(
        player: PlayerSource,
        contentType: String,
        tmdbId: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String {
        return buildPlayerUrl(
            provider = player.name.ifBlank { player.id },
            mediaType = contentType,
            tmdbId = tmdbId,
            imdbId = null,
            season = seasonNumber,
            episode = episodeNumber,
            audio = player.language,
            player = player.internalPlayer,
            color = player.playerColor,
            templateMovieUrl = player.movieTmdbUrl.takeIf { it.isNotBlank() },
            templateTvUrl = player.tvTmdbUrl.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Dedicated URL generator for Mgeb Embed / MegaEmbed Movies
     */
    fun buildMgebMovieUrl(
        tmdbId: Int,
        player: String? = null,
        color: String? = null,
        apiKey: String? = null
    ): String {
        return buildPlayerUrl(
            provider = "Mgeb Embed",
            mediaType = "movie",
            tmdbId = tmdbId,
            player = player,
            color = color,
            apiKey = apiKey
        )
    }

    /**
     * Dedicated URL generator for Mgeb Embed / MegaEmbed TV Show Episodes
     */
    fun buildMgebEpisodeUrl(
        tmdbId: Int,
        season: Int,
        episode: Int,
        player: String? = null,
        color: String? = null,
        apiKey: String? = null
    ): String {
        return buildPlayerUrl(
            provider = "Mgeb Embed",
            mediaType = "tv",
            tmdbId = tmdbId,
            season = season,
            episode = episode,
            player = player,
            color = color,
            apiKey = apiKey
        )
    }

    /**
     * Centralized duration formatter for RONYCINE.
     * Converts seconds (Double, Long, or Int) into HH:MM:SS or MM:SS format accurately.
     * Ensures no truncation, negative values, or incorrect minute divisions.
     *
     * Examples:
     * - 65.0 -> "01:05"
     * - 3600.0 -> "01:00:00"
     * - 3661.0 -> "01:01:01"
     * - 7200.0 -> "02:00:00"
     * - 7425.0 -> "02:03:45"
     */
    fun formatDuration(seconds: Double, forceHours: Boolean = false): String {
        if (seconds.isNaN() || seconds.isInfinite() || seconds <= 0.0) {
            return if (forceHours) "00:00:00" else "00:00"
        }
        val totalSec = seconds.toLong()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hours > 0 || forceHours) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    fun formatDuration(seconds: Long, forceHours: Boolean = false): String =
        formatDuration(seconds.toDouble(), forceHours)

    fun formatDuration(seconds: Int, forceHours: Boolean = false): String =
        formatDuration(seconds.toDouble(), forceHours)

    fun formatDurationMs(ms: Long, forceHours: Boolean = false): String =
        formatDuration((ms / 1000.0), forceHours)

    /**
     * Formats current playback time and total duration pair:
     * e.g., "00:05:32 / 02:03:47" or "01:05 / 45:10"
     */
    fun formatTimeProgress(currentTimeSeconds: Double, totalDurationSeconds: Double): String {
        val showHours = totalDurationSeconds >= 3600.0 || currentTimeSeconds >= 3600.0
        val curStr = formatDuration(currentTimeSeconds, forceHours = showHours)
        val durStr = formatDuration(totalDurationSeconds, forceHours = showHours)
        return "$curStr / $durStr"
    }
}

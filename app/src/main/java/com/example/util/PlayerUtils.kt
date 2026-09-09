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
        templateTvUrl: String? = null
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

        // Check if provider is VidSrc or Audio is Legendado (VidSrc is the exclusive Legendado provider)
        val isSubtitledOrVidSrc = audio.equals("Legendado", ignoreCase = true) ||
                audio.equals("subtitled", ignoreCase = true) ||
                provider.contains("vidsrc", ignoreCase = true)

        if (isSubtitledOrVidSrc) {
            val idSegment = if (hasValidTmdb) tmdbId.toString() else (cleanImdb ?: "")
            if (idSegment.isEmpty()) return ""
            val langQuery = if (!dsLang.isNullOrBlank()) "?ds_lang=${dsLang.trim()}" else ""
            return if (isMovie) {
                "https://vidsrc.tw/embed/movie/$idSegment$langQuery"
            } else {
                "https://vidsrc.tw/embed/tv/$idSegment/$s/$e$langQuery"
            }
        }

        // If custom template URLs are provided (from custom PlayerSource)
        val customTemplate = if (isMovie) templateMovieUrl else templateTvUrl
        if (!customTemplate.isNullOrBlank() && !customTemplate.contains("mgeb.top")) {
            var url = customTemplate
            if (hasValidTmdb) {
                url = url.replace("{tmdb_id}", tmdbId.toString())
                    .replace("{tmdbId}", tmdbId.toString())
            }
            if (!cleanImdb.isNullOrEmpty()) {
                url = url.replace("{imdb_id}", cleanImdb)
                    .replace("{imdbId}", cleanImdb)
            }
            if (!isMovie) {
                url = url.replace("{season_number}", s.toString())
                    .replace("{season}", s.toString())
                    .replace("{episode_number}", e.toString())
                    .replace("{episode}", e.toString())
            }

            // Append query parameters before any existing fragment
            if (cleanPlayer.isNotBlank()) {
                val separator = if (url.contains("?")) "&" else "?"
                url += "${separator}player=$cleanPlayer"
            }
            if (cleanColor.isNotBlank()) {
                url += "#color:$cleanColor"
            }
            return url
        }

        // MegaEmbed Standard (mgeb.top) for DUBLADO
        val baseDomain = "https://mgeb.top"

        return if (hasValidTmdb) {
            if (isMovie) {
                "$baseDomain/embed/$tmdbId?player=$cleanPlayer#color:$cleanColor"
            } else {
                "$baseDomain/embed/$tmdbId/$s/$e?player=$cleanPlayer#color:$cleanColor"
            }
        } else if (!cleanImdb.isNullOrEmpty()) {
            if (isMovie) {
                "$baseDomain/embed/$cleanImdb?player=$cleanPlayer#color:$cleanColor"
            } else {
                if (imdbSeriesFormat.equals("dash", ignoreCase = true)) {
                    "$baseDomain/embed/$cleanImdb-$s-$e?player=$cleanPlayer#color:$cleanColor"
                } else {
                    "$baseDomain/embed/$cleanImdb/$s/$e?player=$cleanPlayer#color:$cleanColor"
                }
            }
        } else {
            ""
        }
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
            templateTvUrl = source.tvTmdbUrl.takeIf { it.isNotBlank() }
        )
    }
}

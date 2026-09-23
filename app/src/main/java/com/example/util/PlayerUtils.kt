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

        // Check if provider is Videasy (https://player.videasy.to)
        val isVideasy = provider.contains("videasy", ignoreCase = true) ||
                (player != null && player.contains("videasy", ignoreCase = true)) ||
                (templateMovieUrl != null && templateMovieUrl.contains("videasy", ignoreCase = true)) ||
                (templateTvUrl != null && templateTvUrl.contains("videasy", ignoreCase = true))

        if (isVideasy) {
            val idSegment = if (hasValidTmdb) tmdbId.toString() else (cleanImdb ?: "")
            if (idSegment.isEmpty()) return ""
            val raw = if (isMovie) {
                "https://player.videasy.to/movie/$idSegment"
            } else {
                "https://player.videasy.to/tv/$idSegment/$s/$e"
            }
            return if (isValidPlayerUrl(raw)) raw else ""
        }

        // Check if provider is MultiEmbed (https://multiembed.mov)
        val isMultiEmbed = provider.contains("multiembed", ignoreCase = true) ||
                (player != null && player.contains("multiembed", ignoreCase = true)) ||
                (templateMovieUrl != null && templateMovieUrl.contains("multiembed", ignoreCase = true)) ||
                (templateTvUrl != null && templateTvUrl.contains("multiembed", ignoreCase = true))

        if (isMultiEmbed) {
            val idSegment = if (hasValidTmdb) tmdbId.toString() else (cleanImdb ?: "")
            if (idSegment.isEmpty()) return ""
            val raw = if (isMovie) {
                "https://multiembed.mov/?video_id=$idSegment&tmdb=1"
            } else {
                "https://multiembed.mov/?video_id=$idSegment&tmdb=1&s=$s&e=$e"
            }
            return if (isValidPlayerUrl(raw)) raw else ""
        }

        // Check if provider is RedeFlixApi (https://redeflixapi.store)
        val isRedeFlix = provider.contains("redeflix", ignoreCase = true) ||
                (player != null && player.contains("redeflix", ignoreCase = true)) ||
                (templateMovieUrl != null && templateMovieUrl.contains("redeflixapi.store", ignoreCase = true)) ||
                (templateTvUrl != null && templateTvUrl.contains("redeflixapi.store", ignoreCase = true))

        if (isRedeFlix) {
            val idSegment = if (hasValidTmdb) tmdbId.toString() else (cleanImdb ?: "")
            if (idSegment.isEmpty()) return ""
            val raw = if (isMovie) {
                "https://redeflixapi.store/filme/$idSegment"
            } else {
                "https://redeflixapi.store/serie/$idSegment/$s/$e"
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
            mediaType = mediaType,
            tmdbId = tmdbId,
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
}

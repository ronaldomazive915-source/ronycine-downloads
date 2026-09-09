package com.example.data.remote

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class PlayerSource(
    val id: String = "",
    val name: String = "",
    val type: String = "Embed", // Embed, MP4, HLS
    val enabled: Boolean = true,
    val priority: Int = 1,
    val language: String = "Dublado", // Dublado, Legendado, Original
    
    val movieTmdbUrl: String = "",
    val movieImdbUrl: String = "",
    
    val tvTmdbUrl: String = "",
    val tvImdbUrl: String = "",
    
    val internalPlayer: String = "", // megaplay, vidstack, etc
    val playerColor: String = "", // #fb542b
    
    val isDefault: Boolean = false,
    val isFallback: Boolean = true,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Keep
@JsonClass(generateAdapter = true)
data class MegaEmbedPlayerConfig(
    val enabled: Boolean = true,
    val player: String = "megaplay", // megaplay, megatube, vidstack, clappr
    val color: String = "fb542b", // hex without #
    val imdbSeriesFormat: String = "slash" // "slash" (/season/episode) or "dash" (-season-episode)
)

data class MegaEmbedPlayerOption(
    val code: String,
    val displayName: String,
    val description: String
)

object MegaEmbedPlayerType {
    const val MEGAPLAY = "megaplay"
    const val MEGATUBE = "megatube"
    const val VIDSTACK = "vidstack"
    const val CLAPPR = "clappr"

    val ALL = listOf(MEGAPLAY, MEGATUBE, VIDSTACK, CLAPPR)

    val ALL_PLAYERS = listOf(
        MegaEmbedPlayerOption(MEGAPLAY, "MegaPlay (Premium)", "Player principal do MegaEmbed"),
        MegaEmbedPlayerOption(MEGATUBE, "MegaTube (Youtube)", "Reprodução baseada no player YouTube"),
        MegaEmbedPlayerOption(VIDSTACK, "Vidstack (Moderno)", "Interface moderna"),
        MegaEmbedPlayerOption(CLAPPR, "Clappr (Clássico)", "Player clássico")
    )

    fun getDisplayName(code: String): String = when (code.trim().lowercase()) {
        MEGAPLAY -> "MegaPlay (Premium)"
        MEGATUBE -> "MegaTube (Youtube)"
        VIDSTACK -> "Vidstack (Moderno)"
        CLAPPR -> "Clappr (Clássico)"
        else -> code
    }

    fun getDescription(code: String): String = when (code.trim().lowercase()) {
        MEGAPLAY -> "Player principal do MegaEmbed"
        MEGATUBE -> "Reprodução baseada no player YouTube"
        VIDSTACK -> "Interface moderna"
        CLAPPR -> "Player clássico"
        else -> "Player do MegaEmbed"
    }

    fun isValidHexColor(color: String?): Boolean {
        if (color.isNullOrBlank()) return false
        val clean = color.trim().removePrefix("#")
        return clean.matches(Regex("^[0-9a-fA-F]{3,8}$"))
    }

    fun normalizeColor(rawColor: String?): String {
        if (rawColor.isNullOrBlank()) return "fb542b"
        val clean = rawColor.trim().removePrefix("#")
        return if (clean.matches(Regex("^[0-9a-fA-F]{3,8}$"))) clean else "fb542b"
    }
}

@Keep
@JsonClass(generateAdapter = true)
data class DubbedPlayerConfig(
    val provider: String = "MegaEmbed",
    val internalPlayer: String = "megaplay",
    val color: String = "fb542b",
    val enabled: Boolean = true
)

@Keep
@JsonClass(generateAdapter = true)
data class SubtitledPlayerConfig(
    val provider: String = "vidsrc",
    val defaultLanguage: String = "pt",
    val enabled: Boolean = true
)

@Keep
@JsonClass(generateAdapter = true)
data class PlayerConfig(
    val selectionMode: String = "PRIORITY", // FIXED, PRIORITY, AUTO
    val defaultPlayerId: String = "mgeb",
    val fallbackEnabled: Boolean = true,
    val dubbedPlayer: DubbedPlayerConfig = DubbedPlayerConfig(),
    val subtitledPlayer: SubtitledPlayerConfig = SubtitledPlayerConfig(),
    val megaEmbed: MegaEmbedPlayerConfig = MegaEmbedPlayerConfig(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "admin@ronycine.app"
)

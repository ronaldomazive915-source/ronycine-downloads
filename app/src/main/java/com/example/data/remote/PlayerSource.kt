package com.example.data.remote

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class PlayerSource(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "Embed", // Embed, Iframe, WebView, MP4, HLS
    val enabled: Boolean = true,
    val priority: Int = 1,
    val language: String = "Dublado", // Dublado, Legendado, Original
    val isSystem: Boolean = false,
    val supportedContent: List<String> = listOf("movie", "tv"), // "movie", "tv"
    
    val movieTmdbUrl: String = "",
    val movieImdbUrl: String = "",
    
    val tvTmdbUrl: String = "",
    val tvImdbUrl: String = "",
    
    val internalPlayer: String = "", // megaplay, megatube, vidstack, vidcore, redeflixapi
    val playerColor: String = "", // #fb542b
    
    val isDefault: Boolean = false,
    val isFallback: Boolean = true,
    
    // Modo de reprodução oficial
    val playbackMode: String = "standard", // "standard" (Padrão) ou "official_ad_free" (Sem anúncios - oficial)
    val isOfficialAdFreeSupported: Boolean = false,
    val adFreeStatusMessage: String = "",
    val officialApiKey: String = "", // Chave de API / Token Pro oficial configurável
    val officialProEndpoint: String = "", // Endpoint Pro oficial configurável
    val providerOrigin: String = "", // "RedeFlixApi", "MegaEmbed", "VidSrc"

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getEffectiveOrigin(): String {
        if (providerOrigin.isNotBlank()) return providerOrigin
        val lowerId = id.lowercase()
        val lowerName = name.lowercase()
        val lowerUrl = (movieTmdbUrl + tvTmdbUrl).lowercase()
        return when {
            lowerId.contains("redeflix") || lowerName.contains("redeflix") || lowerUrl.contains("redeflix") -> "RedeFlixApi"
            lowerId.contains("mgeb") || lowerName.contains("megaembed") || lowerUrl.contains("mgeb.top") -> "MegaEmbed"
            lowerId.contains("vidsrc") || lowerName.contains("vidsrc") || lowerUrl.contains("vidsrc") -> "VidSrc"
            else -> name.ifBlank { "Provedor Externo" }
        }
    }

    fun isAdFreeAvailable(): Boolean {
        return officialApiKey.isNotBlank() || officialProEndpoint.isNotBlank()
    }

    fun getAdvertisingLabel(): String {
        return if (isAdFreeAvailable()) {
            "Sem anúncios — disponível"
        } else {
            "Publicidade fornecida pelo player"
        }
    }

    fun getAdvertisingDetail(): String {
        val origin = getEffectiveOrigin()
        return if (isAdFreeAvailable()) {
            "Modalidade oficial sem anúncios ativa através das credenciais oficiais configuradas para este player."
        } else {
            when (origin) {
                "RedeFlixApi" -> "LIMITAÇÃO DO FORNECEDOR: O fornecedor externo RedeFlixApi exibe publicidade própria na página web do player público. A API oficial gratuita não disponibiliza chave para remoção de anúncios nesta conta."
                "MegaEmbed" -> "LIMITAÇÃO DO FORNECEDOR: Anúncios veiculados pela infraestrutura aberta do MegaEmbed. Para reprodução sem anúncios oficial, configure a Chave/Token Pro nas opções avançadas."
                "VidSrc" -> "LIMITAÇÃO DO FORNECEDOR: Player legendado via embed público vidsrc.tw que inclui publicidade na CDN de origem do fornecedor."
                else -> "LIMITAÇÃO DO FORNECEDOR: A publicidade é originada diretamente pelo provedor externo. O RONYCINE não exibe publicidade própria."
            }
        }
    }
}

@Keep
@JsonClass(generateAdapter = true)
data class MegaEmbedPlayerConfig(
    val enabled: Boolean = true,
    val player: String = "megaplay", // megaplay, megatube, vidstack, vidcore, clappr, redeflixapi
    val color: String = "fb542b", // hex without #
    val imdbSeriesFormat: String = "slash", // "slash" (/season/episode) or "dash" (-season-episode)
    val playbackMode: String = "standard",
    val isOfficialAdFreeSupported: Boolean = false,
    val adFreeStatusMessage: String = "",
    val officialApiKey: String = "", // Chave/Token Pro oficial fornecida pelo MegaEmbed
    val officialProEndpoint: String = "" // Endpoint Pro oficial (ex: https://pro.mgeb.top)
) {
    fun isAdFreeAvailable(): Boolean = officialApiKey.isNotBlank() || officialProEndpoint.isNotBlank()

    fun getAdvertisingLabel(): String = if (isAdFreeAvailable()) "Sem anúncios — disponível" else "Publicidade fornecida pelo player"

    fun getAdvertisingDetail(): String {
        return if (isAdFreeAvailable()) {
            "Modalidade oficial sem anúncios ativa via Token/Chave Pro do MegaEmbed."
        } else {
            "LIMITAÇÃO DO FORNECEDOR: Anúncios veiculados pelo MegaEmbed na infraestrutura gratuita. O RONYCINE não adiciona anúncios próprios. Para reprodução oficial sem anúncios, insira a Chave/Token Pro oficial."
        }
    }
}

data class MegaEmbedPlayerOption(
    val code: String,
    val displayName: String,
    val description: String
)

object MegaEmbedPlayerType {
    const val MEGAPLAY = "megaplay"
    const val MEGATUBE = "megatube"
    const val VIDSTACK = "vidstack"
    const val VIDCORE = "vidcore"
    const val CLAPPR = "clappr"
    const val REDEFLIXAPI = "redeflixapi"

    val ALL = listOf(MEGAPLAY, MEGATUBE, VIDSTACK, VIDCORE, REDEFLIXAPI)

    val ALL_PLAYERS = listOf(
        MegaEmbedPlayerOption(MEGAPLAY, "MegaPlay (Premium)", "Player principal do MegaEmbed"),
        MegaEmbedPlayerOption(MEGATUBE, "MegaTube (Youtube)", "Reprodução baseada no player YouTube"),
        MegaEmbedPlayerOption(VIDSTACK, "Vidstack (Moderno)", "Interface moderna com aceleração"),
        MegaEmbedPlayerOption(VIDCORE, "Vidcore (Clássico)", "Player clássico de alta compatibilidade"),
        MegaEmbedPlayerOption(REDEFLIXAPI, "RedeFlixApi (TMDB)", "Player externo via TMDB")
    )

    fun getDisplayName(code: String): String = when (code.trim().lowercase()) {
        MEGAPLAY -> "MegaPlay (Premium)"
        MEGATUBE -> "MegaTube (Youtube)"
        VIDSTACK -> "Vidstack (Moderno)"
        VIDCORE -> "Vidcore (Clássico)"
        CLAPPR -> "Clappr (Clássico)"
        REDEFLIXAPI, "redeflix" -> "RedeFlixApi (TMDB)"
        else -> code
    }

    fun getDescription(code: String): String = when (code.trim().lowercase()) {
        MEGAPLAY -> "Player principal do MegaEmbed"
        MEGATUBE -> "Reprodução baseada no player YouTube"
        VIDSTACK -> "Interface moderna com aceleração"
        VIDCORE, CLAPPR -> "Player clássico de alta compatibilidade"
        REDEFLIXAPI, "redeflix" -> "Player externo via TMDB"
        else -> "Player de reprodução"
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
    val defaultPlayerId: String = "videasy",
    val fallbackEnabled: Boolean = true,
    val dubbedPlayer: DubbedPlayerConfig = DubbedPlayerConfig(),
    val subtitledPlayer: SubtitledPlayerConfig = SubtitledPlayerConfig(),
    val megaEmbed: MegaEmbedPlayerConfig = MegaEmbedPlayerConfig(),
    val playbackMode: String = "standard", // "standard" (Padrão)
    val adsEnabled: Boolean = false, // Configuração central: ads.enabled do próprio RONYCINE
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "admin@ronycine.app"
)

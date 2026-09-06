package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveTvApiResponse<T>(
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "data") val data: T? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiChannel(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String? = "Variedades",
    @Json(name = "logo") val logo: String? = null,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "status") val status: String? = "online",
    @Json(name = "current_program") val currentProgram: String? = "Programação ao Vivo",
    @Json(name = "next_program") val nextProgram: String? = "Transmissão 24 Horas",
    @Json(name = "slug") val slug: String? = null
) {
    fun getEffectiveLogo(): String {
        return logo?.takeIf { it.isNotBlank() }
            ?: logoUrl?.takeIf { it.isNotBlank() }
            ?: "https://images.unsplash.com/photo-1598899134739-24c46f58b8c0?w=300"
    }

    fun getEffectiveEmbedUrl(): String {
        return embedUrl?.takeIf { it.isNotBlank() }
            ?: url?.takeIf { it.isNotBlank() }
            ?: "https://reidosembeds.online/embed/$id"
    }
}

@JsonClass(generateAdapter = true)
data class ApiEventEmbed(
    @Json(name = "provider") val provider: String? = "Principal",
    @Json(name = "quality") val quality: String? = "HD",
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "logo") val logo: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiEvent(
    @Json(name = "id") val id: String,
    @Json(name = "channel_site_id") val channelSiteId: String? = null,
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "time1") val time1: String? = null,
    @Json(name = "time2") val time2: String? = null,
    @Json(name = "time1_name") val time1Name: String? = null,
    @Json(name = "time2_name") val time2Name: String? = null,
    @Json(name = "time1_logo") val time1Logo: String? = null,
    @Json(name = "time2_logo") val time2Logo: String? = null,
    @Json(name = "visual_model") val visualModel: String? = "match", // "match" or "event"
    @Json(name = "event_logo") val eventLogo: String? = null,
    @Json(name = "competition_logo") val competitionLogo: String? = null,
    @Json(name = "sport_key") val sportKey: String? = "futebol",
    @Json(name = "start_time") val startTime: String? = "Ao Vivo",
    @Json(name = "end_time") val endTime: String? = null,
    @Json(name = "status") val status: String? = "live", // "live", "upcoming", "finished"
    @Json(name = "category") val category: String? = "Futebol",
    @Json(name = "competition") val competition: String? = "Campeonato",
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "base_public_slug") val basePublicSlug: String? = null,
    @Json(name = "public_slug") val publicSlug: String? = null,
    @Json(name = "page_url") val pageUrl: String? = null,
    @Json(name = "play_event_url") val playEventUrl: String? = null,
    @Json(name = "has_youtube_live") val hasYoutubeLive: Boolean? = false,
    @Json(name = "youtube_scan") val youtubeScan: String? = null,
    @Json(name = "embeds") val embeds: List<ApiEventEmbed>? = emptyList()
) {
    fun isLive(): Boolean {
        return status.equals("live", ignoreCase = true) ||
                status.equals("ao vivo", ignoreCase = true) ||
                status.equals("em andamento", ignoreCase = true)
    }

    fun getEffectiveEmbedUrl(): String {
        return embeds?.firstOrNull()?.embedUrl?.takeIf { it.isNotBlank() }
            ?: playEventUrl?.takeIf { it.isNotBlank() }
            ?: "https://reidosembeds.online/embed/evento/$id"
    }

    fun getTeam1DisplayName(): String {
        return time1Name?.takeIf { it.isNotBlank() }
            ?: time1?.takeIf { it.isNotBlank() }
            ?: title.split(" x ", " vs ", " X ", " VS ").firstOrNull()
            ?: "Time 1"
    }

    fun getTeam2DisplayName(): String {
        return time2Name?.takeIf { it.isNotBlank() }
            ?: time2?.takeIf { it.isNotBlank() }
            ?: title.split(" x ", " vs ", " X ", " VS ").getOrNull(1)
            ?: "Time 2"
    }
}

@JsonClass(generateAdapter = true)
data class ApiGuideItem(
    @Json(name = "channel_id") val channelId: String? = null,
    @Json(name = "channel_name") val channelName: String? = null,
    @Json(name = "time") val time: String = "20:00",
    @Json(name = "title") val title: String = "Programação",
    @Json(name = "description") val description: String? = null,
    @Json(name = "category") val category: String? = null
)

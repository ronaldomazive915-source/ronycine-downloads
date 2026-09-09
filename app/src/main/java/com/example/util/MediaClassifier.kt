package com.example.util

import com.example.data.local.MediaEntity
import com.example.data.remote.TmdbMediaDto

/**
 * Classificador Central de Mídias do RONYCINE.
 * Determina com precisão a categoria de cada conteúdo com base em múltiplos sinais do TMDB.
 *
 * Categorias permitidas:
 * - "anime"
 * - "dorama"
 * - "movie"
 * - "series"
 * - "live_tv"
 */
object MediaClassifier {

    const val CATEGORY_ANIME = "anime"
    const val CATEGORY_DORAMA = "dorama"
    const val CATEGORY_MOVIE = "movie"
    const val CATEGORY_SERIES = "series"
    const val CATEGORY_LIVE_TV = "live_tv"

    private const val GENRE_ANIMATION = 16
    private const val GENRE_DRAMA = 18
    private const val GENRE_ROMANCE = 10749

    /**
     * Identifica se um item é Anime.
     * Critérios:
     * - Possui gênero Animação (ID 16 ou texto 'Animação'/'Animation'/'Anime')
     * - Idioma original japonês ('ja') OU país de origem Japão ('JP')
     * - Evita falsos positivos como The Simpsons, Tom & Jerry, Bob Esponja etc.
     */
    fun isAnime(
        mediaType: String?,
        genreIds: List<Int>?,
        genreNames: String?,
        originalLanguage: String?,
        originCountry: List<String>?,
        title: String? = null
    ): Boolean {
        val hasAnimationGenre = genreIds?.contains(GENRE_ANIMATION) == true ||
                genreNames?.contains("Animação", ignoreCase = true) == true ||
                genreNames?.contains("Animation", ignoreCase = true) == true ||
                genreNames?.contains("Anime", ignoreCase = true) == true

        if (!hasAnimationGenre) return false

        val lang = originalLanguage?.trim()?.lowercase() ?: ""
        val countries = originCountry?.map { it.trim().uppercase() } ?: emptyList()

        val isJapaneseLanguage = lang == "ja" || lang == "jpn" || lang == "japanese"
        val isJapaneseCountry = countries.contains("JP") || countries.contains("JPN") || countries.contains("JAPAN")
        val hasAnimeTag = genreNames?.contains("Anime", ignoreCase = true) == true

        // O item precisa ter origem japonesa comprovada para ser classificado como anime
        return (isJapaneseLanguage || isJapaneseCountry || hasAnimeTag)
    }

    fun isAnime(dto: TmdbMediaDto): Boolean {
        val mediaType = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
        val genreIds = dto.genreIds ?: dto.genres?.map { it.id } ?: emptyList()
        val genreNames = dto.genres?.joinToString(", ") { it.name } ?: ""
        return isAnime(
            mediaType = mediaType,
            genreIds = genreIds,
            genreNames = genreNames,
            originalLanguage = dto.originalLanguage,
            originCountry = dto.originCountry,
            title = dto.name ?: dto.title
        )
    }

    fun isAnime(entity: MediaEntity): Boolean {
        if (entity.mediaCategory == CATEGORY_ANIME) return true
        val countries = entity.originCountry.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return isAnime(
            mediaType = entity.mediaType,
            genreIds = emptyList(),
            genreNames = entity.genres,
            originalLanguage = entity.originalLanguage,
            originCountry = countries,
            title = entity.title
        )
    }

    /**
     * Identifica se um item é Dorama.
     * Critérios:
     * - mediaType é 'tv' (série) ou drama episódico
     * - Não pode ser anime (regra de exclusão: anime nunca é dorama)
     * - Origem asiática: Coreia do Sul (KR / ko) prioritariamente, ou CN/TW/JP (live-action drama)
     * - Gênero Drama, Romance ou equivalente asiático
     * - Não classifica séries ocidentais (EUA, UK, Brasil) como dorama
     */
    fun isDorama(
        mediaType: String?,
        genreIds: List<Int>?,
        genreNames: String?,
        originalLanguage: String?,
        originCountry: List<String>?,
        title: String? = null
    ): Boolean {
        // Regra de ouro: Anime nunca pode ser Dorama
        if (isAnime(mediaType, genreIds, genreNames, originalLanguage, originCountry, title)) {
            return false
        }

        val type = mediaType?.lowercase() ?: "tv"
        // Doramas são quase universalmente séries de televisão / dramas seriados
        if (type != "tv" && type != "series") {
            // Permitir filmes coreanos apenas se explicitamente marcados como dorama em gêneros
            val isExplicitDorama = genreNames?.contains("Dorama", ignoreCase = true) == true ||
                    genreNames?.contains("K-Drama", ignoreCase = true) == true
            if (!isExplicitDorama) return false
        }

        val lang = originalLanguage?.trim()?.lowercase() ?: ""
        val countries = originCountry?.map { it.trim().uppercase() } ?: emptyList()

        val isKorean = lang == "ko" || lang == "kor" || lang == "korean" ||
                countries.contains("KR") || countries.contains("KOR") || countries.contains("KOREA")

        val isOtherAsianDrama = (lang in listOf("zh", "chi", "cmn", "yue", "tw", "th") ||
                countries.any { it in listOf("CN", "TW", "TH") })

        val hasDramaOrRomance = genreIds?.any { it == GENRE_DRAMA || it == GENRE_ROMANCE } == true ||
                genreNames?.contains("Drama", ignoreCase = true) == true ||
                genreNames?.contains("Romance", ignoreCase = true) == true ||
                genreNames?.contains("Dorama", ignoreCase = true) == true ||
                genreNames?.contains("Coreano", ignoreCase = true) == true ||
                genreNames?.contains("K-Drama", ignoreCase = true) == true

        return (isKorean || isOtherAsianDrama) && hasDramaOrRomance
    }

    fun isDorama(dto: TmdbMediaDto): Boolean {
        val mediaType = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
        val genreIds = dto.genreIds ?: dto.genres?.map { it.id } ?: emptyList()
        val genreNames = dto.genres?.joinToString(", ") { it.name } ?: ""
        return isDorama(
            mediaType = mediaType,
            genreIds = genreIds,
            genreNames = genreNames,
            originalLanguage = dto.originalLanguage,
            originCountry = dto.originCountry,
            title = dto.name ?: dto.title
        )
    }

    fun isDorama(entity: MediaEntity): Boolean {
        val countries = entity.originCountry.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val detected = isDorama(
            mediaType = entity.mediaType,
            genreIds = emptyList(),
            genreNames = entity.genres,
            originalLanguage = entity.originalLanguage,
            originCountry = countries,
            title = entity.title
        )
        if (detected) return true
        return entity.mediaCategory.equals(CATEGORY_DORAMA, ignoreCase = true)
    }

    /**
     * Função Central Única de Classificação.
     * Retorna exatamente uma das categorias:
     * - "anime"
     * - "dorama"
     * - "movie"
     * - "series"
     * - "live_tv"
     */
    fun classifyMedia(
        mediaType: String?,
        genreIds: List<Int>?,
        genreNames: String?,
        originalLanguage: String?,
        originCountry: List<String>?,
        title: String? = null
    ): String {
        val type = mediaType?.trim()?.lowercase() ?: "movie"

        if (type == "live_tv" || type == "channel" || type == "tv_channel") {
            return CATEGORY_LIVE_TV
        }

        if (isAnime(type, genreIds, genreNames, originalLanguage, originCountry, title)) {
            return CATEGORY_ANIME
        }

        if (isDorama(type, genreIds, genreNames, originalLanguage, originCountry, title)) {
            return CATEGORY_DORAMA
        }

        return if (type == "movie") CATEGORY_MOVIE else CATEGORY_SERIES
    }

    fun classifyMedia(dto: TmdbMediaDto, defaultType: String = "movie"): String {
        val type = dto.mediaType ?: defaultType
        val genreIds = dto.genreIds ?: dto.genres?.map { it.id } ?: emptyList()
        val genreNames = dto.genres?.joinToString(", ") { it.name } ?: ""
        return classifyMedia(
            mediaType = type,
            genreIds = genreIds,
            genreNames = genreNames,
            originalLanguage = dto.originalLanguage,
            originCountry = dto.originCountry,
            title = dto.name ?: dto.title
        )
    }

    private val classificationCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun classifyMedia(entity: MediaEntity): String {
        val key = "${entity.tmdbId}_${entity.mediaType}"
        classificationCache[key]?.let { return it }

        // Fast path: se já possuía uma categoria explícita válida salva
        val cat = entity.mediaCategory.trim().lowercase()
        if (cat == CATEGORY_ANIME) { classificationCache[key] = CATEGORY_ANIME; return CATEGORY_ANIME }
        if (cat == CATEGORY_DORAMA) { classificationCache[key] = CATEGORY_DORAMA; return CATEGORY_DORAMA }
        if (cat == CATEGORY_LIVE_TV) { classificationCache[key] = CATEGORY_LIVE_TV; return CATEGORY_LIVE_TV }
        if (cat == CATEGORY_MOVIE) { classificationCache[key] = CATEGORY_MOVIE; return CATEGORY_MOVIE }
        if (cat == CATEGORY_SERIES) { classificationCache[key] = CATEGORY_SERIES; return CATEGORY_SERIES }

        val countries = entity.originCountry.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        // Primeiro analisa sinais reais de Anime (gênero Animação + idioma/país japonês ou tag Anime)
        if (isAnime(
                mediaType = entity.mediaType,
                genreIds = emptyList(),
                genreNames = entity.genres,
                originalLanguage = entity.originalLanguage,
                originCountry = countries,
                title = entity.title
            )
        ) {
            classificationCache[key] = CATEGORY_ANIME
            return CATEGORY_ANIME
        }

        // Segundo analisa sinais reais de Dorama (série/drama + Coreia/Ásia e Romance/Drama)
        if (isDorama(
                mediaType = entity.mediaType,
                genreIds = emptyList(),
                genreNames = entity.genres,
                originalLanguage = entity.originalLanguage,
                originCountry = countries,
                title = entity.title
            )
        ) {
            classificationCache[key] = CATEGORY_DORAMA
            return CATEGORY_DORAMA
        }

        val type = entity.mediaType.trim().lowercase()
        val result = if (type == "movie") CATEGORY_MOVIE else CATEGORY_SERIES
        classificationCache[key] = result
        return result
    }
}

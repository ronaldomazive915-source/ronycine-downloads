package com.example.util

import com.example.data.local.MediaEntity
import com.example.data.remote.CalendarItem
import com.example.data.remote.UserProfile

object ContentAccessManager {

    // Palavras-chave de conteúdo explicitamente SEGURO para crianças / família (PT e EN)
    private val SAFE_GENRES = listOf(
        "kids",
        "family",
        "família",
        "familia",
        "infantil",
        "crianças",
        "crianca",
        "children",
        "desenho infantil",
        "animação infantil",
        "animacao infantil",
        "livre",
        "para toda a família",
        "para toda a familia",
        "para crianças",
        "para criancas",
        "educativo",
        "education",
        "educational"
    )

    // Palavras-chave estritamente PROIBIDAS para perfil infantil (mesmo se vierem com animação)
    private val UNSAFE_KEYWORDS = listOf(
        "terror",
        "horror",
        "erótico",
        "erotico",
        "erotismo",
        "sexo",
        "sexual",
        "crime",
        "guerra",
        "war",
        "suspense",
        "thriller",
        "faroeste",
        "western",
        "adult",
        "adulto",
        "18+",
        "16+",
        "14+",
        "violência",
        "violencia",
        "gore",
        "sangue",
        "psicológico",
        "psychological",
        "ecchi",
        "hentai",
        "seinen",
        "dark",
        "narcotráfico",
        "assassinato",
        "policial pesado"
    )

    /**
     * Verifica se uma mídia é categorizada como segura para o público infantil.
     * Regra estrita:
     * - Nunca permitir se restricted18 == true
     * - Nunca permitir se contiver gêneros ou palavras inseguras (terror, crime, erotismo, etc.)
     * - Animação NÃO significa automaticamente conteúdo infantil (ex: South Park, Attack on Titan, Berserk, Invincible).
     * - DEVE conter pelo menos um gênero explicitamente seguro (Família, Kids, Infantil, Crianças, Livre, Educativo).
     * - Na ausência de informação comprovada, bloqueia por padrão.
     */
    fun isKidsSafe(media: MediaEntity): Boolean {
        if (media.restricted18) return false

        val genresList = media.genres.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val titleLower = media.title.lowercase()
        val overviewLower = media.overview.lowercase()

        // 1. Bloqueio imediato se houver gêneros ou termos de classificação adulta/violenta
        val hasUnsafeGenre = genresList.any { genre ->
            UNSAFE_KEYWORDS.any { unsafe -> genre.contains(unsafe) }
        }
        if (hasUnsafeGenre) return false

        val hasUnsafeTitleOrOverview = UNSAFE_KEYWORDS.any { unsafe ->
            (titleLower.contains(unsafe) || overviewLower.contains(unsafe)) &&
            !SAFE_GENRES.any { safe -> titleLower.contains(safe) || overviewLower.contains(safe) }
        }
        if (hasUnsafeTitleOrOverview) return false

        // 2. Análise de gêneros explicitamente seguros
        val hasExplicitSafeGenre = genresList.any { genre ->
            SAFE_GENRES.any { safe -> genre.contains(safe) }
        }

        // Se for animação ou desenho, exige obrigatoriamente gênero infantil/familiar seguro
        val isAnimation = genresList.any { it.contains("animation") || it.contains("animação") || it.contains("animacao") } ||
                          media.mediaCategory.equals("anime", ignoreCase = true)
        if (isAnimation) {
            return hasExplicitSafeGenre
        }

        return hasExplicitSafeGenre
    }

    /**
     * Valida texto livre / metadados avulsos para verificação em tempo real
     */
    fun isKidsSafe(title: String, genres: String, overview: String = "", restricted18: Boolean = false): Boolean {
        if (restricted18) return false
        val genresList = genres.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val titleLower = title.lowercase()
        val overviewLower = overview.lowercase()

        val hasUnsafeGenre = genresList.any { genre ->
            UNSAFE_KEYWORDS.any { unsafe -> genre.contains(unsafe) }
        }
        if (hasUnsafeGenre) return false

        val hasExplicitSafeGenre = genresList.any { genre ->
            SAFE_GENRES.any { safe -> genre.contains(safe) }
        }

        val isAnimation = genresList.any { it.contains("animation") || it.contains("animação") || it.contains("animacao") }
        if (isAnimation) {
            return hasExplicitSafeGenre
        }

        return hasExplicitSafeGenre
    }

    /**
     * Define se um perfil pode acessar um determinado conteúdo.
     * Fonte de verdade centralizada para todo o app.
     */
    fun canProfileAccessContent(profile: UserProfile?, media: MediaEntity): Boolean {
        if (profile == null) return true // Se nenhum perfil estiver logado ainda, mantém comportamento padrão
        if (!profile.isKidsProfile) return true // Perfil padrão tem acesso ao catálogo completo
        return isKidsSafe(media)
    }

    /**
     * Filtra uma lista de mídias para o perfil atual.
     */
    fun filterContentForProfile(profile: UserProfile?, list: List<MediaEntity>): List<MediaEntity> {
        if (profile == null || !profile.isKidsProfile) return list
        return list.filter { canProfileAccessContent(profile, it) }
    }

    /**
     * Validação para itens do Calendário de lançamentos
     */
    fun canProfileAccessCalendarItem(profile: UserProfile?, item: CalendarItem): Boolean {
        if (profile == null || !profile.isKidsProfile) return true
        val titleLower = item.title.lowercase()
        val epTitleLower = item.episodeTitle?.lowercase() ?: ""

        // Se contiver termos claramente violentos/adultos, bloqueia
        val hasUnsafe = UNSAFE_KEYWORDS.any { unsafe ->
            titleLower.contains(unsafe) || epTitleLower.contains(unsafe)
        }
        if (hasUnsafe) return false

        // Se contiver termos claramente infantis/familiares, permite
        val hasSafe = SAFE_GENRES.any { safe ->
            titleLower.contains(safe) || epTitleLower.contains(safe)
        }
        if (hasSafe) return true

        // Se for classificado como anime ou série sem marcação de kids/família, bloqueia no modo infantil
        return false
    }

    fun filterCalendarItemsForProfile(profile: UserProfile?, list: List<CalendarItem>): List<CalendarItem> {
        if (profile == null || !profile.isKidsProfile) return list
        return list.filter { canProfileAccessCalendarItem(profile, it) }
    }
}


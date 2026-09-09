package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.MediaEntity
import com.example.data.local.PlayFilmeDao
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbNetwork
import com.example.util.CineIntentDetector
import com.example.util.CineIntentResult
import com.example.util.CineIntentType
import com.example.util.MediaFacet
import com.example.util.RecommendationCriteria
import com.example.util.SupportCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class AiConfidence {
    VERIFIED,
    LIKELY,
    UNKNOWN
}

data class VerifiedMediaFact(
    val title: String,
    val mediaType: String,
    val tmdbId: Int,
    val posterPath: String?,
    val rating: Double,
    val releaseYear: String,
    val durationMinutes: Int,
    val seasonsCount: Int,
    val episodesCount: Int,
    val overview: String,
    val director: String,
    val topCastWithCharacters: List<Pair<String, String>>, // Actor to Character
    val genres: String,
    val isInRonycineCatalog: Boolean,
    val confidence: AiConfidence
)

data class CineResponse(
    val text: String,
    val recommendedMediaId: Int? = null,
    val recommendedMediaType: String? = null,
    val recommendedMediaTitle: String? = null,
    val recommendedMediaPoster: String? = null,
    val confidence: AiConfidence = AiConfidence.VERIFIED
)

class CatalogAIService private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao: PlayFilmeDao = AppDatabase.getInstance(appContext).playFilmeDao()
    private val tmdbApi: TmdbApiService = TmdbNetwork.apiService
    private val tmdbApiKey: String
        get() = BuildConfig.TMDB_API_KEY.ifEmpty { "d60f5b5a778fe74b5cf4a371f008725b" }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    // Cache with TTL (1 hour)
    private data class CacheEntry<T>(val data: T, val expiresAt: Long)
    private val tmdbQueryCache = ConcurrentHashMap<String, CacheEntry<VerifiedMediaFact>>()

    companion object {
        @Volatile
        private var instance: CatalogAIService? = null

        fun getInstance(context: Context): CatalogAIService {
            return instance ?: synchronized(this) {
                instance ?: CatalogAIService(context).also { instance = it }
            }
        }

        private fun normalize(text: String): String {
            val nfd = Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD)
            return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        }
    }

    /**
     * Central resolution method:
     * INTENT -> SEARCH REAL DATA -> VALIDATE -> GENERATE RESPONSE
     */
    suspend fun generateResponse(
        userPrompt: String,
        profileId: String? = null,
        cineName: String = "Cine",
        cinePersonality: String = "amigável, cinéfilo e profissional"
    ): CineResponse = withContext(Dispatchers.IO) {
        val intent = CineIntentDetector.analyze(userPrompt)
        Log.d("CatalogAIService", "Detected intent: ${intent.type} for prompt: \"$userPrompt\"")

        when (intent.type) {
            CineIntentType.RECOMMENDATION -> {
                handleRecommendation(intent, profileId, cineName, cinePersonality)
            }
            CineIntentType.MEDIA_FACT -> {
                handleMediaFact(intent, cineName)
            }
            CineIntentType.SUPPORT -> {
                handleSupport(intent.supportCategory ?: SupportCategory.GENERAL, userPrompt)
            }
            CineIntentType.GREETING -> {
                handleGreeting(cineName, userPrompt)
            }
            CineIntentType.NONE -> {
                // If not none but reached here, offer recommendations
                handleRecommendation(intent, profileId, cineName, cinePersonality)
            }
        }
    }

    // ==========================================
    // 1. RECOMMENDATION ENGINE (REAL CATALOG)
    // ==========================================

    suspend fun getRecommendationCandidates(
        criteria: RecommendationCriteria?,
        profileId: String?
    ): List<MediaEntity> = withContext(Dispatchers.IO) {
        val allCatalog = dao.getAllMediaSync()
        if (allCatalog.isEmpty()) return@withContext emptyList()

        // Get watched history to avoid recommending finished content
        val watchedTmdbIds = if (profileId != null) {
            try {
                dao.getWatchHistorySync(profileId).filter { it.progressPercent > 0.90f }.map { it.tmdbId }.toSet()
            } catch (_: Exception) { emptySet() }
        } else emptySet()

        var filtered = allCatalog.filter { media ->
            !watchedTmdbIds.contains(media.tmdbId)
        }
        if (filtered.isEmpty()) {
            filtered = allCatalog // fallback if everything was watched
        }

        // Apply media type filter
        if (criteria?.mediaType != null) {
            filtered = filtered.filter { it.mediaType.equals(criteria.mediaType, ignoreCase = true) }
        }

        // Apply genre filter
        if (criteria?.genre != null) {
            val normGenre = normalize(criteria.genre)
            filtered = filtered.filter { normalize(it.genres).contains(normGenre) }
        }

        // Apply short duration filter
        if (criteria?.isShort == true) {
            filtered = filtered.filter { it.durationMinutes in 1..105 }
        }

        // Apply high rating filter
        if (criteria?.minRating != null) {
            filtered = filtered.filter { it.rating >= criteria.minRating }
        }

        // Apply recent filter
        if (criteria?.isRecent == true) {
            filtered = filtered.filter {
                val year = it.releaseYear.toIntOrNull() ?: 0
                year >= 2023
            }
        }

        // Apply similar to title filter
        if (!criteria?.similarTo.isNullOrBlank()) {
            val normRef = normalize(criteria!!.similarTo!!)
            val refMedia = allCatalog.find { normalize(it.title).contains(normRef) }
            if (refMedia != null) {
                val refGenres = refMedia.genres.split(",").map { normalize(it.trim()) }.filter { it.isNotBlank() }
                filtered = filtered.filter { item ->
                    item.tmdbId != refMedia.tmdbId && refGenres.any { g -> normalize(item.genres).contains(g) }
                }
            }
        }

        // Rank by rating and release year
        val ranked = filtered.sortedWith(
            compareByDescending<MediaEntity> { it.rating }
                .thenByDescending { it.releaseYear.toIntOrNull() ?: 0 }
        )

        val targetQuantity = criteria?.quantity ?: 3
        return@withContext ranked.take(targetQuantity)
    }

    private suspend fun handleRecommendation(
        intent: CineIntentResult,
        profileId: String?,
        cineName: String,
        personality: String
    ): CineResponse = withContext(Dispatchers.IO) {
        val candidates = getRecommendationCandidates(intent.criteria, profileId)

        if (candidates.isEmpty()) {
            // If local catalog is completely empty, fallback gracefully
            val fallbackCatalog = dao.getAllMediaSync()
            if (fallbackCatalog.isNotEmpty()) {
                val sample = fallbackCatalog.take(3)
                val primary = sample.first()
                val text = buildDeterministicRecommendation(sample, cineName)
                return@withContext CineResponse(
                    text = text,
                    recommendedMediaId = primary.tmdbId,
                    recommendedMediaType = primary.mediaType,
                    recommendedMediaTitle = primary.title,
                    recommendedMediaPoster = primary.posterPath,
                    confidence = AiConfidence.VERIFIED
                )
            } else {
                return@withContext CineResponse(
                    text = "🎬 Olá! No momento nosso catálogo está sendo sincronizado. Confira a aba Início em instantes para novidades!",
                    confidence = AiConfidence.UNKNOWN
                )
            }
        }

        val primary = candidates.first()

        // Generate response using verified catalog items
        val responseText = tryGenerateGeminiRecommendation(
            userPrompt = intent.rawText,
            candidates = candidates,
            cineName = cineName,
            personality = personality
        ) ?: buildDeterministicRecommendation(candidates, cineName)

        return@withContext CineResponse(
            text = responseText,
            recommendedMediaId = primary.tmdbId,
            recommendedMediaType = primary.mediaType,
            recommendedMediaTitle = primary.title,
            recommendedMediaPoster = primary.posterPath,
            confidence = AiConfidence.VERIFIED
        )
    }

    private fun buildDeterministicRecommendation(items: List<MediaEntity>, cineName: String): String {
        val movies = items.filter { it.mediaType.equals("movie", ignoreCase = true) }
        val series = items.filter { it.mediaType.equals("tv", ignoreCase = true) }

        val sb = StringBuilder()
        sb.append("🎬 Olá! Com base no catálogo oficial da RONYCINE, separei excelentes recomendações verificadas para você:\n\n")

        if (movies.isNotEmpty()) {
            sb.append("🍿 FILMES\n")
            movies.forEach { m ->
                val year = if (m.releaseYear.isNotBlank()) " • ${m.releaseYear}" else ""
                val genre = if (m.genres.isNotBlank()) " • ${m.genres.split(",").take(2).joinToString(", ").trim()}" else ""
                val rating = if (m.rating > 0) " — ⭐ ${String.format("%.1f", m.rating)}" else ""
                sb.append("• ${m.title}$rating\n  ${m.durationMinutes} min$year$genre\n")
                if (m.overview.isNotBlank()) {
                    sb.append("  Sinopse: ${m.overview.take(110)}...\n")
                }
                sb.append("\n")
            }
        }

        if (series.isNotEmpty()) {
            sb.append("📺 SÉRIES\n")
            series.forEach { s ->
                val seasons = if (s.seasonsCount > 1) "${s.seasonsCount} temporadas" else "${s.seasonsCount} temporada"
                val rating = if (s.rating > 0) " — ⭐ ${String.format("%.1f", s.rating)}" else ""
                val genre = if (s.genres.isNotBlank()) " • ${s.genres.split(",").take(2).joinToString(", ").trim()}" else ""
                sb.append("• ${s.title}$rating\n  $seasons$genre\n")
                if (s.overview.isNotBlank()) {
                    sb.append("  Sinopse: ${s.overview.take(110)}...\n")
                }
                sb.append("\n")
            }
        }

        sb.append("✨ Toque no card abaixo para assistir diretamente no RONYCINE!")
        return sb.toString()
    }

    // ==========================================
    // 2. MEDIA FACT CHECKER (REAL FACTS ONLY)
    // ==========================================

    suspend fun resolveMediaFact(titleQuery: String): VerifiedMediaFact? = withContext(Dispatchers.IO) {
        val normQuery = normalize(titleQuery)
        if (normQuery.isBlank()) return@withContext null

        // Check cache first
        val cached = tmdbQueryCache[normQuery]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return@withContext cached.data
        }

        // 1. Check local catalog first
        val allLocal = dao.getAllMediaSync()
        val localMatch = allLocal.find { normalize(it.title).contains(normQuery) || normalize(it.originalTitle).contains(normQuery) }

        var verifiedFact: VerifiedMediaFact? = null

        if (localMatch != null) {
            // Local item exists! Enrich with TMDB details if needed
            val details = fetchTmdbDetails(localMatch.tmdbId, localMatch.mediaType)
            verifiedFact = VerifiedMediaFact(
                title = localMatch.title,
                mediaType = localMatch.mediaType,
                tmdbId = localMatch.tmdbId,
                posterPath = localMatch.posterPath,
                rating = localMatch.rating,
                releaseYear = localMatch.releaseYear,
                durationMinutes = if (localMatch.durationMinutes > 0) localMatch.durationMinutes else details?.durationMinutes ?: 120,
                seasonsCount = if (localMatch.seasonsCount > 0) localMatch.seasonsCount else details?.seasonsCount ?: 1,
                episodesCount = if (localMatch.episodesCount > 0) localMatch.episodesCount else details?.episodesCount ?: 1,
                overview = localMatch.overview.ifBlank { details?.overview ?: "" },
                director = localMatch.director.ifBlank { details?.director ?: "" },
                topCastWithCharacters = details?.topCastWithCharacters ?: parseLocalCast(localMatch.cast),
                genres = localMatch.genres,
                isInRonycineCatalog = true,
                confidence = AiConfidence.VERIFIED
            )
        } else {
            // Not in local catalog, search TMDB directly
            try {
                val searchRes = tmdbApi.searchMulti(apiKey = tmdbApiKey, query = titleQuery)
                val topResult = searchRes.results.firstOrNull { it.mediaType == "movie" || it.mediaType == "tv" }
                if (topResult != null) {
                    val mType = topResult.mediaType ?: "movie"
                    val details = fetchTmdbDetails(topResult.id, mType)
                    verifiedFact = VerifiedMediaFact(
                        title = topResult.title ?: topResult.name ?: titleQuery,
                        mediaType = mType,
                        tmdbId = topResult.id,
                        posterPath = topResult.posterPath,
                        rating = topResult.voteAverage ?: 0.0,
                        releaseYear = (topResult.releaseDate ?: topResult.firstAirDate ?: "").take(4),
                        durationMinutes = details?.durationMinutes ?: 120,
                        seasonsCount = details?.seasonsCount ?: 1,
                        episodesCount = details?.episodesCount ?: 1,
                        overview = topResult.overview ?: details?.overview ?: "",
                        director = details?.director ?: "",
                        topCastWithCharacters = details?.topCastWithCharacters ?: emptyList(),
                        genres = details?.genres ?: "",
                        isInRonycineCatalog = false,
                        confidence = AiConfidence.VERIFIED
                    )
                }
            } catch (e: Exception) {
                Log.e("CatalogAIService", "Error searching TMDB for $titleQuery: ${e.message}")
            }
        }

        if (verifiedFact != null) {
            tmdbQueryCache[normQuery] = CacheEntry(verifiedFact, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
        }

        return@withContext verifiedFact
    }

    private suspend fun fetchTmdbDetails(tmdbId: Int, mediaType: String): VerifiedMediaFact? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (mediaType == "tv") {
                val details = tmdbApi.getSeriesDetails(seriesId = tmdbId, apiKey = tmdbApiKey, appendToResponse = "credits,videos")
                val credits = details.credits
                val castList = credits?.cast?.take(6)?.map {
                    (it.name ?: "") to (it.character ?: "")
                }?.filter { it.first.isNotBlank() } ?: emptyList()

                val director = credits?.crew?.find { it.job.equals("Director", ignoreCase = true) || it.job.equals("Executive Producer", ignoreCase = true) }?.name ?: ""

                VerifiedMediaFact(
                    title = details.name ?: "",
                    mediaType = "tv",
                    tmdbId = tmdbId,
                    posterPath = details.posterPath,
                    rating = details.voteAverage ?: 0.0,
                    releaseYear = (details.firstAirDate ?: "").take(4),
                    durationMinutes = details.runtime ?: 45,
                    seasonsCount = details.numberOfSeasons ?: 1,
                    episodesCount = details.numberOfEpisodes ?: 1,
                    overview = details.overview ?: "",
                    director = director,
                    topCastWithCharacters = castList,
                    genres = details.genres?.joinToString(", ") { it.name } ?: "",
                    isInRonycineCatalog = false,
                    confidence = AiConfidence.VERIFIED
                )
            } else {
                val details = tmdbApi.getMovieDetails(movieId = tmdbId, apiKey = tmdbApiKey, appendToResponse = "credits,videos")
                val credits = details.credits
                val castList = credits?.cast?.take(6)?.map {
                    (it.name ?: "") to (it.character ?: "")
                }?.filter { it.first.isNotBlank() } ?: emptyList()

                val director = credits?.crew?.find { it.job.equals("Director", ignoreCase = true) }?.name ?: ""

                VerifiedMediaFact(
                    title = details.title ?: "",
                    mediaType = "movie",
                    tmdbId = tmdbId,
                    posterPath = details.posterPath,
                    rating = details.voteAverage ?: 0.0,
                    releaseYear = (details.releaseDate ?: "").take(4),
                    durationMinutes = details.runtime ?: 120,
                    seasonsCount = 1,
                    episodesCount = 1,
                    overview = details.overview ?: "",
                    director = director,
                    topCastWithCharacters = castList,
                    genres = details.genres?.joinToString(", ") { it.name } ?: "",
                    isInRonycineCatalog = false,
                    confidence = AiConfidence.VERIFIED
                )
            }
        } catch (e: Exception) {
            Log.w("CatalogAIService", "Failed to fetch TMDB details for $tmdbId: ${e.message}")
            null
        }
    }

    private fun parseLocalCast(rawCast: String): List<Pair<String, String>> {
        if (rawCast.isBlank()) return emptyList()
        return rawCast.split(",", ";").mapNotNull {
            val t = it.trim()
            if (t.isNotBlank()) t to "" else null
        }.take(6)
    }

    private suspend fun handleMediaFact(intent: CineIntentResult, cineName: String): CineResponse = withContext(Dispatchers.IO) {
        val targetTitle = intent.targetTitle
        if (targetTitle.isNullOrBlank()) {
            return@withContext CineResponse(
                text = "Qual título de filme ou série você gostaria de consultar? Me informe o nome para eu verificar os dados exatos.",
                confidence = AiConfidence.UNKNOWN
            )
        }

        val fact = resolveMediaFact(targetTitle)
        if (fact == null) {
            return@withContext CineResponse(
                text = "Não encontrei esse título nos dados disponíveis da RONYCINE ou no catálogo. Verifique se o nome está correto e tente novamente.",
                confidence = AiConfidence.UNKNOWN
            )
        }

        val responseText = when (intent.facet) {
            MediaFacet.RATING -> {
                val ratingStr = if (fact.rating > 0) String.format("%.1f", fact.rating) else "Não avaliado"
                "A nota verificada de **${fact.title}** é **$ratingStr ⭐** no TMDB.${if (fact.isInRonycineCatalog) " Está disponível no catálogo RONYCINE!" else ""}"
            }
            MediaFacet.CAST -> {
                if (fact.topCastWithCharacters.isNotEmpty()) {
                    val castFormatted = fact.topCastWithCharacters.joinToString("\n") { (actor, char) ->
                        if (char.isNotBlank()) "• **$actor** como *$char*" else "• **$actor**"
                    }
                    "O elenco verificado de **${fact.title}** inclui:\n\n$castFormatted"
                } else {
                    "Não consegui confirmar a lista detalhada do elenco de **${fact.title}** nos dados disponíveis agora."
                }
            }
            MediaFacet.SEASONS -> {
                if (fact.mediaType == "tv") {
                    val sCount = fact.seasonsCount
                    val eps = if (fact.episodesCount > 0) " com um total de ${fact.episodesCount} episódios" else ""
                    "A série **${fact.title}** tem **$sCount ${if (sCount > 1) "temporadas" else "temporada"}**$eps."
                } else {
                    "**${fact.title}** é um filme (longa-metragem), portanto não é dividido em temporadas."
                }
            }
            MediaFacet.EPISODES -> {
                if (fact.mediaType == "tv") {
                    "A série **${fact.title}** tem **${fact.episodesCount} episódios** distribuídos em ${fact.seasonsCount} temporada(s)."
                } else {
                    "**${fact.title}** é um filme de ${fact.durationMinutes} minutos, não uma série de episódios."
                }
            }
            MediaFacet.DURATION -> {
                if (fact.mediaType == "movie") {
                    val hours = fact.durationMinutes / 60
                    val mins = fact.durationMinutes % 60
                    "A duração oficial de **${fact.title}** é de **${hours}h ${mins}min** (${fact.durationMinutes} minutos)."
                } else {
                    "**${fact.title}** é uma série com ${fact.seasonsCount} temporadas. Cada episódio tem em média ${fact.durationMinutes} minutos."
                }
            }
            MediaFacet.OVERVIEW -> {
                if (fact.overview.isNotBlank()) {
                    "**Sinopse de ${fact.title}:**\n\n${fact.overview}"
                } else {
                    "Não encontrei a sinopse oficial registrada para **${fact.title}** nos dados da RONYCINE."
                }
            }
            MediaFacet.DIRECTOR -> {
                if (fact.director.isNotBlank()) {
                    "A direção de **${fact.title}** é de **${fact.director}**."
                } else {
                    "Não consegui confirmar o diretor responsável por **${fact.title}** nos dados disponíveis."
                }
            }
            MediaFacet.CATALOG_STATUS -> {
                if (fact.isInRonycineCatalog) {
                    "Sim! **${fact.title}** está disponível no catálogo do RONYCINE para assistir agora. Toque no card abaixo para abrir!"
                } else {
                    "O título **${fact.title}** foi localizado no TMDB (${fact.releaseYear}), mas ainda não está cadastrado no catálogo local do RONYCINE."
                }
            }
            MediaFacet.AUDIO_DUBBED -> {
                if (fact.isInRonycineCatalog) {
                    "**${fact.title}** está disponível no RONYCINE com opções de áudio no player. Você pode alternar entre Dublado e Legendado diretamente nos controles de reprodução."
                } else {
                    "Não consegui verificar os idiomas disponíveis pois **${fact.title}** não está no catálogo ativo do RONYCINE."
                }
            }
            else -> {
                "**${fact.title}** (${fact.releaseYear}) — ⭐ ${String.format("%.1f", fact.rating)}\n" +
                        "Gêneros: ${fact.genres}\n" +
                        (if (fact.isInRonycineCatalog) "✅ Disponível no RONYCINE para assistir!\n\n" else "\n") +
                        fact.overview.take(160) + "..."
            }
        }

        return@withContext CineResponse(
            text = responseText,
            recommendedMediaId = if (fact.isInRonycineCatalog) fact.tmdbId else null,
            recommendedMediaType = fact.mediaType,
            recommendedMediaTitle = fact.title,
            recommendedMediaPoster = fact.posterPath,
            confidence = AiConfidence.VERIFIED
        )
    }

    // ==========================================
    // 3. OFFICIAL RONYCINE SUPPORT DESK
    // ==========================================

    private fun handleSupport(category: SupportCategory, userPrompt: String): CineResponse {
        val text = when (category) {
            SupportCategory.PLAYBACK -> """
                🛠️ **Suporte RONYCINE — Problemas de Reprodução**
                
                Se o vídeo estiver com tela preta ou travando, siga estes passos na ordem:
                
                1. **Reinicie o Player**: Feche o player voltando à tela de detalhes e abra o conteúdo novamente.
                2. **Alterne o Áudio**: No menu do player, tente alternar entre **Dublado** e **Legendado** (muitas vezes isso carrega uma fonte alternativa de transmissão).
                3. **Troque o Player**: Vá em **Configurações > Opções de Reprodução** e alterne entre o *Player Nativo (ExoPlayer)* e o *Player Web Alternativo*.
                4. **Limpar Cache**: Se persistir, limpe o cache do aplicativo nas configurações do seu Android.
                
                Se o erro persistir, me informe o nome exato do filme ou episódio para verificarmos o servidor!
            """.trimIndent()

            SupportCategory.AUDIO -> """
                🔊 **Suporte RONYCINE — Problemas de Áudio**
                
                Se você não está ouvindo o áudio do filme ou série:
                
                1. **Botão Mudo**: Toque na tela do player e verifique se o ícone de volume não está silenciado.
                2. **Alternar Idioma**: No topo ou nas opções do player, clique no botão de áudio e selecione a outra faixa disponível (Dublado ou Legendado).
                3. **Volume de Mídia**: Certifique-se de que o volume de mídia (e não apenas o de chamadas) do seu aparelho está no nível adequado.
                4. **Dispositivos Bluetooth**: Se estiver usando fone Bluetooth, desconecte e reconecte para restabelecer a saída estéreo.
            """.trimIndent()

            SupportCategory.PROFILE -> """
                👤 **Suporte RONYCINE — Foto e Perfil**
                
                Para personalizar sua foto e perfil:
                
                1. Abra o menu lateral ou toque no seu avatar no topo da tela.
                2. Acesse **Gerenciar Perfis** ou **Meu Perfil**.
                3. Toque na sua foto atual:
                   • **Catálogo RONYCINE**: Escolha um dos avatares temáticos exclusivos de cinema.
                   • **Sua Foto**: Toque em *"Escolher da Galeria"* para enviar qualquer imagem do seu celular.
                4. Salve as alterações. Ela será sincronizada instantaneamente na comunidade e em todo o aplicativo!
            """.trimIndent()

            SupportCategory.LOGIN -> """
                🔐 **Suporte RONYCINE — Acesso e Senha**
                
                Se você está com dificuldades para entrar na sua conta:
                
                1. **Esqueci Minha Senha**: Na tela de login, clique em *"Esqueci a senha"*, informe seu e-mail cadastrado e verifique a sua caixa de entrada (e pasta de spam) para redefinir.
                2. **E-mail e Senha**: Certifique-se de que não há espaços antes ou depois do e-mail.
                3. **Criar Nova Conta**: Caso ainda não tenha cadastro, use a opção *"Criar Conta"* informando um e-mail válido.
            """.trimIndent()

            SupportCategory.PLAYER_SWITCH -> """
                ⚙️ **Suporte RONYCINE — Troca de Player e Legendas**
                
                O RONYCINE possui mecanismos inteligentes de reprodução:
                
                • **Player Nativo**: Melhor desempenho e suporte a gestos de brilho/volume na tela.
                • **Player Web / MegaEmbed**: Ideal caso o servidor nativo esteja congestionado.
                • **Legendas**: Caso a legenda não apareça, toque no ícone de engrenagem do player e selecione a faixa *"Português (BR)"*.
            """.trimIndent()

            SupportCategory.TV_LIVE -> """
                📺 **Suporte RONYCINE — TV ao Vivo**
                
                Para assistir canais de TV ao vivo com máxima fluidez:
                
                1. Acesse a aba **TV ao Vivo** na barra de navegação inferior.
                2. Como as transmissões de TV são fluxos contínuos (HLS/m3u8 em tempo real), certifique-se de estar conectado a uma rede Wi-Fi estável de pelo menos 10 Mbps.
                3. Se um canal travar, toque em voltar e selecione-o novamente para restabelecer a conexão ao vivo.
            """.trimIndent()

            SupportCategory.MY_LIST -> """
                ⭐ **Suporte RONYCINE — Minha Lista e Histórico**
                
                • **Adicionar aos Favoritos**: Na tela de detalhes de qualquer filme ou série, toque no botão **+ Minha Lista**.
                • **Onde Encontrar**: Seus itens salvos aparecem na seção *"Minha Lista"* na tela inicial e no menu lateral.
                • **Continue Assistindo**: Sempre que você interrompe um vídeo, o RONYCINE grava o minuto exato para você retomar de onde parou.
            """.trimIndent()

            SupportCategory.APP_UPDATE -> """
                📲 **Suporte RONYCINE — Atualizações do Aplicativo**
                
                O RONYCINE conta com atualizador oficial integrado:
                
                1. Vá em **Configurações > Sobre o RONYCINE > Verificar Atualizações**.
                2. Se houver uma nova versão disponível, o aplicativo fará o download direto do APK oficial.
                3. Se o Android solicitar permissão para instalar aplicativos dessa fonte, permita e conclua a instalação normalmente.
            """.trimIndent()

            SupportCategory.GENERAL -> """
                👋 **Central de Ajuda Oficial RONYCINE**
                
                Posso ajudar você com qualquer dúvida sobre o aplicativo:
                • Reprodução de filmes e séries (vídeo preto, áudio, player)
                • Recomendações personalizadas do catálogo
                • Informações de atores, diretores, temporadas e episódios
                • Perfil, avatares e configurações da conta
                
                Como posso te ajudar hoje?
            """.trimIndent()
        }

        return CineResponse(text = text, confidence = AiConfidence.VERIFIED)
    }

    private fun handleGreeting(cineName: String, prompt: String): CineResponse {
        return CineResponse(
            text = "🎬 Olá! Eu sou o **$cineName**, seu assistente oficial do RONYCINE.\n\nPosso te indicar os melhores filmes e séries do catálogo, tirar dúvidas sobre episódios e temporadas, ou te ajudar com qualquer suporte técnico no aplicativo. O que você quer assistir hoje?",
            confidence = AiConfidence.VERIFIED
        )
    }

    // ==========================================
    // 4. GEMINI API ENHANCEMENT (STRICT REAL DATA)
    // ==========================================

    private suspend fun tryGenerateGeminiRecommendation(
        userPrompt: String,
        candidates: List<MediaEntity>,
        cineName: String,
        personality: String
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.ifBlank { BuildConfig.API_KEY_FIREBASE }
        if (apiKey.isBlank()) return@withContext null

        return@withContext try {
            val catalogFormatted = candidates.joinToString("\n") { m ->
                val typeName = if (m.mediaType == "tv") "Série" else "Filme"
                val duration = if (m.mediaType == "movie") " | Duração: ${m.durationMinutes} min" else " | Temporadas: ${m.seasonsCount}"
                "- ID: ${m.tmdbId} | $typeName: ${m.title} (${m.releaseYear}) | Nota: ${m.rating} ⭐ | Gêneros: ${m.genres}$duration | Sinopse: ${m.overview.take(130)}"
            }

            val systemInstruction = """
                Você é $cineName, o assistente oficial de streaming do aplicativo RONYCINE.
                Sua personalidade: $personality.
                Regras obrigatórias:
                1. Responda em português brasileiro com tom profissional, cinéfilo, direto e amigável.
                2. NUNCA invente títulos, notas, anos ou dados que não estejam na lista de mídias verificadas abaixo.
                3. Apresente as recomendações organizadas com marcadores elegantes, informando o título, ano, nota e um breve motivo pelo qual vale a pena assistir.
                4. No final da mensagem, mencione que o usuário pode tocar no card abaixo para assistir direto.
                5. Seja conciso e evite enrolação.
                
                TÍTULOS REAIS E VERIFICADOS DO CATÁLOGO RONYCINE:
                $catalogFormatted
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "O usuário pediu: \"$userPrompt\". Crie uma resposta de recomendação profissional baseando-se estritamente nas mídias listadas.")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4) // low temperature to prioritize precision
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val text = root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                text.trim()
            }
        } catch (e: Exception) {
            Log.w("CatalogAIService", "Gemini call failed, falling back to deterministic: ${e.message}")
            null
        }
    }
}

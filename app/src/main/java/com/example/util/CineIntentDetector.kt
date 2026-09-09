package com.example.util

import java.text.Normalizer

enum class CineIntentType {
    RECOMMENDATION,
    MEDIA_FACT,
    SUPPORT,
    GREETING,
    NONE
}

enum class MediaFacet {
    RATING,
    CAST,
    SEASONS,
    EPISODES,
    DURATION,
    OVERVIEW,
    DIRECTOR,
    TRAILER,
    CATALOG_STATUS,
    AUDIO_DUBBED,
    GENERAL
}

enum class SupportCategory {
    PLAYBACK,      // Vídeo preto, travando, tela preta, buffering
    AUDIO,         // Sem áudio, som mudo, delay
    LOGIN,         // Esqueci senha, criar conta, erro de login
    PROFILE,       // Foto de perfil, avatar, galeria, nome
    PLAYER_SWITCH, // Trocar player ExoPlayer/Web, legenda
    TV_LIVE,       // TV ao vivo, canais
    MY_LIST,       // Minha lista, favoritos, continue assistindo
    APP_UPDATE,    // Atualizar app, versão nova, APK
    GENERAL        // Dúvidas gerais de suporte
}

data class RecommendationCriteria(
    val genre: String? = null,
    val mediaType: String? = null, // "movie", "tv", or null for both
    val minRating: Double? = null,
    val isShort: Boolean = false,
    val isRecent: Boolean = false,
    val similarTo: String? = null,
    val quantity: Int = 3
)

data class CineIntentResult(
    val type: CineIntentType,
    val rawText: String,
    val targetTitle: String? = null,
    val facet: MediaFacet? = null,
    val supportCategory: SupportCategory? = null,
    val criteria: RecommendationCriteria? = null,
    val isExplicitCineCall: Boolean = false
)

object CineIntentDetector {

    private fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    /**
     * Backward-compatible check for recommendation intent
     */
    fun isRecommendationRequest(rawText: String): Boolean {
        val analysis = analyze(rawText)
        return analysis.type == CineIntentType.RECOMMENDATION || analysis.type == CineIntentType.MEDIA_FACT || analysis.type == CineIntentType.SUPPORT || analysis.type == CineIntentType.GREETING
    }

    /**
     * Determines whether CINE should automatically intervene and reply to the message
     */
    fun shouldCineRespond(rawText: String): Boolean {
        val analysis = analyze(rawText)
        return analysis.type != CineIntentType.NONE
    }

    /**
     * Full semantic analysis of the user message
     */
    fun analyze(rawText: String): CineIntentResult {
        val text = normalize(rawText)
        if (text.isBlank()) return CineIntentResult(CineIntentType.NONE, rawText)

        val isExplicit = text.contains("cine") || text.contains("@cine") ||
                text.contains("ronycineia") || text.contains("@ia") || text.contains("assistente")

        // 1. Pure greetings/chatter check without explicit call
        val pureGreetings = setOf(
            "bom dia", "boa tarde", "boa noite", "ola", "oi", "oii", "oiii",
            "e ai", "eai", "fala galera", "fala ai", "alguem on", "alguem online",
            "vamos assistir", "quem ta ai", "quem esta aqui", "gostei desse filme",
            "kkk", "kkkk", "kkkkk", "hahaha", "hahahaha", "rsrs", "show", "legal",
            "top", "muito bom", "adorei", "valeu", "tmj", "obrigado", "obrigada"
        )
        if (pureGreetings.contains(text) && !isExplicit) {
            return CineIntentResult(CineIntentType.NONE, rawText)
        }

        // If explicit call with just greeting
        if (isExplicit && (text == "cine" || text == "@cine" || pureGreetings.any { text.replace("cine", "").trim().contains(it) })) {
            return CineIntentResult(
                type = CineIntentType.GREETING,
                rawText = rawText,
                isExplicitCineCall = true
            )
        }

        // 2. SUPPORT INTENT CHECK
        val supportCategory = detectSupportCategory(text)
        if (supportCategory != null) {
            return CineIntentResult(
                type = CineIntentType.SUPPORT,
                rawText = rawText,
                supportCategory = supportCategory,
                isExplicitCineCall = isExplicit
            )
        }

        // 3. MEDIA FACT QUERY CHECK (Specific questions about movies/series)
        val mediaFacet = detectMediaFacet(text)
        if (mediaFacet != null) {
            val title = extractTitleFromQuery(text, mediaFacet)
            return CineIntentResult(
                type = CineIntentType.MEDIA_FACT,
                rawText = rawText,
                targetTitle = title,
                facet = mediaFacet,
                isExplicitCineCall = isExplicit
            )
        }

        // 4. RECOMMENDATION INTENT CHECK
        val isRec = isRecommendationSemantics(text, isExplicit)
        if (isRec) {
            val criteria = extractRecommendationCriteria(text)
            return CineIntentResult(
                type = CineIntentType.RECOMMENDATION,
                rawText = rawText,
                criteria = criteria,
                isExplicitCineCall = isExplicit
            )
        }

        // 5. If user explicitly called Cine with any query
        if (isExplicit) {
            // General recommendation or greeting fallback
            return CineIntentResult(
                type = CineIntentType.RECOMMENDATION,
                rawText = rawText,
                criteria = extractRecommendationCriteria(text),
                isExplicitCineCall = true
            )
        }

        return CineIntentResult(CineIntentType.NONE, rawText)
    }

    private fun detectSupportCategory(text: String): SupportCategory? {
        return when {
            text.contains("video preto") || text.contains("tela preta") || text.contains("tela escura") ||
                    text.contains("video travando") || text.contains("nao reproduz") || text.contains("nao roda") ||
                    text.contains("nao carrega o filme") || text.contains("carregamento infinito") ||
                    text.contains("erro no player") || text.contains("erro ao reproduzir") || text.contains("travou no meio") ->
                SupportCategory.PLAYBACK

            text.contains("sem audio") || text.contains("sem som") || text.contains("audio mudo") ||
                    text.contains("audio atrasado") || text.contains("som chiando") || text.contains("audio baixo") ->
                SupportCategory.AUDIO

            text.contains("foto de perfil") || text.contains("mudar foto") || text.contains("mudar avatar") ||
                    text.contains("foto da galeria") || text.contains("trocar foto") || text.contains("trocar avatar") ||
                    text.contains("nome de usuario") || text.contains("criar perfil") ->
                SupportCategory.PROFILE

            text.contains("esqueci a senha") || text.contains("esqueci minha senha") || text.contains("recuperar senha") ||
                    text.contains("trocar senha") || text.contains("erro no login") || text.contains("nao consigo entrar") ||
                    text.contains("como criar conta") ->
                SupportCategory.LOGIN

            text.contains("trocar player") || text.contains("player nativo") || text.contains("player web") ||
                    text.contains("como colocar legenda") || text.contains("legenda sumiu") || text.contains("como colocar dublado") ->
                SupportCategory.PLAYER_SWITCH

            text.contains("tv ao vivo") || text.contains("canais") || text.contains("canal nao abre") ||
                    text.contains("ao vivo travando") ->
                SupportCategory.TV_LIVE

            text.contains("minha lista") || text.contains("adicionar a lista") || text.contains("continue assistindo") ||
                    text.contains("onde fica meu historico") ->
                SupportCategory.MY_LIST

            text.contains("atualizar o app") || text.contains("atualizar o ronycine") || text.contains("nova versao") ||
                    text.contains("baixar atualizacao") || text.contains("apk novo") ->
                SupportCategory.APP_UPDATE

            else -> null
        }
    }

    private fun detectMediaFacet(text: String): MediaFacet? {
        return when {
            text.contains("qual a nota") || text.contains("qual e a nota") || text.contains("nota de") ||
                    text.contains("nota do") || text.contains("avaliacao de") || text.contains("quantas estrelas") ->
                MediaFacet.RATING

            text.contains("quem esta no elenco") || text.contains("quem faz o papel") || text.contains("quem interpreta") ||
                    text.contains("atores de") || text.contains("atriz de") || text.contains("elenco de") || text.contains("ator principal") ->
                MediaFacet.CAST

            text.contains("quantas temporadas") || text.contains("quantas temp") || text.contains("numero de temporadas") ->
                MediaFacet.SEASONS

            text.contains("quantos episodios") || text.contains("quantos eps") || text.contains("qual o primeiro episodio") ||
                    text.contains("primeiro ep") || text.contains("ultimo episodio") ->
                MediaFacet.EPISODES

            text.contains("qual a duracao") || text.contains("quantos minutos") || text.contains("quantas horas") ||
                    text.contains("tempo de duracao") ->
                MediaFacet.DURATION

            text.contains("sobre o que e") || text.contains("sobre o que fala") || text.contains("sinopse de") ||
                    text.contains("resumo de") || text.contains("qual a historia") ->
                MediaFacet.OVERVIEW

            text.contains("quem dirigiu") || text.contains("diretor de") || text.contains("diretora de") ||
                    text.contains("quem e o diretor") ->
                MediaFacet.DIRECTOR

            text.contains("tem trailer") || text.contains("onde vejo o trailer") || text.contains("trailer de") ->
                MediaFacet.TRAILER

            text.contains("tem no catalogo") || text.contains("esta no catalogo") || text.contains("tem no ronycine") ||
                    text.contains("esta disponivel") || text.contains("quando chega") ->
                MediaFacet.CATALOG_STATUS

            text.contains("tem dublado") || text.contains("e dublado") || text.contains("tem legendado") ||
                    text.contains("tem em portugues") ->
                MediaFacet.AUDIO_DUBBED

            else -> null
        }
    }

    private fun extractTitleFromQuery(text: String, facet: MediaFacet): String? {
        // Strip out facet prefix to find the target title
        val patterns = listOf(
            "qual a nota de ", "qual e a nota de ", "qual a nota do ", "qual e a nota do ", "nota de ", "nota do ",
            "quem interpreta em ", "quem interpreta o ", "quem interpreta ", "quem faz o papel de ", "quem faz o papel em ",
            "quem esta no elenco de ", "elenco de ", "elenco do ",
            "quantas temporadas tem ", "quantas temporadas de ", "quantas temp tem ",
            "quantos episodios tem ", "quantos episodios de ", "quantos eps tem ",
            "qual a duracao de ", "qual a duracao do ", "duracao de ", "duracao do ",
            "sobre o que e o filme ", "sobre o que e a serie ", "sobre o que e ", "sinopse de ", "sinopse do ",
            "quem dirigiu ", "diretor de ", "diretor do ",
            "tem trailer de ", "tem trailer do ", "trailer de ", "trailer do ",
            "tem no catalogo o filme ", "tem no catalogo a serie ", "tem no catalogo ", "esta no catalogo ",
            "tem dublado o ", "tem dublado a ", "tem dublado "
        )

        for (pattern in patterns) {
            val idx = text.indexOf(pattern)
            if (idx >= 0) {
                val candidate = text.substring(idx + pattern.length).trim('?', '.', '!', ' ')
                if (candidate.isNotBlank()) return candidate
            }
        }

        // Fallback: look for quotes "title"
        val quoteMatch = Regex("\"([^\"]+)\"").find(text)
        if (quoteMatch != null) {
            return quoteMatch.groups[1]?.value?.trim()
        }

        return null
    }

    private fun isRecommendationSemantics(text: String, isExplicit: Boolean): Boolean {
        if (isExplicit) return true

        val recKeywords = listOf(
            "recomenda", "recomende", "recomendem", "recomendacao", "recomendacoes",
            "indica", "indique", "indiquem", "indicacao", "indicacoes",
            "sugere", "sugira", "sugiram", "sugestao", "sugestoes",
            "dica de filme", "dica de serie", "dicas de filme", "dicas de serie",
            "dica pra assistir", "dica para assistir", "dicas para assistir"
        )

        val watchingPatterns = listOf(
            "o que assistir", "oque assistir", "q assistir", "oq assistir",
            "pra assistir", "para assistir", "assistir hoje", "assistir agora",
            "assistir a noite", "quero assistir", "pensando em assistir",
            "procurando filme", "procurando serie", "nao sei o que assistir",
            "sem ideia do que assistir", "duvida do que assistir", "ajuda a escolher",
            "escolher um filme", "escolher uma serie", "vale a pena assistir", "vale assistir"
        )

        val queryPatterns = listOf(
            "qual filme", "quais filmes", "qual serie", "quais series",
            "qual dorama", "quais doramas", "qual anime", "quais animes",
            "filme bom", "filmes bons", "serie boa", "series boas",
            "filme top", "serie top", "filmes novos", "series novas",
            "filmes em alta", "series em alta", "filmes parecidos",
            "parecido com", "parecida com", "na mesma pegada de"
        )

        val genrePatterns = listOf(
            "filme de terror", "filme de acao", "filme de comedia", "filme de romance",
            "filme de ficcao", "filme de suspense", "filme de drama", "filme de animacao",
            "serie de terror", "serie de suspense", "serie de acao", "serie de comedia",
            "serie de ficcao", "serie de drama"
        )

        val availabilityPatterns = listOf(
            "tem algum filme", "tem alguma serie", "o que tem de bom", "oque tem de bom",
            "o que tem para assistir", "oq tem de bom"
        )

        return recKeywords.any { text.contains(it) } ||
                watchingPatterns.any { text.contains(it) } ||
                queryPatterns.any { text.contains(it) } ||
                genrePatterns.any { text.contains(it) } ||
                availabilityPatterns.any { text.contains(it) }
    }

    private fun extractRecommendationCriteria(text: String): RecommendationCriteria {
        val mediaType = when {
            text.contains("filme") || text.contains("filmes") || text.contains("longa") -> "movie"
            text.contains("serie") || text.contains("series") || text.contains("temporada") -> "tv"
            text.contains("anime") || text.contains("animes") -> "tv"
            text.contains("dorama") || text.contains("doramas") -> "tv"
            else -> null
        }

        val genre = when {
            text.contains("acao") || text.contains("tiro") || text.contains("luta") -> "Ação"
            text.contains("terror") || text.contains("medo") || text.contains("assustador") -> "Terror"
            text.contains("comedia") || text.contains("engracado") || text.contains("rir") -> "Comédia"
            text.contains("drama") || text.contains("triste") || text.contains("chorar") -> "Drama"
            text.contains("ficcao") || text.contains("sci-fi") || text.contains("espaco") -> "Ficção científica"
            text.contains("romance") || text.contains("romantico") || text.contains("amor") -> "Romance"
            text.contains("animacao") || text.contains("desenho") || text.contains("anime") -> "Animação"
            text.contains("suspense") || text.contains("misterio") || text.contains("investigacao") -> "Suspense"
            text.contains("aventura") -> "Aventura"
            text.contains("fantasia") || text.contains("magia") -> "Fantasia"
            text.contains("familia") || text.contains("familiar") || text.contains("crianca") -> "Família"
            else -> null
        }

        val minRating = if (text.contains("nota alta") || text.contains("muito bem avaliado") ||
            text.contains("melhores") || text.contains("top") || text.contains("obra prima")
        ) {
            7.8
        } else null

        val isShort = text.contains("curto") || text.contains("rapido") || text.contains("pouco tempo") ||
                text.contains("menos de 2 horas") || text.contains("menos de duas horas")

        val isRecent = text.contains("recente") || text.contains("recentes") || text.contains("novo") ||
                text.contains("novos") || text.contains("lancamento") || text.contains("ultimos anos") ||
                text.contains("2024") || text.contains("2025") || text.contains("2026")

        val similarTo = when {
            text.contains("parecido com ") -> text.substringAfter("parecido com ").substringBefore(" ").trim()
            text.contains("parecida com ") -> text.substringAfter("parecida com ").substringBefore(" ").trim()
            text.contains("estilo ") -> text.substringAfter("estilo ").substringBefore(" ").trim()
            text.contains("mesma pegada de ") -> text.substringAfter("mesma pegada de ").substringBefore(" ").trim()
            else -> null
        }

        val quantity = when {
            text.contains("5 ") || text.contains("cinco ") || text.contains("5 coisas") || text.contains("5 filmes") || text.contains("5 series") -> 5
            text.contains("4 ") || text.contains("quatro ") -> 4
            text.contains("2 ") || text.contains("dois ") || text.contains("duas ") -> 2
            text.contains("1 ") || text.contains("um filme") || text.contains("uma serie") -> 1
            else -> 3
        }

        return RecommendationCriteria(
            genre = genre,
            mediaType = mediaType,
            minRating = minRating,
            isShort = isShort,
            isRecent = isRecent,
            similarTo = similarTo,
            quantity = quantity
        )
    }
}

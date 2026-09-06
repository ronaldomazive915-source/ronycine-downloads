package com.example.data.repository

import android.util.Log
import com.example.data.remote.ApiChannel
import com.example.data.remote.ApiEvent
import com.example.data.remote.ApiEventEmbed
import com.example.data.remote.ApiGuideItem
import com.example.data.remote.ReiDosEmbedsApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveTvApiStats(
    val isOnline: Boolean = true,
    val lastSync: String = "Sincronizado recentemente",
    val channelsCount: Int = 0,
    val eventsCount: Int = 0,
    val categoriesCount: Int = 0,
    val guideStatus: String = "Ativo (EPG 24h)"
)

class LiveTvRepository(
    private val apiService: ReiDosEmbedsApiService = ReiDosEmbedsApiService()
) {
    private val TAG = "LiveTvRepository"

    private val _cachedChannels = MutableStateFlow<List<ApiChannel>>(getFallbackChannels())
    val cachedChannels: StateFlow<List<ApiChannel>> = _cachedChannels.asStateFlow()

    private val _cachedEvents = MutableStateFlow<List<ApiEvent>>(getFallbackEvents())
    val cachedEvents: StateFlow<List<ApiEvent>> = _cachedEvents.asStateFlow()

    private val _cachedChannelCategories = MutableStateFlow<List<String>>(listOf("Todos", "Abertos", "Esportes", "Filmes & Séries", "Notícias", "Documentários", "Infantil", "Variedades"))
    val cachedChannelCategories: StateFlow<List<String>> = _cachedChannelCategories.asStateFlow()

    private val _cachedEventCategories = MutableStateFlow<List<String>>(listOf("Todos", "Futebol", "Basquete", "Lutas / MMA", "Vôlei", "Automobilismo", "Tênis"))
    val cachedEventCategories: StateFlow<List<String>> = _cachedEventCategories.asStateFlow()

    private val _cachedGuide = MutableStateFlow<List<ApiGuideItem>>(getFallbackGuide())
    val cachedGuide: StateFlow<List<ApiGuideItem>> = _cachedGuide.asStateFlow()

    private val _apiStats = MutableStateFlow(
        LiveTvApiStats(
            isOnline = true,
            lastSync = getCurrentTimestamp(),
            channelsCount = _cachedChannels.value.size,
            eventsCount = _cachedEvents.value.size,
            categoriesCount = _cachedChannelCategories.value.size + _cachedEventCategories.value.size,
            guideStatus = "Ativo (Atualização diária)"
        )
    )
    val apiStats: StateFlow<LiveTvApiStats> = _apiStats.asStateFlow()

    private var lastGuideFetchTime = 0L
    private val GUIDE_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun refreshChannels(category: String? = null): List<ApiChannel> {
        return try {
            val remote = apiService.getChannels(category)
            if (remote.isNotEmpty()) {
                _cachedChannels.value = remote
                updateStats(isOnline = true, channels = remote.size)
                remote
            } else {
                _cachedChannels.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing channels: ${e.message}")
            _cachedChannels.value
        }
    }

    suspend fun getChannelById(channelId: String): ApiChannel? {
        val inMemory = _cachedChannels.value.find { it.id.equals(channelId, ignoreCase = true) || it.slug.equals(channelId, ignoreCase = true) }
        if (inMemory != null) return inMemory

        return try {
            val remote = apiService.getChannelById(channelId)
            remote ?: _cachedChannels.value.firstOrNull()
        } catch (e: Exception) {
            _cachedChannels.value.firstOrNull()
        }
    }

    suspend fun refreshChannelCategories(): List<String> {
        return try {
            val remote = apiService.getChannelCategories()
            if (remote.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(remote.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedChannelCategories.value = list
                updateStats(isOnline = true, categories = list.size + _cachedEventCategories.value.size)
                list
            } else {
                _cachedChannelCategories.value
            }
        } catch (e: Exception) {
            _cachedChannelCategories.value
        }
    }

    suspend fun refreshEvents(category: String? = null, status: String? = null): List<ApiEvent> {
        return try {
            val remote = apiService.getEvents(category, status)
            if (remote.isNotEmpty()) {
                _cachedEvents.value = remote
                updateStats(isOnline = true, events = remote.size)
                remote
            } else {
                _cachedEvents.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing events: ${e.message}")
            _cachedEvents.value
        }
    }

    suspend fun getEventById(eventId: String): ApiEvent? {
        val inMemory = _cachedEvents.value.find { it.id.equals(eventId, ignoreCase = true) || it.slug.equals(eventId, ignoreCase = true) }
        if (inMemory != null) return inMemory

        return try {
            val remote = apiService.getEventById(eventId)
            remote ?: _cachedEvents.value.firstOrNull()
        } catch (e: Exception) {
            _cachedEvents.value.firstOrNull()
        }
    }

    suspend fun refreshEventCategories(): List<String> {
        return try {
            val remote = apiService.getEventCategories()
            if (remote.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(remote.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedEventCategories.value = list
                updateStats(isOnline = true, categories = _cachedChannelCategories.value.size + list.size)
                list
            } else {
                _cachedEventCategories.value
            }
        } catch (e: Exception) {
            _cachedEventCategories.value
        }
    }

    suspend fun search(query: String): Pair<List<ApiChannel>, List<ApiEvent>> {
        if (query.isBlank()) {
            return Pair(_cachedChannels.value, _cachedEvents.value)
        }

        val localChannels = _cachedChannels.value.filter {
            it.name.contains(query, ignoreCase = true) ||
                    (it.category?.contains(query, ignoreCase = true) == true) ||
                    (it.currentProgram?.contains(query, ignoreCase = true) == true)
        }
        val localEvents = _cachedEvents.value.filter {
            it.title.contains(query, ignoreCase = true) ||
                    (it.category?.contains(query, ignoreCase = true) == true) ||
                    (it.competition?.contains(query, ignoreCase = true) == true) ||
                    (it.time1Name?.contains(query, ignoreCase = true) == true) ||
                    (it.time2Name?.contains(query, ignoreCase = true) == true)
        }

        return try {
            val (remoteChannels, remoteEvents) = apiService.search(query)
            val combinedChannels = if (remoteChannels.isNotEmpty()) remoteChannels else localChannels
            val combinedEvents = if (remoteEvents.isNotEmpty()) remoteEvents else localEvents
            Pair(combinedChannels, combinedEvents)
        } catch (e: Exception) {
            Pair(localChannels, localEvents)
        }
    }

    suspend fun getGuide(forceRefresh: Boolean = false): List<ApiGuideItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _cachedGuide.value.isNotEmpty() && (now - lastGuideFetchTime < GUIDE_CACHE_DURATION_MS)) {
            return _cachedGuide.value
        }

        return try {
            val remote = apiService.getGuide()
            if (remote.isNotEmpty()) {
                _cachedGuide.value = remote
                lastGuideFetchTime = now
                updateStats(isOnline = true)
                remote
            } else {
                _cachedGuide.value
            }
        } catch (e: Exception) {
            _cachedGuide.value
        }
    }

    suspend fun syncAll(): LiveTvApiStats {
        return try {
            val channels = apiService.getChannels()
            if (channels.isNotEmpty()) _cachedChannels.value = channels

            val events = apiService.getEvents()
            if (events.isNotEmpty()) _cachedEvents.value = events

            val chCats = apiService.getChannelCategories()
            if (chCats.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(chCats.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedChannelCategories.value = list
            }

            val evCats = apiService.getEventCategories()
            if (evCats.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(evCats.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedEventCategories.value = list
            }

            val guide = apiService.getGuide()
            if (guide.isNotEmpty()) {
                _cachedGuide.value = guide
                lastGuideFetchTime = System.currentTimeMillis()
            }

            updateStats(
                isOnline = true,
                channels = _cachedChannels.value.size,
                events = _cachedEvents.value.size,
                categories = _cachedChannelCategories.value.size + _cachedEventCategories.value.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
            updateStats(isOnline = false)
        }
    }

    private fun updateStats(
        isOnline: Boolean = true,
        channels: Int? = null,
        events: Int? = null,
        categories: Int? = null
    ): LiveTvApiStats {
        val updated = _apiStats.value.copy(
            isOnline = isOnline,
            lastSync = getCurrentTimestamp(),
            channelsCount = channels ?: _cachedChannels.value.size,
            eventsCount = events ?: _cachedEvents.value.size,
            categoriesCount = categories ?: (_cachedChannelCategories.value.size + _cachedEventCategories.value.size),
            guideStatus = "Sincronizado (EPG 24h)"
        )
        _apiStats.value = updated
        return updated
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
    }

    companion object {
        @Volatile
        private var instance: LiveTvRepository? = null

        fun getInstance(): LiveTvRepository {
            return instance ?: synchronized(this) {
                instance ?: LiveTvRepository().also { instance = it }
            }
        }

        fun getFallbackChannels(): List<ApiChannel> {
            return listOf(
                ApiChannel(
                    id = "globo",
                    name = "Globo SP",
                    category = "Abertos",
                    logo = "https://logodownload.org/wp-content/uploads/2013/12/rede-globo-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/globo",
                    currentProgram = "Jornal Nacional",
                    nextProgram = "Novela das Nove",
                    description = "Rede Globo de Televisão com programação ao vivo em alta definição."
                ),
                ApiChannel(
                    id = "sbt",
                    name = "SBT",
                    category = "Abertos",
                    logo = "https://logodownload.org/wp-content/uploads/2014/04/sbt-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/sbt",
                    currentProgram = "Programa do Ratinho",
                    nextProgram = "The Noite",
                    description = "Sistema Brasileiro de Televisão com entretenimento e programas de auditório."
                ),
                ApiChannel(
                    id = "record",
                    name = "Record TV",
                    category = "Abertos",
                    logo = "https://logodownload.org/wp-content/uploads/2014/05/record-tv-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/record",
                    currentProgram = "Jornal da Record",
                    nextProgram = "Série Bíblica",
                    description = "Record TV com jornalismo, séries e variedades."
                ),
                ApiChannel(
                    id = "band",
                    name = "Band TV",
                    category = "Abertos",
                    logo = "https://logodownload.org/wp-content/uploads/2014/05/band-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/band",
                    currentProgram = "Jornal da Band",
                    nextProgram = "Perrengue na Band",
                    description = "Rede Bandeirantes com jornalismo e cobertura esportiva."
                ),
                ApiChannel(
                    id = "sportv",
                    name = "SporTV",
                    category = "Esportes",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/sportv-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/sportv",
                    currentProgram = "Troca de Passes",
                    nextProgram = "SporTV News",
                    description = "O canal campeão com as melhores coberturas esportivas nacionais e internacionais."
                ),
                ApiChannel(
                    id = "sportv2",
                    name = "SporTV 2",
                    category = "Esportes",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/sportv-2-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/sportv2",
                    currentProgram = "Vôlei Superliga",
                    nextProgram = "Futebol Internacional",
                    description = "Transmissões ao vivo e análises aprofundadas dos esportes olímpicos e futebol."
                ),
                ApiChannel(
                    id = "espn",
                    name = "ESPN Brasil",
                    category = "Esportes",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/espn-brasil-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/espn",
                    currentProgram = "SportsCenter",
                    nextProgram = "Linha de Passe",
                    description = "Informação com credibilidade e grandes torneios mundiais de futebol e basquete."
                ),
                ApiChannel(
                    id = "espn4",
                    name = "ESPN 4",
                    category = "Esportes",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/espn-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/espn4",
                    currentProgram = "Premier League Ao Vivo",
                    nextProgram = "NBA Action",
                    description = "Futebol europeu, NBA, NFL e grandes emoções."
                ),
                ApiChannel(
                    id = "premiere",
                    name = "Premiere Clubes",
                    category = "Esportes",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/premiere-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/premiere",
                    currentProgram = "Brasileirão Série A",
                    nextProgram = "Aquecimento Brasileirão",
                    description = "O melhor do futebol brasileiro ao vivo."
                ),
                ApiChannel(
                    id = "tnt",
                    name = "TNT",
                    category = "Filmes & Séries",
                    logo = "https://logodownload.org/wp-content/uploads/2016/10/tnt-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/tnt",
                    currentProgram = "Champions League / Filme",
                    nextProgram = "Cine TNT",
                    description = "Filmes de grande sucesso e Champions League."
                ),
                ApiChannel(
                    id = "space",
                    name = "Space",
                    category = "Filmes & Séries",
                    logo = "https://logodownload.org/wp-content/uploads/2016/10/space-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/space",
                    currentProgram = "Filme de Ação",
                    nextProgram = "Terror no Space",
                    description = "Muita ação, suspense, ficção científica e emoção sem limites."
                ),
                ApiChannel(
                    id = "megapix",
                    name = "Megapix",
                    category = "Filmes & Séries",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/megapix-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/megapix",
                    currentProgram = "Sessão Megapix",
                    nextProgram = "Sucessos de Bilheteria",
                    description = "Os maiores sucessos do cinema dublados em português."
                ),
                ApiChannel(
                    id = "telecine_premium",
                    name = "Telecine Premium",
                    category = "Filmes & Séries",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/telecine-premium-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/telecine-premium",
                    currentProgram = "Estreia da Semana",
                    nextProgram = "Sessão Superestreia",
                    description = "As maiores e mais recentes produções do cinema mundial."
                ),
                ApiChannel(
                    id = "telecine_action",
                    name = "Telecine Action",
                    category = "Filmes & Séries",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/telecine-action-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/telecine-action",
                    currentProgram = "Adrenalina Máxima",
                    nextProgram = "Noite de Ação",
                    description = "O canal exclusivo para quem adora filmes de ação e adrenalina."
                ),
                ApiChannel(
                    id = "cnn_brasil",
                    name = "CNN Brasil",
                    category = "Notícias",
                    logo = "https://logodownload.org/wp-content/uploads/2020/03/cnn-brasil-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/cnn-brasil",
                    currentProgram = "CNN 360°",
                    nextProgram = "CNN Prime Time",
                    description = "Notícias 24 horas com análises políticas e econômicas em tempo real."
                ),
                ApiChannel(
                    id = "globonews",
                    name = "GloboNews",
                    category = "Notícias",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/globonews-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/globonews",
                    currentProgram = "Edição das 18h",
                    nextProgram = "Jornal das Dez",
                    description = "Jornalismo que nunca desliga, com furos e análises exclusivas."
                ),
                ApiChannel(
                    id = "discovery_channel",
                    name = "Discovery Channel",
                    category = "Documentários",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/discovery-channel-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/discovery",
                    currentProgram = "Largados e Pelados",
                    nextProgram = "Febre do Ouro",
                    description = "Ciência, natureza, tecnologia e sobrevivência."
                ),
                ApiChannel(
                    id = "cartoon_network",
                    name = "Cartoon Network",
                    category = "Infantil",
                    logo = "https://logodownload.org/wp-content/uploads/2017/04/cartoon-network-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/cartoon",
                    currentProgram = "O Incrível Mundo de Gumball",
                    nextProgram = "Jovens Titãs em Ação",
                    description = "Os melhores desenhos e animações para toda a família."
                )
            )
        }

        fun getFallbackEvents(): List<ApiEvent> {
            return listOf(
                ApiEvent(
                    id = "palmeiras-x-flamengo",
                    title = "Palmeiras x Flamengo",
                    time1 = "Palmeiras",
                    time2 = "Flamengo",
                    time1Name = "Palmeiras",
                    time2Name = "Flamengo",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2017/02/palmeiras-logo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2016/09/flamengo-logo-escudo-0.png",
                    startTime = "Hoje às 21:30",
                    status = "live",
                    category = "Futebol",
                    competition = "Brasileirão Série A",
                    sportKey = "futebol",
                    visualModel = "match",
                    description = "Grande clássico decisivo do futebol brasileiro válido pelo Campeonato Brasileiro.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Premiere 1 HD", quality = "1080p", embedUrl = "https://reidosembeds.online/embed/premiere", name = "Opção 1 (Premiere)"),
                        ApiEventEmbed(provider = "Globo SP HD", quality = "720p", embedUrl = "https://reidosembeds.online/embed/globo", name = "Opção 2 (Globo)")
                    )
                ),
                ApiEvent(
                    id = "real-madrid-x-barcelona",
                    title = "Real Madrid x Barcelona",
                    time1 = "Real Madrid",
                    time2 = "Barcelona",
                    time1Name = "Real Madrid",
                    time2Name = "Barcelona",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2016/03/real-madrid-logo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2016/03/barcelona-logo-0.png",
                    startTime = "Ao Vivo",
                    status = "live",
                    category = "Futebol",
                    competition = "La Liga",
                    sportKey = "futebol",
                    visualModel = "match",
                    description = "El Clásico mundial direto do Santiago Bernabéu.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "ESPN 4 HD", quality = "1080p", embedUrl = "https://reidosembeds.online/embed/espn4", name = "ESPN 4")
                    )
                ),
                ApiEvent(
                    id = "lakers-x-celtics",
                    title = "LA Lakers x Boston Celtics",
                    time1 = "Los Angeles Lakers",
                    time2 = "Boston Celtics",
                    time1Name = "Lakers",
                    time2Name = "Celtics",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2018/02/los-angeles-lakers-logo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2018/02/boston-celtics-logo-0.png",
                    startTime = "Hoje às 22:30",
                    status = "live",
                    category = "Basquete",
                    competition = "NBA Temporada Regular",
                    sportKey = "basquete",
                    visualModel = "match",
                    description = "O maior clássico da história da NBA com transmissão ao vivo.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "ESPN Principal", quality = "HD", embedUrl = "https://reidosembeds.online/embed/espn", name = "ESPN")
                    )
                ),
                ApiEvent(
                    id = "ufc-300-main-card",
                    title = "UFC Fight Night: Disputa de Cinturão",
                    startTime = "Sábado às 23:00",
                    status = "upcoming",
                    category = "Lutas / MMA",
                    competition = "UFC",
                    sportKey = "mma",
                    visualModel = "event",
                    poster = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=600",
                    description = "Card principal do UFC com disputa de cinturão e lutas de tirar o fôlego.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Combate HD", quality = "HD", embedUrl = "https://reidosembeds.online/embed/combate", name = "Canal Combate")
                    )
                ),
                ApiEvent(
                    id = "sao-paulo-x-corinthians",
                    title = "São Paulo x Corinthians",
                    time1 = "São Paulo",
                    time2 = "Corinthians",
                    time1Name = "São Paulo",
                    time2Name = "Corinthians",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2016/09/sao-paulo-logo-escudo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2016/09/corinthians-logo-escudo-0.png",
                    startTime = "Amanhã às 16:00",
                    status = "upcoming",
                    category = "Futebol",
                    competition = "Paulistão / Brasileirão",
                    sportKey = "futebol",
                    visualModel = "match",
                    description = "Clássico Majestoso no Morumbi com casa cheia.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Premiere 2 HD", quality = "HD", embedUrl = "https://reidosembeds.online/embed/premiere2", name = "Premiere")
                    )
                ),
                ApiEvent(
                    id = "f1-gp-monaco",
                    title = "Fórmula 1: Grande Prêmio de Mônaco",
                    startTime = "Domingo às 10:00",
                    status = "upcoming",
                    category = "Automobilismo",
                    competition = "Fórmula 1",
                    sportKey = "f1",
                    visualModel = "event",
                    poster = "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=600",
                    description = "Corrida clássica nas ruas estreitas de Monte Carlo.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Band TV HD", quality = "1080p", embedUrl = "https://reidosembeds.online/embed/band", name = "Band TV")
                    )
                )
            )
        }

        fun getFallbackGuide(): List<ApiGuideItem> {
            return listOf(
                ApiGuideItem(channelId = "globo", channelName = "Globo SP", time = "19:40", title = "Jornal Nacional", description = "As principais notícias do Brasil e do mundo.", category = "Jornalismo"),
                ApiGuideItem(channelId = "globo", channelName = "Globo SP", time = "20:30", title = "Novela das Nove", description = "Capítulo inédito com reviravoltas.", category = "Novela"),
                ApiGuideItem(channelId = "globo", channelName = "Globo SP", time = "21:30", title = "Futebol ao Vivo", description = "Campeonato Brasileiro Série A.", category = "Esportes"),
                ApiGuideItem(channelId = "sportv", channelName = "SporTV", time = "20:00", title = "Troca de Passes", description = "Debates e resenha dos jogos da rodada.", category = "Esportes"),
                ApiGuideItem(channelId = "sportv", channelName = "SporTV", time = "22:00", title = "SporTV News", description = "Resumo dos acontecimentos esportivos da noite.", category = "Jornalismo"),
                ApiGuideItem(channelId = "espn", channelName = "ESPN Brasil", time = "20:00", title = "SportsCenter", description = "Notícias, gols e melhores momentos com convidados especiais.", category = "Esportes"),
                ApiGuideItem(channelId = "sbt", channelName = "SBT", time = "19:45", title = "SBT Brasil", description = "Jornalismo com dinamismo e credibilidade.", category = "Jornalismo"),
                ApiGuideItem(channelId = "sbt", channelName = "SBT", time = "21:00", title = "Programa do Ratinho", description = "Atrações, humor e música ao vivo.", category = "Auditório")
            )
        }
    }
}

package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.MediaEntity
import com.example.data.remote.FirebaseService
import com.example.data.remote.TmdbMediaDto
import com.example.data.remote.TmdbNetwork
import com.example.util.MediaClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AnimeDoramaTab(val title: String, val category: String) {
    ALL("🎌 & 🇰🇷 TODOS", "all"),
    ANIMES("🎌 ANIMES", MediaClassifier.CATEGORY_ANIME),
    DORAMAS("🇰🇷 DORAMAS", MediaClassifier.CATEGORY_DORAMA)
}

enum class MediaSortFilter(val label: String) {
    POPULARES("Mais populares"),
    MELHORES_AVALIADOS("Mais bem avaliados"),
    NOVOS("Novos"),
    EM_ALTA("Em alta")
}

class AnimesDoramasViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).playFilmeDao()
    private val firebaseService = try { FirebaseService.getInstance(application) } catch (_: Exception) { null }
    private val apiService = TmdbNetwork.apiService

    private val apiKey: String
        get() = BuildConfig.TMDB_API_KEY.ifEmpty { "d60f5b5a778fe74b5cf4a371f008725b" }

    // Tab state (Default ALL to show both dedicated sections on the screen)
    private val _selectedTab = MutableStateFlow(AnimeDoramaTab.ALL)
    val selectedTab: StateFlow<AnimeDoramaTab> = _selectedTab.asStateFlow()

    // Sort / Filter state
    private val _sortFilter = MutableStateFlow(MediaSortFilter.POPULARES)
    val sortFilter: StateFlow<MediaSortFilter> = _sortFilter.asStateFlow()

    // Search query within the screen
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Loading & Error States
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Local room streams (Strict separation: Animes ONLY, Doramas ONLY)
    val localAnimes: StateFlow<List<MediaEntity>> = dao.getAllMedia()
        .combine(_searchQuery) { list: List<MediaEntity>, query: String ->
            val animes = list.filter {
                (it.mediaCategory == MediaClassifier.CATEGORY_ANIME ||
                        (it.mediaCategory.isBlank() && MediaClassifier.isAnime(it))) &&
                        it.mediaCategory != MediaClassifier.CATEGORY_DORAMA
            }
            if (query.isBlank()) animes
            else animes.filter { it.title.contains(query, ignoreCase = true) || it.originalTitle.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localDoramas: StateFlow<List<MediaEntity>> = dao.getAllMedia()
        .combine(_searchQuery) { list: List<MediaEntity>, query: String ->
            val doramas = list.filter {
                (it.mediaCategory == MediaClassifier.CATEGORY_DORAMA ||
                        (it.mediaCategory.isBlank() && MediaClassifier.isDorama(it))) &&
                        it.mediaCategory != MediaClassifier.CATEGORY_ANIME
            }
            if (query.isBlank()) doramas
            else doramas.filter { it.title.contains(query, ignoreCase = true) || it.originalTitle.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            checkAndSeedFromTmdb()
        }
    }

    fun selectTab(tab: AnimeDoramaTab) {
        _selectedTab.value = tab
    }

    fun setSortFilter(filter: MediaSortFilter) {
        _sortFilter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private suspend fun checkAndSeedFromTmdb() {
        withContext(Dispatchers.IO) {
            // First check Firestore with specific mediaCategory queries
            try {
                firebaseService?.fetchAnimesFromFirestore()
                firebaseService?.fetchDoramasFromFirestore()
            } catch (e: Exception) {
                Log.w("AnimesDoramasVM", "Firestore check error: ${e.message}")
            }

            val animeCount = dao.getMediaCountByCategory(MediaClassifier.CATEGORY_ANIME)
            val doramaCount = dao.getMediaCountByCategory(MediaClassifier.CATEGORY_DORAMA)

            if (animeCount == 0) {
                fetchAndStoreAnimesFromTmdb()
            }
            if (doramaCount == 0) {
                fetchAndStoreDoramasFromTmdb()
            }
        }
    }

    fun refreshCurrentTab() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Sincroniza Firestore com filtros dedicados
                firebaseService?.fetchAnimesFromFirestore()
                firebaseService?.fetchDoramasFromFirestore()

                when (_selectedTab.value) {
                    AnimeDoramaTab.ALL -> {
                        fetchAndStoreAnimesFromTmdb()
                        fetchAndStoreDoramasFromTmdb()
                    }
                    AnimeDoramaTab.ANIMES -> fetchAndStoreAnimesFromTmdb()
                    AnimeDoramaTab.DORAMAS -> fetchAndStoreDoramasFromTmdb()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.value = "Erro ao buscar do TMDB: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun fetchAndStoreAnimesFromTmdb() = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            val response = apiService.discoverTv(
                apiKey = apiKey,
                language = "pt-BR",
                withGenres = "16",
                withOriginalLanguage = "ja",
                includeAdult = false,
                page = 1,
                sortBy = "popularity.desc"
            )

            val entities = response.results.map { dto ->
                dtoToEntity(dto, MediaClassifier.CATEGORY_ANIME, "ja", "JP")
            }

            dao.insertMediaList(entities)
            firebaseService?.upsertMediaListInCloud(entities)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("AnimesDoramasVM", "Erro ao carregar animes: ${e.message}")
            _errorMessage.value = "Falha ao conectar com o TMDB"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchAndStoreDoramasFromTmdb() = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            val response = apiService.discoverTv(
                apiKey = apiKey,
                language = "pt-BR",
                withGenres = "18",
                withOriginCountry = "KR",
                withOriginalLanguage = "ko",
                includeAdult = false,
                page = 1,
                sortBy = "popularity.desc"
            )

            val entities = response.results.map { dto ->
                dtoToEntity(dto, MediaClassifier.CATEGORY_DORAMA, "ko", "KR")
            }

            dao.insertMediaList(entities)
            firebaseService?.upsertMediaListInCloud(entities)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("AnimesDoramasVM", "Erro ao carregar doramas: ${e.message}")
            _errorMessage.value = "Falha ao conectar com o TMDB"
        } finally {
            _isLoading.value = false
        }
    }

    private fun dtoToEntity(
        dto: TmdbMediaDto,
        category: String,
        fallbackLang: String,
        fallbackCountry: String
    ): MediaEntity {
        val title = dto.name ?: dto.title ?: "Sem título"
        val originalTitle = dto.originalName ?: dto.originalTitle ?: title
        val poster = dto.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val backdrop = dto.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
        val year = (dto.firstAirDate ?: dto.releaseDate ?: "2024").take(4)
        val rating = Math.round((dto.voteAverage ?: 8.0) * 10) / 10.0
        val lang = dto.originalLanguage ?: fallbackLang
        val country = dto.originCountry?.joinToString(",") ?: fallbackCountry

        return MediaEntity(
            tmdbId = dto.id,
            title = title,
            originalTitle = originalTitle,
            mediaType = "tv",
            mediaCategory = category,
            originalLanguage = lang,
            originCountry = country,
            posterPath = poster,
            backdropPath = backdrop,
            overview = if (dto.overview.isNullOrBlank()) "Uma emocionante história exclusiva no RONYCINE." else dto.overview,
            releaseYear = year,
            rating = rating,
            genres = if (category == MediaClassifier.CATEGORY_ANIME) "Animação, Ação, Aventura" else "Drama, Romance",
            durationMinutes = 45,
            cast = "Elenco Principal",
            director = "Produção TMDB",
            seasonsCount = 1,
            episodesCount = 12,
            trailerKey = null,
            isHeroFeatured = false
        )
    }
}

package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ApiChannel
import com.example.data.remote.ApiEvent
import com.example.data.remote.ApiGuideItem
import com.example.data.repository.LiveTvApiStats
import com.example.data.repository.LiveTvRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveTvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LiveTvRepository.getInstance()

    val channels: StateFlow<List<ApiChannel>> = repository.cachedChannels
    val events: StateFlow<List<ApiEvent>> = repository.cachedEvents
    val channelCategories: StateFlow<List<String>> = repository.cachedChannelCategories
    val eventCategories: StateFlow<List<String>> = repository.cachedEventCategories
    val guide: StateFlow<List<ApiGuideItem>> = repository.cachedGuide
    val apiStats: StateFlow<LiveTvApiStats> = repository.apiStats

    private val prefs = getApplication<Application>().getSharedPreferences("ronycine_live_favorites", android.content.Context.MODE_PRIVATE)
    private val _favoriteChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteChannelIds: StateFlow<Set<String>> = _favoriteChannelIds.asStateFlow()

    // Popularity logic for TOP 10 HOJE
    private val _topChannels = MutableStateFlow<List<ApiChannel>>(emptyList())
    val topChannels: StateFlow<List<ApiChannel>> = _topChannels.asStateFlow()

    // UI Navigation & Filters
    private val _selectedTab = MutableStateFlow(0) // 0: AO VIVO, 1: CANAIS, 2: EVENTOS, 3: GUIA
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedChannelCategory = MutableStateFlow("Todos")
    val selectedChannelCategory: StateFlow<String> = _selectedChannelCategory.asStateFlow()

    private val _selectedEventCategory = MutableStateFlow("Todos")
    val selectedEventCategory: StateFlow<String> = _selectedEventCategory.asStateFlow()

    private val _eventStatusFilter = MutableStateFlow("all") // "all", "live", "upcoming"
    val eventStatusFilter: StateFlow<String> = _eventStatusFilter.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResultsChannels = MutableStateFlow<List<ApiChannel>>(emptyList())
    val searchResultsChannels: StateFlow<List<ApiChannel>> = _searchResultsChannels.asStateFlow()

    private val _searchResultsEvents = MutableStateFlow<List<ApiEvent>>(emptyList())
    val searchResultsEvents: StateFlow<List<ApiEvent>> = _searchResultsEvents.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // Refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadFavorites()
        loadInitialData()
        observeChannelsForTop10()
    }

    private fun loadFavorites() {
        val saved = prefs.getStringSet("favorite_channels", emptySet()) ?: emptySet()
        _favoriteChannelIds.value = saved
    }

    fun toggleFavorite(channelId: String) {
        val current = _favoriteChannelIds.value.toMutableSet()
        if (current.contains(channelId)) {
            current.remove(channelId)
        } else {
            current.add(channelId)
        }
        _favoriteChannelIds.value = current
        prefs.edit().putStringSet("favorite_channels", current).apply()
    }

    private fun observeChannelsForTop10() {
        viewModelScope.launch {
            channels.collect { allChannels ->
                if (allChannels.isNotEmpty()) {
                    // For now, take top 10 as first 10, or deterministic based on name length/id
                    // In a real app, this would come from a specific popularity API or local analytics
                    _topChannels.value = allChannels.shuffled(java.util.Random(123)).take(10)
                }
            }
        }
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshChannelCategories()
                repository.refreshEventCategories()
                repository.refreshChannels()
                repository.refreshEvents()
                repository.getGuide()
            } catch (e: Exception) {
                _errorMessage.value = "Modo offline ativado: exibindo canais salvos."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun selectChannelCategory(category: String) {
        _selectedChannelCategory.value = category
        viewModelScope.launch {
            val filter = if (category.equals("Todos", ignoreCase = true)) null else category
            repository.refreshChannels(filter)
        }
    }

    fun selectEventCategory(category: String) {
        _selectedEventCategory.value = category
        viewModelScope.launch {
            val catFilter = if (category.equals("Todos", ignoreCase = true)) null else category
            val statusFilter = if (_eventStatusFilter.value == "all") null else _eventStatusFilter.value
            repository.refreshEvents(catFilter, statusFilter)
        }
    }

    fun setEventStatusFilter(status: String) {
        _eventStatusFilter.value = status
        viewModelScope.launch {
            val catFilter = if (_selectedEventCategory.value.equals("Todos", ignoreCase = true)) null else _selectedEventCategory.value
            val statusFilter = if (status == "all") null else status
            repository.refreshEvents(catFilter, statusFilter)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResultsChannels.value = emptyList()
            _searchResultsEvents.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(350)
            try {
                val (chList, evList) = repository.search(query.trim())
                _searchResultsChannels.value = chList
                _searchResultsEvents.value = evList
            } catch (e: Exception) {
                _searchResultsChannels.value = emptyList()
                _searchResultsEvents.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                repository.syncAll()
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao atualizar transmissões."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun syncFromAdmin(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val stats = repository.syncAll()
                onComplete(true, "✓ Sincronização concluída: ${stats.channelsCount} canais, ${stats.eventsCount} eventos e ${stats.categoriesCount} categorias.")
            } catch (e: Exception) {
                onComplete(false, "Erro ao conectar à API reidosembeds.online: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}

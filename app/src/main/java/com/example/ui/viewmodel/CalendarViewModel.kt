package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CalendarDateRange
import com.example.data.remote.CalendarFilter
import com.example.data.remote.CalendarItem
import com.example.data.remote.CalendarMediaType
import com.example.data.remote.FirebaseService
import com.example.data.repository.CalendarRepository
import com.example.data.repository.CalendarUiState
import com.example.util.ContentAccessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CalendarCounts(
    val total: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val animes: Int = 0,
    val doramas: Int = 0
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalendarRepository.getInstance(application)
    private val firebaseService = try { FirebaseService.getInstance(application) } catch (_: Exception) { null }

    private val activeProfile = firebaseService?.activeProfile ?: MutableStateFlow(null)

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CalendarFilter.TODOS)
    val selectedFilter: StateFlow<CalendarFilter> = _selectedFilter.asStateFlow()

    private val _selectedDateRange = MutableStateFlow(CalendarDateRange.TODOS)
    val selectedDateRange: StateFlow<CalendarDateRange> = _selectedDateRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _allItems = MutableStateFlow<List<CalendarItem>>(emptyList())

    // Itens seguros para o perfil atual (filtra se o perfil for Modo Infantil)
    val safeItems: StateFlow<List<CalendarItem>> = combine(_allItems, activeProfile) { items, profile ->
        ContentAccessManager.filterCalendarItemsForProfile(profile, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val counts: StateFlow<CalendarCounts> = safeItems.map { list ->
        CalendarCounts(
            total = list.size,
            movies = list.count { it.mediaType == CalendarMediaType.MOVIE },
            series = list.count { it.mediaType == CalendarMediaType.SERIES },
            animes = list.count { it.mediaType == CalendarMediaType.ANIME },
            doramas = list.count { it.mediaType == CalendarMediaType.DORAMA }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, CalendarCounts())

    val filteredGroupedItems: StateFlow<Map<String, List<CalendarItem>>> = combine(
        safeItems,
        _selectedFilter,
        _selectedDateRange,
        _searchQuery
    ) { items, filter, dateRange, query ->
        var result = items

        // 1. Filtro por Categoria
        result = when (filter) {
            CalendarFilter.TODOS -> result
            CalendarFilter.FILMES -> result.filter { it.mediaType == CalendarMediaType.MOVIE }
            CalendarFilter.SERIES -> result.filter { it.mediaType == CalendarMediaType.SERIES }
            CalendarFilter.ANIMES -> result.filter { it.mediaType == CalendarMediaType.ANIME }
            CalendarFilter.DORAMAS -> result.filter { it.mediaType == CalendarMediaType.DORAMA }
        }

        // 2. Filtro por Intervalo de Datas
        if (dateRange != CalendarDateRange.TODOS) {
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val endOfWeekCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
            val endOfNextWeekCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 14) }

            val todayStr = sdfInput.format(todayCal.time)
            val tomorrowStr = sdfInput.format(tomorrowCal.time)
            val endOfWeekStr = sdfInput.format(endOfWeekCal.time)
            val endOfNextWeekStr = sdfInput.format(endOfNextWeekCal.time)

            result = when (dateRange) {
                CalendarDateRange.TODOS -> result
                CalendarDateRange.HOJE -> result.filter { it.airDate == todayStr }
                CalendarDateRange.AMANHA -> result.filter { it.airDate == tomorrowStr }
                CalendarDateRange.ESTA_SEMANA -> result.filter { it.airDate in todayStr..endOfWeekStr }
                CalendarDateRange.PROXIMA_SEMANA -> result.filter { it.airDate > endOfWeekStr && it.airDate <= endOfNextWeekStr }
                CalendarDateRange.PROXIMO_MES -> result.filter { it.airDate > endOfNextWeekStr }
            }
        }

        // 3. Filtro por Pesquisa
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { item ->
                item.title.lowercase().contains(q) ||
                (item.episodeTitle?.lowercase()?.contains(q) == true) ||
                (item.formattedEpisode?.lowercase()?.contains(q) == true) ||
                item.airDate.contains(q)
            }
        }

        repository.groupItemsByDate(result)
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        loadData(forceRefresh = false)
        viewModelScope.launch {
            safeItems.collect { items ->
                if (_allItems.value.isNotEmpty()) {
                    if (items.isEmpty()) {
                        _uiState.value = CalendarUiState.Empty
                    } else {
                        val grouped = repository.groupItemsByDate(items)
                        _uiState.value = CalendarUiState.Success(items, grouped)
                    }
                }
            }
        }
    }


    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _isRefreshing.value = true
            } else if (_allItems.value.isEmpty()) {
                _uiState.value = CalendarUiState.Loading
            }

            val result = repository.fetchCalendar(forceRefresh = forceRefresh)
            _isRefreshing.value = false

            result.onSuccess { items ->
                _allItems.value = items
                if (items.isEmpty()) {
                    _uiState.value = CalendarUiState.Empty
                } else {
                    val grouped = repository.groupItemsByDate(items)
                    _uiState.value = CalendarUiState.Success(items, grouped)
                }
            }.onFailure { error ->
                if (_allItems.value.isEmpty()) {
                    _uiState.value = CalendarUiState.Error(error.message ?: "Não foi possível carregar o calendário.")
                }
            }
        }
    }

    fun setFilter(filter: CalendarFilter) {
        _selectedFilter.value = filter
    }

    fun setDateRange(range: CalendarDateRange) {
        _selectedDateRange.value = range
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}

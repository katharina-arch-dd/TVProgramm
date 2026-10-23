package kat.android.tvprogramm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kat.android.tvprogramm.data.model.TvCategory
import kat.android.tvprogramm.data.model.TvChannel
import kat.android.tvprogramm.data.model.TvShow
import kat.android.tvprogramm.data.repository.ApiStatus
import kat.android.tvprogramm.data.repository.TvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class TvUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedCategory: TvCategory = TvCategory.ALL,
    val searchQuery: String = "",
    val selectedChannelId: String? = "ard",
    val selectedShowForDetail: TvShow? = null,
    val customApiUrl: String = "https://raw.githubusercontent.com/",
    val isRefreshing: Boolean = false
)

class TvViewModel(
    val repository: TvRepository = TvRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    val channels: StateFlow<List<TvChannel>> = repository.channels
    val favoriteIds: StateFlow<Set<String>> = repository.favoriteIds
    val apiStatus: StateFlow<ApiStatus> = repository.apiStatus

    val filteredShows: StateFlow<List<TvShow>> = combine(
        repository.shows,
        _uiState
    ) { shows, state ->
        val query = state.searchQuery.trim().lowercase()
        val category = state.selectedCategory

        shows.filter { show ->
            val matchesCategory = category == TvCategory.ALL || show.category == category
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                show.title.lowercase().contains(query) ||
                (show.subtitle?.lowercase()?.contains(query) == true) ||
                show.channelName.lowercase().contains(query) ||
                show.description.lowercase().contains(query)
            }
            matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveShows: StateFlow<List<TvShow>> = combine(
        filteredShows,
        _uiState
    ) { shows, _ ->
        val now = LocalDateTime.now()
        shows.filter { it.isLive(now) }
            .sortedBy { getChannelOrder(it.channelId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showsByChannel: StateFlow<Map<String, List<TvShow>>> = combine(
        filteredShows,
        _uiState
    ) { shows, _ ->
        shows.groupBy { it.channelId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val favoriteShows: StateFlow<List<TvShow>> = combine(
        repository.shows,
        favoriteIds
    ) { shows, favs ->
        shows.filter { favs.contains(it.id) }.sortedBy { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        repository.loadProgramForDate(date)
    }

    fun selectCategory(category: TvCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectChannel(channelId: String) {
        _uiState.value = _uiState.value.copy(selectedChannelId = channelId)
    }

    fun openShowDetails(show: TvShow) {
        _uiState.value = _uiState.value.copy(selectedShowForDetail = show)
    }

    fun closeShowDetails() {
        _uiState.value = _uiState.value.copy(selectedShowForDetail = null)
    }

    fun toggleFavorite(showId: String) {
        repository.toggleFavorite(showId)
    }

    fun updateCustomApiUrl(url: String) {
        _uiState.value = _uiState.value.copy(customApiUrl = url)
    }

    fun fetchProgramFromApi() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.fetchFromApi(
                customUrl = _uiState.value.customApiUrl,
                date = _uiState.value.selectedDate
            )
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    private fun getChannelOrder(channelId: String): Int {
        return channels.value.find { it.id == channelId }?.channelNumber ?: 99
    }
}

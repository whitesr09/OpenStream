package com.ivor.openstream.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.repository.HiddenTitlesRepository
import com.ivor.openstream.data.repository.ProfileRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.ivor.openstream.domain.model.AnimeCatalog
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RailStyle {
    /** Big rank numerals beside posters, for a top-ten list. */
    RANKED,

    /** Wide backdrop cards, for what is airing now. */
    LANDSCAPE,

    POSTER
}

data class HomeRail(
    val key: String,
    val title: String,
    val style: RailStyle,
    val items: List<AnimeDto>
)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val hero: List<AnimeDto>,
        val rails: List<HomeRail>,
        val isRefreshing: Boolean = false
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val hiddenTitlesRepository: HiddenTitlesRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    /** The feed as loaded; [uiState] is this minus the titles the user hid. */
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = combine(_uiState, hiddenTitlesRepository.hiddenKeys) { state, hidden ->
        if (state is HomeUiState.Success && hidden.isNotEmpty()) state.without(hidden) else state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    val continueWatching: StateFlow<List<WatchProgress>> = watchProgressRepository.continueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // First load, and again whenever the feed has to change: switching to or from a kids profile.
        viewModelScope.launch {
            profileRepository.activeProfile
                .map { it?.isKids }
                .distinctUntilChanged()
                .collect { kids -> if (kids != null) loadData() }
        }
    }

    fun hideTitle(anime: AnimeDto) {
        viewModelScope.launch { hiddenTitlesRepository.hide(anime.homeMediaType, anime.id, anime.name) }
    }

    fun unhideTitle(anime: AnimeDto) {
        viewModelScope.launch { hiddenTitlesRepository.unhide(anime.homeMediaType, anime.id) }
    }

    fun removeFromContinueWatching(item: WatchProgress) {
        viewModelScope.launch { watchProgressRepository.dismiss(item.mediaType, item.tmdbId) }
    }

    /** Pull-to-refresh keeps the current feed on screen while the new one loads. */
    fun refresh() {
        val current = _uiState.value
        if (current is HomeUiState.Success) _uiState.value = current.copy(isRefreshing = true)
        loadData(showLoading = current !is HomeUiState.Success, forceRefresh = true)
    }

    fun loadData(showLoading: Boolean = true, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _uiState.value = HomeUiState.Loading
            val primary = coroutineScope {
                listOf(AnimeCatalog.PERSONAL_LIBRARY, AnimeCatalog.TRENDING, AnimeCatalog.POPULAR_MOVIES, AnimeCatalog.POPULAR_SERIES)
                    .associateWith { catalog -> async { repository.getCatalog(catalog, forceRefresh).getOrDefault(emptyList()) } }
                    .mapValues { it.value.await() }
            }
            if (primary.values.all { it.isEmpty() }) {
                _uiState.value = HomeUiState.Error("Couldn't reach the catalog")
                return@launch
            }
            fun buildRails(catalogs: Map<AnimeCatalog, List<AnimeDto>>): List<HomeRail> {
                val watching = continueWatching.value
                val moviePreference = watching.count { it.mediaType == "movie" } >= watching.count { it.mediaType == "tv" }
                val firstType = if (moviePreference) AnimeCatalog.POPULAR_MOVIES else AnimeCatalog.POPULAR_SERIES
                val secondType = if (moviePreference) AnimeCatalog.POPULAR_SERIES else AnimeCatalog.POPULAR_MOVIES
                val preferredLabel = if (moviePreference) "movies" else "series"
                val trending = catalogs[AnimeCatalog.TRENDING].orEmpty()
                return listOf(
                    HomeRail("personal-library", "My library", RailStyle.POSTER, catalogs[AnimeCatalog.PERSONAL_LIBRARY].orEmpty()),
                    HomeRail("trending", "Trending now", RailStyle.RANKED, trending.take(10)),
                    HomeRail("preferred", "Because you watch $preferredLabel", RailStyle.POSTER, catalogs[firstType].orEmpty()),
                    HomeRail("continue", "Continue exploring", RailStyle.LANDSCAPE, catalogs[secondType].orEmpty().filter { it.backdropPath != null }),
                    HomeRail("new-episodes", "New episodes this week", RailStyle.LANDSCAPE, catalogs[AnimeCatalog.NEW_EPISODES].orEmpty().filter { it.backdropPath != null }),
                    HomeRail("anime", "Trending anime", RailStyle.POSTER, catalogs[AnimeCatalog.TRENDING_ANIME].orEmpty()),
                    HomeRail("top-rated", "Critically acclaimed", RailStyle.POSTER, catalogs[AnimeCatalog.TOP_RATED_MOVIES].orEmpty()),
                    HomeRail("anime-movies", "Anime movies", RailStyle.POSTER, catalogs[AnimeCatalog.ANIME_MOVIES].orEmpty())
                ).filter { it.items.isNotEmpty() }
            }
            _uiState.value = HomeUiState.Success(
                hero = primary[AnimeCatalog.TRENDING].orEmpty().filter { it.posterPath != null }.take(8),
                rails = buildRails(primary)
            )
            val secondary = coroutineScope {
                listOf(AnimeCatalog.NEW_EPISODES, AnimeCatalog.TRENDING_ANIME, AnimeCatalog.TOP_RATED_MOVIES, AnimeCatalog.ANIME_MOVIES)
                    .associateWith { catalog -> async { repository.getCatalog(catalog, forceRefresh).getOrDefault(emptyList()) } }
                    .mapValues { it.value.await() }
            }
            val all = primary + secondary
            val current = _uiState.value
            if (current is HomeUiState.Success) _uiState.value = current.copy(
                hero = all[AnimeCatalog.TRENDING].orEmpty().filter { it.posterPath != null }.take(8),
                rails = buildRails(all)
            )
        }
    }
}

private val AnimeDto.homeMediaType: String get() = if (isMovie) "movie" else "tv"

private fun HomeUiState.Success.without(hidden: Set<String>): HomeUiState.Success {
    fun List<AnimeDto>.visible() = filterNot { HiddenTitlesRepository.key(it.homeMediaType, it.id) in hidden }
    return copy(
        hero = hero.visible(),
        rails = rails.map { it.copy(items = it.items.visible()) }.filter { it.items.isNotEmpty() }
    )
}

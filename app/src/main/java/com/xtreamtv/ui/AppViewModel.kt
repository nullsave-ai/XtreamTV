package com.xtreamtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xtreamtv.AppContainer
import com.xtreamtv.core.UiState
import com.xtreamtv.core.toUiError
import com.xtreamtv.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeData(
    val channels: List<LiveChannel>,
    val latestMovies: List<Movie>,
    val topMovies: List<Movie>,
    val latestSeries: List<Series>,
    val movieCats: List<Pair<Category, Int>>,
    val seriesCats: List<Pair<Category, Int>>,
) {
    companion object { val Empty = HomeData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) }
}

class AppViewModel(private val c: AppContainer) : ViewModel() {
    val repo = c.repo
    val accounts = c.accounts.accounts
    val activeId = c.accounts.activeId
    val settings = c.settings.flow
    val favorites = c.user.favorites
    val watched = c.user.watched
    val continueWatching = c.user.continueWatching
    val offline = c.repo.offline

    val live = MutableStateFlow<UiState<Catalog<LiveChannel>>>(UiState.Loading)
    val movies = MutableStateFlow<UiState<Catalog<Movie>>>(UiState.Loading)
    val series = MutableStateFlow<UiState<Catalog<Series>>>(UiState.Loading)

    val home: StateFlow<HomeData> = combine(live, movies, series) { l, m, s ->
        val lc = (l as? UiState.Success)?.data
        val mc = (m as? UiState.Success)?.data
        val sc = (s as? UiState.Success)?.data
        HomeData(
            channels = lc?.items?.take(40).orEmpty(),
            latestMovies = mc?.items?.sortedByDescending { it.added }?.take(30).orEmpty(),
            topMovies = mc?.items?.filter { (it.rating ?: 0.0) >= 3.5 }?.sortedByDescending { it.rating }?.take(30).orEmpty(),
            latestSeries = sc?.items?.sortedByDescending { it.lastModified }?.take(30).orEmpty(),
            movieCats = mc?.let { m -> m.nonEmptyCategories.map { it to m.count(it.id) }.sortedByDescending { it.second }.take(14) }.orEmpty(),
            seriesCats = sc?.let { m -> m.nonEmptyCategories.map { it to m.count(it.id) }.sortedByDescending { it.second }.take(14) }.orEmpty(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HomeData.Empty)

    private var liveJob: Job? = null
    private var movieJob: Job? = null
    private var seriesJob: Job? = null

    init {
        viewModelScope.launch {
            combine(accounts, activeId) { list, id -> list.firstOrNull { it.id == id } }
                .distinctUntilChanged()
                .collect { acc ->
                    repo.setAccount(acc)
                    c.user.load(acc?.id)
                    if (acc != null) loadAll(false) else {
                        val e = UiState.Error("لا يوجد حساب. أضف حسابًا من صفحة الحسابات.")
                        live.value = e; movies.value = e; series.value = e
                    }
                }
        }
    }

    private fun <T> load(target: MutableStateFlow<UiState<T>>, old: Job?, block: suspend () -> T): Job {
        old?.cancel()
        target.value = UiState.Loading
        return viewModelScope.launch {
            target.value = try { UiState.Success(block()) } catch (e: CancellationException) { throw e } catch (e: Throwable) { e.toUiError() }
        }
    }

    fun loadLive(force: Boolean) { liveJob = load(live, liveJob) { repo.liveCatalog(force) } }
    fun loadMovies(force: Boolean) { movieJob = load(movies, movieJob) { repo.movieCatalog(force) } }
    fun loadSeries(force: Boolean) { seriesJob = load(series, seriesJob) { repo.seriesCatalog(force) } }
    private fun loadAll(force: Boolean) { loadLive(force); loadMovies(force); loadSeries(force) }
    fun refresh() { if (activeId.value != null) loadAll(true) }
    fun clearCache() { repo.clearCache(); if (activeId.value != null) loadAll(false) }

    fun memory(key: String): ScreenMemory = c.memory.getOrPut(key) { ScreenMemory() }

    // User data
    fun toggleFavorite(key: String) = c.user.toggleFavorite(key)
    fun saveProgress(item: ContinueItem) = c.user.saveProgress(item)
    fun removeContinue(key: String) = c.user.removeContinue(key)
    fun markWatched(epId: String) = c.user.markWatched(epId)
    fun lastLiveId(): Int? = c.user.lastLive()
    fun setLastLive(id: Int) = c.user.setLastLive(id)
    fun updateSettings(f: (AppSettings) -> AppSettings) = c.settings.update(f)

    // Accounts
    /** Authenticates then saves. Returns an error message, or null on success. */
    suspend fun saveAccount(draft: Account, makeActive: Boolean): String? = try {
        repo.authenticate(draft)
        c.accounts.upsert(draft)
        if (makeActive) c.accounts.setActive(draft.id)
        null
    } catch (e: CancellationException) { throw e } catch (e: Throwable) { e.toUiError().message }

    suspend fun checkAccount(a: Account): Result<AccountInfo> = try {
        Result.success(repo.authenticate(a))
    } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }

    fun switchAccount(id: String) = c.accounts.setActive(id)
    fun deleteAccount(id: String) = c.accounts.delete(id)
}

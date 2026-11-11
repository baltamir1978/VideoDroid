package es.altamirano.linvideo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.altamirano.linvideo.LinVideoApp
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.search.FederatedResults
import es.altamirano.linvideo.search.Search
import es.altamirano.linvideo.search.SearchCategory
import es.altamirano.linvideo.search.SearchResult
import es.altamirano.linvideo.search.SearchScope
import es.altamirano.linvideo.search.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que se ofrece para elegir dónde buscar, en el orden de LinVideo. */
val SCOPES: List<SearchScope> = buildList {
    add(SearchScope(source = SearchSource.YOUTUBE))
    add(SearchScope.everywhere())
    SearchCategory.entries.forEach { add(SearchScope(category = it)) }
    SearchSource.entries.filter { it != SearchSource.YOUTUBE }.forEach { add(SearchScope(source = it)) }
}

data class SearchState(
    val scope: SearchScope = SCOPES[0],
    val query: String = "",
    /** La consulta de los resultados que se ven (puede diferir de lo escrito). */
    val searched: String = "",
    val results: List<SearchResult> = emptyList(),
    val loading: Boolean = false,
    val remaining: Int = 0,
    val failures: List<Pair<SearchSource, String>> = emptyList(),
    val page: Int = 1,
    val canLoadMore: Boolean = false,
)

class SearchViewModel : ViewModel() {
    private val hub = LinVideoApp.instance.hub
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null
    private val shown = mutableSetOf<String>()

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun setScope(scope: SearchScope) {
        _state.update { it.copy(scope = scope) }
        if (_state.value.searched.isNotEmpty()) run()
    }

    fun run() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        shown.clear()
        _state.update { it.copy(searched = query, results = emptyList(), failures = emptyList(), page = 1, canLoadMore = false) }
        fetch()
    }

    fun loadMore() {
        if (_state.value.loading) return
        _state.update { it.copy(page = it.page + 1) }
        fetch()
    }

    private fun fetch() {
        job?.cancel()
        val s = _state.value
        val (query, scope, page) = Triple(s.searched, s.scope, s.page)
        _state.update { it.copy(loading = true, remaining = scope.sources.size) }
        job = viewModelScope.launch {
            if (!scope.isMulti) {
                val source = scope.sources[0]
                val found = withContext(Dispatchers.IO) {
                    try {
                        FederatedResults(results = Search.search(query, source, 24, page))
                    } catch (e: Exception) {
                        FederatedResults(failures = listOf(source to Search.describe(e)))
                    }
                }
                arrived(found, 0, final = true)
            } else {
                Search.searchMany(query, scope.sources, 10, page) { found, remaining ->
                    viewModelScope.launch { arrived(found, remaining, remaining == 0) }
                }
            }
        }
    }

    private fun arrived(found: FederatedResults, remaining: Int, final: Boolean) {
        var added = 0
        val fresh = found.results.filter { shown.add(it.pageUrl) }.also { added = it.size }
        _state.update {
            val results = it.results + fresh
            it.copy(
                results = results,
                remaining = remaining,
                failures = found.failures,
                loading = !final,
                canLoadMore = if (final) results.isNotEmpty() && (added > 0 || it.page == 1) else it.canLoadMore,
            )
        }
    }

    fun download(result: SearchResult, kind: MediaKind) {
        hub.add(result.pageUrl, kind, title = result.title)
    }
}

package es.altamirano.linvideo.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/** Lo que devuelve una búsqueda en varias webs. */
data class FederatedResults(
    val results: List<SearchResult> = emptyList(),
    /** Fuentes que fallaron, con su motivo, para avisar sin bloquear el resto. */
    val failures: List<Pair<SearchSource, String>> = emptyList(),
)

/**
 * El buscador (`search.py`). Las fuentes que leen HTML «a mano» se romperán cuando la web cambie
 * su maquetación; cada una tiene su función de lectura, separada de la red, para probarla con una
 * página guardada.
 */
object Search {
    fun search(query: String, source: SearchSource, limit: Int = 24, page: Int = 1): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val p = maxOf(1, page)
        val found = when (source) {
            SearchSource.YOUTUBE -> YtdlpSearch.search("ytsearch", q, limit, p)
            SearchSource.SOUNDCLOUD -> YtdlpSearch.search("scsearch", q, limit, p)
            SearchSource.DAILYMOTION -> Apis.dailymotion(q, limit, p)
            SearchSource.PEERTUBE -> Apis.peertube(q, limit, p)
            SearchSource.ARCHIVE -> Apis.archive(q, limit, p)
            SearchSource.MIXCLOUD -> Apis.mixcloud(q, limit, p)
            SearchSource.REDDIT -> Reddit.find(q, limit, p)
            SearchSource.XVIDEOS -> XVideosSite.XVIDEOS.find(q, limit, p)
            SearchSource.XNXX -> XVideosSite.XNXX.find(q, limit, p)
            SearchSource.XHAMSTER -> Tube.xhamster(q, limit, p)
            SearchSource.PORNHUB -> Tube.pornhub(q, limit, p)
            SearchSource.REDTUBE -> Apis.redtube(q, limit, p)
            SearchSource.YOUPORN -> Tube.youporn(q, limit, p)
            SearchSource.EPORNER -> Apis.eporner(q, limit, p)
            SearchSource.REDGIFS -> Apis.redgifs(q, limit, p)
            SearchSource.TNAFLIX, SearchSource.PORNTREX, SearchSource.YOUJIZZ, SearchSource.THISVID ->
                HtmlSites.find(source, q, limit, p)
        }
        return found.map { it.copy(source = source) }
    }

    /**
     * Busca en varias webs a la vez y mezcla los resultados por rondas, para que ninguna acapare
     * el principio de la lista. [onPartial] recibe lo que hay hasta ahora y cuántas webs faltan.
     */
    suspend fun searchMany(
        query: String,
        sources: List<SearchSource>,
        limitPerSource: Int = 10,
        page: Int = 1,
        onPartial: ((FederatedResults, Int) -> Unit)? = null,
    ): FederatedResults {
        val q = query.trim()
        if (q.isEmpty() || sources.isEmpty()) return FederatedResults()
        val perSource = mutableMapOf<SearchSource, List<SearchResult>>()
        val failures = mutableListOf<Pair<SearchSource, String>>()

        fun snapshot() = FederatedResults(interleave(perSource, sources), failures.sortedBy { it.first.title })

        coroutineScope {
            // La fuente viaja con su resultado: no hay forma de archivarlo bajo la web equivocada.
            val done = Channel<Pair<SearchSource, Result<List<SearchResult>>>>(sources.size)
            sources.forEach { source ->
                launch(Dispatchers.IO) {
                    done.send(source to runCatching { search(q, source, limitPerSource, page) })
                }
            }
            var remaining = sources.size
            repeat(sources.size) {
                val (source, result) = done.receive()
                result.onSuccess { perSource[source] = it }.onFailure { failures += source to describe(it) }
                remaining--
                onPartial?.invoke(snapshot(), remaining)
            }
        }
        return snapshot()
    }

    /** Uno de cada web, luego el segundo de cada una…, respetando el orden pedido. */
    fun interleave(perSource: Map<SearchSource, List<SearchResult>>, order: List<SearchSource>): List<SearchResult> {
        val deepest = perSource.values.maxOfOrNull { it.size } ?: 0
        val mixed = mutableListOf<SearchResult>()
        for (index in 0 until deepest) {
            for (source in order) {
                perSource[source]?.getOrNull(index)?.let { mixed += it }
            }
        }
        return mixed
    }

    fun describe(error: Throwable): String = when (error) {
        is SearchError -> error.message.orEmpty()
        is Net.HttpError -> "Fallo de red: el servidor respondió ${error.code}"
        is IOException -> "Fallo de red: ${error.message ?: error.javaClass.simpleName}"
        else -> "No se pudieron leer los resultados: ${error.message ?: error}"
    }
}


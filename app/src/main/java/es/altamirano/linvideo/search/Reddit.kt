package es.altamirano.linvideo.search

import es.altamirano.linvideo.search.Text.decodeEntities
import es.altamirano.linvideo.search.Text.firstMatch
import es.altamirano.linvideo.search.Text.quote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Reddit, con el fragmento HTML del buscador. */
object Reddit {
    /**
     * La API JSON de Reddit responde 403 sin sesión (septiembre de 2026), pero el fragmento que
     * pinta la pestaña «Multimedia» del buscador sí se sirve. Con `site:v.redd.it` todas las
     * publicaciones son vídeo. Pagina con un cursor opaco: se siguen cursores desde el principio
     * hasta juntar la página pedida.
     */
    fun find(query: String, limit: Int, page: Int): List<SearchResult> {
        val base = "https://www.reddit.com/svc/shreddit/search/?q=${quote("$query site:v.redd.it")}&type=media"
        val wanted = limit * page
        val collected = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        repeat(5 * page) {
            val (found, next) = parse(Net.fetchText(base + (cursor?.let { "&cursor=$it" } ?: "")))
            for (result in found) if (seen.add(result.id)) collected += result
            if (collected.size >= wanted || next == null) return collected.drop(limit * (page - 1)).take(limit)
            cursor = next
        }
        return collected.drop(limit * (page - 1)).take(limit)
    }

    /** Los vídeos de la página y el cursor de la siguiente. Las publicaciones de imagen se descartan. */
    fun parse(page: String): Pair<List<SearchResult>, String?> {
        val results = mutableListOf<SearchResult>()
        for (block in page.split("<search-telemetry-tracker").drop(1)) {
            if ("<shreddit-player" !in block) continue
            val path = firstMatch(block, """href="(/r/[^"]+/comments/[^"]+)"""") ?: continue
            val rawContext = firstMatch(block, """data-faceplate-tracking-context="([^"]+)"""") ?: continue
            val context = try {
                Json.parseToJsonElement(decodeEntities(rawContext)) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: continue
            val post = context["post"] as? JsonObject ?: continue
            val postId = post["id"].asString() ?: continue
            val subreddit = (context["subreddit"] as? JsonObject)?.get("name").asString()
            val poster = firstMatch(block, """poster="([^"]+)"""")
            val duration = firstMatch(block, """&quot;duration&quot;:([0-9.]+)""")
            results += SearchResult(
                id = postId,
                title = post["title"].asString()?.ifEmpty { null } ?: "(sin título)",
                pageUrl = "https://www.reddit.com$path",
                thumbnailUrl = poster?.let(::decodeEntities),
                duration = duration?.toDoubleOrNull(),
                uploader = subreddit?.let { "r/$it" },
            )
        }
        val cursor = firstMatch(page, """svc/shreddit/search/\?[^"]*?cursor=([^"&]+)""")
        return results to cursor
    }
}

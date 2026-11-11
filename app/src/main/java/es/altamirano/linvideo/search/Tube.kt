package es.altamirano.linvideo.search

import es.altamirano.linvideo.search.Text.decodeEntities
import es.altamirano.linvideo.search.Text.extractBalancedJson
import es.altamirano.linvideo.search.Text.firstMatch
import es.altamirano.linvideo.search.Text.parseLooseDuration
import es.altamirano.linvideo.search.Text.quote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Pornhub, xHamster y YouPorn. */
object Tube {
    fun pornhub(query: String, limit: Int, page: Int): List<SearchResult> {
        // Una búsqueda sin resultados responde 404, con la página de «no hay nada» dentro.
        val html = Net.fetchText("https://www.pornhub.com/video/search?search=${quote(query)}&page=$page", allowNotFound = true)
        return HtmlSites.checked(parsePornhub(html, limit), html, SearchSource.PORNHUB)
    }

    fun parsePornhub(page: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        for (block in page.split("<li data-block-position=").drop(1)) {
            val path = firstMatch(block, """href="(/view_video\.php\?viewkey=[^"]+)"""")
            // El título viaja en el alt de la miniatura.
            val rawTitle = firstMatch(block, """alt="([^"]{3,})"""")
            if (path == null || rawTitle == null) continue
            val duration = firstMatch(block, """class="[^"]*\btime">([0-9:]+)<""")
            val uploader = firstMatch(block, """class="uploaderLink"[^>]*>([^<]+)<""")
            results += SearchResult(
                id = firstMatch(block, """data-video-id="([0-9]+)"""") ?: path,
                title = decodeEntities(rawTitle),
                pageUrl = "https://www.pornhub.com$path",
                thumbnailUrl = firstMatch(block, """data-poster="([^"]+)"""") ?: firstMatch(block, """data-path="([^"]+)""""),
                duration = duration?.let(::parseLooseDuration),
                uploader = uploader?.let(::decodeEntities),
            )
            if (results.size >= limit) break
        }
        return results
    }

    fun xhamster(query: String, limit: Int, page: Int): List<SearchResult> {
        val slug = query.lowercase().split(Regex("[^0-9a-z]+")).filter { it.isNotEmpty() }.joinToString("-")
        if (slug.isEmpty()) throw SearchError("consulta no válida")
        val url = "https://xhamster.com/search/$slug" + (if (page > 1) "?page=$page" else "")
        return parseXhamster(Net.fetchText(url), limit)
    }

    /** xHamster trae los resultados en un JSON incrustado en la página. */
    fun parseXhamster(page: String, limit: Int): List<SearchResult> {
        val raw = extractBalancedJson(page, "window.initials")
        val thumbs = try {
            val root = raw?.let { Json.parseToJsonElement(it) } as? JsonObject
            ((root?.get("searchResult") as? JsonObject)?.get("videoThumbProps")).asArray()
        } catch (e: Exception) {
            null
        } ?: throw SearchError("xHamster cambió el formato de la página")
        return thumbs.take(limit).mapNotNull { e ->
            val t = e as? JsonObject ?: return@mapNotNull null
            val pageUrl = t["pageURL"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            SearchResult(
                id = if (t["id"].asLong() != null) t["id"].asText()!! else pageUrl,
                title = t["title"].asString()?.ifEmpty { null } ?: "(sin título)",
                pageUrl = pageUrl,
                thumbnailUrl = t["thumbURL"].asString(),
                duration = t["duration"].asDouble(),
                uploader = t["landing"].asObject()["name"].asString(),
                views = t["views"].asLong(),
            )
        }
    }

    fun youporn(query: String, limit: Int, page: Int): List<SearchResult> {
        // Como Pornhub: sin resultados, 404 con la página de «no hay nada».
        val html = Net.fetchText("https://www.youporn.com/search/?query=${quote(query)}&page=$page", allowNotFound = true)
        return HtmlSites.checked(parseYouporn(html, limit), html, SearchSource.YOUPORN)
    }

    fun parseYouporn(page: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        for (block in page.split("data-video-id=").drop(1)) {
            val path = firstMatch(block, """href="(/watch/[^"]+)"""")
            val rawTitle = firstMatch(block, """aria-label="([^"]{3,})"""")
            if (path == null || rawTitle == null) continue
            val identifier = firstMatch(block, """^"?([0-9]+)""") ?: path
            if (!seen.add(identifier)) continue
            val duration = firstMatch(block, """video-duration[^>]*>\s*<span>\s*([0-9:]+)""")
                ?: firstMatch(block, """class="video-duration[^"]*"[^>]*>.{0,80}?([0-9]{1,2}:[0-9]{2})""")
            val uploader = firstMatch(block, """data-uploader-name="([^"]+)"""")
            results += SearchResult(
                id = identifier,
                title = decodeEntities(rawTitle),
                pageUrl = "https://www.youporn.com$path",
                thumbnailUrl = firstMatch(block, """data-src="(https://[^"]+\.(?:jpg|webp)[^"]*)""""),
                duration = duration?.let(::parseLooseDuration),
                uploader = uploader?.let(::decodeEntities),
            )
            if (results.size >= limit) break
        }
        return results
    }
}

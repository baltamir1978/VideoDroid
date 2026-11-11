package es.altamirano.linvideo.search

import es.altamirano.linvideo.search.Text.absoluteUrl
import es.altamirano.linvideo.search.Text.decodeEntities
import es.altamirano.linvideo.search.Text.firstMatch
import es.altamirano.linvideo.search.Text.parseCompactNumber
import es.altamirano.linvideo.search.Text.parseLooseDuration
import es.altamirano.linvideo.search.Text.quote
import es.altamirano.linvideo.search.Text.quotePlus

/** TNAFlix, PornTrex, YouJizz y ThisVid, y cómo dice cada web que no ha encontrado nada. */
object HtmlSites {
    fun find(source: SearchSource, query: String, limit: Int, page: Int): List<SearchResult> {
        // Las que llevan la búsqueda en la ruta quieren las palabras unidas por guiones.
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::quote)
        if (words.isEmpty()) throw SearchError("consulta no válida")
        val slug = words.joinToString("-")
        val plus = quotePlus(query)
        val (url, parser) = when (source) {
            SearchSource.TNAFLIX ->
                ("https://www.tnaflix.com/search?what=$plus" + (if (page > 1) "&page=$page" else "")) to ::parseTnaflix
            SearchSource.PORNTREX ->
                ("https://www.porntrex.com/search/$slug/" + (if (page > 1) "$page/" else "")) to ::parsePorntrex
            SearchSource.YOUJIZZ -> "https://www.youjizz.com/search/$slug-$page.html" to ::parseYoujizz
            else -> ("https://thisvid.com/search/" + (if (page > 1) "$page/" else "") + "?q=$plus") to ::parseThisvid
        }
        val html = Net.fetchText(url)
        return checked(parser(html, limit), html, source)
    }

    private val NO_RESULTS: Map<SearchSource, List<Regex>> = mapOf(
        SearchSource.XVIDEOS to listOf("No v(?:í|i|&iacute;)deos encontrados", "No videos? (?:found|match)"),
        SearchSource.XNXX to listOf("No v(?:í|i|&iacute;)deos encontrados", "No videos? (?:found|match)"),
        SearchSource.TNAFLIX to listOf(""">\s*No results\s*<"""),
        SearchSource.YOUJIZZ to listOf("no videos found matching", "search - 0 results"),
        SearchSource.THISVID to listOf("No results found"),
        // Con el idioma en español, Pornhub redirige a es.pornhub.com y lo dice en español.
        SearchSource.PORNHUB to listOf("No Search Results", "No results found", "No se han encontrado resultados"),
        SearchSource.YOUPORN to listOf("couldn(?:'|&#039;)t find what you were looking for"),
    ).mapValues { (_, patterns) -> patterns.map { Regex(it, setOf(RegexOption.IGNORE_CASE)) } }

    /**
     * Cómo dice cada web que su búsqueda no ha encontrado nada. PornTrex no da ninguna señal
     * clara, así que con ella una lista vacía sigue contando como fallo.
     */
    fun saysNoResults(page: String, source: SearchSource) = NO_RESULTS[source].orEmpty().any { it.containsMatchIn(page) }

    /**
     * Si la web dice que no ha encontrado nada, la lista va vacía aunque se haya leído algo
     * (ThisVid pone vídeos «en tendencia» debajo del aviso). Sólo una lectura vacía sin ese
     * aviso cuenta como error.
     */
    fun checked(results: List<SearchResult>, page: String, source: SearchSource): List<SearchResult> {
        if (saysNoResults(page, source)) return emptyList()
        if (results.isEmpty()) throw SearchError("${source.title} no devolvió resultados legibles")
        return results
    }

    fun parseTnaflix(page: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        for (block in page.split("<div data-vid=\"").drop(1)) {
            val videoId = firstMatch(block, """^([0-9]+)"""")
            val pageUrl = firstMatch(block, """href="(https://www\.tnaflix\.com/[^"]+/video[0-9]+)"""")
            val rawTitle = firstMatch(block, """class="video-title[^"]*">\s*([^<]+?)\s*<""")
            if (videoId.isNullOrEmpty() || pageUrl.isNullOrEmpty() || rawTitle.isNullOrEmpty()) continue
            // El primer resultado no se carga en diferido: lleva la miniatura en `src`, no en
            // `data-src` (noviembre de 2026; LinVideo la dejaba en blanco).
            val thumb = firstMatch(block, """data-src="([^"]+)"""")
                ?: firstMatch(block, """<img[^>]*\ssrc="(https://[^"]+)"""")
            val duration = firstMatch(block, """video-duration">([0-9:]+)<""")
            val uploader = firstMatch(block, """/profile/[^"]+"[^>]*>\s*([^<]+?)\s*<""")
            val views = firstMatch(block, """icon-eye"></i>([0-9.,]+[KMkm]?)""")
            results += SearchResult(
                id = videoId, title = decodeEntities(rawTitle), pageUrl = pageUrl,
                thumbnailUrl = thumb?.ifEmpty { null }?.let(::absoluteUrl),
                duration = duration?.ifEmpty { null }?.let(::parseLooseDuration),
                uploader = uploader?.ifEmpty { null }?.let(::decodeEntities),
                views = views?.ifEmpty { null }?.let(::parseCompactNumber),
            )
            if (results.size >= limit) break
        }
        return results
    }

    fun parsePorntrex(page: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        for (block in page.split("data-item-id=\"").drop(1)) {
            val videoId = firstMatch(block, """^([0-9]+)"""")
            val pageUrl = firstMatch(block, """href="(https://www\.porntrex\.com/video/[0-9]+/[^"]+)"""")
            val rawTitle = firstMatch(block, """class="inf"><a [^>]*title="([^"]+)"""")?.ifEmpty { null }
                ?: firstMatch(block, """alt="([^"]+)"""")
            if (videoId.isNullOrEmpty() || pageUrl.isNullOrEmpty() || rawTitle.isNullOrEmpty()) continue
            // «12 118 views»: el separador de miles es un espacio.
            val views = firstMatch(block, """viewsthumb">([0-9 .,]+) views""")
            val thumb = firstMatch(block, """data-src="([^"]+)"""")
            val duration = firstMatch(block, """durations">.*?([0-9]+:[0-9:]+)""")
            results += SearchResult(
                id = videoId, title = decodeEntities(rawTitle), pageUrl = pageUrl,
                thumbnailUrl = thumb?.ifEmpty { null }?.let(::absoluteUrl),
                duration = duration?.ifEmpty { null }?.let(::parseLooseDuration),
                views = views?.ifEmpty { null }?.let { parseCompactNumber(it.replace(" ", "")) },
            )
            if (results.size >= limit) break
        }
        return results
    }

    fun parseYoujizz(page: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        for (block in page.split("<div class=\"video-thumb\" data-videoId=\"").drop(1)) {
            // YouJizz mezcla comillas simples y dobles en los enlaces.
            val videoId = firstMatch(block, """^([0-9]+)"""")
            val path = firstMatch(block, """href=["'](/videos/[^"']+\.html)["']""")
            val rawTitle = firstMatch(block, """class="video-title">\s*<a [^>]*>([^<]+)</a>""")
            if (videoId.isNullOrEmpty() || path.isNullOrEmpty() || rawTitle.isNullOrEmpty()) continue
            val thumb = firstMatch(block, """data-original="([^"]+)"""")
            val duration = firstMatch(block, """class="time">.*?([0-9]+:[0-9:]+)""")
            val views = firstMatch(block, """format-views">([0-9]+)<""")
            results += SearchResult(
                id = videoId, title = decodeEntities(rawTitle).trim(),
                pageUrl = "https://www.youjizz.com$path",
                thumbnailUrl = thumb?.ifEmpty { null }?.let(::absoluteUrl),
                duration = duration?.ifEmpty { null }?.let(::parseLooseDuration),
                views = views?.ifEmpty { null }?.toLongOrNull(),
            )
            if (results.size >= limit) break
        }
        return results
    }

    fun parseThisvid(page: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        for (block in page.split("<a href=\"https://thisvid.com/videos/").drop(1)) {
            // Sólo las tarjetas de resultado, y sin los vídeos privados, que piden amistad con
            // quien los subió y yt-dlp no puede bajar.
            val tag = block.substringBefore(">")
            if ("tumbpu" !in tag || "thumb private" in block) continue
            val slug = firstMatch(block, """^([^"/]+)/?"""")
            val rawTitle = firstMatch(block, """title="([^"]+)"""")
            if (slug.isNullOrEmpty() || rawTitle.isNullOrEmpty()) continue
            val thumb = firstMatch(block, """data-original="([^"]+)"""")
            val duration = firstMatch(block, """class="duration">([0-9:]+)<""")
            val views = firstMatch(block, """ico-view"></i>([0-9]+)""")
            results += SearchResult(
                id = slug, title = decodeEntities(rawTitle),
                pageUrl = "https://thisvid.com/videos/$slug/",
                thumbnailUrl = thumb?.ifEmpty { null }?.let(::absoluteUrl),
                duration = duration?.ifEmpty { null }?.let(::parseLooseDuration),
                views = views?.ifEmpty { null }?.toLongOrNull(),
            )
            if (results.size >= limit) break
        }
        return results
    }
}

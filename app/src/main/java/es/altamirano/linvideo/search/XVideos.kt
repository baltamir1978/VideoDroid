package es.altamirano.linvideo.search

import es.altamirano.linvideo.search.Text.decodeEntities
import es.altamirano.linvideo.search.Text.firstMatch
import es.altamirano.linvideo.search.Text.parseCompactNumber
import es.altamirano.linvideo.search.Text.parseLooseDuration
import es.altamirano.linvideo.search.Text.quote
import es.altamirano.linvideo.search.Text.slugify

/** XVideos y XNXX: mismo grupo, misma maquetación. */
enum class XVideosSite(val base: String, val displayName: String) {
    XVIDEOS("https://www.xvideos.com", "XVideos"),
    XNXX("https://www.xnxx.com", "XNXX");

    /** XVideos usa /video.ID/slug y XNXX /video-ID/slug. */
    val linkPattern get() = if (this == XVIDEOS) """href="(/video\.[^"]+)"""" else """href="(/video-[^"]+)""""

    val source get() = if (this == XVIDEOS) SearchSource.XVIDEOS else SearchSource.XNXX

    /** Ruta canónica del vídeo a partir del identificador corto (`data-eid`). */
    fun videoPath(videoId: String, slug: String) = "/video${if (this == XVIDEOS) "." else "-"}$videoId/$slug"

    /** XVideos pagina con `&p=` y XNXX añadiendo `/N` a la ruta; los dos desde 0. */
    fun pagedUrl(query: String, page: Int): String {
        val index = maxOf(0, page - 1)
        return if (this == XVIDEOS) "$base/?k=${quote(query)}" + (if (index > 0) "&p=$index" else "")
        else "$base/search/${quote(query)}" + (if (index > 0) "/$index" else "")
    }

    fun find(query: String, limit: Int, page: Int): List<SearchResult> {
        val html = Net.fetchText(pagedUrl(query, page))
        return HtmlSites.checked(parse(html, limit), html, source)
    }

    fun parse(page: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        // Cada resultado es un bloque que empieza por <div id="video_…">.
        for (block in page.split("<div id=\"video_").drop(1)) {
            val rawTitle = firstMatch(block, """title="([^"]*)"""") ?: continue
            val title = decodeEntities(rawTitle)
            // Desde julio de 2026 XVideos sirve a parte de las sesiones un enlace ofuscado
            // (`/search-video/<blob>`). El identificador corto sigue en `data-eid`, y con él se
            // rehace la ruta: el sitio redirige al slug bueno sea cual sea el que se pida.
            val path = firstMatch(block, linkPattern)
                ?: firstMatch(block, """data-eid="([^"]+)"""")?.let { videoPath(it, slugify(title)) }
                ?: continue
            // XVideos marca la duración con <span class="duration">; XNXX la deja suelta
            // («6min») dentro del bloque de metadatos.
            val span = firstMatch(block, """<span class="duration">([^<]+)</span>""")
            val metadata = firstMatch(block, """<p class="metadata">(.*?)</p>""")
            val duration = span?.let(::parseLooseDuration)?.takeIf { it != 0.0 } ?: metadata?.let(::parseLooseDuration)
            // Las visitas van justo antes del icono del ojo: «1.2M <span class="icon-f icf-eye">».
            val views = firstMatch(block, """([0-9][0-9.,]*[KMkm]?)\s*<span class="icon-f icf-eye"""")
            val uploader = firstMatch(block, """<span class="name">([^<]+)</span>""")
            results += SearchResult(
                id = firstMatch(block, """data-id="([0-9]+)"""") ?: path,
                title = title,
                pageUrl = base + path,
                thumbnailUrl = firstMatch(block, """data-src="([^"]+)""""),
                duration = duration,
                uploader = uploader?.let(::decodeEntities),
                views = views?.let(::parseCompactNumber),
            )
            if (results.size >= limit) break
        }
        return results
    }
}

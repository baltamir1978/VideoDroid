package es.altamirano.linvideo.search

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** YouTube y SoundCloud, a través de yt-dlp. */
object YtdlpSearch {
    fun search(prefix: String, query: String, limit: Int, page: Int): List<SearchResult> {
        // yt-dlp no admite desplazamiento: se piden todas las páginas hasta la actual y se
        // descartan las anteriores.
        val wanted = limit * page
        val request = YoutubeDLRequest("$prefix$wanted:$query")
            .addCommands(listOf("--ignore-config", "--no-warnings", "--flat-playlist", "-J"))
        val out = try {
            YoutubeDL.getInstance().execute(request).out
        } catch (e: Exception) {
            val lines = e.message.orEmpty().trim().lines().filter { it.isNotBlank() }
            throw SearchError("No se pudieron leer los resultados: ${lines.lastOrNull() ?: "búsqueda sin resultados"}")
        }
        val root = try {
            Json.parseToJsonElement(out)
        } catch (e: Exception) {
            throw SearchError("respuesta inesperada de yt-dlp")
        }
        return parseEntries(root, skip = limit * (page - 1))
    }

    fun parseEntries(root: JsonElement, skip: Int = 0): List<SearchResult> {
        val entries = (root as? JsonObject)?.get("entries").asArray() ?: throw SearchError("respuesta inesperada de yt-dlp")
        val results = mutableListOf<SearchResult>()
        for (element in entries.drop(skip)) {
            val entry = element as? JsonObject ?: continue
            val id = entry["id"].asString() ?: continue
            // En SoundCloud `url` apunta a la API; la página buena es `webpage_url`.
            val pageUrl = entry["webpage_url"].asString()?.ifEmpty { null } ?: entry["url"].asString()?.ifEmpty { null } ?: continue
            // La lista de miniaturas viene de menor a mayor calidad.
            val thumb = (entry["thumbnails"].asArray()?.lastOrNull() as? JsonObject)?.get("url").asString()
            results += SearchResult(
                id = id,
                title = entry["title"].asString()?.ifEmpty { null } ?: "(sin título)",
                pageUrl = pageUrl,
                thumbnailUrl = thumb,
                duration = entry["duration"].asDouble(),
                uploader = entry["uploader"].asString()?.ifEmpty { null } ?: entry["channel"].asString(),
                views = entry["view_count"].asLong(),
            )
        }
        return results
    }
}

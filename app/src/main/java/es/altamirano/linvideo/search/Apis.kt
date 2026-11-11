package es.altamirano.linvideo.search

import es.altamirano.linvideo.search.Text.parseCompactNumber
import es.altamirano.linvideo.search.Text.parseLooseDuration
import es.altamirano.linvideo.search.Text.quote
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Las webs con API abierta: Dailymotion, PeerTube, Mixcloud, Archive.org, RedTube, Eporner y RedGifs. */
object Apis {
    private fun String?.orUntitled() = this?.ifEmpty { null } ?: "(sin título)"

    fun dailymotion(query: String, limit: Int, page: Int) = parseDailymotion(
        Net.fetchJson(
            "https://api.dailymotion.com/videos?search=${quote(query)}&page=$page&limit=$limit" +
                "&fields=id,title,duration,thumbnail_360_url,owner.screenname,views_total,url",
        ),
    )

    fun parseDailymotion(data: JsonElement): List<SearchResult> {
        val items = (data as? JsonObject)?.get("list").asArray() ?: throw SearchError("respuesta inesperada de Dailymotion")
        return items.mapNotNull { e ->
            val v = e as? JsonObject ?: return@mapNotNull null
            val id = v["id"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val url = v["url"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            SearchResult(
                id = id, title = v["title"].asString().orUntitled(), pageUrl = url,
                thumbnailUrl = v["thumbnail_360_url"].asString(), duration = v["duration"].asDouble(),
                uploader = v["owner.screenname"].asString(), views = v["views_total"].asLong(),
            )
        }
    }

    fun peertube(query: String, limit: Int, page: Int) = parsePeertube(
        Net.fetchJson(
            "https://search.joinpeertube.org/api/v1/search/videos?search=${quote(query)}&count=$limit&start=${limit * (page - 1)}",
        ),
    )

    fun parsePeertube(data: JsonElement): List<SearchResult> {
        val items = (data as? JsonObject)?.get("data").asArray() ?: throw SearchError("respuesta inesperada de PeerTube")
        return items.mapNotNull { e ->
            val v = e as? JsonObject ?: return@mapNotNull null
            val uuid = v["uuid"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val url = v["url"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val account = v["account"].asObject()
            // thumbnailUrl es absoluta; thumbnailPath es relativa al servidor que la aloja.
            var thumb = v["thumbnailUrl"].asString()?.ifEmpty { null }
            val path = v["thumbnailPath"].asString()?.ifEmpty { null }
            val host = account["host"].asString()?.ifEmpty { null }
            if (thumb == null && path != null && host != null) thumb = "https://$host$path"
            SearchResult(
                id = uuid, title = v["name"].asString().orUntitled(), pageUrl = url,
                thumbnailUrl = thumb, duration = v["duration"].asDouble(),
                uploader = account["displayName"].asString()?.ifEmpty { null }?.trim(), views = v["views"].asLong(),
            )
        }
    }

    fun mixcloud(query: String, limit: Int, page: Int) = parseMixcloud(
        Net.fetchJson("https://api.mixcloud.com/search/?q=${quote(query)}&type=cloudcast&limit=$limit&offset=${limit * (page - 1)}"),
    )

    fun parseMixcloud(data: JsonElement): List<SearchResult> {
        val items = (data as? JsonObject)?.get("data").asArray() ?: throw SearchError("respuesta inesperada de Mixcloud")
        return items.mapNotNull { e ->
            val c = e as? JsonObject ?: return@mapNotNull null
            val url = c["url"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val pictures = c["pictures"].asObject()
            SearchResult(
                id = c["key"].asString()?.ifEmpty { null } ?: url, title = c["name"].asString().orUntitled(), pageUrl = url,
                thumbnailUrl = listOf("large", "medium", "thumbnail").firstNotNullOfOrNull { pictures[it].asString()?.ifEmpty { null } },
                duration = c["audio_length"].asDouble(), uploader = c["user"].asObject()["name"].asString(),
                views = c["play_count"].asLong(),
            )
        }
    }

    fun archive(query: String, limit: Int, page: Int): List<SearchResult> {
        val expression = "$query AND (mediatype:movies OR mediatype:audio)"
        return parseArchive(
            Net.fetchJson(
                "https://archive.org/advancedsearch.php?q=${quote(expression)}" +
                    "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=downloads" +
                    "&fl%5B%5D=runtime&sort%5B%5D=downloads+desc&rows=$limit&page=$page&output=json",
            ),
        )
    }

    fun parseArchive(data: JsonElement): List<SearchResult> {
        val docs = ((data as? JsonObject)?.get("response") as? JsonObject)?.get("docs").asArray()
            ?: throw SearchError("respuesta inesperada de Archive.org")

        // `creator` y `title` llegan a veces como cadena y a veces como lista.
        fun first(value: JsonElement?): String? =
            if (value is JsonArray) value.firstOrNull().asString() else value.asString()

        return docs.mapNotNull { e ->
            val doc = e as? JsonObject ?: return@mapNotNull null
            val identifier = doc["identifier"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val runtime = doc["runtime"].asString()?.ifEmpty { null }
            SearchResult(
                id = identifier, title = first(doc["title"])?.ifEmpty { null } ?: identifier,
                pageUrl = "https://archive.org/details/$identifier",
                thumbnailUrl = "https://archive.org/services/img/$identifier",
                duration = runtime?.let(::parseLooseDuration),
                uploader = first(doc["creator"]), views = doc["downloads"].asLong(),
            )
        }
    }

    fun redtube(query: String, limit: Int, page: Int) = parseRedtube(
        Net.fetchJson(
            "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&thumbsize=medium&page=$page&search=${quote(query)}",
        ),
        limit,
    )

    fun parseRedtube(data: JsonElement, limit: Int): List<SearchResult> {
        val items = (data as? JsonObject)?.get("videos").asArray() ?: throw SearchError("respuesta inesperada de RedTube")
        return items.take(limit).mapNotNull { wrapper ->
            // Cada elemento envuelve el vídeo en una clave "video".
            val video = (wrapper as? JsonObject)?.get("video") as? JsonObject ?: return@mapNotNull null
            val url = video["url"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val views = video["views"]
            val duration = video["duration"].asString()?.ifEmpty { null }
            SearchResult(
                id = video["video_id"].takeIf { it.truthy() }.asText() ?: url,
                title = video["title"].asString().orUntitled(), pageUrl = url,
                thumbnailUrl = video["default_thumb"].asString(),
                duration = duration?.let(::parseLooseDuration),
                views = if (views is JsonPrimitive && views.isString) parseCompactNumber(views.content) else views.asLong(),
            )
        }
    }

    fun eporner(query: String, limit: Int, page: Int) = parseEporner(
        Net.fetchJson(
            "https://www.eporner.com/api/v2/video/search/?query=${quote(query)}&per_page=$limit&page=$page" +
                "&thumbsize=medium&order=top-weekly&format=json",
        ),
    )

    fun parseEporner(data: JsonElement): List<SearchResult> {
        val items = (data as? JsonObject)?.get("videos").asArray() ?: throw SearchError("respuesta inesperada de Eporner")
        return items.mapNotNull { e ->
            val v = e as? JsonObject ?: return@mapNotNull null
            val id = v["id"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val url = v["url"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            SearchResult(
                id = id, title = v["title"].asString().orUntitled(), pageUrl = url,
                thumbnailUrl = v["default_thumb"].asObject()["src"].asString(),
                duration = v["length_sec"].asDouble(), views = v["views"].asLong(),
            )
        }
    }

    fun redgifs(query: String, limit: Int, page: Int): List<SearchResult> {
        // API pública con un token temporal que se pide en cada búsqueda.
        val token = (Net.fetchJson("https://api.redgifs.com/v2/auth/temporary") as? JsonObject)?.get("token").asString()
            ?: throw SearchError("RedGifs no devolvió un token")
        return parseRedgifs(
            Net.fetchJson(
                "https://api.redgifs.com/v2/gifs/search?search_text=${quote(query)}&order=trending&count=$limit&page=$page",
                headers = mapOf("Authorization" to "Bearer $token"),
            ),
        )
    }

    fun parseRedgifs(data: JsonElement): List<SearchResult> {
        val gifs = (data as? JsonObject)?.get("gifs").asArray() ?: throw SearchError("respuesta inesperada de RedGifs")
        return gifs.mapNotNull { e ->
            val g = e as? JsonObject ?: return@mapNotNull null
            val id = g["id"].asString()?.ifEmpty { null } ?: return@mapNotNull null
            val urls = g["urls"].asObject()
            val tags = g["tags"].asArray().orEmpty().mapNotNull { it.asString() }
            SearchResult(
                id = id, title = if (tags.isNotEmpty()) tags.joinToString(", ") else id,
                pageUrl = "https://www.redgifs.com/watch/$id",
                thumbnailUrl = urls["thumbnail"].asString()?.ifEmpty { null } ?: urls["poster"].asString(),
                duration = g["duration"].asDouble(), uploader = g["userName"].asString(), views = g["views"].asLong(),
            )
        }
    }
}

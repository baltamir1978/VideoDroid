package es.altamirano.linvideo.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Red de las búsquedas, como `fetch_text`/`fetch_json` de LinVideo. */
object Net {
    /**
     * El mismo de AppVideo y LinVideo, aunque esto sea Android: las webs sirven maquetaciones
     * distintas según el navegador, y los lectores se escribieron para la que reciben con este.
     */
    const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    private const val TIMEOUT = 20L

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT * 2, TimeUnit.SECONDS)
            .build()
    }

    class HttpError(val code: Int) : IOException("HTTP $code")

    /** [allowNotFound]: un 404 devuelve la página (Pornhub y YouPorn lo usan para decir que no hay resultados). */
    fun fetchText(url: String, headers: Map<String, String> = emptyMap(), allowNotFound: Boolean = false): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && !(allowNotFound && response.code == 404)) throw HttpError(response.code)
            return response.body?.bytes()?.decodeToString() ?: ""
        }
    }

    fun fetchJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        try {
            Json.parseToJsonElement(fetchText(url, headers))
        } catch (e: kotlinx.serialization.SerializationException) {
            throw SearchError("No se pudieron leer los resultados: la respuesta no es JSON")
        } catch (e: IllegalArgumentException) {
            throw SearchError("No se pudieron leer los resultados: la respuesta no es JSON")
        }
}

// --- Lectura de JSON con los mismos criterios que `_int`, `_float`, `_str` y `_dict` -------

internal fun JsonElement?.asLong(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p.booleanOrNull != null || p.content == "null") return null
    return p.content.toLongOrNull() ?: p.content.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
}

internal fun JsonElement?.asDouble(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p.booleanOrNull != null || p.content == "null") return null
    return p.content.toDoubleOrNull()
}

internal fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

/** `str(valor)` de Python para identificadores que llegan como número o como texto. */
internal fun JsonElement?.asText(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (!p.isString && p.content == "null") return null
    return p.content
}

/** El valor «verdadero» en el sentido de Python: ni null, ni vacío, ni cero, ni false. */
internal fun JsonElement?.truthy(): Boolean = when (this) {
    null -> false
    is JsonPrimitive -> if (isString) content.isNotEmpty() else content !in setOf("null", "false", "0", "0.0")
    is JsonObject -> isNotEmpty()
    is JsonArray -> isNotEmpty()
}

package es.altamirano.linvideo.engine

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

class InspectError(message: String) : Exception(message)

/** Un formato que ofrece la web (`formats.py`). */
data class MediaFormat(
    /** format_id de yt-dlp */
    val id: String,
    val ext: String,
    val resolution: String,
    val height: Int,
    val fps: Double?,
    val videoCodec: String?,
    val audioCodec: String?,
    val sizeBytes: Double?,
    /** kbit/s */
    val bitrate: Double?,
    val note: String?,
) {
    val isAudioOnly get() = videoCodec == null && audioCodec != null
    val isVideoOnly get() = videoCodec != null && audioCodec == null
    val isComplete get() = videoCodec != null && audioCodec != null

    val sizeText get() = if (sizeBytes == null || sizeBytes <= 0) "—" else Formatters.humanBytes(sizeBytes)

    val qualityText: String
        get() {
            if (isAudioOnly) return bitrate?.takeIf { it != 0.0 }?.let { "${it.toInt()} kbps" } ?: note ?: "audio"
            var text = resolution
            if (fps != null && fps > 0) text += " · ${fps.toInt()} fps"
            return text
        }

    val codecText: String
        get() {
            val video = videoCodec?.let(Formats::shortCodec) ?: "—"
            val audio = audioCodec?.let(Formats::shortCodec) ?: "—"
            return when {
                isAudioOnly -> audio
                isVideoOnly -> video
                else -> "$video + $audio"
            }
        }

    /**
     * Lo que se le pasa a `-f`. A un formato de sólo vídeo se le añade el mejor audio, prefiriendo
     * AAC para que el MP4 lo abra cualquier cosa.
     */
    val selector get() = if (isVideoOnly) "$id+bestaudio[ext=m4a]/$id+bestaudio/$id" else id
}

object Formats {
    /** Nombre corto del códec, sin el perfil que le cuelga yt-dlp. */
    fun shortCodec(raw: String): String {
        val base = raw.split(".")[0]
        return when (base.lowercase()) {
            "avc1", "h264" -> "H.264"
            "hev1", "hvc1" -> "HEVC"
            "av01" -> "AV1"
            "vp9", "vp09" -> "VP9"
            "mp4a" -> "AAC"
            "opus" -> "Opus"
            "vorbis" -> "Vorbis"
            "ec-3", "eac3" -> "E-AC3"
            else -> base
        }
    }

    /** `yt-dlp -J`: todo lo que yt-dlp sabe de un enlace, sin descargar. */
    suspend fun runJson(link: String): JsonObject = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(link).addCommands(listOf("--ignore-config", "--no-warnings", "--no-playlist", "-J"))
        val out = try {
            YoutubeDL.getInstance().execute(request).out
        } catch (e: Exception) {
            val lines = e.message.orEmpty().lines().filter { it.isNotBlank() }
            throw InspectError(Errors.explain(lines, 1))
        }
        val root = try {
            Json.parseToJsonElement(out)
        } catch (e: Exception) {
            throw InspectError("La respuesta de yt-dlp no se entiende.")
        }
        root as? JsonObject ?: throw InspectError("La respuesta de yt-dlp no se entiende.")
    }

    fun numberOrNull(value: Any?): Double? {
        val p = value as? JsonPrimitive ?: return null
        if (p.isString || p.booleanOrNull != null) return null
        return p.doubleOrNull
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Como `str(raw.get(key) or "")` en Python. */
    private fun JsonObject.loose(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (!p.isString && p.content == "null") return null
        return p.content.ifEmpty { null }
    }

    fun parseFormats(root: JsonObject): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (element in root["formats"] as? JsonArray ?: JsonArray(emptyList())) {
            val raw = element as? JsonObject ?: continue
            val id = raw.text("format_id") ?: continue
            // yt-dlp marca «none» cuando la pista no existe.
            val video = raw.loose("vcodec")?.takeIf { it != "none" }
            val audio = raw.loose("acodec")?.takeIf { it != "none" }
            if (video == null && audio == null) continue
            formats += MediaFormat(
                id = id,
                ext = raw.loose("ext") ?: "",
                resolution = raw.loose("resolution") ?: "—",
                height = numberOrNull(raw["height"])?.toInt() ?: 0,
                fps = numberOrNull(raw["fps"]),
                videoCodec = video,
                audioCodec = audio,
                sizeBytes = numberOrNull(raw["filesize"])?.takeIf { it != 0.0 } ?: numberOrNull(raw["filesize_approx"]),
                bitrate = numberOrNull(raw["abr"])?.takeIf { it != 0.0 } ?: numberOrNull(raw["tbr"]),
                note = raw.text("format_note"),
            )
        }
        // De mayor a menor calidad: primero los que traen vídeo.
        return formats.sortedWith(compareByDescending<MediaFormat> { it.height }.thenByDescending { it.bitrate ?: 0.0 })
    }

    /** Pregunta a la web qué formatos ofrece para ese enlace. */
    suspend fun inspect(link: String): Pair<String, List<MediaFormat>> {
        val root = runJson(link)
        if (root["formats"] !is JsonArray) throw InspectError("La respuesta de yt-dlp no trae la lista de formatos.")
        val title = root.text("title") ?: link
        return title to parseFormats(root)
    }
}

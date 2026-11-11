package es.altamirano.linvideo.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Elige qué flujo reproducir en la vista previa, sin descargar nada (`preview.py`). ExoPlayer abre
 * lo mismo que GStreamer en Linux (HLS, MP4, WebM, VP9, Opus, Matroska), así que el orden es el de
 * LinVideo: YouTube ya no da archivos con vídeo y audio juntos, y su HLS maestro une las dos
 * pistas y arranca enseguida, así que va primero. Fuera quedan los segmentos DASH sueltos.
 */
object Preview {
    enum class Kind(val title: String) {
        VIDEO("Vídeo"),
        VIDEO_ONLY("Vídeo sin sonido"),
        AUDIO("Sólo audio"),
    }

    data class Stream(
        val url: String,
        val kind: Kind,
        val title: String,
        /** Cabeceras que pide la web (User-Agent, Referer…); sin ellas algunas dan 403. */
        val headers: Map<String, String> = emptyMap(),
    ) {
        val isHls get() = url.substringBefore('?').endsWith(".m3u8") || hls
        internal var hls = false
    }

    /** yt-dlp distingue «none» (la pista no existe) de ausente (no lo sabe). */
    private enum class Track { ABSENT, UNKNOWN, PRESENT }

    private val AUDIO_CONTAINERS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    private val PLAYABLE_CONTAINERS = setOf("mp4", "m4a", "m4v", "mov", "mp3", "aac", "webm", "mkv", "ogg", "opus", "flac", "wav")
    private val WANTED_HEADERS = setOf("user-agent", "referer", "origin", "cookie")

    private class Candidate(
        val url: String,
        val manifestUrl: String?,
        val proto: String,
        val ext: String,
        val video: Track,
        val audio: Track,
        val height: Int?,
        val bitrate: Double?,
        val headers: Map<String, String>,
    ) {
        val hasVideo get() = video != Track.ABSENT
        val hasAudio get() = audio != Track.ABSENT
        val isHls get() = proto.startsWith("m3u8")
        val isPlayable get() = isHls || (proto in setOf("https", "http") && ext in PLAYABLE_CONTAINERS)
    }

    private fun track(value: Any?): Track {
        val p = value as? JsonPrimitive ?: return Track.UNKNOWN
        if (!p.isString) return Track.UNKNOWN
        return if (p.content == "none") Track.ABSENT else Track.PRESENT
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun candidate(raw: JsonObject): Candidate? {
        val url = raw.str("url")?.ifEmpty { null } ?: return null
        val ext = (raw["ext"] as? JsonPrimitive)?.takeIf { it.content != "null" }?.content.orEmpty().lowercase()
        // En un .mp3 o .m4a no hay imagen aunque yt-dlp no lo diga.
        val video = if (ext in AUDIO_CONTAINERS) Track.ABSENT else track(raw["vcodec"])
        val audio = track(raw["acodec"])
        // Las miniaturas en mosaico también vienen como «formato».
        if (ext == "mhtml" || (video == Track.ABSENT && audio == Track.ABSENT)) return null
        val headers = (raw["http_headers"] as? JsonObject).orEmpty()
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content } }
            .filter { it.first.lowercase() in WANTED_HEADERS }
            .toMap()
        return Candidate(
            url = url,
            manifestUrl = raw.str("manifest_url"),
            proto = raw.str("protocol") ?: url.substringBefore(':'),
            ext = ext,
            video = video,
            audio = audio,
            height = Formats.numberOrNull(raw["height"])?.toInt(),
            bitrate = Formats.numberOrNull(raw["abr"])?.takeIf { it != 0.0 } ?: Formats.numberOrNull(raw["tbr"]),
            headers = headers,
        )
    }

    /** La calidad más alta sin pasar de 720p; si todas pasan, la más baja. Sin altura cuenta como aceptable. */
    private fun closestTo720(candidates: List<Candidate>): Candidate? {
        if (candidates.isEmpty()) return null
        val fitting = candidates.filter { (it.height ?: 0) <= 720 }
        return if (fitting.isNotEmpty()) fitting.maxBy { it.height ?: 0 } else candidates.minBy { it.height ?: 0 }
    }

    /** De mejor a peor: HLS maestro, archivo con vídeo y audio, vídeo sin sonido y, al final, sólo audio. */
    fun pick(root: JsonObject): Stream? {
        // Archive.org y otras devuelven una lista de pistas: basta con la primera que suene.
        (root["entries"] as? JsonArray)?.let { entries ->
            return entries.firstNotNullOfOrNull { (it as? JsonObject)?.let(::pick) }
        }
        val title = root.str("title").orEmpty()
        var rawList = (root["formats"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        // Algunos extractores devuelven un único formato en la raíz, sin lista.
        if (rawList.isEmpty() && !root.str("url").isNullOrEmpty()) rawList = listOf(root)
        val candidates = rawList.mapNotNull(::candidate)

        fun stream(url: String, c: Candidate, kind: Kind, hls: Boolean) =
            Stream(url, kind, title, c.headers).also { it.hls = hls }

        // 1. HLS maestro: agrupa varias calidades y, si hace falta, el audio aparte.
        candidates.firstOrNull { it.isHls && it.manifestUrl != null && it.hasVideo }?.let { c ->
            // Los vídeos mudos de Reddit no traen ninguna pista de audio en toda la lista.
            val hasAudio = candidates.any { it.hasAudio }
            return stream(c.manifestUrl!!, c, if (hasAudio) Kind.VIDEO else Kind.VIDEO_ONLY, hls = true)
        }

        val playable = candidates.filter { it.isPlayable }
        // 2. Un archivo o lista HLS con las dos pistas, lo más cerca posible de 720p.
        closestTo720(playable.filter { it.hasVideo && it.hasAudio })?.let { return stream(it.url, it, Kind.VIDEO, it.isHls) }
        // 3. Vídeo sin sonido.
        closestTo720(playable.filter { it.hasVideo })?.let { return stream(it.url, it, Kind.VIDEO_ONLY, it.isHls) }
        // 4. Sólo audio, el de más calidad.
        val audio = playable.filter { !it.hasVideo && it.hasAudio }
        audio.firstOrNull { it.isHls && it.manifestUrl != null }?.let { return stream(it.manifestUrl!!, it, Kind.AUDIO, hls = true) }
        return audio.maxByOrNull { it.bitrate ?: 0.0 }?.let { stream(it.url, it, Kind.AUDIO, it.isHls) }
    }

    suspend fun resolve(link: String): Stream =
        pick(Formats.runJson(link)) ?: throw InspectError(
            "Esta web sólo ofrece formatos que no se pueden previsualizar. Se puede descargar " +
                "igualmente o abrir en el navegador.",
        )
}

package es.altamirano.linvideo.engine

import es.altamirano.linvideo.model.Edition
import java.net.URI
import java.net.URLDecoder

/** Enlaces que llegan pegados, compartidos o en un `linvideo:` (`settings.py`). */
object Links {
    /**
     * Separados por espacios, saltos o comas, sin las comillas ni los ángulos de alrededor. En la
     * edición «sólo YouTube» lo demás no cuenta como enlace: ni se ofrece ni se descarga.
     */
    fun extractUrls(text: String, onlyYouTube: Boolean = Edition.ONLY_YOUTUBE): List<String> =
        text.replace(",", " ").split(Regex("\\s+"))
            .map { it.trim('<', '>', '"', '\'') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .filter { !onlyYouTube || isYouTube(it) }

    private val YOUTUBE_HOSTS = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    /** youtube.com y sus subdominios (www, m, music), youtu.be y youtube-nocookie.com; nada más. */
    fun isYouTube(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.trimEnd('.') ?: return false
        return YOUTUBE_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** `linvideo:https://…`, `linvideo://https://…`, `linvideo://https%3A…` o un enlace tal cual → el enlace. */
    fun fromArgument(raw: String): String? {
        var text = raw.trim()
        if (text.lowercase().startsWith("linvideo:")) {
            text = text.substring("linvideo:".length)
            if (text.startsWith("//")) text = text.substring(2)
            val lower = text.lowercase()
            if (lower.startsWith("http%3a") || lower.startsWith("https%3a")) {
                text = URLDecoder.decode(text, "UTF-8")
            }
        }
        if (!text.startsWith("http://") && !text.startsWith("https://")) return null
        return if (!Edition.ONLY_YOUTUBE || isYouTube(text)) text else null
    }
}

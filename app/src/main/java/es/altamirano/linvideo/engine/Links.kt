package es.altamirano.linvideo.engine

import java.net.URLDecoder

/** Enlaces que llegan pegados, compartidos o en un `linvideo:` (`settings.py`). */
object Links {
    /** Separados por espacios, saltos o comas, sin las comillas ni los ángulos de alrededor. */
    fun extractUrls(text: String): List<String> =
        text.replace(",", " ").split(Regex("\\s+"))
            .map { it.trim('<', '>', '"', '\'') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }

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
        return if (text.startsWith("http://") || text.startsWith("https://")) text else null
    }
}

package es.altamirano.linvideo.engine

/**
 * Traduce la salida de error de yt-dlp a algo que se pueda leer y accionar (`errors.py`).
 * Mismas reglas y en el mismo orden; los textos que hablaban de Linux están adaptados a Android.
 */
object Errors {
    private val RULES: List<Pair<List<String>, String>> = listOf(
        listOf("unable to obtain file audio codec") to
            "Este vídeo no tiene pista de audio, así que no se puede sacar de él un archivo de " +
            "sonido. Descárgalo como vídeo o como GIF.",
        listOf("requested format is not available", "requested format not available") to
            "No hay ningún formato que cumpla los filtros elegidos. Prueba con «Máxima» en calidad " +
            "o con otro formato.",
        listOf("sign in to confirm your age", "age-restricted", "confirm your age", "inappropriate for some users") to
            "La web exige confirmar la edad. En Ajustes › Red, importa un cookies.txt de un " +
            "navegador donde tengas la sesión abierta.",
        listOf("sign in to confirm", "not a bot", "cookies are no longer valid") to
            "La web pide iniciar sesión para seguir. En Ajustes › Red, importa un cookies.txt de " +
            "un navegador donde tengas la sesión abierta.",
        listOf("video unavailable", "video is unavailable", "this video has been removed", "has been terminated") to
            "El vídeo ya no está disponible: lo han retirado o la cuenta ha desaparecido.",
        listOf("private video", "this video is private") to "El vídeo es privado.",
        listOf("members-only", "join this channel", "premium", "paid") to
            "El contenido es de pago o sólo para suscriptores.",
        listOf("not available in your country", "geo restricted", "geo-restricted", "blocked it in your country") to
            "No está disponible en tu país.",
        listOf("unsupported url") to
            "yt-dlp no reconoce esa dirección. Comprueba que sea la página del vídeo y no un " +
            "resultado de búsqueda o una portada.",
        listOf("http error 403", "forbidden") to
            "La web ha rechazado la descarga (403). Suele arreglarse actualizando yt-dlp en " +
            "Ajustes › Herramientas, o usando un cookies.txt.",
        // Antes que la del 404: «ffmpeg not found» contiene «not found».
        listOf("ffmpeg not found", "ffprobe not found", "ffmpeg is not installed") to
            "No se encuentra ffmpeg, que viene dentro de la app y hace falta para unir y convertir. " +
            "Reinstala LinVideo.",
        listOf("http error 404", "not found") to
            "La página no existe (404). El enlace puede estar mal o el vídeo borrado.",
        listOf("http error 429", "too many requests") to
            "La web ha cortado por exceso de peticiones. Espera un rato o baja las descargas " +
            "simultáneas en Ajustes › Red.",
        listOf("precondition failed", "http error 412") to "La web bloquea las peticiones automáticas desde aquí.",
        listOf("no space left") to "No queda espacio en el móvil.",
        listOf("permission denied", "read-only file system") to
            "No hay permiso para escribir en la carpeta de destino. Elige otra en Ajustes › General.",
        listOf(
            "unable to download webpage", "temporary failure in name resolution",
            "network is unreachable", "connection reset", "timed out",
        ) to "Fallo de red al contactar con la web. Comprueba la conexión y reinténtalo.",
        listOf("postprocessing") to
            "Falló el paso de conversión posterior a la descarga. El registro tiene el detalle de ffmpeg.",
    )

    /** Mensaje en claro a partir de las líneas de error. Sin regla conocida, el texto de yt-dlp limpio. */
    fun explain(rawLines: List<String>, exitCode: Int): String {
        val joined = rawLines.joinToString(" ").lowercase()
        for ((needles, message) in RULES) {
            if (needles.any { it in joined }) return message
        }
        rawLines.lastOrNull { it.isNotEmpty() }?.let { return clean(it) }
        return "La descarga no se completó (código $exitCode). El registro tiene el detalle."
    }

    private val SITE_PREFIX = Regex("""^\[[^\]]+\]\s*[^:]*:\s*""")

    /** Quita los prefijos y adornos con los que yt-dlp escribe sus errores. */
    fun clean(line: String): String {
        var text = line
        for (prefix in listOf("ERROR: ", "WARNING: ", "ERROR:", "WARNING:")) {
            if (text.startsWith(prefix)) text = text.substring(prefix.length)
        }
        // «[youtube] abc123: mensaje» → «mensaje»
        text = SITE_PREFIX.replaceFirst(text, "")
        // Se recorta la explicación entre paréntesis que yt-dlp añade al final.
        val cut = text.indexOf(" (caused by")
        if (cut >= 0) text = text.substring(0, cut)
        return text.trim()
    }
}

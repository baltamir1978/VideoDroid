package es.altamirano.linvideo.engine

import java.io.File

/** Eventos que el motor emite hacia la interfaz, leídos de la salida de yt-dlp (`DownloadJob._handle`). */
sealed interface Event {
    data class Title(val text: String) : Event
    data class Progress(
        /** 0…1, o -1 si no se sabe el total. */
        val fraction: Double,
        val speed: String,
        val speedBytes: Double,
        val eta: String,
        val size: String,
    ) : Event
    /** «Uniendo vídeo y audio», «Convirtiendo a GIF»… */
    data class Stage(val text: String) : Event
    data class Log(val line: String) : Event
    data class ProducedFile(val file: File) : Event
    data class Failed(val message: String) : Event
}

/** Lee la salida de yt-dlp línea a línea. Un objeto por descarga. */
class OutputParser {
    private val errorLines = ArrayDeque<String>()
    private val produced = mutableListOf<File>()

    /** Las últimas líneas de error, para explicar el fallo al terminar. */
    val errors: List<String> @Synchronized get() = errorLines.toList()
    /** Ficheros que yt-dlp dejó en su sitio, en orden. */
    val files: List<File> @Synchronized get() = produced.toList()

    /** Traduce una línea en eventos. Los ficheros no se anuncian aquí: se anuncian al terminar, ya renombrados. */
    @Synchronized
    fun handle(line: String): List<Event> {
        val text = line.trim()
        if (text.isEmpty()) return emptyList()
        if (text.startsWith(Arguments.PROGRESS_TAG)) return listOfNotNull(parseProgress(text))
        if (text.startsWith(Arguments.TITLE_TAG)) {
            val title = text.removePrefix(Arguments.TITLE_TAG)
            return if (title.isNotEmpty() && title != "NA") listOf(Event.Title(title)) else emptyList()
        }
        if (text.startsWith(Arguments.FILE_TAG)) {
            val path = text.removePrefix(Arguments.FILE_TAG)
            if (path.isNotEmpty() && path != "NA") produced += File(path)
            return emptyList()
        }

        val events = mutableListOf<Event>(Event.Log(text))
        val lower = text.lowercase()
        if (lower.startsWith("error") || "error:" in lower || lower.startsWith("warning:")) {
            errorLines.addLast(text)
            while (errorLines.size > 20) errorLines.removeFirst()
        }
        stageOf(text)?.let { events += Event.Stage(it) }
        return events
    }

    companion object {
        /** Fases de posprocesado que merece la pena mostrar. */
        fun stageOf(line: String): String? = when {
            "[Merger]" in line -> "Uniendo vídeo y audio"
            "[ExtractAudio]" in line -> "Extrayendo audio"
            "[VideoRemuxer]" in line -> "Empaquetando en MP4"
            "[Metadata]" in line || "[ThumbnailsConvertor]" in line -> "Añadiendo metadatos"
            else -> null
        }

        fun parseProgress(line: String): Event.Progress? {
            val fields = line.removePrefix(Arguments.PROGRESS_TAG).split("|")
            if (fields.size < 5) return null
            // yt-dlp escribe «NA» cuando no lo sabe.
            fun number(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            fun nonZero(v: Double?) = v?.takeIf { it != 0.0 }

            val downloaded = number(fields[0]) ?: 0.0
            val total = nonZero(number(fields[1])) ?: nonZero(number(fields[2])) ?: 0.0
            val speed = number(fields[3])
            val eta = number(fields[4])
            val fraction = if (total > 0) minOf(1.0, downloaded / total) else -1.0
            val size = if (total > 0) "${Formatters.humanBytes(downloaded)} / ${Formatters.humanBytes(total)}"
            else Formatters.humanBytes(downloaded)
            return Event.Progress(
                fraction = fraction,
                speed = if (speed != null) "${Formatters.humanBytes(speed)}/s" else "",
                speedBytes = speed ?: 0.0,
                eta = if (eta != null) Formatters.duration(eta) else "",
                size = size,
            )
        }

        /** Añade « (2)», « (3)»… al nombre si el archivo ya existe. */
        fun uniquePath(file: File): File {
            if (!file.exists()) return file
            val (base, ext) = Filenames.splitExtension(file.name)
            var index = 2
            while (true) {
                val name = if (ext.isNotEmpty()) "$base ($index).$ext" else "$base ($index)"
                val candidate = File(file.parentFile, name)
                if (!candidate.exists()) return candidate
                index++
            }
        }
    }
}

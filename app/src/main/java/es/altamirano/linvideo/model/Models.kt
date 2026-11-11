package es.altamirano.linvideo.model

/** Qué queremos obtener del enlace. [value] es el mismo valor JSON que en LinVideo. */
enum class MediaKind(val value: String, val title: String, val shortTitle: String, val help: String) {
    VIDEO_MAX(
        "videoMax", "Vídeo (máxima calidad)", "Vídeo",
        "Descarga la pista de vídeo y audio de mayor calidad disponible (VP9/AV1/H.264) " +
            "y las une en .mp4 sin recodificar. Calidad original.",
    ),
    VIDEO_COMPAT(
        "videoCompat", "Vídeo (máxima compatibilidad)", "Vídeo compat.",
        "Elige la mejor pista H.264 + AAC. Algo menos de calidad en YouTube, pero " +
            "reproducible en cualquier aparato, incluidos móviles y teles viejas.",
    ),
    AUDIO_MP3(
        "audioMP3", "Audio MP3", "MP3",
        "Extrae el audio y lo convierte a MP3 a la máxima calidad (recodifica, por compatibilidad).",
    ),
    AUDIO_ORIGINAL(
        "audioOriginal", "Audio original", "Audio orig.",
        "Extrae la pista de audio tal cual viene (m4a, opus, webm). Sin pérdida añadida.",
    ),
    GIF(
        "gif", "GIF", "GIF",
        "Descarga el vídeo y genera un GIF optimizado con paleta propia (ideal para " +
            "RedGifs y clips cortos).",
    );

    val isAudio get() = this == AUDIO_MP3 || this == AUDIO_ORIGINAL

    companion object {
        fun fromValue(value: String): MediaKind? = entries.firstOrNull { it.value == value }
    }
}

/** Techo de resolución para las descargas de vídeo. */
enum class VideoResolution(val value: Int, val title: String) {
    BEST(0, "Máxima"),
    UHD(2160, "4K · 2160p"),
    QHD(1440, "1440p"),
    FHD(1080, "1080p"),
    HD(720, "720p"),
    SD(480, "480p");

    /** Filtro de altura para el selector de formato de yt-dlp. */
    val heightFilter get() = if (this == BEST) "" else "[height<=$value]"

    companion object {
        fun fromValue(value: Int): VideoResolution? = entries.firstOrNull { it.value == value }
    }
}

/** Cuánto se limpia el nombre del archivo descargado. */
enum class FilenameCleanup(val value: String, val title: String, val help: String) {
    NONE("none", "Dejarlo como viene", "Se guarda con el título tal cual, incluido el identificador del vídeo."),
    BASIC(
        "basic", "Limpieza básica",
        "Quita el identificador, los emojis, los hashtags, las direcciones web y las " +
            "etiquetas de relleno («Official Video», «HD», «4K»…), y ordena espacios y guiones.",
    ),
    STRICT(
        "strict", "Limpieza estricta",
        "Además de lo anterior, quita todo lo que va entre paréntesis o corchetes, " +
            "arregla los títulos escritos en mayúsculas y acorta más el nombre.",
    );

    companion object {
        fun fromValue(value: String): FilenameCleanup? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Ajustes que se aplican a la descarga. Se guardan enteros en los ajustes, menos el recorte y el
 * formato elegido a mano, que son de cada descarga.
 *
 * En Android no se pueden leer las cookies del navegador: en lugar de `cookie_source` hay
 * [cookiesFile], la ruta de un `cookies.txt` importado (vacío: sin cookies).
 */
data class DownloadOptions(
    val cookiesFile: String = "",
    val resolution: VideoResolution = VideoResolution.BEST,
    /** Recorte por tiempo, en `mm:ss` o `hh:mm:ss`. Vacío: sin recorte. */
    val clipStart: String = "",
    val clipEnd: String = "",
    /** Selector de yt-dlp elegido a mano para esta descarga. Vacío: decide el modo. */
    val formatOverride: String = "",
    val downloadPlaylist: Boolean = false,
    /** Límite de velocidad en formato de yt-dlp («500K», «2M»). Vacío: sin límite. */
    val speedLimit: String = "",
    val filenameCleanup: FilenameCleanup = FilenameCleanup.BASIC,
    /** Conservar el identificador del vídeo al final del nombre. */
    val keepIdentifier: Boolean = false,
    val embedMetadata: Boolean = true,
    /** En audio: deducir artista y título cuando el vídeo se llama «Artista - Título». */
    val smartAudioTags: Boolean = true,
    val embedSubtitles: Boolean = false,
    val embedThumbnail: Boolean = true,
    val concurrentFragments: Int = 4,
    val gifFps: Int = 15,
    val gifWidth: Int = 480,
    val extraArguments: String = "",
) {
    companion object
}

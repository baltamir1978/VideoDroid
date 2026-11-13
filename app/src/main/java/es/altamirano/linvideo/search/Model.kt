package es.altamirano.linvideo.search

import es.altamirano.linvideo.engine.Formatters
import es.altamirano.linvideo.model.MediaKind
import java.util.Locale

enum class SearchCategory(val value: String, val title: String) {
    VIDEO("video", "Vídeo"),
    AUDIO("audio", "Audio"),
    ADULT("adult", "Para adultos"),
}

enum class SearchSource(val value: String, val title: String, val category: SearchCategory) {
    // Vídeo
    YOUTUBE("youtube", "YouTube", SearchCategory.VIDEO),
    DAILYMOTION("dailymotion", "Dailymotion", SearchCategory.VIDEO),
    PEERTUBE("peertube", "PeerTube", SearchCategory.VIDEO),
    ARCHIVE("archive", "Archive.org", SearchCategory.VIDEO),
    REDDIT("reddit", "Reddit", SearchCategory.VIDEO),
    // Audio
    SOUNDCLOUD("soundcloud", "SoundCloud", SearchCategory.AUDIO),
    MIXCLOUD("mixcloud", "Mixcloud", SearchCategory.AUDIO),
    // Para adultos
    XVIDEOS("xvideos", "XVideos", SearchCategory.ADULT),
    XNXX("xnxx", "XNXX", SearchCategory.ADULT),
    XHAMSTER("xhamster", "xHamster", SearchCategory.ADULT),
    PORNHUB("pornhub", "Pornhub", SearchCategory.ADULT),
    REDTUBE("redtube", "RedTube", SearchCategory.ADULT),
    YOUPORN("youporn", "YouPorn", SearchCategory.ADULT),
    EPORNER("eporner", "Eporner", SearchCategory.ADULT),
    REDGIFS("redgifs", "RedGifs", SearchCategory.ADULT),
    TNAFLIX("tnaflix", "TNAFlix", SearchCategory.ADULT),
    PORNTREX("porntrex", "PornTrex", SearchCategory.ADULT),
    YOUJIZZ("youjizz", "YouJizz", SearchCategory.ADULT),
    THISVID("thisvid", "ThisVid", SearchCategory.ADULT);

    /** El formato que tiene más sentido al descargar desde esta fuente. */
    val preferredKind: MediaKind
        get() = when (this) {
            SOUNDCLOUD, MIXCLOUD -> MediaKind.AUDIO_MP3
            REDGIFS -> MediaKind.GIF
            else -> MediaKind.VIDEO_MAX
        }

    /** Texto de ayuda que se muestra antes de la primera búsqueda. */
    val hint: String
        get() = when (this) {
            YOUTUBE -> "Escribe lo que quieras encontrar. Los resultados se pueden descargar como vídeo o como MP3."
            DAILYMOTION -> "Buscador oficial de Dailymotion."
            PEERTUBE -> "Red descentralizada de servidores PeerTube, a través del buscador Sepia."
            ARCHIVE -> "Fondo público de Archive.org: películas, conciertos y grabaciones de dominio público."
            REDDIT -> "Vídeos subidos a Reddit. Sólo se muestran las publicaciones con vídeo, no las de imagen o texto."
            SOUNDCLOUD -> "Pistas y mezclas de SoundCloud. Lo habitual aquí es descargar en MP3."
            MIXCLOUD -> "Sesiones y programas de Mixcloud. Lo habitual aquí es descargar en MP3."
            REDGIFS -> "Búsqueda por etiquetas en RedGifs. «GIF» convierte el clip; «Vídeo» guarda el MP4 original."
            THISVID -> "Se consulta el buscador de ThisVid. Los vídeos privados se omiten porque no se pueden descargar."
            else -> "Se consulta el buscador de $title y se muestran los vídeos encontrados."
        }

    /** Buscadores que admiten `"frase"`, `-palabra` y operadores propios (`intitle:`, `year:`…). */
    val understandsOperators get() = this in setOf(YOUTUBE, ARCHIVE, REDDIT)

    /** Fuentes que leen la web «a mano» y pueden romperse si el sitio cambia. */
    val isScraped get() = this in setOf(XVIDEOS, XNXX, XHAMSTER, PORNHUB, YOUPORN, REDDIT, TNAFLIX, PORNTREX, YOUJIZZ, THISVID)

    companion object {
        fun inCategory(category: SearchCategory) = entries.filter { it.category == category }
    }
}

/** Una web, una categoría o todas. */
data class SearchScope(val source: SearchSource? = null, val category: SearchCategory? = null) {
    val sources: List<SearchSource>
        get() = when {
            source != null -> listOf(source)
            category != null -> SearchSource.inCategory(category)
            else -> SearchSource.entries
        }

    val isMulti get() = source == null

    val title get() = source?.title ?: category?.title ?: "Todas las webs"

    val preferredKind: MediaKind
        get() = sources.map { it.preferredKind }.toSet().singleOrNull() ?: MediaKind.VIDEO_MAX

    companion object {
        fun everywhere() = SearchScope()

        fun parse(text: String): SearchScope? {
            val t = text.lowercase()
            if (t == "all" || t == "todas") return everywhere()
            SearchCategory.entries.firstOrNull { it.value == t }?.let { return SearchScope(category = it) }
            return SearchSource.entries.firstOrNull { it.value == t }?.let { SearchScope(source = it) }
        }
    }
}

data class SearchResult(
    val id: String,
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String? = null,
    val duration: Double? = null,
    val uploader: String? = null,
    val views: Long? = null,
    /** Se rellena al devolver los resultados; importa cuando se busca en varias webs. */
    val source: SearchSource = SearchSource.YOUTUBE,
) {
    val durationText: String? get() = duration?.takeIf { it > 0 }?.let(Formatters::duration)

    val viewsText: String?
        get() {
            val v = views?.takeIf { it > 0 } ?: return null
            return when {
                v >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM visitas", v / 1_000_000.0).replace('.', ',')
                v >= 1_000 -> "${v / 1_000}K visitas"
                else -> "$v visitas"
            }
        }
}

class SearchError(message: String) : Exception(message)

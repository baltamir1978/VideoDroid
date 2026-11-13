package es.altamirano.linvideo.search

import java.text.Normalizer

/** Duración de un resultado, con los cortes de YouTube (4 y 20 minutos). */
enum class SearchLength(val title: String) {
    SHORT("Corto"),
    MEDIUM("Medio"),
    LONG("Largo");

    fun matches(seconds: Double): Boolean = when (this) {
        SHORT -> seconds < 4 * 60
        MEDIUM -> seconds >= 4 * 60 && seconds <= 20 * 60
        LONG -> seconds > 20 * 60
    }
}

enum class SearchOrder(val title: String) {
    /** El de las webs: su relevancia, mezclada por rondas. */
    ARRIVAL("Como llegan"),
    VIEWS("Más vistos"),
    LONGEST("Más largos"),
    SHORTEST("Más cortos"),
}

/** Lo que se elige bajo el campo de búsqueda. Se aplica a lo ya cargado: no vuelve a preguntar a las webs. */
data class Refinement(
    /** Exigir todas las palabras de la consulta en el título o en el autor. */
    val allWords: Boolean = false,
    val length: SearchLength? = null,
    val order: SearchOrder = SearchOrder.ARRIVAL,
) {
    val filters get() = allWords || length != null
}

/**
 * Una consulta separada en lo que entiende la app: palabras, `"frases exactas"`, `-exclusiones`
 * y operadores de la web (`intitle:x`, `year:1975`…), que aquí no se interpretan.
 */
data class ParsedQuery(
    val raw: String,
    val words: List<String>,
    val phrases: List<List<String>>,
    /** Cada exclusión es una secuencia: una palabra suelta o una frase entre comillas. */
    val excluded: List<List<String>>,
    /** Lo que se manda a las webs que no entienden operadores: sólo lo que se busca, sin signos. */
    val plain: String,
)

/**
 * Afinar los resultados en el móvil. Casi ninguna de las webs admite comillas ni exclusiones, y
 * cada una ordena a su manera: filtrando aquí, lo mismo vale para todas. La contrapartida es que
 * sólo se filtra lo que ya ha llegado.
 */
object Refine {
    private val QUOTED = Regex("""(-?)([A-Za-z]+:)?"([^"]*)"""")
    private val OPERATOR = Regex("""-?[A-Za-z]+:\S+""")

    fun parse(query: String): ParsedQuery {
        val words = mutableListOf<String>()
        val phrases = mutableListOf<List<String>>()
        val excluded = mutableListOf<List<String>>()
        val plain = mutableListOf<String>()

        val rest = QUOTED.replace(query) { m ->
            val (minus, operator, text) = m.destructured
            val tokens = tokens(text)
            when {
                // `creator:"fulano"` habla de un campo que aquí no se ve: se deja a la web.
                operator.isNotEmpty() || tokens.isEmpty() -> {}
                minus.isNotEmpty() -> excluded += tokens
                else -> {
                    phrases += tokens
                    plain += text.trim()
                }
            }
            " "
        }
        for (piece in rest.split(Regex("\\s+"))) {
            if (piece.isEmpty() || OPERATOR.matches(piece)) continue
            val negative = piece.length > 1 && piece.startsWith("-")
            val tokens = tokens(piece)
            if (tokens.isEmpty()) continue
            if (negative) {
                excluded += tokens
            } else {
                words += tokens
                plain += piece
            }
        }
        return ParsedQuery(query.trim(), words, phrases, excluded, plain.joinToString(" "))
    }

    /** Filtra y ordena. Las exclusiones y las frases valen siempre: quien las escribe las quiere. */
    fun apply(results: List<SearchResult>, query: ParsedQuery, refinement: Refinement): List<SearchResult> {
        val kept = results.filter { result ->
            val title = tokens(result.title)
            val uploader = tokens(result.uploader.orEmpty())
            val all = title + uploader
            val duration = result.duration?.takeIf { it > 0 }
            // Sin duración conocida no se puede decir que cumpla: fuera mientras el filtro esté puesto.
            (refinement.length == null || (duration != null && refinement.length.matches(duration))) &&
                query.excluded.none { contains(title, it, prefix = true) || contains(uploader, it, prefix = true) } &&
                query.phrases.all { contains(title, it, prefix = false) || contains(uploader, it, prefix = false) } &&
                (!refinement.allWords || query.words.all { word -> all.any { it.startsWith(word) } })
        }
        // `sortedBy` es estable: a igualdad (o sin dato) se conserva el orden de llegada.
        return when (refinement.order) {
            SearchOrder.ARRIVAL -> kept
            SearchOrder.VIEWS -> kept.sortedBy { -(it.views?.takeIf { v -> v > 0 } ?: -1L) }
            SearchOrder.LONGEST -> kept.sortedBy { -(it.duration?.takeIf { d -> d > 0 } ?: -1.0) }
            SearchOrder.SHORTEST -> kept.sortedBy { it.duration?.takeIf { d -> d > 0 } ?: Double.MAX_VALUE }
        }
    }

    /**
     * Palabras comparables: minúsculas y sin tildes, pero con la ñ («ano» no es «año»).
     * Una palabra de la consulta casa con el principio de una del título («gato» con «gatos»).
     */
    fun tokens(text: String): List<String> {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val plain = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                ch == '̃' && plain.endsWith('n') -> plain[plain.length - 1] = 'ñ'
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> {}
                Character.isLetterOrDigit(ch) -> plain.append(ch)
                else -> plain.append(' ')
            }
        }
        return plain.split(' ').filter { it.isNotEmpty() }
    }

    /** ¿Aparece la secuencia seguida? Con [prefix], la última palabra puede ser sólo el principio. */
    private fun contains(haystack: List<String>, needle: List<String>, prefix: Boolean): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        return (0..haystack.size - needle.size).any { start ->
            needle.indices.all { i ->
                val word = haystack[start + i]
                if (prefix && i == needle.lastIndex) word.startsWith(needle[i]) else word == needle[i]
            }
        }
    }
}

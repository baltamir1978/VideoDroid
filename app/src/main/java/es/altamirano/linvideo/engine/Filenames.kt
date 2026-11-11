package es.altamirano.linvideo.engine

import es.altamirano.linvideo.model.FilenameCleanup

/**
 * Convierte el título que da la web en un nombre de archivo presentable (`filenames.py`).
 * Se aplica renombrando el archivo ya descargado. Las expresiones llevan `(?U)` para que `\w`,
 * `\s`, `\b` y las mayúsculas se comporten con Unicode, como en Python.
 */
object Filenames {
    /** Etiquetas de relleno que suelen colgar de los títulos. */
    val FILLER_WORDS = listOf(
        "official video", "official music video", "official audio", "official lyric video",
        "official visualizer", "official trailer", "lyric video", "lyrics", "visualizer",
        "video oficial", "audio oficial", "vídeo oficial", "videoclip oficial", "letra",
        "full hd", "fullhd", "hd", "hq", "sd", "4k", "8k", "2160p", "1440p", "1080p", "720p",
        "480p", "60fps", "50fps", "uhd", "remastered", "remasterizado",
        "free download", "descarga gratis", "download free", "watch free", "gratis",
        "full video", "vídeo completo", "video completo", "complete video",
        "porn", "xxx", "sex video",
    )

    private class Filler(word: String) {
        private val e = Regex.escape(word)
        val bracketed = Regex("""(?iU)\s*[\[(]\s*$e\s*[\])]""")
        val separated = Regex("""(?iU)\s*[-–—|·]\s*$e\s*(?=$|[-–—|·])""")
        val anywhere = Regex("""(?iU)\b$e\b""")
        val trailing = Regex("""(?iU)\s*[-–—|·]?\s*$e\s*$""")
    }

    private val FILLERS = FILLER_WORDS.map(::Filler)

    fun clean(rawName: String, level: FilenameCleanup, keepIdentifier: Boolean = false): String {
        if (level == FilenameCleanup.NONE) return rawName
        val strict = level == FilenameCleanup.STRICT
        val (base, ext) = splitExtension(rawName)
        var name = base

        if (!keepIdentifier) name = removeTrailingIdentifier(name)
        name = removeEmoji(name)
        name = removeHashtags(name)
        name = removeWebAddresses(name)
        if (strict) name = removeBracketedText(name)
        name = removeFillerTags(name, strict)
        name = replaceProblematicCharacters(name)
        name = collapseSeparators(name)
        if (strict) name = fixShouting(name)

        name = truncate(name, if (strict) 90 else 120)
        name = name.trim { it in " -_.,;·|" }

        // Si la limpieza se lo ha llevado todo, mejor el nombre original.
        if (name.isEmpty()) return rawName
        return if (ext.isNotEmpty()) "$name.$ext" else name
    }

    /**
     * Separa nombre y extensión. Sólo cuenta como extensión un sufijo corto y alfanumérico tras el
     * último punto, para no destrozar títulos con puntos. Una barra en el título no es una carpeta.
     */
    fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name to ""
        val candidate = name.substring(dot + 1)
        if (candidate.length in 1..5 && candidate.all { it.isLetterOrDigit() }) {
            return name.substring(0, dot) to candidate
        }
        return name to ""
    }

    // --- Pasos ---------------------------------------------------------------------

    private val TRAILING_ID = Regex("""\s*[\[(][A-Za-z0-9_\-]{5,}[\])]\s*$""")

    /** Quita el « [dQw4w9WgXcQ]» final que añade la plantilla de descarga. */
    fun removeTrailingIdentifier(name: String) = TRAILING_ID.replace(name, "")

    private fun isEmoji(cp: Int): Boolean =
        cp >= 0x1F000 ||
            cp in 0x2600..0x27BF ||      // símbolos varios y dingbats
            cp in 0x238D..0x23FF ||      // ⌚ ⏰ ⏩…
            cp in 0x25A0..0x25FF ||      // ▪ ▶ ◀ ◻ ◼
            cp in 0x2B00..0x2BFF ||      // ⭐ ⬛ ⬆…
            cp in 0xFE00..0xFE0F ||      // selectores de variación
            cp in intArrayOf(0x200D, 0x20E3, 0x24C2, 0x3030, 0x303D, 0x3297, 0x3299)

    fun removeEmoji(name: String): String {
        val out = StringBuilder()
        name.codePoints().forEach { if (!isEmoji(it)) out.appendCodePoint(it) }
        return out.toString()
    }

    private val HASHTAG = Regex("""(?U)#\w+""")
    fun removeHashtags(name: String) = HASHTAG.replace(name, " ")

    private val URL = Regex("""(?iU)\bhttps?://\S+""")
    private val DOMAIN = Regex("""(?iU)\b(?:www\.)?[a-z0-9\-]{3,}\.(?:com|net|org|tv|xxx|es|to|cc|me)\b(?:/\S*)?""")
    fun removeWebAddresses(name: String) = DOMAIN.replace(URL.replace(name, " "), " ")

    private val PARENS = Regex("""\([^()]*\)""")
    private val BRACKETS = Regex("""\[[^\[\]]*\]""")
    fun removeBracketedText(name: String) = BRACKETS.replace(PARENS.replace(name, " "), " ")

    /** Quita etiquetas de relleno, tanto entre paréntesis como sueltas al final. */
    fun removeFillerTags(name: String, strict: Boolean): String {
        var result = name
        for (f in FILLERS) {
            result = f.bracketed.replace(result, " ")
            result = f.separated.replace(result, " ")
            if (strict) result = f.anywhere.replace(result, " ")
        }
        // Etiquetas pegadas al final sin separador («… 4K FULL HD»): varias pasadas.
        repeat(3) { for (f in FILLERS) result = f.trailing.replace(result, "") }
        return result
    }

    private val SWAPS = listOf(
        "/" to "-", "\\" to "-", ":" to " -", "|" to "-",
        "*" to "", "?" to "", "\"" to "'", "<" to "", ">" to "",
        " " to " ",   // espacio duro
        "​" to "",    // espacio de ancho cero
    )

    /** Cambia lo que da problemas en nombres de archivo, también al copiarlos a FAT y exFAT. */
    fun replaceProblematicCharacters(name: String): String {
        var result = name
        for ((old, new) in SWAPS) result = result.replace(old, new)
        return result.filter { it.code > 0x1F }
    }

    private val EMPTY_BRACKETS = Regex("""[\[(]\s*[\])]""")
    private val SPACES = Regex("""(?U)\s+""")
    private val REPEATED_SEP = Regex("""(?U)\s*([-–—|·])\s*\1+""")
    private val LEADING = Regex("""(?U)^[\s\-–—|·,.]+""")
    private val TRAILING = Regex("""(?U)[\s\-–—|·,.]+$""")
    private val SPACE_BEFORE_PUNCT = Regex("""(?U)\s+([,;.!?])""")

    /** Espacios y guiones repetidos, y paréntesis que se han quedado vacíos. */
    fun collapseSeparators(name: String): String {
        var result = EMPTY_BRACKETS.replace(name, " ")
        result = SPACES.replace(result, " ")
        result = REPEATED_SEP.replace(result, " $1 ")
        result = LEADING.replace(result, "")
        result = TRAILING.replace(result, "")
        result = SPACE_BEFORE_PUNCT.replace(result, "$1")
        return result.trim()
    }

    private val WORD = Regex("""\S+""")

    /** «ESTO ES UN TÍTULO A GRITOS» → «Esto Es Un Título A Gritos». */
    fun fixShouting(name: String): String {
        val letters = name.filter { it.isLetter() }
        if (letters.length < 8) return name
        val uppercase = letters.count { it.isUpperCase() }
        if (uppercase.toDouble() / letters.length <= 0.8) return name
        // Palabra a palabra, para no dejar «Don'T».
        return WORD.replace(name) { m ->
            val w = m.value
            w.take(1).uppercase() + w.drop(1).lowercase()
        }
    }

    /** Recorta sin partir palabras por la mitad. */
    fun truncate(name: String, limit: Int): String {
        if (name.length <= limit) return name
        val cut = name.substring(0, limit)
        val space = cut.lastIndexOf(' ')
        return if (space > limit / 2) cut.substring(0, space) else cut
    }
}

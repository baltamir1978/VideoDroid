package es.altamirano.linvideo.search

import es.altamirano.linvideo.engine.uRegex
import java.text.Normalizer

/** Utilidades de texto de `search.py`. */
object Text {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    /** Primer grupo de la primera coincidencia; el punto cruza saltos de línea, como `re.S`. */
    fun firstMatch(text: String, pattern: String): String? {
        val regex = cache.getOrPut(pattern) { Regex(pattern, RegexOption.DOT_MATCHES_ALL) }
        return regex.find(text)?.groupValues?.get(1)
    }

    /** Las miniaturas llegan a veces sin esquema («//cdn…»). */
    fun absoluteUrl(text: String): String {
        val decoded = decodeEntities(text)
        return if (decoded.startsWith("//")) "https:$decoded" else decoded
    }

    /** Un título como parte legible de la URL. El sitio redirige al slug real: basta con algo válido. */
    fun slugify(text: String): String {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFKD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val words = Regex("[A-Za-z0-9]+").findAll(plain).map { it.value }
        val slug = words.joinToString("_").take(60).lowercase().trimEnd('_')
        return slug.ifEmpty { "video" }
    }

    /** El primer objeto JSON completo que sigue a un marcador, contando llaves. */
    fun extractBalancedJson(text: String, marker: String): String? {
        val position = text.indexOf(marker)
        if (position < 0) return null
        val start = text.indexOf('{', position + marker.length)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
            } else when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    /** «1.2M», «340K» o «5.320» como número de visitas. */
    fun parseCompactNumber(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val multiplier = when (trimmed.last()) {
            'M', 'm' -> 1_000_000
            'K', 'k' -> 1_000
            else -> 1
        }
        // Sin multiplicador, el punto y la coma separan miles; con él, son decimales.
        val digits = if (multiplier == 1) trimmed.replace(".", "").replace(",", "")
        else trimmed.dropLast(1).replace(",", ".")
        val value = digits.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return (value * multiplier).toLong()
    }

    private val DURATION_PAIRS = uRegex("""([0-9]+)\s*(h|min|sec|s)\b""")

    /** «32 min», «1 h 5 min», «12 sec» o «mm:ss» en segundos. */
    fun parseLooseDuration(text: String): Double? {
        val lower = text.lowercase()
        val factors = mapOf("h" to 3600, "min" to 60, "sec" to 1, "s" to 1)
        val pairs = DURATION_PAIRS.findAll(lower).toList()
        if (pairs.isNotEmpty()) {
            return pairs.sumOf { it.groupValues[1].toLong() * factors.getValue(it.groupValues[2]) }.toDouble()
        }
        val parts = lower.split(":").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size < 2) return null
        return parts.fold(0.0) { total, part -> total * 60 + part }
    }

    /** Entidades HTML de los títulos (`&amp;`, `&eacute;`, `&#233;`…), como `html.unescape`. */
    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { m ->
            val body = m.groupValues[1]
            val decoded = when {
                body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED[body]
            }
            decoded ?: m.value
        }.replace(' ', ' ')
    }

    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});")

    private fun codePoint(cp: Int): String = when {
        cp == 0 || cp in 0xD800..0xDFFF || cp > 0x10FFFF -> "�"
        // Como html.unescape: los números de Windows-1252 se leen como tales.
        cp in 0x80..0x9F -> WINDOWS_1252[cp - 0x80]?.toString() ?: String(Character.toChars(cp))
        else -> String(Character.toChars(cp))
    }

    private val WINDOWS_1252: Array<Char?> = arrayOf(
        '€', null, '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', null, 'Ž', null,
        null, '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', null, 'ž', 'Ÿ',
    )

    /** Las entidades con nombre que aparecen en títulos: todo Latin-1 y la tipografía habitual. */
    private val NAMED: Map<String, String> = buildMap {
        val latin1 = listOf(
            "nbsp", "iexcl", "cent", "pound", "curren", "yen", "brvbar", "sect", "uml", "copy", "ordf",
            "laquo", "not", "shy", "reg", "macr", "deg", "plusmn", "sup2", "sup3", "acute", "micro",
            "para", "middot", "cedil", "sup1", "ordm", "raquo", "frac14", "frac12", "frac34", "iquest",
            "Agrave", "Aacute", "Acirc", "Atilde", "Auml", "Aring", "AElig", "Ccedil", "Egrave",
            "Eacute", "Ecirc", "Euml", "Igrave", "Iacute", "Icirc", "Iuml", "ETH", "Ntilde", "Ograve",
            "Oacute", "Ocirc", "Otilde", "Ouml", "times", "Oslash", "Ugrave", "Uacute", "Ucirc",
            "Uuml", "Yacute", "THORN", "szlig", "agrave", "aacute", "acirc", "atilde", "auml", "aring",
            "aelig", "ccedil", "egrave", "eacute", "ecirc", "euml", "igrave", "iacute", "icirc", "iuml",
            "eth", "ntilde", "ograve", "oacute", "ocirc", "otilde", "ouml", "divide", "oslash",
            "ugrave", "uacute", "ucirc", "uuml", "yacute", "thorn", "yuml",
        )
        latin1.forEachIndexed { i, name -> put(name, (0xA0 + i).toChar().toString()) }
        putAll(
            mapOf(
                "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
                "hellip" to "…", "ndash" to "–", "mdash" to "—", "lsquo" to "‘", "rsquo" to "’",
                "sbquo" to "‚", "ldquo" to "“", "rdquo" to "”", "bdquo" to "„", "bull" to "•",
                "euro" to "€", "trade" to "™", "dagger" to "†", "Dagger" to "‡", "permil" to "‰",
                "lsaquo" to "‹", "rsaquo" to "›", "OElig" to "Œ", "oelig" to "œ", "Scaron" to "Š",
                "scaron" to "š", "Yuml" to "Ÿ", "fnof" to "ƒ", "circ" to "ˆ", "tilde" to "˜",
                "ensp" to " ", "emsp" to " ", "thinsp" to " ", "zwnj" to "‌",
                "zwj" to "‍", "lrm" to "‎", "rlm" to "‏", "hearts" to "♥", "star" to "☆",
                "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓", "infin" to "∞",
                "num" to "#", "excl" to "!", "quest" to "?", "comma" to ",", "period" to ".",
                "colon" to ":", "semi" to ";", "lpar" to "(", "rpar" to ")", "lsqb" to "[",
                "rsqb" to "]", "lbrack" to "[", "rbrack" to "]", "sol" to "/", "bsol" to "\\",
                "vert" to "|", "verbar" to "|", "ast" to "*", "plus" to "+", "equals" to "=",
                "percnt" to "%", "dollar" to "$", "commat" to "@", "lowbar" to "_", "Hat" to "^",
                "grave" to "`", "lcub" to "{", "rcub" to "}", "lbrace" to "{", "rbrace" to "}",
            ),
        )
    }

    /** `urllib.parse.quote(text, safe="")`: todo menos letras, cifras y `_.-~`, en UTF-8. */
    fun quote(text: String): String = buildString {
        for (b in text.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF)
            if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c.toChar() in "_.-~") {
                append(c.toChar())
            } else {
                append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
        }
    }

    /** `urllib.parse.quote_plus`: como [quote] pero el espacio va como «+». */
    fun quotePlus(text: String): String = text.split(' ').joinToString("+") { quote(it) }
}

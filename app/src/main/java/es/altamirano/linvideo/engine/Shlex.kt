package es.altamirano.linvideo.engine

/**
 * Parte una línea como `shlex.split` de Python en modo POSIX: espacios separan, las comillas
 * simples y dobles agrupan, la barra invertida escapa. Devuelve null si queda una comilla abierta
 * o una barra al final (en Python, `ValueError`).
 */
object Shlex {
    fun split(text: String): List<String>? {
        val out = mutableListOf<String>()
        val word = StringBuilder()
        var inWord = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> {
                    if (inWord) { out += word.toString(); word.clear(); inWord = false }
                }
                c == '\'' -> {
                    val end = text.indexOf('\'', i + 1)
                    if (end < 0) return null
                    word.append(text, i + 1, end)
                    inWord = true
                    i = end
                }
                c == '"' -> {
                    inWord = true
                    i++
                    while (true) {
                        if (i >= text.length) return null
                        val d = text[i]
                        if (d == '"') break
                        // Dentro de comillas dobles, la barra sólo escapa \ " $ ` y el salto de línea.
                        if (d == '\\' && i + 1 < text.length && text[i + 1] in "\\\"$`\n") {
                            word.append(text[i + 1]); i += 2; continue
                        }
                        word.append(d)
                        i++
                    }
                }
                c == '\\' -> {
                    if (i + 1 >= text.length) return null
                    word.append(text[i + 1])
                    inWord = true
                    i++
                }
                else -> { word.append(c); inWord = true }
            }
            i++
        }
        if (inWord) out += word.toString()
        return out
    }
}

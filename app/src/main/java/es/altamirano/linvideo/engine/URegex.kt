package es.altamirano.linvideo.engine

/** Android usa ICU, que no admite la bandera `(?U)` pero ya trata `\w`, `\s` y `\b` como Unicode. */
private val IS_ANDROID = System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == true

/**
 * Expresión con `\w`, `\s`, `\b` y mayúsculas Unicode, como `re` de Python: en la JVM (pruebas)
 * se añade `(?U)`; en Android no hace falta y además rompería.
 */
fun uRegex(pattern: String, ignoreCase: Boolean = false): Regex {
    val flags = buildString {
        if (ignoreCase) append('i')
        if (!IS_ANDROID) append('U')
    }
    return Regex(if (flags.isEmpty()) pattern else "(?$flags)$pattern")
}

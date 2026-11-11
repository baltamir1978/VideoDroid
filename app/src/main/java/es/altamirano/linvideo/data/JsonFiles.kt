package es.altamirano.linvideo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

internal val PrettyJson = Json { prettyPrint = true }

/** Escribe en un temporal y lo cambia de nombre: nunca queda un JSON a medias. */
internal fun writeAtomic(file: File, data: JsonElement) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, file.name + ".tmp")
    temporary.writeText(PrettyJson.encodeToString(JsonElement.serializer(), data))
    if (!temporary.renameTo(file)) {
        file.delete()
        if (!temporary.renameTo(file)) throw java.io.IOException("No se pudo escribir $file")
    }
}

internal fun readJson(file: File): JsonElement? =
    try { Json.parseToJsonElement(file.readText()) } catch (e: Exception) { null }

package es.altamirano.linvideo.data

import es.altamirano.linvideo.model.PendingEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Las descargas **sin terminar**, para recuperarlas si la app se cierra a medias.
 * No es un historial: lo que se completa desaparece, y sin nada pendiente el archivo se borra.
 * Al volver a abrir no se reanuda nada solo: se enseña la lista para elegir.
 */
class PendingQueue(val file: File) {
    @Synchronized
    fun save(entries: List<PendingEntry>) {
        if (entries.isEmpty()) {
            clear()
            return
        }
        try {
            writeAtomic(file, JsonArray(entries.map { it.toJson() }))
        } catch (e: Exception) {
            // Que no se pueda guardar la cola no debe estropear la descarga en curso.
        }
    }

    @Synchronized
    fun load(): List<PendingEntry> {
        val data = readJson(file) as? JsonArray ?: return emptyList()
        return data.mapNotNull { (it as? JsonObject)?.let(PendingEntry::fromJson) }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }
}

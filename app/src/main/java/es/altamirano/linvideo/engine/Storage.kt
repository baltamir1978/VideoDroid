package es.altamirano.linvideo.engine

import android.os.Environment
import es.altamirano.linvideo.model.Edition
import java.io.File

/** Dónde se guardan las descargas. */
object Storage {
    /** Subcarpeta de `Download/` por defecto (el nombre de la app). Con almacenamiento acotado se escribe sin permisos. */
    val DEFAULT_FOLDER = Edition.NAME


    fun downloadDirectory(folder: String = DEFAULT_FOLDER): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), cleanFolder(folder))

    /** Una subcarpeta de `Download/`, sin salirse de ella. Vacía o inválida → la de por defecto. */
    fun cleanFolder(folder: String): String {
        val parts = folder.split('/', '\\')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return if (parts.isEmpty()) DEFAULT_FOLDER else parts.joinToString("/")
    }
}

package es.altamirano.linvideo.queue

import android.content.Context
import androidx.core.content.ContextCompat
import android.content.Intent
import es.altamirano.linvideo.data.PendingQueue
import es.altamirano.linvideo.data.SettingsRepository
import es.altamirano.linvideo.engine.Storage
import es.altamirano.linvideo.engine.YtdlpJob
import es.altamirano.linvideo.model.DownloadOptions
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.model.PendingEntry
import es.altamirano.linvideo.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** La cola de la app, con su estado publicado para la interfaz y el servicio. Una por proceso. */
class QueueHub(private val context: Context, private val settings: SettingsRepository) {
    private val pending = PendingQueue(File(context.filesDir, "pendientes.json"))

    /** Lo que quedó sin terminar la última vez, hasta que el usuario decide qué recuperar. */
    private val _recoverable = MutableStateFlow(pending.load())
    val recoverable: StateFlow<List<PendingEntry>> = _recoverable.asStateFlow()

    private val _items = MutableStateFlow<List<ItemSnapshot>>(emptyList())
    val items: StateFlow<List<ItemSnapshot>> = _items.asStateFlow()

    private val _summary = MutableStateFlow(BatchSummary(0, 0, 0, 0, 0, 0.0, 0.0))
    val summary: StateFlow<BatchSummary> = _summary.asStateFlow()

    val queue = DownloadQueue(
        maxConcurrent = { settings.settings.value.maxConcurrent },
        onChange = { publish() },
        // Mientras no se decide qué recuperar, lo de la vez anterior sigue guardado.
        saver = { entries ->
            val undecided = _recoverable.value.filter { old -> entries.none { it.id == old.id } }
            pending.save(entries + undecided)
        },
        jobFactory = { YtdlpJob(context, it) },
    )

    private fun publish() {
        _items.value = queue.snapshot()
        _summary.value = queue.summary()
    }

    /** Añade un enlace con los ajustes guardados; [kind] y los de esta descarga mandan sobre ellos. */
    fun add(
        url: String,
        kind: MediaKind? = null,
        title: String = "",
        tweak: (DownloadOptions) -> DownloadOptions = { it },
    ): PendingEntry {
        val s = settings.settings.value
        val entry = PendingEntry(
            url = url,
            kind = kind ?: s.kind,
            destination = Storage.downloadDirectory(s.destination).absolutePath,
            title = title,
            options = tweak(s.options),
        )
        queue.add(entry)
        startService()
        return entry
    }

    /** Cambia el tipo de una descarga que aún no ha terminado (desde la notificación de «Compartir»). */
    fun changeKind(id: String, kind: MediaKind): PendingEntry? {
        val item = queue.find(id) ?: return null
        if (item.state.finished || item.entry.kind == kind) return null
        queue.remove(item)
        val entry = item.entry.copy(kind = kind, id = java.util.UUID.randomUUID().toString().replace("-", ""))
        queue.add(entry)
        startService()
        return entry
    }

    fun recover(selected: List<PendingEntry>) {
        _recoverable.value = emptyList()
        selected.forEach { queue.add(it) }
        // Lo que no se ha elegido sale del archivo.
        queue.save()
        if (selected.isNotEmpty()) startService()
    }

    fun cancel(id: String) = queue.find(id)?.let(queue::cancel)
    fun remove(id: String) = queue.find(id)?.let(queue::remove)
    fun retry(id: String) {
        queue.find(id)?.let(queue::retry) ?: return
        startService()
    }
    fun clearFinished() = queue.clearFinished()
    fun cancelAll() = queue.cancelAll()
    fun logOf(id: String) = queue.logOf(id)

    private fun startService() {
        if (!queue.summary().active) return
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }
}

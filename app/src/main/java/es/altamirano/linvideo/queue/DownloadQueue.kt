package es.altamirano.linvideo.queue

import es.altamirano.linvideo.engine.DownloadJob
import es.altamirano.linvideo.engine.DownloadRequest
import es.altamirano.linvideo.engine.Event
import es.altamirano.linvideo.model.PendingEntry
import java.io.File

/** Una descarga que ya está convirtiendo con ffmpeg cuenta como casi terminada. */
const val POSTPROCESS_FRACTION = 0.95
const val LOG_LIMIT = 2000

enum class JobState(val title: String) {
    WAITING("Esperando"),
    RUNNING("Descargando"),
    DONE("Terminada"),
    FAILED("Falló"),
    CANCELLED("Cancelada");

    val finished get() = this == DONE || this == FAILED || this == CANCELLED
}

/** Estado vivo de una fila. Sólo se toca con el cerrojo de la cola. */
class QueueItem(val entry: PendingEntry) {
    var state = JobState.WAITING
    var title = ""
    /** -1 si no se sabe el total. */
    var fraction = 0.0
    var speed = ""
    var speedBytes = 0.0
    var eta = ""
    var size = ""
    var stage = ""
    var error = ""
    var files = mutableListOf<File>()
    val log = ArrayDeque<String>()
    var job: DownloadJob? = null

    val displayTitle get() = title.ifEmpty { entry.title.ifEmpty { entry.url } }

    /** Lo que aporta al porcentaje del lote (0…1). */
    val batchFraction: Double
        get() = when {
            state == JobState.DONE -> 1.0
            state != JobState.RUNNING -> 0.0
            stage.isNotEmpty() -> POSTPROCESS_FRACTION
            else -> maxOf(0.0, fraction) * POSTPROCESS_FRACTION
        }

    fun snapshot() = ItemSnapshot(
        id = entry.id, url = entry.url, kind = entry.kind, displayTitle = displayTitle,
        state = state, fraction = fraction, speed = speed, eta = eta, size = size,
        stage = stage, error = error, files = files.toList(),
    )
}

/** Copia inmutable de una fila, para la interfaz. */
data class ItemSnapshot(
    val id: String,
    val url: String,
    val kind: es.altamirano.linvideo.model.MediaKind,
    val displayTitle: String,
    val state: JobState,
    val fraction: Double,
    val speed: String,
    val eta: String,
    val size: String,
    val stage: String,
    val error: String,
    val files: List<File>,
)

data class BatchSummary(
    /** Sin contar las canceladas ni las fallidas. */
    val total: Int,
    val done: Int,
    val running: Int,
    val waiting: Int,
    val failed: Int,
    /** 0…1 */
    val fraction: Double,
    val speedBytes: Double,
) {
    val unfinished get() = running + waiting
    val active get() = unfinished > 0
}

/**
 * La cola de descargas (`queue.py`): varias a la vez, estado de cada una y el lote. No sabe nada
 * de Android: avisa con [onChange] desde el hilo que toque. Así se prueba en la JVM.
 */
class DownloadQueue(
    private val maxConcurrent: () -> Int,
    private val onChange: (QueueItem) -> Unit,
    /** Guarda lo pendiente; null para no guardar nada. */
    private val saver: ((List<PendingEntry>) -> Unit)?,
    private val starter: (() -> Unit) -> Unit = { work -> Thread(work, "descarga").apply { isDaemon = true }.start() },
    private val jobFactory: (DownloadRequest) -> DownloadJob,
) {
    private val lock = Any()
    private val _items = mutableListOf<QueueItem>()
    @Volatile private var persist = saver != null

    val items: List<QueueItem> get() = synchronized(lock) { _items.toList() }

    fun snapshot(): List<ItemSnapshot> = synchronized(lock) { _items.map { it.snapshot() } }

    fun find(id: String): QueueItem? = synchronized(lock) { _items.firstOrNull { it.entry.id == id } }

    fun logOf(id: String): List<String> = synchronized(lock) { find(id)?.log?.toList().orEmpty() }

    // --- Lo que pide la interfaz -------------------------------------------------------

    fun add(entry: PendingEntry): QueueItem {
        val item = QueueItem(entry)
        synchronized(lock) { _items += item }
        changed(item)
        pump()
        return item
    }

    fun cancel(item: QueueItem) {
        val job: DownloadJob?
        synchronized(lock) {
            if (item.state.finished) return
            job = item.job
            item.state = JobState.CANCELLED
            item.speed = ""; item.eta = ""; item.stage = ""
            item.speedBytes = 0.0
        }
        job?.cancel()
        changed(item)
        pump()
    }

    fun cancelAll() {
        val pending = synchronized(lock) { _items.filter { !it.state.finished } }
        pending.forEach(::cancel)
    }

    fun retry(item: QueueItem) {
        synchronized(lock) {
            if (item.state != JobState.FAILED && item.state != JobState.CANCELLED) return
            item.state = JobState.WAITING
            item.error = ""; item.stage = ""; item.speed = ""; item.eta = ""
            item.fraction = 0.0
            item.speedBytes = 0.0
            item.files = mutableListOf()
            item.job = null
        }
        changed(item)
        pump()
    }

    fun remove(item: QueueItem) {
        cancel(item)
        synchronized(lock) { _items.remove(item) }
        save()
        onChange(item)
    }

    fun clearFinished(): List<QueueItem> {
        val gone: List<QueueItem>
        synchronized(lock) {
            gone = _items.filter { it.state.finished }
            _items.removeAll(gone)
        }
        gone.firstOrNull()?.let(onChange)
        return gone
    }

    /** Para los procesos sin tocar la cola guardada, para ofrecerla al volver. */
    fun stopForExit() {
        val jobs: List<DownloadJob>
        synchronized(lock) {
            save()
            persist = false
            jobs = _items.filter { it.state == JobState.RUNNING }.mapNotNull { it.job }
        }
        jobs.forEach { it.cancel() }
    }

    fun summary(): BatchSummary = synchronized(lock) {
        val counted = _items.filter { it.state != JobState.CANCELLED && it.state != JobState.FAILED }
        val total = counted.size
        BatchSummary(
            total = total,
            done = counted.count { it.state == JobState.DONE },
            running = counted.count { it.state == JobState.RUNNING },
            waiting = counted.count { it.state == JobState.WAITING },
            failed = _items.count { it.state == JobState.FAILED },
            fraction = if (total > 0) counted.sumOf { it.batchFraction } / total else 0.0,
            speedBytes = counted.filter { it.state == JobState.RUNNING }.sumOf { it.speedBytes },
        )
    }

    // --- Por dentro --------------------------------------------------------------------

    /** Arranca las que esperan mientras haya hueco. */
    private fun pump() {
        val toStart = mutableListOf<Pair<QueueItem, DownloadJob>>()
        synchronized(lock) {
            var running = _items.count { it.state == JobState.RUNNING }
            for (item in _items) {
                if (running >= maxOf(1, maxConcurrent())) break
                if (item.state == JobState.WAITING) {
                    item.state = JobState.RUNNING
                    val e = item.entry
                    val job = jobFactory(DownloadRequest(e.url, e.kind, File(e.destination), e.options))
                    item.job = job
                    running++
                    toStart += item to job
                }
            }
        }
        for ((item, job) in toStart) {
            changed(item)
            starter { run(item, job) }
        }
    }

    private fun run(item: QueueItem, job: DownloadJob) {
        val ok = try {
            job.run { event(item, it) }
        } catch (e: Exception) {
            // Que un fallo raro no deje la fila «descargando».
            event(item, Event.Failed("Error inesperado: ${e.message ?: e}"))
            false
        }
        synchronized(lock) {
            if (item.state == JobState.RUNNING && item.job === job) {
                if (ok) {
                    item.state = JobState.DONE
                    item.fraction = 1.0
                } else {
                    item.state = JobState.FAILED
                    if (item.error.isEmpty()) item.error = "La descarga no se completó."
                }
                item.speed = ""; item.eta = ""; item.stage = ""
                item.speedBytes = 0.0
            }
        }
        changed(item)
        pump()
    }

    /** Aplica un evento de la descarga. Público para las pruebas, como `_event` en LinVideo. */
    fun event(item: QueueItem, event: Event) {
        synchronized(lock) {
            if (item.state != JobState.RUNNING) return
            when (event) {
                is Event.Title -> {
                    item.title = event.text
                    item.entry.title = event.text
                }
                is Event.Progress -> {
                    // yt-dlp baja vídeo y audio por separado: no volver a mostrar «uniendo»
                    // mientras descarga la segunda pista.
                    item.stage = ""
                    item.fraction = event.fraction
                    item.speed = event.speed
                    item.speedBytes = event.speedBytes
                    item.eta = event.eta
                    item.size = event.size
                }
                is Event.Stage -> {
                    item.stage = event.text
                    item.speed = ""; item.eta = ""
                    item.speedBytes = 0.0
                }
                is Event.Log -> {
                    item.log.addLast(event.line)
                    while (item.log.size > LOG_LIMIT) item.log.removeFirst()
                    return // el registro no merece repintar la fila
                }
                is Event.ProducedFile -> item.files += event.file
                is Event.Failed -> item.error = event.message
            }
        }
        changed(item, save = event is Event.Title)
    }

    private fun changed(item: QueueItem, save: Boolean = true) {
        if (save) save()
        onChange(item)
    }

    /** Lo que queda por descargar, para recuperarlo si se cierra a medias. */
    fun save() {
        val saver = saver ?: return
        if (!persist) return
        val entries = synchronized(lock) {
            _items.filter { it.state == JobState.WAITING || it.state == JobState.RUNNING }.map { it.entry }
        }
        saver(entries)
    }
}

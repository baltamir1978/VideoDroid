package es.altamirano.linvideo.queue

import es.altamirano.linvideo.data.PendingQueue
import es.altamirano.linvideo.engine.DownloadJob
import es.altamirano.linvideo.engine.DownloadRequest
import es.altamirano.linvideo.engine.Event
import es.altamirano.linvideo.model.DownloadOptions
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.model.PendingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** `test_queue.py`: la cola sin Android ni yt-dlp, con trabajos falsos que emiten eventos a mano. */
class DownloadQueueTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Se queda «descargando» hasta que la prueba lo suelta. */
    class FakeJob(val request: DownloadRequest) : DownloadJob {
        val release = CountDownLatch(1)
        @Volatile var ok = true
        @Volatile var cancelled = false
        var events = listOf<Event>()

        override fun cancel() {
            cancelled = true
            release.countDown()
        }

        override fun run(emit: (Event) -> Unit): Boolean {
            events.forEach(emit)
            release.await(5, TimeUnit.SECONDS)
            return ok && !cancelled
        }
    }

    private val jobs = mutableListOf<FakeJob>()
    private val threads = mutableListOf<Thread>()

    private fun make(limit: Int = 1, pending: PendingQueue? = null) = DownloadQueue(
        maxConcurrent = { limit },
        onChange = {},
        saver = pending?.let { p -> { entries: List<PendingEntry> -> p.save(entries) } },
        starter = { work -> Thread(work).also { synchronized(threads) { threads += it }; it.start() } },
        jobFactory = { FakeJob(it).also { job -> synchronized(jobs) { jobs += job } } },
    )

    private fun entry(url: String = "https://example.com/v") =
        PendingEntry(url, MediaKind.VIDEO_MAX, File(tmp.root, "out").path, "", DownloadOptions())

    private fun finish() {
        synchronized(jobs) { jobs.toList() }.forEach { it.release.countDown() }
        // Al terminar una pueden arrancar otras: se espera hasta que no quede ningún hilo vivo.
        while (true) {
            val alive = synchronized(threads) { threads.filter { it.isAlive } }
            if (alive.isEmpty()) break
            synchronized(jobs) { jobs.toList() }.forEach { it.release.countDown() }
            alive.forEach { it.join(5000) }
        }
    }

    @Test fun respetaElLimiteDeSimultaneas() {
        val queue = make(limit = 2)
        val items = (0 until 3).map { queue.add(entry("https://e.com/$it")) }
        assertEquals(listOf(JobState.RUNNING, JobState.RUNNING, JobState.WAITING), items.map { it.state })
        jobs[0].release.countDown()
        threads[0].join(5000)
        assertEquals(JobState.RUNNING, items[2].state) // entra la tercera al quedar hueco
        finish()
        assertTrue(items.all { it.state == JobState.DONE })
        assertEquals(1.0, queue.summary().fraction, 0.0)
    }

    @Test fun eventosYFallo() {
        val queue = make()
        val item = queue.add(entry())
        val job = jobs[0]
        queue.event(item, Event.Title("Un título"))
        queue.event(item, Event.Progress(0.5, "1 MB/s", 1e6, "0:10", "5 MB / 10 MB"))
        assertEquals("Un título", item.displayTitle)
        assertEquals(0.5 * 0.95, item.batchFraction, 1e-9)
        queue.event(item, Event.Stage("Uniendo vídeo y audio"))
        assertEquals(0.95, item.batchFraction, 1e-9)
        queue.event(item, Event.Failed("La web dice que no"))
        job.ok = false
        finish()
        assertEquals(JobState.FAILED, item.state)
        assertEquals("La web dice que no", item.error)
        assertEquals(0, queue.summary().total) // lo fallido no cuenta para el lote
        assertEquals(1, queue.summary().failed)
    }

    @Test fun cancelarYReintentar() {
        val queue = make()
        val item = queue.add(entry())
        queue.cancel(item)
        finish()
        assertEquals(JobState.CANCELLED, item.state)
        assertTrue(jobs[0].cancelled)
        queue.retry(item)
        assertEquals(JobState.RUNNING, item.state)
        assertEquals(2, jobs.size)
        finish()
        assertEquals(JobState.DONE, item.state)
    }

    @Test fun archivoProducido() {
        val queue = make()
        val item = queue.add(entry())
        queue.event(item, Event.ProducedFile(File(tmp.root, "a.mp4")))
        finish()
        assertEquals(listOf(File(tmp.root, "a.mp4")), item.files)
        assertEquals(listOf(item), queue.clearFinished())
        assertTrue(queue.items.isEmpty())
    }

    @Test fun elRegistroTieneLimite() {
        val queue = make()
        val item = queue.add(entry())
        repeat(LOG_LIMIT + 10) { queue.event(item, Event.Log("línea $it")) }
        assertEquals(LOG_LIMIT, item.log.size)
        assertEquals("línea 10", item.log.first())
        finish()
    }

    @Test fun unaExcepcionNoDejaLaFilaDescargando() {
        val queue = DownloadQueue(
            maxConcurrent = { 1 }, onChange = {}, saver = null,
            starter = { it() },
            jobFactory = {
                object : DownloadJob {
                    override fun run(emit: (Event) -> Unit): Boolean = error("pum")
                    override fun cancel() {}
                }
            },
        )
        val item = queue.add(entry())
        assertEquals(JobState.FAILED, item.state)
        assertTrue("pum" in item.error)
    }

    @Test fun laColaPendienteSeGuardaYSeBorra() {
        val pending = PendingQueue(File(tmp.root, "pendientes.json"))
        val queue = make(limit = 1, pending = pending)
        queue.add(entry("https://e.com/1"))
        queue.add(entry("https://e.com/2"))
        assertEquals(listOf("https://e.com/1", "https://e.com/2"), pending.load().map { it.url })
        finish()
        assertEquals(emptyList<PendingEntry>(), pending.load())
        assertFalse(pending.file.exists())
    }

    @Test fun elTituloSeGuardaEnLaCola() {
        val pending = PendingQueue(File(tmp.root, "pendientes.json"))
        val queue = make(limit = 1, pending = pending)
        val item = queue.add(entry())
        queue.event(item, Event.Title("Ya tiene nombre"))
        assertEquals("Ya tiene nombre", pending.load().single().title)
        finish()
    }

    @Test fun alSalirSeConservaLaCola() {
        val pending = PendingQueue(File(tmp.root, "pendientes.json"))
        val queue = make(limit = 1, pending = pending)
        queue.add(entry("https://e.com/1"))
        queue.stopForExit()
        finish()
        assertEquals(listOf("https://e.com/1"), pending.load().map { it.url })
    }

    @Test fun pendientesEstropeados() {
        val file = File(tmp.root, "pendientes.json")
        file.writeText("esto no es json")
        assertEquals(emptyList<PendingEntry>(), PendingQueue(file).load())
        file.writeText("""[{"id": "1"}, 3, {"id":"2","url":"https://a","kind":"gif","destination":"/d"}]""")
        val loaded = PendingQueue(file).load()
        assertEquals(1, loaded.size)
        assertSame(MediaKind.GIF, loaded[0].kind)
    }
}

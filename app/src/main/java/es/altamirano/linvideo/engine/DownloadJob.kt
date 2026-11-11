package es.altamirano.linvideo.engine

import android.content.Context
import android.media.MediaScannerConnection
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import es.altamirano.linvideo.model.DownloadOptions
import es.altamirano.linvideo.model.FilenameCleanup
import es.altamirano.linvideo.model.MediaKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

data class DownloadRequest(
    val url: String,
    val kind: MediaKind,
    val destination: File,
    val options: DownloadOptions,
)

/** Una descarga concreta. Un objeto por intento; se puede cancelar desde otro hilo. */
interface DownloadJob {
    /** Lanza la descarga y va emitiendo eventos. Bloquea hasta terminar; true si terminó bien. */
    fun run(emit: (Event) -> Unit): Boolean
    fun cancel()
}

/** La descarga de verdad, con yt-dlp dentro del APK. */
class YtdlpJob(private val context: Context, val request: DownloadRequest) : DownloadJob {
    private val processId = UUID.randomUUID().toString()
    @Volatile private var cancelled = false

    override fun cancel() {
        cancelled = true
        // Lo descargado se queda en .part y se reanuda al reintentar.
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    override fun run(emit: (Event) -> Unit): Boolean {
        // Al abrir la app con algo compartido, yt-dlp puede estar aún descomprimiéndose.
        runBlocking { withTimeoutOrNull(120_000) { Engine.status.first { it !is Engine.Status.Starting } } }
        if (Engine.status.value !is Engine.Status.Ready) {
            emit(Event.Failed("yt-dlp no está listo todavía. Reinténtalo en unos segundos."))
            return false
        }
        val destination = request.destination
        if (!destination.isDirectory && !destination.mkdirs()) {
            emit(Event.Failed(Errors.explain(listOf("Permission denied: $destination"), 1)))
            return false
        }
        val args = Arguments.build(request.url, request.kind, request.options, destination.absolutePath)
        emit(Event.Log("$ yt-dlp " + args.joinToString(" ") { quote(it) }))

        val parser = OutputParser()
        val ytRequest = YoutubeDLRequest(request.url).addCommands(args.dropLast(1))
        if (cancelled) return false
        val exitCode = try {
            // stderr junto a stdout: el orden de las líneas importa para el registro y los errores.
            YoutubeDL.getInstance().execute(ytRequest, processId, true) { _, _, line ->
                line.split('\r', '\n').forEach { part -> parser.handle(part).forEach(emit) }
            }.exitCode
        } catch (e: YoutubeDL.CanceledException) {
            return false
        } catch (e: InterruptedException) {
            return false
        } catch (e: Exception) {
            if (cancelled) return false
            // La librería lanza una excepción con la salida de error si el código no es 0.
            val lines = parser.errors.ifEmpty { e.message.orEmpty().lines().filter { it.isNotBlank() }.takeLast(20) }
            emit(Event.Failed(Errors.explain(lines, 1)))
            return false
        }
        if (cancelled) return false
        if (exitCode != 0) {
            emit(Event.Failed(Errors.explain(parser.errors, exitCode)))
            return false
        }
        val finals = parser.files.map { tidyName(it, emit) }
        if (finals.isEmpty()) {
            emit(Event.Failed("yt-dlp terminó sin dejar ningún archivo."))
            return false
        }
        finals.forEach { emit(Event.ProducedFile(it)) }
        // Que aparezca en la galería y en la app de música.
        MediaScannerConnection.scanFile(context, finals.map { it.absolutePath }.toTypedArray(), null, null)
        return true
    }

    /** Renombra según los ajustes. Si algo falla se queda el original: nunca se pierde la descarga por esto. */
    private fun tidyName(file: File, emit: (Event) -> Unit): File {
        val options = request.options
        if (options.filenameCleanup == FilenameCleanup.NONE) return file
        val cleaned = Filenames.clean(file.name, options.filenameCleanup, options.keepIdentifier)
        if (cleaned == file.name) return file
        val target = OutputParser.uniquePath(File(file.parentFile, cleaned))
        if (!file.renameTo(target)) {
            emit(Event.Log("No se pudo renombrar el archivo: ${file.name}"))
            return file
        }
        emit(Event.Log("Renombrado: ${file.name} → ${target.name}"))
        return target
    }

    private fun quote(arg: String) =
        if (arg.isNotEmpty() && arg.all { it.isLetterOrDigit() || it in "-_./:=+,@%" }) arg
        else "'" + arg.replace("'", "'\"'\"'") + "'"
}

package es.altamirano.linvideo.engine

import es.altamirano.linvideo.model.DownloadOptions
import es.altamirano.linvideo.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Los argumentos de verdad contra el yt-dlp de la librería, con el Python y el ffmpeg del equipo.
 * Sólo corre si se le dan: LINVIDEO_YTDLP (el zipapp) y LINVIDEO_URL (un vídeo con audio servido
 * por HTTP). Ver Tools/probar-ytdlp.sh.
 */
class RealYtdlpTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ytdlp = System.getenv("LINVIDEO_YTDLP")
    private val url = System.getenv("LINVIDEO_URL")

    private fun download(kind: MediaKind, options: DownloadOptions = DownloadOptions()): List<File> {
        assumeTrue(ytdlp != null && url != null)
        val dest = tmp.newFolder()
        val args = Arguments.build(url!!, kind, options, dest.path, ffmpegDir = "/usr/bin")
        val process = ProcessBuilder(listOf("python3", ytdlp!!) + args).redirectErrorStream(true).start()
        val parser = OutputParser()
        val log = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line ->
            log.appendLine(line)
            parser.handle(line)
        }
        val code = process.waitFor()
        assertEquals("yt-dlp falló:\n$log", 0, code)
        assertTrue("sin archivo:\n$log", parser.files.isNotEmpty())
        parser.files.forEach { assertTrue("no existe $it\n$log", it.isFile) }
        return parser.files
    }

    @Test fun videoMax() = assertEquals("mp4", download(MediaKind.VIDEO_MAX).single().extension)
    @Test fun videoCompat() = assertEquals("mp4", download(MediaKind.VIDEO_COMPAT).single().extension)

    @Test fun mp3ConEtiquetas() {
        val file = download(MediaKind.AUDIO_MP3, DownloadOptions(embedThumbnail = false)).single()
        assertEquals("mp3", file.extension)
    }

    @Test fun audioOriginal() {
        assertTrue(download(MediaKind.AUDIO_ORIGINAL).single().extension in setOf("m4a", "aac", "opus", "mp3", "ogg"))
    }

    @Test fun gif() {
        val file = download(MediaKind.GIF, DownloadOptions(gifWidth = 160, gifFps = 8)).single()
        assertEquals("gif", file.extension)
        assertEquals("GIF89a", file.readBytes().copyOfRange(0, 6).decodeToString())
        // Sólo queda el GIF: el vídeo intermedio se borra.
        assertEquals(listOf(file.name), file.parentFile!!.list()!!.toList())
    }

    @Test fun recorte() {
        val file = download(MediaKind.VIDEO_MAX, DownloadOptions(clipStart = "0:01", clipEnd = "0:03")).single()
        val probe = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file.path)
            .start().inputStream.bufferedReader().readText().trim().toDouble()
        assertTrue("dura $probe", probe in 1.5..2.6)
    }
}

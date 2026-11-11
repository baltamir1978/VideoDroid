package es.altamirano.linvideo.engine

import es.altamirano.linvideo.model.DownloadOptions
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `test_download_arguments.py`: si esto se tuerce, no descarga nada o descarga otra cosa. */
class ArgumentsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dest = "/tmp/linvideo-pruebas"

    private fun arguments(kind: MediaKind, options: DownloadOptions = DownloadOptions()) =
        Arguments.build("https://ejemplo.test/video", kind, options, dest)

    private fun valueAfter(flag: String, args: List<String>): String? {
        val i = args.indexOf(flag)
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
    }

    @Test fun elEnlaceVaAlFinal() {
        assertEquals("https://ejemplo.test/video", arguments(MediaKind.VIDEO_MAX).last())
    }

    @Test fun techoDeResolucion() {
        val options = DownloadOptions(resolution = VideoResolution.FHD)
        assertTrue("[height<=1080]" in valueAfter("-f", arguments(MediaKind.VIDEO_MAX, options)).orEmpty())
        // «Máxima» no debe poner ningún filtro.
        assertFalse("height" in valueAfter("-f", arguments(MediaKind.VIDEO_MAX)).orEmpty())
    }

    @Test fun formatoElegidoAMano() {
        val options = DownloadOptions(resolution = VideoResolution.SD, formatOverride = "137+bestaudio")
        val args = arguments(MediaKind.VIDEO_MAX, options)
        assertEquals("137+bestaudio", valueAfter("-f", args))
        assertFalse("height<=" in args.joinToString(" "))
    }

    @Test fun recorte() {
        val args = arguments(MediaKind.VIDEO_MAX, DownloadOptions(clipStart = "1:30", clipEnd = "2:45"))
        assertEquals("*1:30-2:45", valueAfter("--download-sections", args))
        assertTrue("--force-keyframes-at-cuts" in args)
    }

    @Test fun recorteParcial() {
        assertEquals("*0:30-inf", Arguments.downloadSection("0:30", ""))
        assertEquals("*0-1:00", Arguments.downloadSection("", "1:00"))
        assertNull(Arguments.downloadSection("", ""))
    }

    @Test fun recorteInvalido() {
        assertNull(Arguments.normalizeTimestamp("hola"))
        assertEquals("1:30", Arguments.normalizeTimestamp(" 1:30 "))
    }

    @Test fun etiquetasDeAudio() {
        val args = arguments(MediaKind.AUDIO_MP3)
        val text = args.joinToString(" ")
        assertTrue("--embed-metadata" in args)
        assertTrue("--embed-thumbnail" in args)
        assertEquals("jpg", valueAfter("--convert-thumbnails", args))
        assertTrue("meta_artist" in text && "meta_title" in text)
        // El álbum se deja en manos de yt-dlp: forzarlo escribía «NA».
        assertFalse("meta_album" in text)
    }

    @Test fun etiquetasSimples() {
        val text = arguments(MediaKind.AUDIO_MP3, DownloadOptions(smartAudioTags = false)).joinToString(" ")
        assertFalse("(?P<meta_artist>" in text)
    }

    @Test fun gifSinAudio() {
        assertFalse("ba" in valueAfter("-f", arguments(MediaKind.GIF)).orEmpty())
    }

    @Test fun gifConPaleta() {
        val args = arguments(MediaKind.GIF, DownloadOptions(gifFps = 10, gifWidth = 32))
        assertEquals("gif", valueAfter("--recode-video", args))
        val pp = valueAfter("--postprocessor-args", args).orEmpty()
        assertTrue(pp.startsWith("VideoConvertor:"))
        assertTrue("fps=10,scale=64:-1" in pp) // ancho mínimo 64, como en LinVideo
        assertTrue("palettegen" in pp && "-loop 0" in pp)
    }

    @Test fun modoCompatible() {
        val selector = valueAfter("-f", arguments(MediaKind.VIDEO_COMPAT)).orEmpty()
        assertTrue("avc1" in selector && "mp4a" in selector)
    }

    @Test fun listasDeReproduccion() {
        assertTrue("--no-playlist" in arguments(MediaKind.VIDEO_MAX))
        assertTrue("--yes-playlist" in arguments(MediaKind.VIDEO_MAX, DownloadOptions(downloadPlaylist = true)))
    }

    @Test fun limiteDeVelocidad() {
        assertFalse("--limit-rate" in arguments(MediaKind.VIDEO_MAX))
        val args = arguments(MediaKind.VIDEO_MAX, DownloadOptions(speedLimit = "500K"))
        assertEquals("500K", valueAfter("--limit-rate", args))
    }

    @Test fun cookies() {
        // En Android no hay cookies del navegador: un cookies.txt importado.
        assertFalse("--cookies" in arguments(MediaKind.VIDEO_MAX))
        val args = arguments(MediaKind.VIDEO_MAX, DownloadOptions(cookiesFile = "/data/cookies.txt"))
        assertEquals("/data/cookies.txt", valueAfter("--cookies", args))
        assertFalse("--cookies-from-browser" in args)
    }

    @Test fun opcionesExtra() {
        val args = arguments(MediaKind.VIDEO_MAX, DownloadOptions(extraArguments = "--limit-rate 2M"))
        assertTrue("--limit-rate" in args && "2M" in args)
    }

    @Test fun opcionesExtraConComillas() {
        val args = arguments(MediaKind.VIDEO_MAX, DownloadOptions(extraArguments = "--user-agent \"Navegador raro 1.0\""))
        assertEquals("Navegador raro 1.0", valueAfter("--user-agent", args))
    }

    @Test fun opcionesExtraConComillaAbiertaSePartenPorEspacios() {
        val args = arguments(MediaKind.VIDEO_MAX, DownloadOptions(extraArguments = "--a \"b c"))
        assertTrue("\"b" in args && "c" in args)
    }

    @Test fun ffmpegExplicito() {
        val args = Arguments.build("https://x.test", MediaKind.VIDEO_MAX, DownloadOptions(), dest, "/usr/bin")
        assertEquals("/usr/bin", valueAfter("--ffmpeg-location", args))
        // Por defecto no: lo pone la librería.
        assertFalse("--ffmpeg-location" in arguments(MediaKind.VIDEO_MAX))
    }

    @Test fun marcasDeProgreso() {
        val text = arguments(MediaKind.VIDEO_MAX).joinToString(" ")
        assertTrue(Arguments.PROGRESS_TAG in text && Arguments.TITLE_TAG in text && Arguments.FILE_TAG in text)
    }

    @Test fun lecturaDelProgreso() {
        val event = OutputParser.parseProgress("${Arguments.PROGRESS_TAG}524288|1048576|NA|262144|2")!!
        assertEquals(0.5, event.fraction, 0.0)
        assertEquals("256,0 KB/s", event.speed)
        assertEquals("0:02", event.eta)
        assertEquals("512,0 KB / 1,0 MB", event.size)
        // Sin total conocido, barra indeterminada.
        val unknown = OutputParser.parseProgress("${Arguments.PROGRESS_TAG}1024|NA|NA|NA|NA")!!
        assertEquals(-1.0, unknown.fraction, 0.0)
        assertEquals("", unknown.speed)
        // El total estimado sirve si no hay exacto.
        val estimated = OutputParser.parseProgress("${Arguments.PROGRESS_TAG}100|NA|400|NA|NA")!!
        assertEquals(0.25, estimated.fraction, 0.0)
    }

    @Test fun nombreUnico() {
        val dir = tmp.root
        java.io.File(dir, "vídeo.mp4").writeText("")
        java.io.File(dir, "vídeo (2).mp4").writeText("")
        assertEquals("vídeo (3).mp4", OutputParser.uniquePath(java.io.File(dir, "vídeo.mp4")).name)
        assertEquals("otro.mp4", OutputParser.uniquePath(java.io.File(dir, "otro.mp4")).name)
    }

    @Test fun lectorDeSalida() {
        val parser = OutputParser()
        assertEquals(listOf(Event.Title("Hola")), parser.handle("${Arguments.TITLE_TAG}Hola"))
        assertTrue(parser.handle("${Arguments.TITLE_TAG}NA").isEmpty())
        assertTrue(parser.handle("${Arguments.FILE_TAG}/x/a.mp4").isEmpty())
        assertEquals(listOf(java.io.File("/x/a.mp4")), parser.files)
        val merger = parser.handle("[Merger] Merging formats into \"a.mp4\"")
        assertTrue(Event.Stage("Uniendo vídeo y audio") in merger)
        parser.handle("ERROR: algo")
        assertEquals(listOf("ERROR: algo"), parser.errors)
    }
}

package es.altamirano.linvideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `test_errors.py`: lo que ve el usuario tiene que decir qué ha pasado y qué hacer. */
class ErrorsTest {
    private fun explain(vararg lines: String, code: Int = 1) = Errors.explain(lines.toList(), code)

    @Test fun sinPistaDeAudio() {
        val m = explain("ERROR: Postprocessing: WARNING: unable to obtain file audio codec with ffprobe")
        assertTrue("no tiene pista de audio" in m)
        assertTrue("vídeo" in m.lowercase() || "gif" in m.lowercase())
    }

    @Test fun videoRetirado() {
        val m = explain("ERROR: [youtube] BROWqjuTM0g: Video unavailable. It was removed following a copyright removal request")
        assertTrue("ya no está disponible" in m)
    }

    @Test fun videoNoDisponible() {
        assertTrue("ya no está disponible" in explain("ERROR: [youtube] xxxxxxxxxxx: This video is unavailable"))
    }

    @Test fun verificacionDeEdad() {
        val m = explain("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate for some users.")
        assertTrue("Ajustes" in m && "edad" in m.lowercase())
        assertTrue("cookies.txt" in m)
    }

    @Test fun error403() {
        val m = explain("ERROR: unable to download video data: HTTP Error 403: Forbidden")
        assertTrue("403" in m && "yt-dlp" in m)
    }

    @Test fun error429() {
        val m = explain("ERROR: HTTP Error 429: Too Many Requests")
        assertTrue("simultáneas" in m || "Espera" in m)
    }

    @Test fun sinEspacio() {
        assertTrue("espacio" in explain("ERROR: unable to write data: No space left on device"))
    }

    @Test fun ffmpegQueFaltaNoEsUn404() {
        val m = explain("ERROR: ffmpeg not found.")
        assertTrue("ffmpeg" in m)
        assertFalse("404" in m)
    }

    @Test fun errorDesconocido() {
        val m = explain("ERROR: [sitio] 123: algo muy raro ha pasado")
        assertFalse(m.startsWith("ERROR"))
        assertTrue("algo muy raro" in m)
    }

    @Test fun sinLineas() {
        assertTrue("2" in Errors.explain(emptyList(), 2))
    }

    @Test fun limpiaElTexto() {
        assertEquals("algo", Errors.clean("ERROR: algo"))
        assertEquals("otra cosa", Errors.clean("WARNING: otra cosa"))
        assertEquals("mensaje", Errors.clean("[youtube] abc123: mensaje"))
        assertEquals("fallo", Errors.clean("fallo (caused by TransportError())"))
    }
}

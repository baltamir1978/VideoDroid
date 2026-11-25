package es.altamirano.linvideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test fun variosEnlaces() {
        assertEquals(3, Links.extractUrls("https://uno.test/a\nhttps://dos.test/b, https://tres.test/c", onlyYouTube = false).size)
    }

    @Test fun descartaTextoSuelto() {
        assertEquals(emptyList<String>(), Links.extractUrls("esto no es un enlace"))
        assertEquals(emptyList<String>(), Links.extractUrls("ftp://viejo.test/x"))
    }

    @Test fun limpiaAdornos() {
        assertEquals(listOf("https://uno.test/a"), Links.extractUrls("<https://uno.test/a>", onlyYouTube = false))
    }

    @Test fun enlacesLinvideo() {
        assertEquals("https://youtu.be/x", Links.fromArgument("linvideo:https://youtu.be/x"))
        assertEquals("https://youtu.be/x", Links.fromArgument("linvideo://https://youtu.be/x"))
        assertEquals("https://youtu.be/x", Links.fromArgument("linvideo://https%3A%2F%2Fyoutu.be%2Fx"))
        assertEquals("https://youtu.be/x", Links.fromArgument("https://youtu.be/x"))
        assertNull(Links.fromArgument("linvideo:basura"))
    }

    @Test fun soloYouTube() {
        val texto = "https://www.youtube.com/watch?v=a https://youtu.be/b https://music.youtube.com/watch?v=c " +
            "https://m.youtube.com/shorts/d https://vimeo.com/1 https://youtube.com.malo.test/x https://noyoutube.com/y"
        assertEquals(
            listOf("https://www.youtube.com/watch?v=a", "https://youtu.be/b", "https://music.youtube.com/watch?v=c", "https://m.youtube.com/shorts/d"),
            Links.extractUrls(texto, onlyYouTube = true),
        )
        assertEquals(7, Links.extractUrls(texto, onlyYouTube = false).size)
    }

    @Test fun reconoceYouTube() {
        assertEquals(true, Links.isYouTube("https://YouTube.com/watch?v=a"))
        assertEquals(true, Links.isYouTube("https://www.youtube-nocookie.com/embed/a"))
        assertEquals(false, Links.isYouTube("https://evil.test/?u=youtube.com"))
        assertEquals(false, Links.isYouTube("no es un enlace"))
    }
}

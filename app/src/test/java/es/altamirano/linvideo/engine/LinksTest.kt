package es.altamirano.linvideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test fun variosEnlaces() {
        assertEquals(3, Links.extractUrls("https://uno.test/a\nhttps://dos.test/b, https://tres.test/c").size)
    }

    @Test fun descartaTextoSuelto() {
        assertEquals(emptyList<String>(), Links.extractUrls("esto no es un enlace"))
        assertEquals(emptyList<String>(), Links.extractUrls("ftp://viejo.test/x"))
    }

    @Test fun limpiaAdornos() {
        assertEquals(listOf("https://uno.test/a"), Links.extractUrls("<https://uno.test/a>"))
    }

    @Test fun enlacesLinvideo() {
        assertEquals("https://youtu.be/x", Links.fromArgument("linvideo:https://youtu.be/x"))
        assertEquals("https://youtu.be/x", Links.fromArgument("linvideo://https://youtu.be/x"))
        assertEquals("https://youtu.be/x", Links.fromArgument("linvideo://https%3A%2F%2Fyoutu.be%2Fx"))
        assertEquals("https://youtu.be/x", Links.fromArgument("https://youtu.be/x"))
        assertNull(Links.fromArgument("linvideo:basura"))
    }
}

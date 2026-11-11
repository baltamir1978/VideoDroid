package es.altamirano.linvideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShlexTest {
    @Test fun comoPython() {
        assertEquals(listOf("a", "b c", "d"), Shlex.split("a \"b c\" d"))
        assertEquals(listOf("it's"), Shlex.split("\"it's\""))
        assertEquals(listOf("a b"), Shlex.split("'a b'"))
        assertEquals(listOf("ab"), Shlex.split("a'b'"))
        assertEquals(listOf("a b"), Shlex.split("a\\ b"))
        assertEquals(listOf("x\"y"), Shlex.split("\"x\\\"y\""))
        assertEquals(listOf("--pp", "FFmpeg:-vf scale=1"), Shlex.split("  --pp   'FFmpeg:-vf scale=1'  "))
        assertEquals(emptyList<String>(), Shlex.split("   "))
    }

    @Test fun comillasAbiertas() {
        assertNull(Shlex.split("\"abierta"))
        assertNull(Shlex.split("'abierta"))
        assertNull(Shlex.split("barra\\"))
    }
}

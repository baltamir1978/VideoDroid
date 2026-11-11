package es.altamirano.linvideo.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun bytes() {
        assertEquals("512 B", Formatters.humanBytes(512.0))
        assertEquals("1,5 KB", Formatters.humanBytes(1536.0))
        assertEquals("1,0 GB", Formatters.humanBytes(1024.0 * 1024 * 1024))
    }

    @Test fun duraciones() {
        assertEquals("0:02", Formatters.duration(2.0))
        assertEquals("1:01:05", Formatters.duration(3665.0))
        assertEquals("", Formatters.duration(-1.0))
        assertEquals("", Formatters.duration(Double.NaN))
    }
}

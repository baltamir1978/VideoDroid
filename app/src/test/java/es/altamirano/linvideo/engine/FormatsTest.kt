package es.altamirano.linvideo.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    private fun format(id: String, video: String?, audio: String?) = MediaFormat(
        id = id, ext = "mp4", resolution = "1920x1080", height = 1080, fps = 24.0,
        videoCodec = video, audioCodec = audio, sizeBytes = 1024.0 * 1024, bitrate = 500.0, note = null,
    )

    @Test fun selectorConAudio() {
        val selector = format("137", "avc1.640028", null).selector
        assertTrue(selector.startsWith("137+") && "m4a" in selector)
        assertTrue(selector.endsWith("/137")) // alternativa por si no hay audio suelto
    }

    @Test fun selectorCompleto() = assertEquals("18", format("18", "avc1", "mp4a").selector)

    @Test fun nombresDeCodecs() {
        assertEquals("H.264", Formats.shortCodec("avc1.640028"))
        assertEquals("AV1", Formats.shortCodec("av01.0.00M.08"))
        assertEquals("VP9", Formats.shortCodec("vp9"))
        assertEquals("AAC", Formats.shortCodec("mp4a.40.2"))
        assertEquals("Opus", Formats.shortCodec("opus"))
    }

    @Test fun clasificacion() {
        assertTrue(format("1", null, "opus").isAudioOnly)
        assertTrue(format("2", "vp9", null).isVideoOnly)
        assertTrue(format("3", "vp9", "opus").isComplete)
    }

    @Test fun textos() {
        val f = format("3", "vp9", "opus")
        assertEquals("1920x1080 · 24 fps", f.qualityText)
        assertEquals("VP9 + Opus", f.codecText)
        assertEquals("1,0 MB", f.sizeText)
        assertEquals("500 kbps", format("1", null, "opus").qualityText)
    }

    @Test fun leeLaRespuestaDeYtdlp() {
        val root = Json.parseToJsonElement(
            """
            {"title": "X", "formats": [
              {"format_id": "sb0", "vcodec": "none", "acodec": "none", "ext": "mhtml"},
              {"format_id": "140", "vcodec": "none", "acodec": "mp4a.40.2", "ext": "m4a", "abr": 129.5, "filesize": 1000},
              {"format_id": "137", "vcodec": "avc1.640028", "acodec": "none", "height": 1080, "fps": 30, "tbr": 4000, "resolution": "1920x1080"},
              {"format_id": "18", "vcodec": "avc1", "acodec": "mp4a", "height": 360, "filesize": null, "filesize_approx": 2048},
              {"format_id": 5, "vcodec": "vp9"}
            ]}
            """,
        ).jsonObject
        val formats = Formats.parseFormats(root)
        assertEquals(listOf("137", "18", "140"), formats.map { it.id })
        assertEquals(2048.0, formats[1].sizeBytes!!, 0.0)
        assertEquals("—", formats[1].resolution)
        assertEquals(129.5, formats[2].bitrate!!, 0.0)
    }
}

package es.altamirano.linvideo.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `test_preview.py`: la vista previa sólo sirve si elige algo que se abra y arranque rápido. */
class PreviewTest {
    private fun fmt(
        id: String, url: String, proto: String = "https", ext: String = "mp4",
        vcodec: String? = null, acodec: String? = null, height: Int? = null,
        manifest: String? = null, abr: Double? = null,
    ): JsonObject = buildJsonObject {
        put("format_id", id); put("url", url); put("protocol", proto); put("ext", ext)
        putJsonObject("http_headers") { put("User-Agent", "UA"); put("Accept", "*/*"); put("Referer", "https://web/") }
        vcodec?.let { put("vcodec", it) }
        acodec?.let { put("acodec", it) }
        height?.let { put("height", it) }
        manifest?.let { put("manifest_url", it) }
        abr?.let { put("abr", it) }
    }

    private fun root(vararg formats: JsonObject, title: String? = null) = buildJsonObject {
        title?.let { put("title", it) }
        put("formats", kotlinx.serialization.json.JsonArray(formats.toList()))
    }

    @Test fun youtubeUsaElMaestro() {
        val s = Preview.pick(
            root(
                fmt("sb0", "https://i.ytimg.com/sb", proto = "mhtml", ext = "mhtml", vcodec = "none", acodec = "none"),
                fmt("140", "https://gv/140", ext = "m4a", vcodec = "none", acodec = "mp4a.40.2"),
                fmt("234", "https://m/234", proto = "m3u8_native", vcodec = "none", acodec = "mp4a.40.2", manifest = "https://m/master.m3u8"),
                fmt("135", "https://gv/135", vcodec = "avc1.4d401f", acodec = "none", height = 480),
                fmt("230", "https://m/230", proto = "m3u8_native", vcodec = "avc1.4D401E", acodec = "none", height = 360, manifest = "https://m/master.m3u8"),
                title = "Gatos",
            ),
        )!!
        assertEquals("https://m/master.m3u8", s.url)
        assertEquals(Preview.Kind.VIDEO, s.kind)
        assertEquals("Gatos", s.title)
        assertTrue(s.isHls)
    }

    @Test fun maestroMudo() {
        val s = Preview.pick(
            root(
                fmt("hls-994", "https://v.redd.it/x/HLS_480.m3u8", proto = "m3u8_native", vcodec = "avc1.4D401F", acodec = "none",
                    height = 854, manifest = "https://v.redd.it/x/HLSPlaylist.m3u8"),
                fmt("dash-4", "https://v.redd.it/x/DASH_480.mp4", vcodec = "avc1.4d401f", acodec = "none", height = 854),
            ),
        )!!
        assertEquals("https://v.redd.it/x/HLSPlaylist.m3u8", s.url)
        assertEquals(Preview.Kind.VIDEO_ONLY, s.kind)
    }

    @Test fun mp4CompletoHasta720() {
        val s = Preview.pick(
            root(
                fmt("240", "https://v/240", vcodec = "avc1", acodec = "mp4a", height = 240),
                fmt("720", "https://v/720", vcodec = "avc1", acodec = "mp4a", height = 720),
                fmt("1080", "https://v/1080", vcodec = "avc1", acodec = "mp4a", height = 1080),
                fmt("webm", "https://v/webm", ext = "webm", vcodec = "vp9", acodec = "opus", height = 480),
            ),
        )!!
        assertEquals("https://v/720", s.url)
        assertEquals(Preview.Kind.VIDEO, s.kind)
        assertTrue(!s.isHls)
    }

    @Test fun webmVale() {
        val s = Preview.pick(root(fmt("webm", "https://v/webm", ext = "webm", vcodec = "vp9", acodec = "opus", height = 480)))!!
        assertEquals("https://v/webm", s.url)
    }

    @Test fun videoSinSonidoAntesQueAudio() {
        val s = Preview.pick(
            root(
                fmt("a", "https://v/a", ext = "m4a", vcodec = "none", acodec = "mp4a.40.2"),
                fmt("v", "https://v/v", vcodec = "avc1.4d401f", acodec = "none", height = 480),
            ),
        )!!
        assertEquals("https://v/v", s.url)
        assertEquals(Preview.Kind.VIDEO_ONLY, s.kind)
    }

    @Test fun soloAudioElDeMasCalidad() {
        val s = Preview.pick(
            root(
                fmt("64", "https://s/64", ext = "mp3", acodec = "mp3", abr = 64.0),
                fmt("128", "https://s/128", ext = "mp3", acodec = "mp3", abr = 128.0),
                fmt("opus", "https://s/opus", ext = "opus", vcodec = "none", acodec = "opus", abr = 160.0),
            ),
        )!!
        assertEquals("https://s/opus", s.url)
        assertEquals(Preview.Kind.AUDIO, s.kind)
    }

    @Test fun listaDePistas() {
        val r = Json.parseToJsonElement(
            """{"_type": "playlist", "entries": [
              {"formats": [{"format_id": "x", "url": "https://a/1.xyz", "protocol": "https", "ext": "xyz"}]},
              {"formats": [{"format_id": "mp3", "url": "https://a/2.mp3", "protocol": "https", "ext": "mp3", "acodec": "mp3"}]}
            ]}""",
        ).jsonObject
        val s = Preview.pick(r)!!
        assertEquals("https://a/2.mp3", s.url)
        assertEquals(Preview.Kind.AUDIO, s.kind)
    }

    @Test fun nadaReproducible() {
        assertNull(
            Preview.pick(
                root(
                    fmt("dash", "https://v/dash", proto = "http_dash_segments", vcodec = "avc1", acodec = "mp4a", manifest = "https://v/manifest.mpd"),
                    fmt("sb", "https://v/sb", proto = "mhtml", ext = "mhtml"),
                ),
            ),
        )
    }

    @Test fun cabeceras() {
        val s = Preview.pick(root(fmt("hls", "https://x/hls", proto = "m3u8_native", manifest = "https://x/master.m3u8")))!!
        assertEquals(mapOf("User-Agent" to "UA", "Referer" to "https://web/"), s.headers)
    }
}

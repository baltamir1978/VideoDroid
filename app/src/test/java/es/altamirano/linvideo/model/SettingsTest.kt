package es.altamirano.linvideo.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {
    @Test fun ajustesIdaYVuelta() {
        val settings = Settings(
            kind = MediaKind.AUDIO_MP3, destination = "Música",
            options = DownloadOptions(resolution = VideoResolution.HD, clipStart = "1:00", formatOverride = "137"),
        )
        val loaded = Settings.fromJson(settings.toJson())
        assertEquals(MediaKind.AUDIO_MP3, loaded.kind)
        assertEquals("Música", loaded.destination)
        assertEquals(VideoResolution.HD, loaded.options.resolution)
        // El recorte y el formato a mano son de cada descarga: no se guardan.
        assertEquals("", loaded.options.clipStart)
        assertEquals("", loaded.options.formatOverride)
    }

    @Test fun ajustesEstropeados() {
        // Un valor raro no tira el resto.
        val data = Json.parseToJsonElement(
            """{"kind": "inventado", "max_concurrent": "tres", "options": {"resolution": 999, "gif_fps": 30, "embed_metadata": 1}}""",
        ).jsonObject
        val loaded = Settings.fromJson(data)
        assertEquals(MediaKind.VIDEO_MAX, loaded.kind)
        assertEquals(2, loaded.maxConcurrent)
        assertEquals(VideoResolution.BEST, loaded.options.resolution)
        assertEquals(30, loaded.options.gifFps)
        assertEquals(true, loaded.options.embedMetadata)
    }

    @Test fun mismosNombresQueLinVideo() {
        val keys = Settings().toJson().keys
        for (k in listOf("kind", "destination", "options", "max_concurrent", "auto_update", "legal_accepted", "watch_clipboard")) {
            assert(k in keys) { k }
        }
        val options = (Settings().toJson()["options"] as JsonObject).keys
        assert("concurrent_fragments" in options && "gif_width" in options && "smart_audio_tags" in options)
    }

    @Test fun pendienteIdaYVuelta() {
        val entry = PendingEntry("https://e.test/v", MediaKind.GIF, "/sdcard/Download/LinVideo", "Título",
            DownloadOptions(clipStart = "0:10"))
        val back = PendingEntry.fromJson(entry.toJson())!!
        assertEquals(entry, back)
        assertEquals("0:10", back.options.clipStart)
    }

    @Test fun pendienteIncompleto() {
        assertNull(PendingEntry.fromJson(Json.parseToJsonElement("""{"id": "1", "url": "x"}""").jsonObject))
        assertNull(PendingEntry.fromJson(Json.parseToJsonElement(
            """{"id": "1", "url": "x", "kind": "nada", "destination": "/d"}""").jsonObject))
    }
}

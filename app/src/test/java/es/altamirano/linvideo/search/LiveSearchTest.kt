package es.altamirano.linvideo.search

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Búsquedas de verdad contra las webs (menos YouTube y SoundCloud, que van por yt-dlp). Sólo con
 * LINVIDEO_LIVE=1. Usa búsquedas corrientes: con «jazz piano» varias webs no tienen nada y no
 * por eso están rotas (ver el CLAUDE.md de LinVideo).
 */
class LiveSearchTest {
    @Test fun todasLasWebsDevuelvenAlgo() {
        assumeTrue(System.getenv("LINVIDEO_LIVE") == "1")
        val report = StringBuilder()
        var failed = 0
        for (source in SearchSource.entries) {
            if (source == SearchSource.YOUTUBE || source == SearchSource.SOUNDCLOUD) continue
            val query = when (source.category) {
                SearchCategory.ADULT -> "amateur"
                SearchCategory.AUDIO -> "jazz"
                SearchCategory.VIDEO -> "cats"
            }
            val line = try {
                val found = Search.search(query, source, limit = 5)
                if (found.isEmpty()) failed++
                val first = found.firstOrNull()
                "${found.size} · ${first?.title?.take(40)} · ${first?.durationText} · ${first?.viewsText} · miniatura=${first?.thumbnailUrl != null}"
            } catch (e: Exception) {
                failed++
                "FALLO: ${Search.describe(e)}"
            }
            report.appendLine("${source.title.padEnd(12)} $line")
        }
        println(report)
        java.io.File(System.getProperty("java.io.tmpdir"), "linvideo-live.txt").writeText(report.toString())
        assert(failed == 0) { "Fallaron $failed webs:\n$report" }
    }
}

package es.altamirano.linvideo.engine

import es.altamirano.linvideo.model.FilenameCleanup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `test_filenames.py`: casos de descargas reales y los bordes que rompieron la primera versión. */
class FilenamesTest {
    private val basic = FilenameCleanup.BASIC
    private val strict = FilenameCleanup.STRICT
    private fun clean(name: String, level: FilenameCleanup, keep: Boolean = false) = Filenames.clean(name, level, keep)

    @Test fun quitaElIdentificador() {
        assertEquals("Me at the zoo.mp4", clean("Me at the zoo [jNQXAC9IVRw].mp4", basic))
    }

    @Test fun quitaEtiquetasDeRelleno() {
        assertEquals(
            "Luis Fonsi - Despacito ft. Daddy Yankee.mp4",
            clean("Luis Fonsi - Despacito ft. Daddy Yankee (Official Video) [kJQP7kiw5Fk].mp4", basic),
        )
    }

    @Test fun quitaRuido() {
        assertFalse("🥵" in clean("Juro a Dios 🥵😬 esta boca [68e2bb75].mp4", basic))
        val noHashtags = clean("Playa #amateur #real [1atcfzcf].mp4", basic)
        assertTrue("#" !in noHashtags && noHashtags.startsWith("Playa"))
        assertFalse("xvideos" in clean("Algo bonito | XVIDEOS.COM [abc123].mp4", basic).lowercase())
    }

    @Test fun quitaEtiquetasFinales() {
        assertEquals("Documental sobre jazz.mp4", clean("Documental sobre jazz 4K FULL HD [x8mlmc3].mp4", basic))
    }

    @Test fun respetaLasBarras() {
        val cleaned = clean("Best of lofi 2021 [beats to relax/study to] [n61ULEU7CO0].webm", basic)
        assertTrue(cleaned.startsWith("Best of lofi 2021"))
        assertTrue(cleaned.endsWith(".webm"))
        assertFalse("/" in cleaned)
    }

    @Test fun modoEstricto() {
        assertEquals("Concierto.mp4", clean("Concierto (en directo) [abc12345].mp4", strict))
        assertEquals(
            "Este Título Está Todo En Mayúsculas.mp4",
            clean("ESTE TÍTULO ESTÁ TODO EN MAYÚSCULAS [abc12345].mp4", strict),
        )
    }

    @Test fun mayusculasConApostrofo() {
        assertEquals("Don't Stop Me Now Queen.mp4", clean("DON'T STOP ME NOW QUEEN [abc12345].mp4", strict))
    }

    @Test fun etiquetaConTildeEnMayusculas() {
        // En Java, (?i) sin Unicode no casaría «VÍDEO» con «vídeo».
        assertEquals("Canción.mp4", clean("Canción (VÍDEO OFICIAL) [abc12345].mp4", basic))
    }

    @Test fun sinLimpieza() {
        val original = "Me at the zoo [jNQXAC9IVRw].mp4"
        assertEquals(original, clean(original, FilenameCleanup.NONE))
    }

    @Test fun conservaElIdentificador() {
        assertTrue("[jNQXAC9IVRw]" in clean("Me at the zoo [jNQXAC9IVRw].mp4", basic, keep = true))
    }

    @Test fun nuncaVacio() {
        assertTrue(clean("#️⃣ [abc12345].mp4", strict).isNotEmpty())
    }

    @Test fun separaExtension() {
        assertEquals("vídeo" to "mp4", Filenames.splitExtension("vídeo.mp4"))
        assertEquals("", Filenames.splitExtension("Cap. 3 del documental").second)
        assertEquals("", Filenames.splitExtension("sin extension").second)
    }

    @Test fun recorta() {
        val cut = Filenames.truncate("palabra ".repeat(40), 50)
        assertTrue(cut.length <= 50)
        assertFalse(cut.endsWith("pal"))
    }

    @Test fun caracteresProblematicos() {
        val cleaned = Filenames.replaceProblematicCharacters("a/b:c|d")
        assertTrue("/" !in cleaned && "|" !in cleaned)
    }
}

package es.altamirano.linvideo.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RefineTest {
    private fun result(title: String, duration: Double? = null, views: Long? = null, uploader: String? = null) =
        SearchResult(id = title, title = title, pageUrl = "https://example.org/$title", duration = duration, views = views, uploader = uploader)

    private fun titles(results: List<SearchResult>, query: String, refinement: Refinement = Refinement()) =
        Refine.apply(results, Refine.parse(query), refinement).map { it.title }

    @Test
    fun separaPalabrasFrasesYExclusiones() {
        val q = Refine.parse("""  gato "noche cerrada" -perro -"en directo" Árbol """)
        assertEquals(listOf("gato", "arbol"), q.words)
        assertEquals(listOf(listOf("noche", "cerrada")), q.phrases)
        assertEquals(listOf(listOf("en", "directo"), listOf("perro")), q.excluded)
        assertEquals("noche cerrada gato Árbol", q.plain)
    }

    @Test
    fun losOperadoresDeLaWebNoSeInterpretan() {
        val q = Refine.parse("""concierto year:1975 creator:"Fulano de Tal" intitle:vivo""")
        assertEquals(listOf("concierto"), q.words)
        assertEquals(emptyList<List<String>>(), q.phrases)
        assertEquals("concierto", q.plain)
        // Unos dos puntos al final de palabra o entre cifras no son un operador.
        assertEquals(listOf("wars", "episodio", "12", "30"), Refine.parse("Wars: episodio 12:30").words)
    }

    @Test
    fun unGuionSueltoOEnMedioNoExcluye() {
        val q = Refine.parse("spider-man - 2")
        assertEquals(listOf("spider", "man", "2"), q.words)
        assertEquals(emptyList<List<String>>(), q.excluded)
    }

    @Test
    fun sinTildesPeroConEñe() {
        assertEquals(listOf("cancion", "año", "ano", "ac", "dc"), Refine.tokens("Canción AÑO ano AC/DC"))
    }

    @Test
    fun lasExclusionesYLasFrasesValenSiempre() {
        val results = listOf(
            result("Gato negro en la noche"),
            result("Gato y perros"),
            result("Negro gato"),
            result("Otra cosa", uploader = "Gato Negro TV"),
        )
        assertEquals(listOf("Gato negro en la noche", "Negro gato", "Otra cosa"), titles(results, "gato -perro"))
        assertEquals(listOf("Gato negro en la noche", "Otra cosa"), titles(results, "\"gato negro\""))
        // Sin el interruptor, las palabras sueltas no filtran: eso lo decide la web.
        assertEquals(4, titles(results, "jirafa").size)
    }

    @Test
    fun todasLasPalabrasEnTituloOAutor() {
        val results = listOf(
            result("Yesterday (Remastered)", uploader = "The Beatles"),
            result("Yesterday, versión de otro"),
            result("Los Beatles cantan Yesterdays"),
            result("Alegato final"),
        )
        val strict = Refinement(allWords = true)
        assertEquals(listOf("Yesterday (Remastered)", "Los Beatles cantan Yesterdays"), titles(results, "beatles yesterday", strict))
        // Casa con el principio de la palabra, no con cualquier trozo.
        assertEquals(emptyList<String>(), titles(results, "gato", strict))
    }

    @Test
    fun duracion() {
        val results = listOf(result("corto", 239.0), result("medio", 240.0), result("limite", 1200.0), result("largo", 1201.0), result("sin dato"), result("cero", 0.0))
        assertEquals(listOf("corto"), titles(results, "x", Refinement(length = SearchLength.SHORT)))
        assertEquals(listOf("medio", "limite"), titles(results, "x", Refinement(length = SearchLength.MEDIUM)))
        assertEquals(listOf("largo"), titles(results, "x", Refinement(length = SearchLength.LONG)))
    }

    @Test
    fun ordenConLosSinDatoAlFinal() {
        val results = listOf(result("a", 100.0, 5), result("b", null, null), result("c", 300.0, 50), result("d", 200.0, 50))
        assertEquals(listOf("a", "b", "c", "d"), titles(results, "x"))
        assertEquals(listOf("c", "d", "a", "b"), titles(results, "x", Refinement(order = SearchOrder.VIEWS)))
        assertEquals(listOf("c", "d", "a", "b"), titles(results, "x", Refinement(order = SearchOrder.LONGEST)))
        assertEquals(listOf("a", "d", "c", "b"), titles(results, "x", Refinement(order = SearchOrder.SHORTEST)))
    }

    @Test
    fun soloLasWebsConOperadoresRecibenLaConsultaTalCual() {
        assertEquals(setOf(SearchSource.YOUTUBE, SearchSource.ARCHIVE, SearchSource.REDDIT), SearchSource.entries.filter { it.understandsOperators }.toSet())
        // Una consulta que sólo excluye no tiene nada que pedir a las demás.
        assertEquals(emptyList<SearchResult>(), Search.search("-perro", SearchSource.XVIDEOS))
    }
}

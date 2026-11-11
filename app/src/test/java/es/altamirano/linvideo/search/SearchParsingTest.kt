package es.altamirano.linvideo.search

import es.altamirano.linvideo.model.MediaKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `test_search_parsing.py`: las webs que se leen a mano son lo más frágil. Los fragmentos
 * reproducen la estructura real de cada sitio con datos inventados (los mismos de AppVideo).
 */
class SearchParsingTest {
    private val xvideosHtml = """
<div class="mozaique">
<div id="video_abc123" data-id="12345678" class="frame-block thumb-block">
  <div class="thumb-inside"><div class="thumb">
    <a href="/video.abc123/paseo_por_la_playa">
      <img src="blank.gif" data-src="https://thumb.example/imagen.jpg" />
    </a>
  </div></div>
  <div class="thumb-under">
    <p class="title"><a href="/video.abc123/paseo_por_la_playa"
       title="Paseo por la playa &amp; monta&ntilde;a">Paseo por la playa</a></p>
    <p class="metadata"><span class="bg"><span class="duration">32 min</span>
      <span><a href="/profiles/quien"><span class="name">Quien Sea</span></a></span></span></p>
  </div>
</div>
<div id="video_def456" data-id="87654321" class="frame-block thumb-block">
  <div class="thumb"><a href="/video.def456/otro_video">
    <img data-src="https://thumb.example/otra.jpg" /></a></div>
  <div class="thumb-under"><p class="title">
    <a href="/video.def456/otro_video" title="Otro vídeo">Otro vídeo</a></p>
    <p class="metadata"><span class="duration">5 min</span></p></div>
</div>
</div>
"""

    @Test fun xvideos() {
        val results = XVideosSite.XVIDEOS.parse(xvideosHtml, 24)
        assertEquals(2, results.size)
        val first = results[0]
        assertEquals("Paseo por la playa & montaña", first.title) // entidades decodificadas
        assertEquals("https://www.xvideos.com/video.abc123/paseo_por_la_playa", first.pageUrl)
        assertEquals("https://thumb.example/imagen.jpg", first.thumbnailUrl)
        assertEquals(32.0 * 60, first.duration!!, 0.0)
        assertEquals("Quien Sea", first.uploader)
        assertEquals("12345678", first.id)
    }

    @Test fun xnxx() {
        val page = """
    <div id="video_100xo195" data-id="60509809" class="thumb-block">
      <div class="thumb"><a href="/video-100xo195/prueba">
        <img data-src="https://thumb.example/xn.jpg" /></a></div>
      <div class="thumb-under"><p><a href="/video-100xo195/prueba"
         title="Prueba de m&aacute;quina">Prueba</a></p>
        <p class="metadata"><span class="right">1.2M <span class="icon-f icf-eye"></span></span>
        6min</p></div>
    </div>
    """
        val first = XVideosSite.XNXX.parse(page, 24)[0]
        assertEquals("https://www.xnxx.com/video-100xo195/prueba", first.pageUrl)
        assertEquals("Prueba de máquina", first.title)
        assertEquals(6.0 * 60, first.duration!!, 0.0) // «6min» suelto en los metadatos
        assertEquals(1_200_000L, first.views) // «1.2M» antes del icono del ojo
    }

    @Test fun xvideosConEnlaceOfuscado() {
        val page = """
    <div id="video_optvacm03b5" data-id="90217159" data-eid="optvacm03b5"
         class="frame-block thumb-block">
      <div class="thumb"><a href="/search-video/eAE9Ue1q3EAMfJWyv4Uja_Q==/e045c0e3">
        <img data-src="https://thumb.example/of.jpg" /></a></div>
      <div class="thumb-under"><p class="title">
        <a href="/search-video/eAE9Ue1q3EAMfJWyv4Uja_Q==/e045c0e3"
           title="La cachonda experimenta su primera vez">Título</a></p>
        <p class="metadata"><span class="duration">32 min</span>
        <span><a href="/profiles/quien"><span class="name">Quien Sea</span></a></span></p>
      </div>
    </div>
    """
        val first = XVideosSite.XVIDEOS.parse(page, 24)[0]
        assertEquals("https://www.xvideos.com/video.optvacm03b5/la_cachonda_experimenta_su_primera_vez", first.pageUrl)
        assertEquals("La cachonda experimenta su primera vez", first.title)
        assertEquals(32.0 * 60, first.duration!!, 0.0)
        assertEquals("90217159", first.id)
    }

    @Test fun slugLimpio() {
        assertEquals("paseo_por_la_playa_montana", Text.slugify("Paseo por la playa & montaña"))
        assertEquals("video", Text.slugify("¡¿...!!"))
        assertEquals("recorte_78c2_de83_mov", Text.slugify("recorte.78C2-DE83.MOV"))
    }

    @Test fun paginaVacia() = assertEquals(emptyList<SearchResult>(), XVideosSite.XVIDEOS.parse("<html>nada</html>", 24))

    @Test fun respetaElLimite() = assertEquals(1, XVideosSite.XVIDEOS.parse(xvideosHtml, 1).size)

    @Test fun paginacionXvideosYXnxx() {
        assertEquals("https://www.xvideos.com/?k=a%20b", XVideosSite.XVIDEOS.pagedUrl("a b", 1))
        assertEquals("https://www.xvideos.com/?k=a&p=2", XVideosSite.XVIDEOS.pagedUrl("a", 3))
        assertEquals("https://www.xnxx.com/search/a/1", XVideosSite.XNXX.pagedUrl("a", 2))
    }

    @Test fun pornhub() {
        val page = """
    <li data-block-position="1" data-video-id="475426235">
      <a class="js-pop" href="/view_video.php?viewkey=68e2bb7570170" data-video-id="475426235"
         data-poster="https://pix.example/poster.jpg"
         alt="Un t&iacute;tulo cualquiera"></a>
      <div class="duration thumbOverlay"><span class="bgEffect time">10:30</span></div>
      <a class="uploaderLink" href="/model/alguien">Alguien</a>
    </li>
    """
        val first = Tube.parsePornhub(page, 24)[0]
        assertEquals("Un título cualquiera", first.title)
        assertEquals("https://www.pornhub.com/view_video.php?viewkey=68e2bb7570170", first.pageUrl)
        assertEquals(630.0, first.duration!!, 0.0)
        assertEquals("Alguien", first.uploader)
    }

    @Test fun youporn() {
        val page = """
    <div data-video-id="223611391" data-uploader-name="Quien Sea"
         aria-label="Detr&aacute;s de las c&aacute;maras">
      <a href="/watch/223611391/" class="video-box-image">
        <img class="thumb-image" data-src="https://pix.example/yp.jpg" />
      </a>
      <div class="video-properties">
        <div class="video-duration tm_video_duration"><span>
        01:46
        </span></div>
      </div>
    </div>
    """
        val first = Tube.parseYouporn(page, 24)[0]
        assertEquals("Detrás de las cámaras", first.title)
        assertEquals("https://www.youporn.com/watch/223611391/", first.pageUrl)
        assertEquals("Quien Sea", first.uploader)
        assertEquals(106.0, first.duration!!, 0.0)
    }

    @Test fun xhamster() {
        val page = """
    <script>window.initials={"searchResult":{"videoThumbProps":[
      {"id":28952176,"title":"Un título","pageURL":"https://xhamster.com/videos/uno-abc",
       "duration":612,"thumbURL":"https://ic.example/uno.webp","views":3088683,
       "landing":{"type":"channel","name":"Un canal"}}
    ]},"otraCosa":{"anidado":{"llaves":"}"}}};</script>
    """
        val first = Tube.parseXhamster(page, 24)[0]
        assertEquals("Un título", first.title)
        assertEquals("https://xhamster.com/videos/uno-abc", first.pageUrl)
        assertEquals(612.0, first.duration!!, 0.0)
        assertEquals("Un canal", first.uploader)
        assertEquals(3_088_683L, first.views)
        assertEquals("28952176", first.id)
    }

    @Test fun xhamsterCambiado() {
        // Si cambia el formato, se avisa en vez de devolver vacío en silencio.
        assertThrows(SearchError::class.java) { Tube.parseXhamster("<html>sin datos</html>", 24) }
    }

    @Test fun jsonBalanceado() {
        val text = """basura window.initials={"a":{"b":"}"},"c":1}; más basura"""
        assertEquals("""{"a":{"b":"}"},"c":1}""", Text.extractBalancedJson(text, "window.initials"))
    }

    @Test fun reddit() {
        fun context(postId: String, title: String) =
            "{&quot;action_info&quot;:{&quot;type&quot;:&quot;post&quot;},&quot;post&quot;:" +
                "{&quot;id&quot;:&quot;$postId&quot;,&quot;nsfw&quot;:false,&quot;title&quot;:" +
                "&quot;$title&quot;},&quot;subreddit&quot;:{&quot;name&quot;:&quot;cats&quot;}}"

        val page = """
    <search-telemetry-tracker data-faceplate-tracking-context="${context("t3_abc", "Gato &amp; perro")}">
      <a href="/r/cats/comments/abc/gato_perro/">
      <shreddit-player src="https://packaged-media.redd.it/x.mp4"
        packaged-media-json="{&quot;playbackMp4s&quot;:{&quot;duration&quot;:41,&quot;permutations&quot;:[]}}"
        poster="https://external-preview.redd.it/p.png?format=pjpg&amp;s=1"></shreddit-player></a>
    </search-telemetry-tracker>
    <search-telemetry-tracker data-faceplate-tracking-context="${context("t3_img", "Sólo una foto")}">
      <a href="/r/cats/comments/img/foto/"><img src="https://preview.redd.it/f.jpg"></a>
    </search-telemetry-tracker>
    <faceplate-partial src="/svc/shreddit/search/?q=gatos&amp;type=media&amp;cursor=eyJhIjoxfQ%3D%3D">
    """
        val (results, cursor) = Reddit.parse(page)
        assertEquals(1, results.size) // la foto se descarta
        val video = results[0]
        assertEquals("t3_abc", video.id)
        assertEquals("Gato & perro", video.title)
        assertEquals("https://www.reddit.com/r/cats/comments/abc/gato_perro/", video.pageUrl)
        assertEquals("https://external-preview.redd.it/p.png?format=pjpg&s=1", video.thumbnailUrl)
        assertEquals(41.0, video.duration!!, 0.0)
        assertEquals("r/cats", video.uploader)
        assertEquals("eyJhIjoxfQ%3D%3D", cursor)
    }

    @Test fun tnaflix() {
        val page = """
    <div data-vid="7233056" data-num="59" class="col-xs-6">
      <a class="thumb" href="https://www.tnaflix.com/solo-porn/Un-Titulo/video7233056">
        <img class="lazyload" src="/placeholder.jpg" data-src="https://img.tnaflix.com/a/10.jpg" alt="Un título" />
        <div class="thumb-icon video-duration">12:02</div></a>
      <a href="https://www.tnaflix.com/solo-porn/Un-Titulo/video7233056" class="video-title text-break">
          Un t&iacute;tulo       </a>
      <div class="d-flex">
        <a href="https://www.tnaflix.com/profile/alguien" class="me-2 badge">
            alguien     </a>
        <div class="me-2"><i class="icon-eye"></i>8.4K   </div>
      </div>
    </div>
    """
        val video = HtmlSites.parseTnaflix(page, 24)[0]
        assertEquals("7233056", video.id)
        assertEquals("Un título", video.title)
        assertEquals("https://www.tnaflix.com/solo-porn/Un-Titulo/video7233056", video.pageUrl)
        assertEquals("https://img.tnaflix.com/a/10.jpg", video.thumbnailUrl)
        assertEquals(722.0, video.duration!!, 0.0)
        assertEquals("alguien", video.uploader)
        assertEquals(8400L, video.views)
    }

    @Test fun porntrex() {
        val page = "<div class=\"video-preview-screen video-item thumb-item  \" data-item-id=\"3062828\">" +
            "<a href=\"https://www.porntrex.com/video/3062828/un-video\"  class=\"thumb rotator-screen\" >" +
            "<img class=\"cover lazyload\"data-src=\"//ptx.cdntrex.com/shots/1.jpg?v=3\"    alt=\"Un vídeo\" />" +
            "</a><div class=\"viewsthumb\">12 118 views</div><div class=\"durations\">" +
            "<i class=\"fa fa-clock-o\"></i> 30:25</div><p class=\"inf\">" +
            "<a href=\"https://www.porntrex.com/video/3062828/un-video\" title=\"Un v&iacute;deo\" >" +
            "Un vídeo</a></p></div>"
        val video = HtmlSites.parsePorntrex(page, 24)[0]
        assertEquals("3062828", video.id)
        assertEquals("Un vídeo", video.title)
        assertEquals("https://www.porntrex.com/video/3062828/un-video", video.pageUrl)
        assertEquals("https://ptx.cdntrex.com/shots/1.jpg?v=3", video.thumbnailUrl)
        assertEquals(30.0 * 60 + 25, video.duration!!, 0.0)
        assertEquals(12_118L, video.views)
    }

    @Test fun youjizz() {
        val page = """
    <div class="video-thumb" data-videoId="2171023">
      <div class="frame-wrapper"><a data-video-id="2171023" class="frame video" href="/videos/music-lessons-2171023.html">
        <img class="img-responsive lazy" src="spacer.gif" data-original="//cdne-pics.youjizz.com/b/f.jpg" alt="" /></a></div>
      <div class="video-title">
        <a href='/videos/music-lessons-2171023.html' class="">Music lessons</a>
      </div>
      <span class="time"><i class="fa fa-clock-o"></i>&nbsp;25:02</span>
      <span class="views"><i class="fa fa-eye"></i>&nbsp;<span class="format-views">2395120</span></span>
    </div>
    """
        val video = HtmlSites.parseYoujizz(page, 24)[0]
        assertEquals("2171023", video.id)
        assertEquals("Music lessons", video.title)
        assertEquals("https://www.youjizz.com/videos/music-lessons-2171023.html", video.pageUrl)
        assertEquals("https://cdne-pics.youjizz.com/b/f.jpg", video.thumbnailUrl)
        assertEquals(25.0 * 60 + 2, video.duration!!, 0.0)
        assertEquals(2_395_120L, video.views)
    }

    @Test fun thisvid() {
        val page = """
    <a href="https://thisvid.com/videos/">Todos los vídeos</a>
    <a href="https://thisvid.com/videos/publico/" title="Vídeo p&uacute;blico" class="tumbpu">
      <span class="thumb " ><img class="lazy-load" data-original="//static.thisvid.com/s/1.jpg" />
        <span class="duration">2:20</span>
        <span class="info"><span class="view"><i class="ico-view"></i>1204</span></span></span>
      <span class="title">Vídeo público</span></a>
    <a href="https://thisvid.com/videos/privado/" title="Privado" class="tumbpu">
      <span class="thumb private" style="background: url(//static.thisvid.com/s/2.jpg)">
        <span class="duration">1:00</span></span></a>
    """
        val results = HtmlSites.parseThisvid(page, 24)
        assertEquals(1, results.size)
        val video = results[0]
        assertEquals("publico", video.id)
        assertEquals("Vídeo público", video.title)
        assertEquals("https://thisvid.com/videos/publico/", video.pageUrl)
        assertEquals("https://static.thisvid.com/s/1.jpg", video.thumbnailUrl)
        assertEquals(140.0, video.duration!!, 0.0)
        assertEquals(1204L, video.views)
    }

    @Test fun resultadosDeYtdlp() {
        val root = Json.parseToJsonElement(
            """{"entries": [
            {"id": "a", "title": "Uno", "url": "https://www.youtube.com/watch?v=a", "duration": 61,
             "channel": "Canal", "view_count": 1500,
             "thumbnails": [{"url": "https://i/peq.jpg"}, {"url": "https://i/grande.jpg"}]},
            {"id": "b", "title": "Dos", "url": "https://api.soundcloud.com/x", "webpage_url": "https://soundcloud.com/x"},
            {"title": "sin id"}
            ]}""",
        )
        val results = YtdlpSearch.parseEntries(root)
        assertEquals(listOf("a", "b"), results.map { it.id })
        assertEquals("https://i/grande.jpg", results[0].thumbnailUrl)
        assertEquals("Canal", results[0].uploader)
        assertEquals(1500L, results[0].views)
        assertEquals("https://soundcloud.com/x", results[1].pageUrl)
        // La página 2 descarta las anteriores.
        assertEquals(listOf("b"), YtdlpSearch.parseEntries(root, skip = 1).map { it.id })
    }

    // --- Búsquedas sin resultados ---------------------------------------------------

    @Test fun paginasSinResultados() {
        val cases = listOf(
            // Fragmentos reales de las páginas de «no hay nada» (octubre de 2026).
            SearchSource.XVIDEOS to "<div id=\"content\"> <h3>No v&iacute;deos encontrados con esta b&uacute;squeda.</h3>",
            SearchSource.XNXX to "<h3>No vídeos encontrados con esta búsqueda.</h3>",
            SearchSource.TNAFLIX to "<div class=\"no-results\">\n  No results\n</div>",
            SearchSource.YOUJIZZ to "'jazz piano' search - 0 results &nbsp;",
            SearchSource.THISVID to "<p>No results found. We couldn't find any videos</p>",
            SearchSource.PORNHUB to "<title>No results found - Pornhub.com</title>",
            SearchSource.PORNHUB to "<title>No se han encontrado resultados - Pornhub.com</title>",
            SearchSource.YOUPORN to "Sorry!! We couldn&#039;t find what you were looking for.",
        )
        for ((source, page) in cases) {
            assertTrue("$source: $page", HtmlSites.saysNoResults(page, source))
            assertEquals(emptyList<SearchResult>(), HtmlSites.checked(emptyList(), page, source))
        }
    }

    @Test fun paginaDesconocidaEsFallo() {
        assertFalse(HtmlSites.saysNoResults("<html>otra maquetación</html>", SearchSource.XVIDEOS))
        // PornTrex no avisa de ninguna forma clara.
        assertFalse(HtmlSites.saysNoResults("No results found", SearchSource.PORNTREX))
        assertThrows(SearchError::class.java) { HtmlSites.checked(emptyList(), "<html>otra cosa</html>", SearchSource.TNAFLIX) }
    }

    @Test fun tendenciasDeThisvidNoSonResultados() {
        val page = """
    <p>No results found. We couldn't find any videos matching your search.</p>
    <h2>Trending Videos</h2>
    <a href="https://thisvid.com/videos/tendencia/" title="Tendencia" class="tumbpu">
      <span class="thumb "><span class="duration">2:20</span></span></a>
    """
        assertEquals(1, HtmlSites.parseThisvid(page, 24).size)
        assertEquals(emptyList<SearchResult>(), HtmlSites.checked(HtmlSites.parseThisvid(page, 24), page, SearchSource.THISVID))
    }
}

/** Las pruebas de búsqueda de `test_utilities.py`. */
class SearchUtilitiesTest {
    @Test fun duracionesEnPalabras() {
        assertEquals(32.0 * 60, Text.parseLooseDuration("32 min")!!, 0.0)
        assertEquals(6.0 * 60, Text.parseLooseDuration("6min")!!, 0.0)
        assertEquals(3900.0, Text.parseLooseDuration("1 h 5 min")!!, 0.0)
        assertEquals(45.0, Text.parseLooseDuration("45 sec")!!, 0.0)
        assertEquals(630.0, Text.parseLooseDuration("10:30")!!, 0.0)
        assertEquals(3723.0, Text.parseLooseDuration("1:02:03")!!, 0.0)
        assertNull(Text.parseLooseDuration("sin datos"))
    }

    @Test fun visitasAbreviadas() {
        assertEquals(1_200_000L, Text.parseCompactNumber("1.2M"))
        assertEquals(340_000L, Text.parseCompactNumber("340K"))
        assertEquals(5320L, Text.parseCompactNumber("5.320")) // punto de millares
        assertEquals(42L, Text.parseCompactNumber("42"))
        assertNull(Text.parseCompactNumber("nada"))
    }

    @Test fun entidadesHtml() {
        assertEquals("café & té", Text.decodeEntities("caf&eacute; &amp; t&eacute;"))
        assertEquals("resistência", Text.decodeEntities("resist&ecirc;ncia"))
        assertEquals("éxito", Text.decodeEntities("&#233;xito"))
        assertEquals("ABC", Text.decodeEntities("&#x41;BC"))
        assertEquals("sin entidades", Text.decodeEntities("sin entidades"))
        assertEquals("a b", Text.decodeEntities("a&nbsp;b"))
        assertEquals("it's", Text.decodeEntities("it&#039;s"))
        assertEquals("“hola”", Text.decodeEntities("&#147;hola&#148;")) // Windows-1252, como Python
        assertEquals("&desconocida;", Text.decodeEntities("&desconocida;"))
    }

    @Test fun codificaComoPython() {
        assertEquals("a%20b%2Fc%C3%B1~", Text.quote("a b/cñ~"))
        assertEquals("a+b%2B", Text.quotePlus("a b+"))
    }

    private fun result(id: String) = SearchResult(id = id, title = id, pageUrl = "https://e.test/$id")

    @Test fun intercalaPorRondas() {
        val mixed = Search.interleave(
            mapOf(SearchSource.YOUTUBE to listOf(result("y1"), result("y2")), SearchSource.DAILYMOTION to listOf(result("d1"), result("d2"))),
            listOf(SearchSource.YOUTUBE, SearchSource.DAILYMOTION),
        )
        assertEquals(listOf("y1", "d1", "y2", "d2"), mixed.map { it.id })
    }

    @Test fun fuentesDesiguales() {
        val mixed = Search.interleave(
            mapOf(SearchSource.YOUTUBE to listOf(result("y1"), result("y2"), result("y3")), SearchSource.PEERTUBE to listOf(result("p1"))),
            listOf(SearchSource.YOUTUBE, SearchSource.PEERTUBE),
        )
        assertEquals(listOf("y1", "p1", "y2", "y3"), mixed.map { it.id })
    }

    @Test fun sinResultados() = assertEquals(emptyList<SearchResult>(), Search.interleave(emptyMap(), listOf(SearchSource.YOUTUBE)))

    @Test fun categorias() {
        assertEquals(listOf(SearchSource.SOUNDCLOUD, SearchSource.MIXCLOUD), SearchSource.inCategory(SearchCategory.AUDIO))
        assertTrue(SearchSource.YOUTUBE in SearchSource.inCategory(SearchCategory.VIDEO))
        assertTrue(SearchSource.REDGIFS in SearchSource.inCategory(SearchCategory.ADULT))
        val grouped = SearchCategory.entries.flatMap { SearchSource.inCategory(it) }
        assertEquals(SearchSource.entries.toSet(), grouped.toSet())
        assertEquals(19, SearchSource.entries.size)
    }

    @Test fun todasLasWebs() {
        assertEquals(SearchSource.entries.size, SearchScope.everywhere().sources.size)
        assertTrue(SearchScope.everywhere().isMulti)
        assertFalse(SearchScope(source = SearchSource.YOUTUBE).isMulti)
        assertEquals(SearchScope(category = SearchCategory.ADULT), SearchScope.parse("adult"))
        assertEquals(SearchScope.everywhere(), SearchScope.parse("todas"))
        assertNull(SearchScope.parse("nada"))
    }

    @Test fun formatoPropuesto() {
        assertEquals(MediaKind.AUDIO_MP3, SearchScope(source = SearchSource.SOUNDCLOUD).preferredKind)
        assertEquals(MediaKind.GIF, SearchScope(source = SearchSource.REDGIFS).preferredKind)
        assertEquals(MediaKind.AUDIO_MP3, SearchScope(category = SearchCategory.AUDIO).preferredKind)
        assertEquals(MediaKind.VIDEO_MAX, SearchScope(source = SearchSource.YOUTUBE).preferredKind)
    }

    @Test fun visitasEnTexto() {
        assertEquals("1,5M visitas", result("a").copy(views = 1_500_000).viewsText)
        assertEquals("12K visitas", result("a").copy(views = 12_345).viewsText)
        assertNull(result("a").copy(views = 0).viewsText)
    }

    @Test fun variasWebsAlaVez() = kotlinx.coroutines.runBlocking {
        // Sin red: una consulta vacía no pregunta a nadie.
        assertEquals(FederatedResults(), Search.searchMany("  ", SearchSource.entries))
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

/** Juegos, paso 5: miniaturas de Commons, el servidor sin TMDB y el crédito al actualizar los datos. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JuegosPaso5Test {
    private val mini = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/68/Celeste_box_art_cropped.png/500px-Celeste_box_art_cropped.png"
    private val original = "https://upload.wikimedia.org/wikipedia/commons/6/68/Celeste_box_art_cropped.png"
    private val filePath = "https://commons.wikimedia.org/wiki/Special:FilePath/Celeste_box_art_cropped.png?width=400"

    /* ----- Miniaturas de Commons ----- */

    @Test fun laMiniaturaDeCommonsPasaDeThumbAUploadSinParametros() {
        // Como responde hoy la API de Commons (imageinfo con iiurlwidth=500): la miniatura en thumb.wikimedia.org con «?utm_…».
        val info = buildJsonObject {
            put("thumburl", "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/68/Celeste_box_art_cropped.png/500px-Celeste_box_art_cropped.png?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail")
            put("url", "$original?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original")
        }
        assertEquals(mini, Wikimedia.urlSubida(info))
        // Imagen más chica que el ancho: Commons da el original como miniatura («thumbnail_unscaled»).
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/a/a9/Example.jpg", Wikimedia.urlSubida(buildJsonObject {
            put("thumburl", "https://upload.wikimedia.org/wikipedia/commons/a/a9/Example.jpg?utm_source=commons.wikimedia.org&utm_content=thumbnail_unscaled")
        }))
        // Un SVG: su miniatura en PNG; un nombre largo: «500px-thumbnail.jpg», tal como lo da Commons.
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/3/35/Tux.svg/500px-Tux.svg.png",
            Wikimedia.urlSubida(buildJsonObject { put("thumburl", "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/35/Tux.svg/500px-Tux.svg.png?utm_source=x") }))
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/e/ed/Largo.jpg/500px-thumbnail.jpg",
            Wikimedia.urlSubida(buildJsonObject { put("thumburl", "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ed/Largo.jpg/500px-thumbnail.jpg?utm_source=x") }))
        // Lo que no es de Commons (otra wiki, otro dominio) no sirve.
        assertNull(Wikimedia.urlSubida(buildJsonObject { put("thumburl", "https://upload.wikimedia.org/wikipedia/en/a/ab/Portada.jpg") }))
        assertNull(Wikimedia.urlSubida(buildJsonObject { put("thumburl", "https://thumb.wikimedia.org.example/wikipedia/commons/x.png") }))
    }

    @Test fun deLaComunidadSeUsaLaMiniaturaDeLaMismaImagen() {
        val credito = ImagenElegida("wikimedia", mini, "Ana", "CC BY 4.0")
        assertTrue(Imagenes.esMiniaturaCommons(mini))
        assertFalse(Imagenes.esMiniaturaCommons(original))
        // La de la comunidad llega por su original (la clave de los votos): se usa la miniatura conocida.
        assertEquals(credito, Imagenes.miniaturaConocida(original, null, credito))
        assertNull(Imagenes.miniaturaConocida(original, ImagenElegida("wikimedia", original, "Ana", "CC BY 4.0")))
        assertNull(Imagenes.miniaturaConocida(original, credito.copy(autor = null)))
        val llegada = PortadasComunidad(comunidad = original, miVoto = original)
        assertEquals(mini, Imagenes.comunidadLigera(llegada, credito).comunidad)
        assertEquals(original, Imagenes.comunidadLigera(llegada, credito).miVoto)
        // Otra imagen o ningún crédito: la de la comunidad queda igual.
        assertEquals(original, Imagenes.comunidadLigera(llegada, credito.copy(url = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Otra.png/500px-Otra.png")).comunidad)
        assertEquals(original, Imagenes.comunidadLigera(llegada, null).comunidad)
        // La miniatura y el original son la misma imagen para votar.
        assertEquals(Imagenes.clave(original), Imagenes.clave(mini))
        assertEquals(Imagenes.clave(original), Imagenes.clave(filePath))
        assertTrue(Imagenes.votable("wd:Q1", mini, credito))
    }

    @Test fun elCreditoDeLaComunidadNoSePierdeNiSePideCadaVez() = runTest {
        val credito = ImagenElegida("wikimedia", mini, "Ana", "CC BY 4.0")
        var pedidos = 0
        suspend fun pedirSinCredito(): ImagenElegida? { pedidos++; return ImagenElegida("wikimedia", mini) }
        // Commons vuelve sin autor ni licencia: queda el crédito conocido (de la obra), no se pierde.
        val deLaObra = ImagenElegida("wikimedia", original, "Ana", "CC BY 4.0")
        assertEquals("Ana", Imagenes.creditoDeComunidad(original, deLaObra, null) { pedirSinCredito() }?.autor)
        // Una imagen de menos de 500 px: Commons da el original (no una ruta /thumb/). Ya pedida con crédito, no se vuelve a pedir.
        val chica = "https://upload.wikimedia.org/wikipedia/commons/a/a9/Example.jpg"
        val yaPedida = ImagenElegida("wikimedia", chica, "Beto", "CC0")
        pedidos = 0
        assertEquals(yaPedida, Imagenes.creditoDeComunidad(chica, null, yaPedida) { pedirSinCredito() })
        assertEquals(0, pedidos)
        // La miniatura conocida, sin pedir; si no se sabe nada, lo que dé Commons (aunque sea sin crédito).
        assertEquals(credito, Imagenes.creditoDeComunidad(original, null, credito) { pedirSinCredito() })
        assertEquals(0, pedidos)
        assertEquals(ImagenElegida("wikimedia", mini), Imagenes.creditoDeComunidad(original, null, null) { pedirSinCredito() })
        assertEquals(1, pedidos)
        // Lo pedido con crédito gana; lo de otra imagen no sirve.
        assertEquals(credito, Imagenes.creditoDeComunidad(original, null, null) { credito })
        assertNull(Imagenes.creditoDeComunidad(original, null, null) { ImagenElegida("wikimedia", chica, "X", "CC0") })
    }

    /* ----- Servidor sin TMDB ----- */

    @Test fun elServidorSinTmdbNoSeConfundeConOcupado() {
        assertTrue(falloDeRespuesta(503, "/v1/tmdb/search/movie", { """{"error":"sin_tmdb"}""" }, null) is SinTmdb)
        assertTrue(falloDeRespuesta(503, "/v1/tmdb/search/movie", { "" }, "30") is Ocupado)
        assertTrue(falloDeRespuesta(503, "/ws/2/release-group", { """{"error":"sin_tmdb"}""" }, null) is Ocupado)
        assertTrue(falloDeRespuesta(429, "/v1/tmdb/search/movie", { error("no se lee") }, "5") is Ocupado)
        assertTrue(falloDeRespuesta(401, "/v1/tmdb/movie/1", { error("no se lee") }, null) is ClaveMala)
        assertNull(falloDeRespuesta(200, "/v1/tmdb/movie/1", { error("no se lee") }, null))
        // Si no se puede leer el cuerpo de ese 503, es ocupado (no «sin internet»).
        assertTrue(falloDeRespuesta(503, "/v1/tmdb/search/movie", { throw java.io.IOException("cortado") }, null) is Ocupado)
        // Sin TMDB, buscar o actualizar a mano dice que los datos de cine no están (no «sin internet» ni «prueba de nuevo»).
        assertEquals(R.string.buscar_sin_tmdb, avisoErrorBusqueda(SinTmdb()))
        assertEquals(R.string.buscar_fallo, avisoErrorBusqueda(Ocupado()))
    }

    @Test fun sinTmdbNoSeReintentaComoOcupado() = runTest {
        var pedidos = 0
        try { reintentarOcupado { pedidos++; throw SinTmdb() }; fail("Debía propagar el fallo") } catch (_: SinTmdb) {}
        assertEquals(1, pedidos)
        assertEquals(0L, testScheduler.currentTime)
        // El trabajo masivo lo recuerda y no lo vuelve a pedir; no cuenta como corte de red.
        val enlace = EnlaceMasivo()
        assertFalse(enlace.sinTmdb)
        enlace.sinTmdb = true
        kotlinx.coroutines.withContext(enlace) { enlace.comprobar() }
    }

    /* ----- «Actualizar datos» conserva el crédito ----- */

    private val credito = ImagenElegida("wikimedia", mini, "Ana", "CC BY 4.0")
    private val guardado = Obra("o1", Tipos.JUEGO, "Celeste", portada = mini, portadaRecomendada = mini, imagen = credito, fuente = "wd:Q28451532")

    /** Lo que hace «Actualizar datos» con la portada y su procedencia (`traerDatos`). */
    private fun actualizar(v: Obra, d: Obra, comunidad: PortadasComunidad = PortadasComunidad(), creditoComunidad: ImagenElegida? = null): Obra {
        val portada = if (v.portadaPropia) v.portada else Imagenes.elegir(v, comunidad, d.portada.ifBlank { v.portadaRecomendada ?: v.portada })
        return v.copy(portada = portada, portadaRecomendada = d.portada.ifBlank { v.portadaRecomendada ?: v.portada },
            imagen = Imagenes.alRenovar(v, d, portada, creditoComunidad))
    }

    @Test fun actualizarDatosConservaElCreditoDeLaImagen() {
        val ui = io.github.fogonapps.cajon.ui.creditoImagen(guardado)
        assertEquals("Ana · CC BY 4.0", ui)
        // Wikidata trae la misma imagen con su crédito.
        val conImagen = actualizar(guardado, Obra("", Tipos.JUEGO, "Celeste", portada = mini, imagen = credito, fuente = "wd:Q28451532"))
        assertEquals(ui, io.github.fogonapps.cajon.ui.creditoImagen(conImagen))
        // Commons falló (el detalle llega sin imagen): la portada y su crédito se quedan.
        val sinImagen = actualizar(guardado, Obra("", Tipos.JUEGO, "Celeste", fuente = "wd:Q28451532"))
        assertEquals(mini, sinImagen.portada)
        assertEquals(ui, io.github.fogonapps.cajon.ui.creditoImagen(sinImagen))
        // Guardada antes como «Special:FilePath»: el detalle trae la miniatura con su crédito y la ficha lo muestra.
        val viejo = guardado.copy(portada = filePath, portadaRecomendada = filePath, imagen = credito.copy(url = filePath))
        assertEquals(ui, io.github.fogonapps.cajon.ui.creditoImagen(actualizar(viejo, Obra("", Tipos.JUEGO, "Celeste", portada = mini, imagen = credito))))
    }

    @Test fun actualizarDatosNoLeDaALaImagenDeLaComunidadElCreditoDeOtra() {
        val otra = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Otra.png/500px-Otra.png"
        val deLaComunidad = ImagenElegida("wikimedia", otra, "Beto", "CC BY-SA 4.0")
        val v = guardado.copy(portada = otra, imagen = deLaComunidad)
        val d = Obra("", Tipos.JUEGO, "Celeste", portada = mini, imagen = credito)
        val r = actualizar(v, d, PortadasComunidad(comunidad = otra), deLaComunidad)
        assertEquals(otra, r.portada)
        assertEquals("Beto · CC BY-SA 4.0", io.github.fogonapps.cajon.ui.creditoImagen(r))
        // La que eligió la persona no se toca.
        val propia = ImagenElegida("local", "file:///x.jpg")
        val p = actualizar(guardado.copy(portada = "file:///x.jpg", portadaPropia = true, imagen = propia), d)
        assertEquals(propia, p.imagen)
        assertEquals("file:///x.jpg", p.portada)
    }
}

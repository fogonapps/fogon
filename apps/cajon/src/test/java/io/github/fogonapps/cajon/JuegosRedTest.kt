package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Ajustes
import io.github.fogonapps.cajon.datos.Destacados
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.ImagenElegida
import io.github.fogonapps.cajon.datos.Imagenes
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.PartesPerfil
import io.github.fogonapps.cajon.datos.Perfil
import io.github.fogonapps.cajon.datos.PortadasComunidad
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.Resena
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.Visibilidad
import io.github.fogonapps.cajon.datos.Wikimedia
import io.github.fogonapps.social.JsonSocial
import io.github.fogonapps.social.PerfilSocial
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Los videojuegos en las cifras, el perfil y la red (favoritas, destacados, reseñas, historias y portadas de todos). */
class JuegosRedTest {
    private val hoy = LocalDate.of(2026, 11, 23)
    private val pelicula = Obra("p1", Tipos.PELICULA, "Matrix", minutos = 136, estado = Estados.TERMINADO, terminada = "2026-03-01",
        creador = "Lana Wachowski", fuente = "tmdb:movie:603")
    private val libro = Obra("l1", Tipos.LIBRO, "Rayuela", paginas = 600, estado = Estados.TERMINADO, terminada = "2026-04-01")
    private val subida = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Celeste.png/500px-Celeste.png"
    private val juego = Obra("j1", Tipos.JUEGO, "Celeste", anio = 2018, creador = "Maddy Makes Games, Extremely OK Games",
        estado = Estados.TERMINADO, terminada = "2026-05-02", fuente = "wd:Q42887370", portada = subida,
        imagen = ImagenElegida("wikimedia", subida, "Ana", "CC BY 4.0"))

    /* ----- A. Cifras ----- */

    @Test fun unJuegoTerminadoSumaASusCifrasSinTocarLasDemas() {
        val sin = Estado(obras = listOf(pelicula, libro))
        val con = Estado(obras = listOf(pelicula, libro, juego))
        val antes = Logica.cifras(sin, 2026)
        val despues = Logica.cifras(con, 2026)
        assertEquals(1, despues.porTipo[Tipos.JUEGO])
        assertEquals(antes.porTipo.values.sum() + 1, despues.porTipo.values.sum())
        // Horas de cine, páginas y canciones no cambian.
        assertEquals(antes.minutosCine, despues.minutosCine)
        assertEquals(antes.minutosSeries, despues.minutosSeries)
        assertEquals(antes.paginas, despues.paginas)
        assertEquals(antes.canciones, despues.canciones)
        // Lo mismo en las cifras del perfil.
        assertEquals("3", RedSocial.valorCifra(PartesPerfil.C_TERMINADAS, con, hoy))
        assertEquals(RedSocial.valorCifra(PartesPerfil.C_HORAS_CINE, sin, hoy), RedSocial.valorCifra(PartesPerfil.C_HORAS_CINE, con, hoy))
        assertEquals(RedSocial.valorCifra(PartesPerfil.C_PAGINAS, sin, hoy), RedSocial.valorCifra(PartesPerfil.C_PAGINAS, con, hoy))
        // Un juego de otro año no cuenta en el año.
        assertEquals(0, Logica.cifras(Estado(obras = listOf(juego.copy(terminada = "2025-05-02"))), 2026).porTipo[Tipos.JUEGO])
    }

    @Test fun cadaEstudioDeUnJuegoCuentaAparte() {
        val otro = juego.copy(id = "j2", titulo = "TowerFall", creador = "Maddy Makes Games")
        val c = Logica.cifras(Estado(obras = listOf(juego, otro)), 2026)
        assertEquals("Maddy Makes Games" to 2, c.creadores.first())
        assertTrue("Extremely OK Games" to 1 in c.creadores)
        // En las demás obras el creador sigue entero (como antes).
        val dos = libro.copy(creador = "Ana, Beto")
        assertEquals(listOf("Ana, Beto" to 1), Logica.cifras(Estado(obras = listOf(dos)), 2026).creadores)
    }

    @Test fun laParteCifrasDelPerfilLlevaLosJuegos() {
        val e = Estado(obras = listOf(pelicula, juego), perfil = Perfil(visibilidad = mapOf(PartesPerfil.CIFRAS to Visibilidad.PUBLICA)))
        val cifras = RedSocial.perfilDe(e).partes.getValue(PartesPerfil.CIFRAS).datos as JsonObject
        assertEquals("1", (cifras[Tipos.JUEGO] as JsonPrimitive).content)
        assertEquals("1", (cifras[Tipos.PELICULA] as JsonPrimitive).content)
    }

    @Test fun quienNoUsaJuegosNoLosVeEnCifras() {
        // Con una barra elegida sin Juegos y sin juegos en el cajón, no salen.
        val sinPestana = Ajustes(barra = listOf("INICIO", "CINE", "LIBROS"), juegosEnBarra = true)
        assertFalse(Tipos.JUEGO in Secciones.tiposVisibles(Estado(ajustes = sinPestana, obras = listOf(pelicula))))
        assertTrue(Tipos.JUEGO in Secciones.tiposVisibles(Estado(ajustes = sinPestana, obras = listOf(pelicula, juego))))
    }

    /* ----- B. Favoritas y destacados ----- */

    @Test fun elHuecoDeJuegoSaleDeLasFavoritas() {
        assertEquals("juego", Destacados.JUEGO)
        assertTrue(Destacados.JUEGO in Destacados.FAVORITOS && Destacados.JUEGO in Logica.HUECOS_DE_FAVORITAS)
        // Los de antes no cambian de id.
        assertEquals(listOf("pelicula", "serie", "anime", "album", "libro"), Destacados.FAVORITOS.take(5))
        assertEquals(Tipos.JUEGO, Logica.tipoDeHueco(Destacados.JUEGO))
        assertEquals(Tipos.LIBRO, Logica.tipoDeHueco(Destacados.LIBRO))
        assertNull(Logica.tipoDeHueco(Destacados.ARTISTA))
        assertNull(Logica.tipoDeHueco(Destacados.MARCO))
        val e = Estado(obras = listOf(pelicula, juego), ajustes = Ajustes(favoritas = listOf("p1", "j1")))
        assertEquals(listOf(juego), Logica.favoritasPara(e, Tipos.JUEGO))
        assertEquals(listOf(pelicula), Logica.favoritasPara(e, Tipos.PELICULA))
    }

    @Test fun quitarLaEstrellaQuitaElJuegoDeLosDestacados() {
        val e = Estado(obras = listOf(juego), ajustes = Ajustes(favoritas = listOf("j1")),
            perfil = Perfil(destacados = mapOf(Destacados.JUEGO to "j1", Destacados.MARCO to "j1")))
        val sin = Logica.favorita(e, "j1", 5)
        assertFalse("j1" in sin.ajustes.favoritas)
        assertNull(sin.perfil.destacados[Destacados.JUEGO])
        // «La obra que me marcó» no sale de las favoritas: se queda.
        assertEquals("j1", sin.perfil.destacados[Destacados.MARCO])
    }

    @Test fun elHuecoDeJuegoSoloSeVeSiSeUsanLosJuegos() {
        val sinJuegos = Estado(obras = listOf(pelicula))
        assertFalse(Destacados.JUEGO in Logica.huecosFavoritos(sinJuegos, conJuegos = false))
        assertTrue(Destacados.JUEGO in Logica.huecosFavoritos(sinJuegos, conJuegos = true))
        // Si ya tiene uno puesto, se ve aunque haya quitado la pestaña.
        val puesto = sinJuegos.copy(perfil = Perfil(destacados = mapOf(Destacados.JUEGO to "j1")))
        assertTrue(Destacados.JUEGO in Logica.huecosFavoritos(puesto, conJuegos = false))
        assertEquals(Destacados.FAVORITOS - Destacados.JUEGO, Logica.huecosFavoritos(sinJuegos, conJuegos = false))
    }

    @Test fun unJuegoFavoritoYDestacadoSubeAlPerfilConSuTipo() {
        val e = Estado(obras = listOf(juego), ajustes = Ajustes(favoritas = listOf("j1")),
            perfil = Perfil(destacados = mapOf(Destacados.JUEGO to "j1"),
                visibilidad = mapOf(PartesPerfil.FAVORITAS to Visibilidad.PUBLICA, PartesPerfil.DESTACADOS to Visibilidad.PUBLICA)))
        val p = RedSocial.perfilDe(e)
        val favorita = (p.partes.getValue(PartesPerfil.FAVORITAS).datos as JsonArray).single() as JsonObject
        assertEquals(Tipos.JUEGO, (favorita["tipoObra"] as JsonPrimitive).content)
        assertEquals("wd:Q42887370", (favorita["fuente"] as JsonPrimitive).content)
        val destacado = (p.partes.getValue(PartesPerfil.DESTACADOS).datos as JsonObject)[Destacados.JUEGO] as JsonObject
        assertEquals("Celeste", (destacado["titulo"] as JsonPrimitive).content)
        assertEquals(subida, (destacado["portada"] as JsonPrimitive).content)
    }

    @Test fun unPerfilConUnHuecoDeJuegoSeLeeConElFormatoDeSiempre() {
        // Así lo lee cualquier versión: las partes son JSON libre y el hueco, una clave más (sin enums).
        val texto = """{"persona":{"usuario":"ana"},"partes":{"destacados":{"juego":{"titulo":"Celeste","tipoObra":"juego"},
            "pelicula":{"titulo":"Matrix","tipoObra":"pelicula"}},"algoNuevo":{"x":1}},"campoNuevo":true}"""
        val p = JsonSocial.decodeFromString(PerfilSocial.serializer(), texto)
        assertEquals(setOf("juego", "pelicula"), (p.partes["destacados"] as JsonObject).keys)
        // Y el cajón propio con el hueco de juego se guarda y se lee igual.
        val e = Estado(perfil = Perfil(destacados = mapOf(Destacados.JUEGO to "j1")))
        assertEquals(e, JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), e)))
    }

    /* ----- C. Reseñas y publicaciones de un juego ----- */

    @Test fun laResenaYElDiarioDeUnJuegoLlevanSuTipoYSuFuente() {
        val conDiario = juego.copy(registros = listOf(Registro("r1", "2026-05-02", 8, visibilidad = Visibilidad.PUBLICA)))
        val e = Estado(obras = listOf(conDiario), resenas = listOf(Resena("s1", "j1", texto = "Precioso", nota = 9,
            visibilidad = Visibilidad.PUBLICA, fecha = "2026-05-03")))
        val subidas = RedSocial.publicacionesDe(e)
        val resena = subidas.first { it.tipo == "resena" }.datos
        assertEquals(Tipos.JUEGO, (resena["tipoObra"] as JsonPrimitive).content)
        assertEquals("wd:Q42887370", (resena["fuente"] as JsonPrimitive).content)
        assertEquals(subida, (resena["portada"] as JsonPrimitive).content)
        val diario = subidas.first { it.tipo == "diario" }.datos
        assertEquals(Tipos.JUEGO, (diario["tipoObra"] as JsonPrimitive).content)
    }

    @Test fun unaPortadaDeCommonsGuardadaComoFilePathViajaComoUpload() {
        // Viaja el original, que siempre existe aunque la imagen mida menos que el ancho pedido.
        val viejo = juego.copy(portada = "https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500")
        val original = "https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png"
        assertEquals(original, (RedSocial.obraBreve(viejo)["portada"] as JsonPrimitive).content)
        // Una portada del teléfono no viaja.
        assertEquals("", (RedSocial.obraBreve(juego.copy(portada = "file:///x.jpg"))["portada"] as JsonPrimitive).content)
        assertEquals(original, (obraJson(viejo)["portada"] as JsonPrimitive).content)
    }

    /* ----- E. Portadas de la comunidad ----- */

    @Test fun deCommonsPasaFilePathAUpload() {
        // Con o sin ancho, el original: una miniatura más grande que una imagen chica no existe y quedaría rota en la red.
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/2/25/Hollow_Knight_(captura).png",
            Imagenes.deCommons("https://commons.wikimedia.org/wiki/Special:FilePath/Hollow%20Knight%20%28captura%29.png?width=500"))
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png",
            Imagenes.paraRed("https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500"))
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/2/25/Hollow_Knight_(captura).png",
            Imagenes.deCommons("https://commons.wikimedia.org/wiki/Special:FilePath/Hollow%20Knight%20%28captura%29.png"))
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/b/bc/%C3%91and%C3%BA_x.jpg",
            Imagenes.deCommons("https://commons.wikimedia.org/wiki/Special:FilePath/%C3%91and%C3%BA%20x.jpg"))
        // Las demás quedan igual.
        listOf(subida, "https://image.tmdb.org/t/p/w342/x.jpg", "file:///x.jpg", "").forEach { assertEquals(it, Imagenes.deCommons(it)) }
        assertEquals("", Imagenes.paraRed("file:///x.jpg"))
        // Para comparar y votar, la FilePath y su upload son la misma imagen.
        assertEquals(Imagenes.clave("https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png"),
            Imagenes.clave("https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500"))
    }

    @Test fun unSvgDeCommonsTieneSuMiniaturaEnPngYUnTiffSuOriginal() {
        val svg = Imagenes.deCommons("https://commons.wikimedia.org/wiki/Special:FilePath/Logo.svg?width=500")
        assertTrue(svg.startsWith("https://upload.wikimedia.org/wikipedia/commons/thumb/") && svg.endsWith("/Logo.svg/500px-Logo.svg.png"))
        val tif = Imagenes.deCommons("https://commons.wikimedia.org/wiki/Special:FilePath/Caja.tif?width=500")
        assertTrue(tif.startsWith("https://upload.wikimedia.org/wikipedia/commons/") && "/thumb/" !in tif && tif.endsWith("/Caja.tif"))
    }

    @Test fun unaMismaImagenDeCommonsTieneUnaSolaClave() {
        val original = "https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png"
        // La miniatura que da la API, la de otro ancho, el original y la FilePath con o sin ancho: la misma clave.
        listOf(subida, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Celeste.png/250px-Celeste.png", original,
            "https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500",
            "https://commons.wikimedia.org/wiki/Special:FilePath/celeste.png").forEach { assertEquals(it, original, Imagenes.clave(it)) }
        // Así el voto de una y el de otra cuentan juntos.
        val votos = PortadasComunidad(votos = mapOf(original to 4))
        assertEquals(4, votos.votos.filterKeys { Imagenes.clave(it) == Imagenes.clave(subida) }.values.sum())
        // Las demás fuentes no cambian.
        assertEquals("https://image.tmdb.org/t/p/original/x.jpg", Imagenes.clave("https://image.tmdb.org/t/p/w342/x.jpg"))
    }

    @Test fun elNombreDeUnArchivoDeCommonsComoLoGuardaMediaWiki() {
        assertEquals("Hollow_knight_x.png", Imagenes.nombreCommons("  hollow  knight__x.png "))
        assertEquals("Celeste.png", Imagenes.nombreCommons("Celeste.png"))
        // Solo una minúscula ASCII sube: lo demás ya viene canónico de Wikidata (ß no pasa a SS).
        assertEquals("ßeta.png", Imagenes.nombreCommons("ßeta.png"))
        assertEquals("éclair.png", Imagenes.nombreCommons("éclair.png"))
    }

    @Test fun lasPortadasViejasDeCommonsSePuedenReportar() {
        assertFalse(Imagenes.autorizada("https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500"))
        assertTrue(Imagenes.autorizada(Imagenes.clave("https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500")))
        assertFalse(Imagenes.autorizada(Imagenes.clave("file:///x.jpg")))
    }

    @Test fun elCreditoConocidoSoloSiTraeAutorYLicencia() {
        val conCredito = ImagenElegida("wikimedia", subida, "Ana", "CC BY 4.0")
        assertEquals(conCredito, Imagenes.creditoConocido(subida, conCredito))
        assertNull(Imagenes.creditoConocido(subida, conCredito.copy(licencia = null)))
        assertNull(Imagenes.creditoConocido(subida, conCredito.copy(autor = "Unknown author")))
        assertNull(Imagenes.creditoConocido(subida, null))
        // Otra imagen no sirve; la misma por otra dirección sí, con la dirección pedida.
        assertNull(Imagenes.creditoConocido(subida, conCredito.copy(url = "https://upload.wikimedia.org/wikipedia/commons/1/12/Otra.png")))
        val vieja = "https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500"
        assertEquals(conCredito.copy(url = vieja), Imagenes.creditoConocido(vieja, conCredito))
        // La de la comunidad sirve aunque la guardada en la obra (tu portada) sea otra.
        val propia = ImagenElegida("local", "file:///x.jpg")
        assertEquals(conCredito, Imagenes.creditoConocido(subida, propia, conCredito))
    }

    @Test fun elArchivoDeUnaImagenDeCommons() {
        assertEquals("Celeste.png", Imagenes.archivoCommons(subida))
        assertEquals("Logo.svg", Imagenes.archivoCommons("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Logo.svg/500px-Logo.svg.png"))
        assertEquals("Celeste.png", Imagenes.archivoCommons("https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png"))
        assertEquals("Hollow%20Knight.png", Imagenes.archivoCommons("https://commons.wikimedia.org/wiki/Special:FilePath/Hollow%20Knight.png?width=500"))
        assertEquals("https://commons.wikimedia.org/wiki/File:Logo.svg",
            Imagenes.enlace(ImagenElegida("wikimedia", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Logo.svg/500px-Logo.svg.png"), "wd:Q1"))
    }

    @Test fun commonsDaLaMiniaturaDeUpload() {
        val info = buildJsonObject {
            put("thumburl", subida); put("url", "https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png")
        }
        assertEquals(subida, Wikimedia.urlSubida(info))
        // Imagen más chica que el ancho: Commons solo da el original.
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png",
            Wikimedia.urlSubida(buildJsonObject { put("url", "https://upload.wikimedia.org/wikipedia/commons/4/49/Celeste.png") }))
        assertNull(Wikimedia.urlSubida(buildJsonObject { put("thumburl", "https://otro.example/x.png") }))
    }

    @Test fun deUnJuegoSoloSeVotaLaImagenConCredito() {
        val credito = ImagenElegida("wikimedia", subida, "Ana", "CC BY 4.0")
        assertTrue(Imagenes.votable("wd:Q1", subida, credito))
        assertFalse(Imagenes.votable("wd:Q1", subida, null))
        assertFalse(Imagenes.votable("wd:Q1", subida, credito.copy(autor = null)))
        assertFalse(Imagenes.votable("wd:Q1", subida, credito.copy(licencia = "")))
        assertFalse(Imagenes.votable("wd:Q1", subida, credito.copy(url = "https://upload.wikimedia.org/wikipedia/commons/1/12/Otra.png")))
        // Una guardada como FilePath se vota como su upload, con su crédito.
        val vieja = "https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500"
        assertTrue(Imagenes.votable("wd:Q1", vieja, credito.copy(url = vieja)))
        // Nada de fuera de los dominios de siempre; las demás obras, como antes.
        assertFalse(Imagenes.votable("wd:Q1", "file:///x.jpg", credito.copy(url = "file:///x.jpg")))
        assertTrue(Imagenes.votable("tmdb:movie:603", "https://image.tmdb.org/t/p/w342/x.jpg", null))
    }

    @Test fun laImagenDeLaComunidadDeUnJuegoSinCreditoNoSeUsa() {
        val llegada = PortadasComunidad(comunidad = subida, votos = mapOf(subida to 5))
        assertNull(Imagenes.comunidadUsable("wd:Q1", llegada, null).comunidad)
        assertNull(Imagenes.comunidadUsable("wd:Q1", llegada, ImagenElegida("wikimedia", subida)).comunidad)
        assertEquals(llegada, Imagenes.comunidadUsable("wd:Q1", llegada, ImagenElegida("wikimedia", subida, "Ana", "CC BY 4.0")))
        // Los votos se conservan aunque la imagen no se use; las demás obras no cambian.
        assertEquals(llegada.votos, Imagenes.comunidadUsable("wd:Q1", llegada, null).votos)
        val tmdb = PortadasComunidad(comunidad = "https://image.tmdb.org/t/p/w780/x.jpg")
        assertEquals(tmdb, Imagenes.comunidadUsable("tmdb:movie:603", tmdb, null))
        // Sin red llega lo que había (o nada): con eso queda el respaldo, sin avisos.
        assertEquals(PortadasComunidad(), Imagenes.comunidadUsable("wd:Q1", PortadasComunidad(), null))
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Conserva las regresiones de Iniciales y comprueba el catálogo que lo sustituye. */
class InicialesTest {
    @get:Rule val temporal = TemporaryFolder()
    private val listas by lazy { Catalogo.leer(File("src/main/assets/datos/catalogos.json").readText()) }
    private fun obra(n: Int, fuente: String = "tmdb:movie:$n") = ObraInicial("o$n", Tipos.PELICULA, "Obra $n", fuente = fuente)
    private fun incluidas() = ListasCatalogo(iconicas = mapOf(Tipos.PELICULA to (1..65).map { obra(it) }))

    @Test fun lecturaDeAmbasSeccionesYSeisTipos() {
        val tipos = setOf("pelicula", "serie", "anime", "libro", "album", "artista")
        assertEquals(tipos, listas.iconicas.keys)
        assertEquals(tipos, listas.premiadas.keys)
        listas.iconicas.forEach { (tipo, obras) -> assertTrue(tipo, obras.size >= 50) }
        val todas = listas.todas().associateBy { it.id }
        assertEquals(listas.todas().size, todas.size)
        for (seccion in listOf(listas.iconicas, listas.premiadas)) seccion.forEach { (tipo, obras) ->
            assertEquals(tipo, obras.size, obras.distinctBy { it.fuente }.size)
            obras.forEach { o ->
                assertEquals(tipo, o.tipo)
                assertTrue(o.titulo.isNotBlank())
                assertTrue(o.tituloEn.isNotBlank())
                assertTrue(o.fuente.matches(Regex("tmdb:(movie|tv):[0-9]+|ol:OL[0-9]+W|mb:(artist|rg):[0-9a-f-]{36}")))
                assertTrue(o.id, o.parecidos.all { it in todas && it != o.id })
            }
        }
        listas.premiadas.values.flatten().forEach { assertTrue(it.premio.isNotBlank()) }
        val premios = listas.premiadas.values.flatten().map { it.premio }
        listOf("Óscar", "Palma de Oro", "León de Oro", "Emmy a mejor serie dramática", "Emmy a mejor serie de comedia",
            "Annecy", "Booker", "Pulitzer", "Premio Cervantes", "Hugo", "Grammy").forEach { nombre ->
            assertTrue(nombre, premios.any { it.startsWith(nombre) })
        }
        (listas.premiadas.getValue("artista") + listas.premiadas.getValue("album")).forEach {
            assertTrue(it.premio, it.premio.startsWith("Grammy"))
        }
    }

    @Test fun titulosSiguenElIdiomaSinCambiarFuenteNiId() {
        val o = listas.todas().first { it.id == "eterno_resplandor" }
        val ingles = Catalogo.enIdioma(o, "en")
        assertEquals("Eternal Sunshine of the Spotless Mind", ingles.titulo)
        assertEquals(o.fuente, ingles.fuente)
        assertEquals(o.id, ingles.id)
        assertEquals(o, Catalogo.enIdioma(o, "es"))
    }

    @Test fun paginasLocalesDeVeinteSinRepetidosNiSaltos() = runTest {
        val catalogo = Catalogo(incluidas(), temporal.newFolder()) { _, _, _, _ -> error("No necesita red") }
        val paginas = (1..4).map { catalogo.pagina(SeccionCatalogo.ICONICAS, Tipos.PELICULA, "es", it) }
        assertEquals(listOf(20, 20, 20, 5), paginas.map { it.obras.size })
        assertEquals((1..65).map { "tmdb:movie:$it" }, paginas.flatMap { it.obras }.map { it.fuente })
        assertFalse(paginas[2].fin)
        assertTrue(paginas[3].fin)
    }

    @Test fun excluyeElegidasObrasYArtistasDelCajonPorFuente() = runTest {
        val e = Estado(obras = listOf(Obra("guardada", Tipos.PELICULA, "Otro nombre", fuente = "tmdb:movie:1")),
            artistas = listOf(Artista("a", "Artista", fuente = "mb:artist:uno")),
            gustos = listOf(Gusto("obra:tmdb:movie:2", "Elegida", Senales.GUSTA, fuente = "tmdb:movie:2")))
        val fuera = Catalogo.excluidas(e)
        assertTrue("mb:artist:uno" in fuera)
        val catalogo = Catalogo(incluidas(), temporal.newFolder()) { _, _, _, _ -> error("No necesita red") }
        val pagina = catalogo.pagina(SeccionCatalogo.ICONICAS, Tipos.PELICULA, "es", 1, fuera)
        assertEquals(18, pagina.obras.size)
        assertTrue(pagina.obras.none { it.fuente in fuera })
        assertEquals(listOf(obra(3)), Catalogo.sinRepetir(listOf(obra(1), obra(3), obra(4, "tmdb:movie:3")), fuera))
    }

    @Test fun paginasDeFuenteSolapadasNoRepitenNiOcultanLasSiguientes() = runTest {
        val c = Catalogo(incluidas(), temporal.newFolder()) { _, _, _, pagina ->
            val inicio = 1 + (pagina - 1) * 19
            PaginaCatalogo((inicio until inicio + 20).map { obra(it) }, pagina == 3)
        }
        val mostradas = mutableListOf<ObraInicial>()
        for (pagina in 1..3) {
            mostradas += c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", pagina, mostradas.map { it.fuente }.toSet()).obras
        }
        assertEquals((1..58).map { "tmdb:movie:$it" }, mostradas.map { it.fuente })
    }

    @Test fun unaSemillaSinResultadosNoAgotaLasDemas() {
        val finales = FinalesCatalogo()
        val semillas = listOf("a", "b")
        assertFalse(finales.registrar(semillas, PaginaCatalogo(emptyList(), semilla = "a", finSemilla = true)))
        assertFalse(finales.registrar(semillas, PaginaCatalogo(listOf(obra(1)), semilla = "b")))
        assertTrue(finales.registrar(semillas, PaginaCatalogo(emptyList(), semilla = "b", finSemilla = true)))
        assertFalse(finales.registrar(semillas + "c", PaginaCatalogo(emptyList(), semilla = "b", finSemilla = true)))
    }

    @Test fun cacheVigenteVencidaYSeparadaPorIdiomaPaginaTipoSeccionYPerfil() = runTest {
        var ahora = 1000L
        var llamadas = 0
        val dir = temporal.newFolder()
        fun catalogo(carpeta: File = dir) = Catalogo(incluidas(), carpeta, reloj = { ahora }) { _, _, _, n ->
            llamadas++; PaginaCatalogo(listOf(obra(n)), true)
        }
        var c = catalogo()
        c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 1)
        ahora += Catalogo.DIA - 1
        c = catalogo() // También se conserva al volver a abrir el perfil.
        assertEquals(0, c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 1, setOf("tmdb:movie:1")).obras.size)
        assertEquals(1, llamadas)
        ahora++
        c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 1)
        assertEquals(2, llamadas)
        c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "en", 1)
        c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 2)
        c.pagina(SeccionCatalogo.TENDENCIAS, "serie", "es", 1)
        c.pagina(SeccionCatalogo.QUERIDAS, "pelicula", "es", 1)
        catalogo(temporal.newFolder()).pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 1)
        assertEquals(7, llamadas)
    }

    @Test fun paraTiCambiaLaCacheCuandoCambianLasSemillas() = runTest {
        var version = "uno"
        var llamadas = 0
        val c = Catalogo(incluidas(), temporal.newFolder(), version = { version }) { _, _, _, _ ->
            llamadas++; PaginaCatalogo(listOf(obra(llamadas)), true)
        }
        c.pagina(SeccionCatalogo.PARA_TI, "pelicula", "es", 1)
        c.pagina(SeccionCatalogo.PARA_TI, "pelicula", "es", 1)
        assertEquals(1, llamadas)
        version = "dos"
        assertEquals("o2", c.pagina(SeccionCatalogo.PARA_TI, "pelicula", "es", 1).obras.single().id)
    }

    @Test fun cacheCorruptaSeRenueva() = runTest {
        val dir = temporal.newFolder()
        File(dir, "tendencias-pelicula-es-1.json").writeText("roto")
        val c = Catalogo(incluidas(), dir) { _, _, _, _ -> PaginaCatalogo(listOf(obra(99)), true) }
        assertEquals("o99", c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 1).obras.single().id)
    }

    @Test fun sinInternetVuelveAIconicasYNoUsaCacheVencida() = runTest {
        var ahora = 1000L
        var conectado = true
        val c = Catalogo(incluidas(), temporal.newFolder(), reloj = { ahora }) { _, _, _, _ ->
            if (!conectado) throw SinRed()
            PaginaCatalogo(listOf(obra(99)), true)
        }
        c.pagina(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 2)
        conectado = false
        ahora += Catalogo.DIA
        val (pagina, respaldo) = c.conRespaldo(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 2)
        assertTrue(respaldo)
        assertEquals("o1", pagina.obras.first().id)
        assertEquals(20, pagina.obras.size)
    }

    @Test fun cancelarNoSeConfundeConFaltaDeInternet() = runTest {
        val c = Catalogo(incluidas(), temporal.newFolder()) { _, _, _, _ -> throw CancellationException() }
        try { c.conRespaldo(SeccionCatalogo.TENDENCIAS, "pelicula", "es", 1); fail("Debe cancelar") }
        catch (_: CancellationException) { }
    }

    @Test fun parecidasIncluidasYDeFuenteEntranAlLadoSinRepetirElegidas() {
        val elegida = obra(1)
        val fila = listOf(elegida, obra(2))
        val nueva = Catalogo.insertarParecidas(fila, elegida, listOf(obra(2), obra(3), obra(4), obra(3)), setOf("tmdb:movie:4"))
        assertEquals(listOf(1, 3, 2).map { "o$it" }, nueva.map { it.id })
        assertEquals(nueva, Catalogo.insertarParecidas(nueva, elegida, listOf(obra(3)), emptySet()))
        val incluidas = listas.todas().associateBy { it.id }
        val o = incluidas.getValue("la_la_land")
        assertEquals("eterno_resplandor", o.parecidos.first())
    }

    @Test fun artistasNuevosYAntiguosSePuedenQuitarYSiguenAlimentandoRecomendaciones() {
        val artista = ObraInicial("a", "artista", "Artista", fuente = "mb:artist:uno")
        val anterior = Estado(gustos = listOf(Gusto("artista:artista", "Artista", Senales.GUSTA, "artista", fuente = artista.fuente)))
        val nuevo = Catalogo.elegir(anterior, artista, true, "", 123)
        assertEquals(1, nuevo.gustosVivos.size)
        assertEquals("obra:mb:artist:uno", nuevo.gustosVivos.single().clave)
        assertEquals(1, Recomendar.perfil(nuevo, null).artistasDichos.size)
        assertTrue(Catalogo.elegir(nuevo, artista, false, "", 124).gustosVivos.isEmpty())
        assertTrue(Catalogo.elegir(anterior, artista, false, "", 124).gustosVivos.isEmpty())
    }

    @Test fun minimoTresSinTopeIncluyeEleccionesEnLineaYAlbumes() {
        assertFalse(Catalogo.minimoCumplido(2))
        assertTrue(Catalogo.minimoCumplido(3))
        val elegido = (1..120).fold(Estado()) { e, n ->
            Catalogo.elegir(e, ObraInicial("album$n", Tipos.ALBUM, "Álbum $n", fuente = "mb:rg:$n"), true, "", n.toLong())
        }
        assertEquals(120, Catalogo.cantidadElegida(elegido))
        assertTrue(Catalogo.minimoCumplido(120))
    }
}

package io.github.fogonapps.social

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ImagenesTest {
    private fun nuevo(): Social {
        val dir = Files.createTempDirectory("imagenes").toFile()
        return Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
    }
    private val obra = "tmdb:movie:157336"
    private val a = "https://image.tmdb.org/t/p/original/a.jpg"
    private val b = "https://image.tmdb.org/t/p/original/b.jpg"

    @Test fun laComunidadEligeConCincoVotosYMasDeLaMitad() {
        val s = nuevo()
        val cuentas = (1..7).map { s.cuentaDe(s.crearCuenta(NuevaCuenta("cuenta$it")).token)!! }
        cuentas.take(4).forEach { s.votarImagen(it, VotoImagen(obra, a)) }
        assertNull(s.imagenObra(null, obra).comunidad)           // 4 votos: aún no
        s.votarImagen(cuentas[4], VotoImagen(obra, a))
        assertEquals(a, s.imagenObra(null, obra).comunidad)
        cuentas.drop(5).forEach { s.votarImagen(it, VotoImagen(obra, b)) }
        assertEquals(a, s.imagenObra(cuentas[0], obra).comunidad)  // 5 de 7
        assertEquals(a, s.imagenObra(cuentas[0], obra).miVoto)
        s.votarImagen(cuentas[0], VotoImagen(obra, b))           // cambiar el voto: 4 contra 3
        assertNull(s.imagenObra(null, obra).comunidad)
    }

    @Test fun soloSeAceptanDominiosAutorizadosYLoBloqueadoNoCuenta() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        assertEquals("url", assertFailsWith<Problema> { s.votarImagen(ana, VotoImagen(obra, "https://ejemplo.com/a.jpg")) }.mensaje)
        assertEquals("url", assertFailsWith<Problema> { s.votarImagen(ana, VotoImagen(obra, "http://image.tmdb.org/a.jpg")) }.mensaje)
        assertEquals("url", assertFailsWith<Problema> { s.reportar(ana, NuevoReporte("imagen", "https://ejemplo.com/a.jpg", "derechos")) }.mensaje)
        s.votarImagen(ana, VotoImagen(obra, a))
        s.reportar(ana, NuevoReporte("imagen", a, "ofensiva"))
    }
}

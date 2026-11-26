package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class TiendaNuevaTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("tienda").toFile()
        val b = Base(File(dir, "social.db").path)
        var reloj = 2_000_000_000_000L
        val s = Social(b, File(dir, "fotos"), { reloj })
        /** Con 1.000 de prueba además de la bienvenida (las cuentas de estas pruebas arrancan con 1.500). */
        fun alta(nombre: String) = s.cuentaDe(s.crearCuenta(NuevaCuenta(nombre)).token)!!.also { c ->
            s.chispas(c); b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,'prueba',1)", c, 1500 - TemasPerfil.BIENVENIDA) }
        }
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun bannersYCompatibilidad() = Entorno().use { e ->
        val yo = e.alta("anabel")
        fun portada(valor: String) = e.s.subirPerfil(yo, PerfilSubido(portada = valor))
        portada("banner:papel")
        assertEquals("banner:papel", e.s.verPerfil(yo, "anabel").portada)
        // Lo que no es un banner permitido se borra en silencio (las apps viejas mandan URLs).
        for (valor in listOf("banner:inventado", "banner:neon", "https://image.tmdb.org/a.jpg")) {
            portada(valor); assertEquals("", e.s.verPerfil(yo, "anabel").portada)
        }
        assertEquals("banner_no_permitido", assertFailsWith<Problema> { e.s.ponerTema(yo, "banner:neon") }.mensaje)
        e.s.subirPerfil(yo, PerfilSubido(portada = "banner:papel", partes = mapOf("prueba" to Parte(Vis.PUBLICA, kotlinx.serialization.json.JsonPrimitive("conservar")))))
        e.s.ponerTema(yo, "banner:puntos")
        assertTrue("prueba" in e.s.verPerfil(yo, "anabel").partes)
        e.s.reclamarTema(yo, "banner:neon")
        portada("banner:neon")
        portada("")
        // Las portadas viejas (URLs) se quitan todas al arrancar; los banners del catálogo se quedan.
        portada("banner:pasto")
        val otra = e.alta("beto")
        e.b.usar { cambiar("UPDATE cuentas SET perfil = ? WHERE id = ?", JsonSocial.encodeToString(PerfilSubido.serializer(), PerfilSubido(portada = "https://anterior.test/a.jpg")), otra) }
        assertEquals(1, e.s.quitarPortadasAjenas())
        assertEquals("", e.s.verPerfil(otra, "beto").portada)
        assertEquals("banner:pasto", e.s.verPerfil(yo, "anabel").portada)
        assertEquals(0, e.s.quitarPortadasAjenas())
        val saldo = e.s.chispas(yo)
        repeat(3) { e.s.cobrarDiario(yo); e.reloj += 86400000L }
        assertEquals(saldo, e.s.chispas(yo))
        assertFalse(e.s.temas(yo).diario)
    }

    @Test fun portadaPropiaCompraAutorAccesoYLimpieza(): Unit = Entorno().use { e ->
        val yo = e.alta("anabel"); val otra = e.alta("beatriz")
        fun foto(cuenta: Long): String {
            val imagen = java.awt.image.BufferedImage(34, 10, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val bytes = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(imagen, "jpg", it) }.toByteArray()
            return e.s.subirImagen(cuenta, bytes).nombre
        }
        val sinCompra = foto(yo)
        e.s.subirPerfil(yo, PerfilSubido(portada = sinCompra))
        assertEquals("", e.s.verPerfil(yo, "anabel").portada)
        assertFailsWith<Problema> { e.s.imagen(sinCompra, yo) }
        e.b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,10000,'prueba',?)", yo, e.reloj) }
        val antes = e.s.chispas(yo)
        e.s.reclamarTema(yo, "portada:propia")
        assertEquals(antes - TemasPerfil.precio("portada:propia")!!, e.s.chispas(yo))
        val propia = foto(yo)
        e.s.subirPerfil(yo, PerfilSubido(portada = propia))
        assertEquals(propia, e.s.verPerfil(otra, "anabel").portada)
        assertTrue(e.s.imagen(propia, otra).isNotEmpty())
        assertEquals(0, e.s.quitarPortadasAjenas())
        e.s.ajustar(yo, Ajustes(true))
        assertEquals("", e.s.verPerfil(otra, "anabel").portada)
        assertFailsWith<Problema> { e.s.imagen(propia, otra) }
        assertTrue(e.s.imagen(propia, yo).isNotEmpty())
        e.s.ajustar(yo, Ajustes(false))
        e.s.bloquear(yo, "beatriz", true)
        assertFailsWith<Problema> { e.s.imagen(propia, otra) }
        val ajena = foto(otra)
        e.s.subirPerfil(yo, PerfilSubido(portada = ajena))
        assertEquals("", e.s.verPerfil(yo, "anabel").portada)
        assertTrue(e.s.imagen(ajena, otra).isNotEmpty())
        assertFailsWith<Problema> { e.s.imagen(propia, yo) }
    }

    @Test fun equiparRechazaYLimpiaYConservaFotosEnUso(): Unit = Entorno().use { e ->
        val yo = e.alta("anabel")
        fun foto(): String {
            val imagen = java.awt.image.BufferedImage(34, 10, java.awt.image.BufferedImage.TYPE_INT_RGB)
            return e.s.subirImagen(yo, java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(imagen, "jpg", it) }.toByteArray()).nombre
        }
        val rechazada = foto()
        assertFailsWith<Problema> { e.s.ponerTema(yo, rechazada) }
        assertEquals(0, e.b.usar { leer("SELECT COUNT(*) FROM imagenes WHERE nombre = ?", rechazada) { it.getInt(1) }.single() })
        e.b.usar { cambiar("INSERT INTO temas(cuenta,tema,cuando) VALUES(?,'portada:propia',?)", yo, e.reloj) }
        val compartida = foto()
        e.s.escaparates.guardar(yo, listOf(Escaparate("galeria", kotlinx.serialization.json.buildJsonObject {
            put("fotos", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.buildJsonObject { put("nombre", kotlinx.serialization.json.JsonPrimitive(compartida)) }) })
        })))
        e.s.ponerTema(yo, compartida)
        e.s.ponerTema(yo, "banner:papel")
        assertTrue(e.s.imagen(compartida, yo).isNotEmpty())
        e.s.ponerTema(yo, compartida)
        e.s.escaparates.guardar(yo, emptyList())
        assertTrue(e.s.imagen(compartida, yo).isNotEmpty())
        e.s.ponerTema(yo, "banner:puntos")
        assertFailsWith<Problema> { e.s.imagen(compartida, yo) }
    }

    @Test fun regaloAtomicoConLimitesYBloqueos() = Entorno().use { e ->
        val yo = e.alta("anabel"); val otra = e.alta("beatriz")
        e.s.regalar(yo, Regalo("marco:doble", "@beatriz"))
        assertEquals(1100, e.s.chispas(yo))
        assertTrue("marco:doble" in e.s.temas(otra).reclamados)
        assertEquals("regalo", e.s.avisos(otra, null).single().tipo)
        assertEquals("ya_lo_tiene", assertFailsWith<Problema> { e.s.regalar(yo, Regalo("marco:doble", "beatriz")) }.mensaje)
        assertFailsWith<Problema> { e.s.regalar(yo, Regalo("nieve", "anabel")) }
        assertEquals(402, assertFailsWith<Problema> { e.s.regalar(yo, Regalo("ciudad", "beatriz")) }.codigo)
        assertEquals(1100, e.s.chispas(yo))
        e.s.bloquear(yo, "beatriz", true)
        assertEquals(403, assertFailsWith<Problema> { e.s.regalar(yo, Regalo("marco:pixel", "beatriz")) }.codigo)
        e.s.bloquear(yo, "beatriz", false); e.s.bloquear(otra, "anabel", true)
        assertEquals(403, assertFailsWith<Problema> { e.s.regalar(yo, Regalo("marco:pixel", "beatriz")) }.codigo)
        e.s.bloquear(otra, "anabel", false)
        e.b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,50000,'prueba',?)", yo, e.reloj) }
        repeat(9) { i -> e.alta("persona$i"); e.s.regalar(yo, Regalo("marco:doble", "persona$i")) }
        assertEquals("tope_regalos", assertFailsWith<Problema> { e.s.regalar(yo, Regalo("marco:pixel", "beatriz")) }.mensaje)
        e.reloj += 86400000L
        e.s.regalar(yo, Regalo("marco:pixel", "beatriz"))
    }
    @Test fun resenasUtilesTopeYUnPremioPorResena() = Entorno().use { e ->
        val autor = e.alta("autora"); val votante = e.alta("lectora"); val otra = e.alta("otra")
        val publicaciones = (1..7).map { i -> PublicacionSubida("r$i", Vis.PUBLICA, i.toLong(), "resena", kotlinx.serialization.json.buildJsonObject {
            put("fuente", kotlinx.serialization.json.JsonPrimitive("tmdb:tv:$i"))
            put("texto", kotlinx.serialization.json.JsonPrimitive("reseña $i"))
        }) }
        e.s.subirPublicaciones(autor, publicaciones)
        val ids = e.b.usar { leer("SELECT id FROM publicaciones WHERE cuenta = ? ORDER BY id", autor) { it.getLong(1) } }
        ids.take(6).forEach { e.s.util(votante, it, true) }
        assertEquals(1600, e.s.chispas(autor))
        e.s.util(votante, ids.first(), false)
        e.reloj += 86400000L
        e.s.util(votante, ids.first(), true); e.s.util(otra, ids.first(), true)
        assertEquals(1600, e.s.chispas(autor))
        e.s.util(otra, ids.last(), true)
        assertEquals(1620, e.s.chispas(autor))
    }

}

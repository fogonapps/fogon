package io.github.fogonapps.social

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ContadoresTest {
    private class Telefono {
        var hoy: LocalDate = LocalDate.of(2026, 11, 1)
        @Volatile var disco = ""
        fun abrir(app: String = "cajon") = Contadores(app, { "3.0" }, { disco }, { disco = it; true }, { hoy })
    }

    @Test
    fun acumulaPorDiaYConservaElDiaEnCursoAlConfirmar() {
        val telefono = Telefono()
        val c = telefono.abrir()
        c.contar("tienda.abrir")
        c.contar("tienda.abrir")
        c.contar("dato.inventado")
        telefono.hoy = telefono.hoy.plusDays(1)
        c.contar("tienda.abrir")
        val lote = requireNotNull(c.preparar())
        assertEquals(listOf(DiaContadores("2026-11-01", mapOf("tienda.abrir" to 2))), lote.dias)
        c.confirmar(lote.lote)
        assertNull(c.preparar())
        telefono.hoy = telefono.hoy.plusDays(1)
        assertEquals(1, c.preparar()?.dias?.single()?.contadores?.get("tienda.abrir"))
    }

    @Test
    fun unLoteRechazadoNoSeReintenta() {
        val telefono = Telefono()
        val c = telefono.abrir()
        c.contar("tienda.abrir")
        telefono.hoy = telefono.hoy.plusDays(1)
        val lote = requireNotNull(c.preparar())
        c.confirmar(lote.lote)
        telefono.hoy = telefono.hoy.plusDays(1)
        assertNull(c.preparar())
    }

    @Test
    fun noBorraYDetieneElConteoYElEnvio() {
        val telefono = Telefono()
        val c = telefono.abrir()
        c.contar("tienda.abrir")
        c.permitir(false)
        c.contar("app.arranque")
        telefono.hoy = telefono.hoy.plusDays(1)
        assertNull(c.preparar())
        val guardado = Json.decodeFromString<EstadoContadores>(telefono.disco)
        assertEquals(PermisoContadores.NO, guardado.permiso)
        assertTrue(guardado.dias.isEmpty())
        val reabierto = telefono.abrir()
        assertEquals(PermisoContadores.NO, reabierto.permiso.value)
        reabierto.permitir(true)
        assertNull(reabierto.preparar())
    }

    @Test
    fun caducanSoloLosDiasConMasDeTreintaDias() {
        val telefono = Telefono()
        val c = telefono.abrir()
        c.contar("app.arranque")
        telefono.hoy = telefono.hoy.plusDays(1)
        c.contar("app.arranque")
        telefono.hoy = telefono.hoy.plusDays(30)
        assertEquals("2026-11-02", c.preparar()?.dias?.single()?.dia)
    }

    @Test
    fun conservaLoteYConfirmacionAlReabrir() {
        val telefono = Telefono()
        val origen = telefono.abrir("alcancia")
        origen.contar("alcancia.meta_creada")
        telefono.hoy = telefono.hoy.plusDays(1)
        val lote = requireNotNull(origen.preparar())
        assertEquals("alcancia", lote.app)
        assertNull(telefono.abrir("alcancia").preparar())
        telefono.hoy = telefono.hoy.plusDays(1)
        val reabierto = telefono.abrir("alcancia")
        assertEquals(lote, reabierto.preparar())
        reabierto.confirmar(lote.lote)
        telefono.hoy = telefono.hoy.plusDays(1)
        assertNull(telefono.abrir("alcancia").preparar())
    }

    @Test
    fun conservaLaEleccionAnteriorYLasSumasNoContestadas() {
        val telefono = Telefono()
        telefono.disco = """{"permiso":"NO","arranques":3}"""
        assertEquals(PermisoContadores.NO, telefono.abrir().permiso.value)
        telefono.disco = """{"permiso":"SIN_PREGUNTAR","arranques":2,"dias":{"2026-10-31":{"app.arranque":2}}}"""
        val contadores = telefono.abrir()
        assertEquals(PermisoContadores.SI, contadores.permiso.value)
        assertEquals(2, contadores.preparar()?.dias?.single()?.contadores?.get("app.arranque"))
    }

    @Test
    fun cuerpoSoloLlevaElEsquemaAnonimo() {
        val datos = LoteContadores("22670273-4ddd-4544-b457-9d902153728a", "cajon", "3.0",
            listOf(DiaContadores("2026-11-01", mapOf("tienda.abrir" to 2))))
        val solicitud = llamadaContadores(datos).request()
        assertEquals("/v1/contadores", solicitud.url.encodedPath)
        assertEquals("POST", solicitud.method)
        assertNull(solicitud.header("Authorization"))
        assertNull(solicitud.header("Cookie"))
        val cuerpo = Buffer().also { solicitud.body!!.writeTo(it) }.readUtf8()
        assertEquals(setOf("lote", "app", "version", "dias"), Json.parseToJsonElement(cuerpo).jsonObject.keys)
        assertEquals(datos, Json.decodeFromString<LoteContadores>(cuerpo))
    }
}

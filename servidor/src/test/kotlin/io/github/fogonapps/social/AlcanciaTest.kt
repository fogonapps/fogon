package io.github.fogonapps.social

import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class AlcanciaTest {
    private fun json(s: String) = JsonSocial.parseToJsonElement(s).jsonObject
    private val buenos = listOf(
        """{"version":1,"logro":"meta_cumplida","meta":"Viaje"}""",
        """{"version":1,"logro":"meta_avance","meta":"Viaje","porcentaje":40,"texto":"Vamos"}""",
        """{"version":1,"logro":"primera_anotacion"}""",
        """{"version":1,"logro":"cierres","cantidad":3}""",
    )

    @Test fun soloLogrosSinDinero() {
        buenos.forEach { validarLogro(json(it)) }
        listOf(
            """{"version":1,"logro":"meta_cumplida","meta":"Viaje","monto":500000}""",
            """{"version":1,"logro":"meta_avance","meta":"Viaje","porcentaje":101}""",
            """{"version":1,"logro":"meta_avance","meta":"Viaje","porcentaje":40.5}""",
            """{"version":1,"logro":"meta_avance","meta":"Viaje","porcentaje":"40"}""",
            """{"version":1,"logro":"meta_cumplida"}""",
            """{"version":1,"logro":"primera_anotacion","meta":"Viaje"}""",
            """{"version":1,"logro":"cierres","cantidad":0}""",
            """{"version":1,"logro":"saldo"}""",
            """{"version":2,"logro":"primera_anotacion"}""",
            """{"version":1,"logro":"meta_cumplida","meta":"${"x".repeat(61)}"}""",
        ).forEach { assertFailsWith<Problema>(it) { validarLogro(json(it)) } }
    }

    @Test fun soloDesdeLaAlcanciaYSoloLogros() {
        val logro = PublicacionSubida("logro-1", Vis.PUBLICA, 1, "logro", json(buenos[0]))
        validarLoteApp("alcancia", CambiosPublicaciones(listOf(logro)))
        assertFailsWith<Problema> { validarLoteApp("cucha", CambiosPublicaciones(listOf(logro))) }
        assertFailsWith<Problema> { validarLoteApp("alcancia", CambiosPublicaciones(listOf(logro.copy(tipo = "plato")))) }
        assertEquals("La Alcancía", AppsSocial.nombre("alcancia"))
    }

    @Test fun seGuardaYSeVeEnSuApp() {
        val dir = Files.createTempDirectory("alcancia-test").toFile()
        val b = Base(java.io.File(dir, "social.db").path)
        try {
            val s = Social(b, java.io.File(dir, "fotos"), { 2_000_000_000_000L })
            val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
            s.subirCambios(yo, CambiosPublicaciones(buenos.mapIndexed { i, d -> PublicacionSubida("logro-$i", Vis.PUBLICA, 1, "logro", json(d)) }), "alcancia")
            assertEquals(4, s.actividad(yo, null, app = "alcancia").size)
            assertTrue(s.actividad(yo, null, app = "cucha").none { it.tipo == "logro" })
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
}

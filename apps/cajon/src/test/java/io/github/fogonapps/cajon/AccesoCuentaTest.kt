package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

class AccesoCuentaTest {
    @Test fun soloCuentaNuevaCon409ContinuaAlRegistro() {
        assertTrue(esCuentaNueva(ErrorSocial(409, "cuenta_nueva")))
        assertFalse(esCuentaNueva(ErrorSocial(400, "cuenta_nueva")))
        assertFalse(esCuentaNueva(ErrorSocial(409, "codigo")))
        assertFalse(esCuentaNueva(ErrorSocial(401, "")))
    }

    @Test fun dispositivoLegibleSinMarcaDuplicadaNiControlesYHasta60Caracteres() {
        assertEquals("Samsung Galaxy S24", nombreDispositivo("Samsung", "Galaxy S24"))
        assertEquals("Samsung S24", nombreDispositivo("Samsung", "Samsung S24"))
        assertEquals("samsung S24", nombreDispositivo("Samsung", "samsung S24"))
        assertEquals("Pixel 9", nombreDispositivo("", " Pixel 9\r\n"))
        assertEquals("Marca Modelo", nombreDispositivo(" Marca\n", "Modelo\u0000"))
        assertEquals(60, nombreDispositivo("Marca", "x".repeat(80)).length)
    }

    @Test fun aparienciaMasRecienteGanaIndependienteDeLosAjustes() {
        val anterior = AparienciaCuenta(estilo = "CLARO", cambio = 10)
        val nueva = AparienciaCuenta(estilo = "MARCA", tamanoTexto = 3, cambio = 20)
        val a = Estado(apariencia = anterior, ajustes = Ajustes(cambio = 100))
        val b = Estado(apariencia = nueva, ajustes = Ajustes(cambio = 1))
        assertEquals(nueva, Logica.unir(a, b).apariencia)
        assertEquals(nueva, Logica.unir(b, a).apariencia)
        assertEquals(100L, Logica.unir(a, b).ajustes.cambio)
    }

    @Test fun cajonViejoSinAparienciaNoLaBorra() {
        val viejo = JsonCajon.decodeFromString(Estado.serializer(), "{}")
        val apariencia = AparienciaCuenta(cambio = 7)
        val actual = Estado(apariencia = apariencia)
        assertEquals(apariencia, Logica.unir(actual, viejo).apariencia)
        assertEquals(apariencia, Logica.unir(viejo, actual).apariencia)
        assertEquals(actual, JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), actual)))
    }

    @Test fun sesionesUsanFechasDelServidorYConservanLaActual() {
        val sesion = JsonCajon.decodeFromString(SesionSocial.serializer(), """{"id":"sesion-1","dispositivo":" Pixel 9 ","creada":123,"ultimo_uso":456,"actual":true}""")
        assertEquals(SesionMostrada("sesion-1", "Pixel 9", 123, 456, true), sesion.paraPantalla())
        assertNull(SesionSocial("otra", "  ").paraPantalla().dispositivo)
        assertFalse(SesionSocial("otra").paraPantalla().actual)
    }
}

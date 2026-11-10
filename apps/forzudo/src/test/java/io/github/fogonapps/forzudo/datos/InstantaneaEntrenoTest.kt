package io.github.fogonapps.forzudo.datos

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class InstantaneaEntrenoTest {
    private val ejercicio = Ejercicio("sentadilla", "Sentadilla", "", "", "", "", 1, 10, 60, "", "w")
    private val biblioteca = mapOf(ejercicio.id to ejercicio)
    private val sesion = Sesion("id-estable", "2026-11-09", title = "Fuerza", ms = 3600000,
        entries = mapOf(ejercicio.id to listOf(Serie(40.0, 10.0, true))))
    @Test fun esquemaCerradoSinCuerpoNiCuaderno() {
        val datos = instantaneaEntreno(sesion, biblioteca)
        assertEquals(setOf("version", "titulo", "fecha", "duracion", "unidadDuracion", "ejercicios"), datos.keys)
        val serie = datos["ejercicios"]!!.jsonArray.first().jsonObject["series"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("repeticiones", "peso", "unidadPeso"), serie.keys)
        assertEquals(3600, datos["duracion"]!!.jsonPrimitive.int)
    }
    @Test fun unidades() {
        fun peso(unidad: String) = instantaneaEntreno(sesion, biblioteca, unidad)["ejercicios"]!!.jsonArray.first().jsonObject["series"]!!.jsonArray.first().jsonObject
        assertEquals(40.0, peso("kg")["peso"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(88.1849, peso("lb")["peso"]!!.jsonPrimitive.double, 0.001)
        assertEquals("lb", peso("lb")["unidadPeso"]!!.jsonPrimitive.content)
    }
    @Test fun claveEstableAunqueCambieContenido() {
        assertEquals(claveEntreno(sesion.id), claveEntreno(sesion.copy(title = "Otro").id))
        assertNotEquals(claveEntreno(sesion.id), claveEntreno("otro"))
        assertTrue(claveEntreno("x".repeat(500)).length <= 80)
    }
    @Test fun quitarEjerciciosYSeries() {
        val otra = ejercicio.copy(id = "otro")
        val doble = sesion.copy(entries = sesion.entries + ("otro" to listOf(Serie(10.0, 5.0))))
        val datos = instantaneaEntreno(doble, biblioteca + ("otro" to otra), excluir = setOf("otro"))
        assertEquals(1, datos["ejercicios"]!!.jsonArray.size)
        val sinSerie = instantaneaEntreno(doble, biblioteca + ("otro" to otra), seriesExcluidas = setOf("sentadilla:0"))
        assertEquals("otro", sinSerie["ejercicios"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content)
    }
    private fun rechaza(s: Sesion) { assertTrue(runCatching { instantaneaEntreno(s, biblioteca) }.isFailure) }
    @Test fun limites() {
        rechaza(sesion.copy(title = "x".repeat(121)))
        rechaza(sesion.copy(ms = 86401000))
        rechaza(sesion.copy(entries = emptyMap()))
        rechaza(sesion.copy(entries = mapOf(ejercicio.id to List(101) { Serie(1.0, 1.0) })))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 10001.0).forEach { peso ->
            rechaza(sesion.copy(entries = mapOf(ejercicio.id to listOf(Serie(peso, 1.0)))))
        }
        rechaza(sesion.copy(entries = mapOf(ejercicio.id to listOf(Serie(1.0, 1.5)))))
        assertTrue(instantaneaEntreno(sesion.copy(title = "x".repeat(120), ms = 86400000), biblioteca).isNotEmpty())
    }
    @Test fun limiteTotalIncluyeDiscriminadorDelServidor() {
        val datos = instantaneaEntreno(sesion, biblioteca)
        assertTrue(cabeInstantaneaEntreno(datos))
        assertFalse(cabeInstantaneaEntreno(JsonObject(datos + ("texto" to JsonPrimitive("x".repeat(20000))))))
        assertTrue(runCatching { instantaneaEntreno(sesion, biblioteca, excluir = setOf(ejercicio.id)) }.isFailure)
        assertTrue(runCatching { instantaneaEntreno(sesion, biblioteca, seriesExcluidas = setOf("sentadilla:0")) }.isFailure)
    }
    @Test fun limitesDeEjerciciosFechaYRepeticiones() {
        val muchos = (0..100).associate { "ej$it" to ejercicio.copy(id = "ej$it") }
        val entradas = muchos.keys.associateWith { listOf(Serie(1.0, 1.0)) }
        assertTrue(runCatching { instantaneaEntreno(sesion.copy(entries = entradas), muchos) }.isFailure)
        rechaza(sesion.copy(date = "1969-12-31"))
        rechaza(sesion.copy(entries = mapOf(ejercicio.id to listOf(Serie(1.0, 10001.0)))))
        rechaza(sesion.copy(entries = mapOf(ejercicio.id to listOf(Serie(1.0, Double.NaN)))))
        val maxima = sesion.copy(entries = mapOf(ejercicio.id to listOf(Serie(10000.0, 10000.0))))
        assertTrue(instantaneaEntreno(maxima, biblioteca).isNotEmpty())
        assertTrue(runCatching { instantaneaEntreno(maxima, biblioteca, "lb") }.isFailure)
    }
    @Test fun segundosSinPesoCorporal() {
        val tiempo = ejercicio.copy(kind = "s")
        val datos = instantaneaEntreno(sesion, mapOf(tiempo.id to tiempo))
        val serie = datos["ejercicios"]!!.jsonArray.first().jsonObject["series"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("duracion", "unidadDuracion"), serie.keys)
    }
}

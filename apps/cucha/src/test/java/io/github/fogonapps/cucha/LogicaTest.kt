package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.Anotado
import io.github.fogonapps.cucha.datos.Base
import io.github.fogonapps.cucha.datos.Comible
import io.github.fogonapps.cucha.datos.Dia
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Logica
import io.github.fogonapps.cucha.datos.Nutrientes
import io.github.fogonapps.cucha.datos.Perfil
import io.github.fogonapps.cucha.datos.leerForzudo
import io.github.fogonapps.cucha.datos.leerProductoOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class LogicaTest {
    private val L = Logica(Base.leer(File("src/main/assets/datos/alimentos.json").readText(), File("src/main/assets/datos/recetas.json").readText()))

    @Test fun laBaseYLasRecetasSonCoherentes() {
        assertTrue(L.base.alimentos.size >= 390)
        assertEquals(L.base.alimentos.size, L.base.alimentos.map { it.id }.toSet().size)
        L.base.alimentos.forEach { a ->
            assertTrue(a.id, a.porciones.isNotEmpty() && a.k >= 0)
            // Las calorías cuadran con los macros (4/4/9), salvo las bebidas (el alcohol), lo que casi no tiene nada
            // y lo que tiene mucha fibra o especias, a lo que USDA le calcula las calorías con factores propios.
            val calc = a.p * 4 + a.c * 4 + a.g * 9
            val factoresPropios = setOf("cacao", "salvado_avena", "kale", "pimienta")
            if (a.grupo != "bebidas" && a.k > 30 && a.id !in factoresPropios) assertTrue("${a.id}: ${a.k} vs $calc", calc in a.k * 0.8..a.k * 1.2)
        }
        L.base.recetas.forEach { r -> r.ingredientes.forEach { assertNotNull("${r.id}: ${it.alimento}", L.base.alimento(it.alimento)) } }
    }

    @Test fun metasConMifflinStJeor() {
        // Hombre de 25 años, 175 cm, 70 kg, actividad moderada: basal 1673,75 × 1,55 = 2594 → 2590.
        val m = L.metasDe(Perfil("m", 25, 175.0, 70.0, 2, 0))
        assertEquals(2590, m.kcal)
        assertEquals(126, m.proteina)
        // Subir 0,5 kg por semana suma 550 kcal.
        assertEquals(3140, L.metasDe(Perfil("m", 25, 175.0, 70.0, 2, 1, 0.5)).kcal)
        assertEquals(2500, m.aguaMl)
    }

    @Test fun buscaSinTildesYEnLosDosIdiomas() {
        val r = L.buscar(Estado(), "frijol", "es")
        assertTrue(r.any { it is Comible.DeBase && it.a.id == "frijol_rojo" })
        assertTrue(L.buscar(Estado(), "ajiaco", "es").first() is Comible.DeReceta)
        assertTrue(L.buscar(Estado(), "rice", "es").any { it.ref == "a:arroz_blanco" })
    }

    @Test fun buscaEnPluralYPorOtrosNombres() {
        assertTrue(L.buscar(Estado(), "huevos", "es").any { it.ref == "a:huevo_frito" })
        assertTrue(L.buscar(Estado(), "champiñones", "es").any { it.ref == "a:champinones_salteados" })
        assertTrue(L.buscar(Estado(), "limones", "es").any { it.ref == "a:limon" })
        assertTrue(L.buscar(Estado(), "palomitas", "es").any { it.ref == "a:crispetas_aceite" })
    }

    @Test fun porcionDeRecetaYTotales() {
        val arepa = L.base.receta("arepa_huevo_queso")!!
        val n = L.porPorcion(arepa)
        // 80 g de arepa + 122 g de huevo revuelto + 30 g de queso campesino.
        val esperado = 219.0 * 0.8 + 149.0 * 1.22 + 299.0 * 0.3
        assertEquals(esperado, n.kcal, 0.5)
        val d = Dia(listOf(Anotado("1", "desayuno", "r:x", "x", 1.0, "", Nutrientes(100.0, 10.0, 0.0, 0.0)), Anotado("2", "cena", "r:x", "x", 1.0, "", Nutrientes(50.0, 5.0, 0.0, 0.0))))
        assertEquals(150.0, L.totalDia(d).kcal, 0.01)
        assertEquals(50.0, L.totalComida(d, "cena").kcal, 0.01)
    }

    @Test fun mercadoDeLaSemanaYSemanasAB() {
        val lunes = LocalDate.of(2026, 10, 19)
        val e = Estado(plan = mapOf(L.clavePlan("A", 1, "desayuno") to "arepa_huevo_queso", L.clavePlan("A", 2, "desayuno") to "arepa_huevo_queso"))
        val lista = L.mercado(e, lunes).toMap().mapKeys { it.key.id }
        assertEquals(160.0, lista["arepa"]!!, 0.01)
        // Con semanas que se turnan, la semana 43 (impar) es B y su menú está vacío.
        assertEquals("B", L.letra(lunes, true))
        assertTrue(L.mercado(e.copy(alternar = true), lunes).isEmpty())
    }

    @Test fun rachaYPromedio() {
        val hoy = LocalDate.of(2026, 10, 24)
        val algo = Dia(listOf(Anotado("1", "almuerzo", "a:arroz_blanco", "Arroz", 100.0, "", Nutrientes(130.0, 2.7, 28.0, 0.3))))
        val e = Estado(dias = mapOf("2026-10-23" to algo, "2026-10-22" to algo, "2026-10-20" to algo))
        assertEquals(2, L.racha(e, hoy))
        assertEquals(130.0, L.promedio(e, hoy)!!.kcal, 0.01)
    }

    @Test fun leeElForzudo() {
        val t = """{"v":2,"sessions":[{"id":"s","date":"2026-10-24","title":"Upper A"},{"id":"t","date":"2026-10-20"}],"body":[{"date":"2026-10-01","kg":68.5},{"date":"2026-10-20","kg":69.2}]}"""
        val d = leerForzudo(t, LocalDate.of(2026, 10, 24))!!
        assertEquals(69.2, d.peso!!.kg, 0.001)
        assertEquals("Upper A", d.entrenoHoy)
        assertEquals(2, d.entrenosSemana)
        assertNull(leerForzudo(t, LocalDate.of(2026, 10, 25))!!.entrenoHoy)
    }

    @Test fun leeOpenFoodFacts() {
        val t = """{"status":1,"product":{"product_name":"Galletas","nutriments":{"energy-kcal_100g":480,"proteins_100g":6.5,"carbohydrates_100g":70,"fat_100g":19},"serving_quantity":"30"}}"""
        val p = leerProductoOff(t, "7702000000000")!!
        assertEquals(480.0, p.por100.kcal, 0.01)
        assertEquals(30.0, p.porcionGramos!!, 0.01)
        assertEquals("off", p.fuente)
        assertNull(leerProductoOff("""{"status":0}""", "1"))
    }
}

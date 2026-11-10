package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class InstantaneasTest {
    private val prohibidas = setOf("dias", "metas", "perfil", "pesos", "pesoKg", "peso", "anotados", "favoritos", "alimento", "id")

    private fun sinPrivado(o: JsonElement) {
        when (o) {
            is JsonObject -> { o.keys.forEach { assertFalse("Salió «$it»", it in prohibidas) }; o.values.forEach(::sinPrivado) }
            is JsonArray -> o.forEach(::sinPrivado)
            else -> {}
        }
    }

    @Test fun platoSinNutricionPorDefecto() {
        val d = instantaneaPlato("  Ajiaco ", 5, "Como el de mi abuela", null, null)
        assertEquals("Ajiaco", d["nombre"]!!.jsonPrimitive.content)
        assertNull(d["nutricion"]); assertNull(d["lugar"])
        sinPrivado(d)
    }

    @Test fun platoConNutricionSoloSiSeActiva() {
        val d = instantaneaPlato("Arepa", null, "", 3, Nutrientes(250.4, 8.04, 35.0, 9.0, fibra = 3.0, azucar = 2.0))
        val n = d["nutricion"]!!.jsonObject
        assertEquals(setOf("kcal", "proteina", "carbohidratos", "grasa"), n.keys)
        assertEquals(250.0, n["kcal"]!!.jsonPrimitive.double, 0.0)
        assertEquals(8.0, n["proteina"]!!.jsonPrimitive.double, 0.0)
        assertEquals(3L, d["lugar"]!!.jsonPrimitive.long)
        assertNull(d["texto"]); assertNull(d["nota"])
    }

    @Test fun limitesDelPlato() {
        assertThrows(IllegalArgumentException::class.java) { instantaneaPlato("", null, "", null, null) }
        assertThrows(IllegalArgumentException::class.java) { instantaneaPlato("x".repeat(121), null, "", null, null) }
        assertThrows(IllegalArgumentException::class.java) { instantaneaPlato("Sopa", 6, "", null, null) }
        assertThrows(IllegalArgumentException::class.java) { instantaneaPlato("Sopa", null, "t".repeat(2001), null, null) }
    }

    @Test fun recetaComoInstantaneaSinIdsLocales() {
        val base = Base(listOf(Alimento("papa", "Papa", "Potato", "v", 1, "x", k = 77.0, p = 2.0, c = 17.0, g = 0.1)), emptyList())
        val receta = Receta("r1", "Sudado", porciones = 2, minutos = 40, ingredientes = listOf(Ingrediente("papa", 300.0), Ingrediente("desconocido", 50.0)),
            pasosEs = listOf("Pelar", " ", "Cocinar"))
        val ingredientes = ingredientesCompartidos(receta, base, emptyList(), "es")
        val d = instantaneaReceta(receta.es, receta.porciones, receta.minutos, ingredientes, receta.pasosEs, "", null)
        assertEquals(listOf("Pelar", "Cocinar"), d["pasos"]!!.jsonArray.map { it.jsonPrimitive.content })
        val ing = d["ingredientes"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("nombre", "cantidad", "unidad"), ing.keys)
        assertEquals("Papa", ing["nombre"]!!.jsonPrimitive.content)
        assertEquals("g", ing["unidad"]!!.jsonPrimitive.content)
        assertEquals(40, d["tiempo"]!!.jsonPrimitive.int)
        assertNull(d["nutricion"])
        sinPrivado(d)
        val conNutricion = instantaneaReceta(receta.es, 2, 0, ingredientes, receta.pasosEs, "", Nutrientes(100.0, 2.0, 20.0, 1.0))
        assertNotNull(conNutricion["nutricion"]); assertNull(conNutricion["tiempo"])
    }

    @Test fun limitesDeLaReceta() {
        val uno = listOf(IngredienteCompartido("Agua", 500.0, "ml"))
        assertThrows(IllegalArgumentException::class.java) { instantaneaReceta("Sopa", 0, 0, uno, listOf("Hervir"), "", null) }
        assertThrows(IllegalArgumentException::class.java) { instantaneaReceta("Sopa", 1, 0, emptyList(), listOf("Hervir"), "", null) }
        assertThrows(IllegalArgumentException::class.java) { instantaneaReceta("Sopa", 1, 0, uno, listOf(" "), "", null) }
        assertThrows(IllegalArgumentException::class.java) { instantaneaReceta("Sopa", 1, 0, listOf(IngredienteCompartido("Sal", 1.0, "puñado")), listOf("x"), "", null) }
        assertThrows(IllegalArgumentException::class.java) { instantaneaReceta("Sopa", 1, 0, listOf(IngredienteCompartido("Sal", 0.0)), listOf("x"), "", null) }
    }

    @Test fun resenaYMultimedia() {
        val d = instantaneaResena(7, 4, "Muy rico", " Bandeja ")
        assertEquals("Bandeja", d["plato"]!!.jsonPrimitive.content)
        assertNull(d["lat"]); assertNull(d["lon"])
        assertThrows(IllegalArgumentException::class.java) { instantaneaResena(7, 0, "", "") }
        assertThrows(IllegalArgumentException::class.java) { instantaneaResena(0, 3, "", "") }
        val fotos = conMultimedia(d, listOf("a.jpg"), "", "")
        assertEquals(1, fotos["imagenes"]!!.jsonArray.size)
        val video = conMultimedia(d, emptyList(), "v.mp4", "p.jpg")
        assertNull(video["imagenes"]); assertEquals("v.mp4", video["video"]!!.jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) { conMultimedia(d, listOf("a.jpg"), "v.mp4", "p.jpg") }
        assertTrue(cabePublicacionCucha(d))
        assertFalse(cabePublicacionCucha(instantaneaPlato("Sopa", null, "", null, null).let { JsonObject(it + ("relleno" to JsonPrimitive("x".repeat(100_000)))) }))
    }

    @Test fun clavesEstables() {
        assertEquals(clavePublicacion(TIPO_RECETA, "r1"), clavePublicacion(TIPO_RECETA, "r1"))
        assertNotEquals(clavePublicacion(TIPO_RECETA, "r1"), clavePublicacion(TIPO_PLATO, "r1"))
        assertTrue(clavePublicacion(TIPO_PLATO, "n1").startsWith("plato-"))
        // El servidor rechaza claves de más de 80 caracteres.
        TIPOS_CUCHA.forEach { assertTrue(clavePublicacion(it, "x".repeat(500)).length <= 80) }
    }
}

class FavoritosTest {
    @Test fun soloSubenLasPartesVisiblesYSinCifras() {
        val partes = partesCucha(listOf("Ajiaco", " ajiaco ", "", "Ajiaco"), "publica", listOf("La Puerta Falsa · Bogotá"), "privado")
        assertEquals(setOf(PARTE_PLATOS), partes.keys)
        val datos = partes.getValue(PARTE_PLATOS).datos.jsonObject
        assertEquals(setOf("version", "lista"), datos.keys)
        assertEquals(listOf("Ajiaco", "ajiaco"), nombresFavoritos(datos))
        assertEquals(20, nombresFavoritos(listaFavoritos((1..30).map { "Plato $it" })).size)
        assertThrows(IllegalArgumentException::class.java) { partesCucha(emptyList(), "todos", emptyList(), "privado") }
        assertEquals(emptyList<String>(), nombresFavoritos(null))
    }
}

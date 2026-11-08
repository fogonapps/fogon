package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ResenasTest {
    private val obra = Obra("obra", Tipos.SERIE, "Serie", fuente = "tmdb:tv:1", nota = 8,
        resena = "Opinión antigua", resenaVisibilidad = Visibilidad.AMIGOS, cambio = 10,
        registros = listOf(Registro("entrada", "2026-01-02", 7, texto = "La segunda vez", visibilidad = Visibilidad.PUBLICA)))

    @Test fun migracionConIdsExactosFechaNotaYRespaldoSinCambiar() {
        val e = Resenas.migrar(Estado(obras = listOf(obra)))
        assertEquals(listOf("o:obra", "r:obra:entrada"), e.resenas.map { it.id })
        assertEquals(obra, e.obras.single())
        val opinion = e.resenas.first()
        assertEquals("Opinión antigua", opinion.texto)
        assertEquals(8, opinion.nota)
        assertEquals(Visibilidad.AMIGOS, opinion.visibilidad)
        val entrada = e.resenas.last()
        assertEquals("entrada", entrada.registro)
        assertEquals("2026-01-02", entrada.fecha)
        assertEquals(7, entrada.nota)
        assertEquals(Visibilidad.PUBLICA, entrada.visibilidad)
        assertEquals(e, Resenas.migrar(e))
        assertEquals(e, Logica.normalizar(Logica.normalizar(e)))
    }

    @Test fun cargaDeDiscoMigraYSerializacionConservaResenasYBorradores() = runTest {
        val inicial = Estado(obras = listOf(obra))
        val cargado = SerializadorEstado.readFrom(ByteArrayInputStream(JsonCajon.encodeToString(Estado.serializer(), inicial).toByteArray()))
        assertEquals(2, cargado.resenas.size)
        val e = cargado.copy(borradoresResena = listOf(Resena("n:borrador", "obra", texto = "pendiente")))
        assertEquals(e, JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), e)))
    }

    @Test fun camposVaciosNoSeMigranYDiarioHeredaVisibilidad() {
        assertTrue(Resenas.migrar(Estado(obras = listOf(obra.copy(resena = " ", registros = emptyList())))).resenas.isEmpty())
        val e = Resenas.migrar(Estado(obras = listOf(obra.copy(resena = "", registros = listOf(obra.registros.single().copy(visibilidad = "")))),
            perfil = Perfil(visibilidad = mapOf(PartesPerfil.DIARIO to Visibilidad.AMIGOS))))
        assertEquals(Visibilidad.AMIGOS, e.resenas.single().visibilidad)
    }

    @Test fun unionConservaEdicionesYBorradosSinResucitarElRespaldo() {
        val viejo = Estado(obras = listOf(obra))
        val migrado = Resenas.migrar(viejo)
        val editado = migrado.copy(resenas = migrado.resenas.map { it.copy(texto = "Nueva", cambio = 20) })
        assertEquals(editado.resenas, Logica.unir(viejo, editado).resenas)
        val borrado = editado.copy(resenas = editado.resenas.map { it.copy(borrada = true, cambio = 30) })
        assertEquals(borrado.resenas, Logica.normalizar(Logica.unir(borrado, viejo)).resenas)
        // Cambiar solo la nota de una obra en un cliente antiguo no puede restaurar su texto de respaldo.
        val respaldoTocado = viejo.copy(obras = listOf(obra.copy(cambio = 1000)))
        assertEquals(borrado.resenas, Logica.normalizar(Logica.unir(borrado, respaldoTocado)).resenas)
        val duplicados = migrado.copy(resenas = migrado.resenas + editado.resenas)
        assertEquals(editado.resenas, Logica.normalizar(duplicados).resenas)
        val nuevo = Resena("n:otra", "obra", texto = "Otra", cambio = 40)
        assertEquals(3, Logica.unir(editado, Estado(resenas = listOf(nuevo))).resenas.size)
    }

    @Test fun subidaUsaClavesDatosFotosYNoDuplicaDiario() {
        val migradas = Resenas.migrar(Estado(obras = listOf(obra)))
        val propias = migradas.resenas.map { it.copy(parte = "ep:S1E3", spoiler = true, fotos = listOf("uno.jpg", "dos.jpg"), nota = 0) }
        val e = migradas.copy(resenas = propias, borradoresResena = listOf(Resena("n:borrador", "obra", texto = "Nunca sale", visibilidad = Visibilidad.PUBLICA)))
        val subidas = RedSocial.publicacionesDe(e)
        assertEquals(setOf("o:obra", "r:obra:entrada"), subidas.map { it.clave }.toSet())
        subidas.forEach { p ->
            assertEquals("resena", p.tipo)
            assertEquals("tmdb:tv:1", p.datos["fuente"]!!.jsonPrimitive.content)
            assertEquals("ep:S1E3", p.datos["parte"]!!.jsonPrimitive.content)
            assertEquals(0, p.datos["nota"]!!.jsonPrimitive.int)
            assertTrue(p.datos["spoiler"]!!.jsonPrimitive.boolean)
            assertEquals(listOf("uno.jpg", "dos.jpg"), p.datos["fotos"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
        assertTrue(RedSocial.publicacionesDe(e.copy(resenas = propias.map { it.copy(borrada = true) })).isEmpty())
        assertTrue(RedSocial.publicacionesDe(e.copy(resenas = propias.map { it.copy(visibilidad = Visibilidad.PRIVADA) })).isEmpty())
        val sinNota = RedSocial.publicacionesDe(e.copy(resenas = propias.map { it.copy(nota = null) }))
        assertTrue(sinNota.all { "nota" !in it.datos && "estrellas" !in it.datos })
    }

    @Test fun diarioSinResenaSigueSubiendo() {
        val e = Estado(obras = listOf(obra.copy(resena = "", registros = listOf(obra.registros.single().copy(texto = "")))))
        assertEquals("diario", RedSocial.publicacionesDe(e).single().tipo)
    }

    @Test fun limitesDelCompositor() {
        val r = Resena("n:1", "obra", texto = "x".repeat(5000), nota = 0, fotos = List(4) { "$it.jpg" })
        assertTrue(Resenas.valida(r))
        assertTrue(Resenas.valida(r.copy(nota = 10)))
        assertTrue(Resenas.valida(r.copy(nota = null)))
        assertFalse(Resenas.valida(r.copy(texto = "x".repeat(5001))))
        assertFalse(Resenas.valida(r.copy(texto = " ")))
        assertFalse(Resenas.valida(r.copy(nota = -1)))
        assertFalse(Resenas.valida(r.copy(nota = 11)))
        assertFalse(Resenas.valida(r.copy(fotos = List(5) { "$it.jpg" })))
        assertFalse(Resenas.valida(r.copy(fotos = listOf(""))))
        assertFalse(Resenas.valida(r.copy(obra = "")))
    }

    @Test fun partesSegunMedioYEtiquetas() {
        val serie = obra.copy(temporadas = listOf(Temporada(1, 3), Temporada(2, 1)))
        assertEquals(listOf("", "temp:1", "ep:S1E1", "ep:S1E2", "ep:S1E3", "temp:2", "ep:S2E1"), Resenas.partes(serie))
        assertEquals(Resenas.partes(serie), Resenas.partes(serie.copy(tipo = Tipos.ANIME)))
        assertTrue(Resenas.parteValida(serie, "ep:S1E3"))
        assertFalse(Resenas.parteValida(serie, "ep:S9E9"))
        val libro = obra.copy(tipo = Tipos.LIBRO)
        assertTrue(Resenas.parteValida(libro, "cap:12"))
        assertTrue(Resenas.parteValida(libro, "pag:200"))
        assertFalse(Resenas.parteValida(libro, "cap:"))
        assertFalse(Resenas.parteValida(libro, "pag:" + "x".repeat(41)))
        val album = obra.copy(tipo = Tipos.ALBUM, canciones = listOf(Cancion("Canción", mbid = "mbid"), Cancion("Sin id")))
        assertEquals(listOf("", "cancion:mbid"), Resenas.partes(album))
        assertEquals(listOf(""), Resenas.partes(obra.copy(tipo = Tipos.PELICULA)))
        assertTrue(Resenas.parteValida(obra, "artista:mb:artist:1"))
        assertTrue(Resenas.parteValida(libro, "autor:ol:author:1"))
        assertEquals("T1 · E3", Resenas.etiqueta("ep:S1E3"))
        assertEquals("S1 · E3", Resenas.etiqueta("ep:S1E3", ingles = true))
        assertEquals("T2", Resenas.etiqueta("temp:2"))
        assertEquals("Cap. 12", Resenas.etiqueta("cap:12"))
        assertEquals("Ch. 12", Resenas.etiqueta("cap:12", ingles = true))
        assertEquals("Pág. 200", Resenas.etiqueta("pag:200"))
        assertEquals("Canción", Resenas.etiqueta("cancion:mbid", album))
    }

    @Test fun publicaResenasDeAutoresYArtistas() {
        val a = Artista("a", "Autora", fuente = "ol:author:1", tipo = Personas.AUTOR)
        val e = Estado(artistas = listOf(a), resenas = listOf(Resena("n:1", "a", parte = "autor:ol:author:1", texto = "Opinión", visibilidad = Visibilidad.PUBLICA)))
        val p = RedSocial.publicacionesDe(e).single()
        assertEquals("ol:author:1", p.datos["fuente"]!!.jsonPrimitive.content)
        assertEquals("Autora", p.datos["titulo"]!!.jsonPrimitive.content)
    }
    @Test fun importarConvierteOpinionesSinEscribirLosCamposViejosNiDuplicarlas() {
        val (e, cantidad) = Importar.sumar(Estado(), listOf(obra), 50)
        assertEquals(1, cantidad)
        assertEquals(2, e.resenas.size)
        assertTrue(e.resenas.all { it.id.startsWith("n:") })
        assertEquals("", e.obras.single().resena)
        assertEquals("", e.obras.single().registros.single().texto)
        val (otra, repetidas) = Importar.sumar(e, listOf(obra), 60)
        assertEquals(0, repetidas)
        assertEquals(e.resenas, otra.resenas)
        assertEquals(2, Resenas.migrar(otra).resenas.size)
    }

    @Test fun votosUtilesSeLeenYLosBorradoresSeUnenSinSubir() {
        val p = JsonCajon.decodeFromString(PublicacionSocial.serializer(),
            """{"id":1,"autor":{"usuario":"ana"},"tipo":"resena","cuando":1,"visibilidad":"publica","datos":{},"utiles":3,"yoUtil":true}""")
        assertEquals(3, p.utiles)
        assertTrue(p.yoUtil)
        val r = Resena("n:borrador", "obra", texto = "Original", cambio = 10, visibilidad = Visibilidad.PUBLICA)
        val a = Estado(borradoresResena = listOf(r))
        val b = Estado(borradoresResena = listOf(r.copy(texto = "Editado", cambio = 20)))
        val e = Logica.unir(a, b)
        assertEquals("Editado", e.borradoresResena.single().texto)
        assertTrue(RedSocial.publicacionesDe(e).isEmpty())
    }

    @Test fun subidaNoLeeElTextoDeLosCamposDeRespaldo() {
        val e = Estado(obras = listOf(obra))
        val p = RedSocial.publicacionesDe(e).single()
        assertEquals("diario", p.tipo)
        assertFalse("texto" in p.datos)
        assertFalse(RedSocial.publicacionesDe(e).any { it.clave == "o:obra" })
    }

}

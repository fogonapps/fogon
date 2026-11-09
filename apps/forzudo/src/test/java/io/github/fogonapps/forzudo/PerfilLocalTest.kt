package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PerfilLocalTest {
    private val logica = Logica(Biblioteca.cargar { File("src/main/assets/datos/$it").readText() })

    @Test fun archivoAnteriorConservaNombreYEntrenos() {
        val estado = JsonGym.decodeFromString(Estado.serializer(), """{"v":2,"nombre":"Ana","sessions":[{"id":"s","date":"2026-11-01"}]}""")
        assertEquals("Ana", estado.nombre)
        assertEquals("s", estado.sessions.single().id)
        assertEquals("", estado.foto)
        assertEquals(0L, estado.perfilActualizado)
    }

    @Test fun respaldoIncluyePerfilSinEntrenos() {
        val estado = Estado(nombre = "Ana", bio = "Hola", foto = "foto", portada = "portada", perfilActualizado = 5)
        assertEquals(estado, leerRespaldoGym(JsonGym.encodeToString(Estado.serializer(), estado)))
    }

    @Test fun clienteAnteriorNoBorraLaPresentacion() {
        val local = Estado(nombre = "Ana", bio = "Hola", foto = "foto", portada = "portada", perfilActualizado = 5)
        val remoto = Estado(updatedAt = 100, sessions = listOf(Sesion("s", "2026-11-01")))
        for ((a, b) in listOf(local to remoto, remoto to local)) {
            val unido = logica.unir(a, b)
            assertEquals("foto", unido.foto)
            assertEquals("Hola", unido.bio)
            assertEquals("s", unido.sessions.single().id)
        }
    }

    @Test fun quitarImagenSePropagaSinResucitarLaAnterior() {
        val anterior = Estado(foto = "foto", portada = "portada", perfilActualizado = 5)
        val sinImagen = Estado(perfilActualizado = 6)
        for ((a, b) in listOf(anterior to sinImagen, sinImagen to anterior)) {
            val unido = logica.unir(a, b)
            assertEquals("", unido.foto)
            assertEquals("", unido.portada)
        }
    }
}

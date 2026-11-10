package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.*
import io.github.fogonapps.social.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PerfilDeportivoTest {
    private val logica = Logica(Biblioteca.cargar { File("src/main/assets/datos/$it").readText() })
    private val hoy = LocalDate.of(2026, 11, 9)
    private val estado = Estado(nombre = "Identidad privada", bio = "Bio privada", body = listOf(PesoCorporal("2026-11-09", 73.123)),
        active = EntrenoActivo("activo-secreto", "2026-11-09", "lunes"),
        sessions = listOf(Sesion("sesion-secreta", "2026-11-08", title = "Titulo privado", entries = mapOf("press-banca" to listOf(Serie(80.0, 8.0, true))))))

    @Test fun noPublicaNadaPorDefecto() { assertTrue(partesDeportivas(estado, logica, hoy, "privado", "privado").isEmpty()) }
    @Test fun soloPublicaNivelEIdentificadoresDeMedallasConVisibilidadExplicita() {
        val partes = partesDeportivas(estado, logica, hoy, "amigos", "publica")
        assertEquals(setOf("rangos", "medallas"), partes.keys)
        assertEquals("amigos", partes.getValue("rangos").visibilidad)
        assertEquals("publica", partes.getValue("medallas").visibilidad)
        val texto = JsonSocial.encodeToString(MapSerializer(String.serializer(), ParteSubida.serializer()), partes)
        listOf("Identidad privada", "Bio privada", "73.123", "body", "active", "activo-secreto", "sesion-secreta", "Titulo privado", "entries", "kg", "reps").forEach {
            assertFalse("No debe publicar $it", texto.contains(it))
        }
    }
    @Test fun retirarUnaParteNoIncluyeSusDatos() {
        assertEquals(setOf("medallas"), partesDeportivas(estado, logica, hoy, "privado", "publica").keys)
    }
}

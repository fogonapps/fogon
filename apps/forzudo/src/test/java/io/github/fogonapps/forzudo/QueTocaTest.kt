package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.Logica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** La tarjeta de Inicio: lo que toca y, con un entreno en curso, lo que viene después (sin repetir el que está abierto). */
class QueTocaTest {
    private val L = Logica(Biblioteca.cargar("es") { File("src/main/assets/datos/$it").readText() })
    private val dias = L.dias(Estado())
    private val hoy = generateSequence(LocalDate.of(2026, 11, 22)) { it.plusDays(1) }.first { L.diaDeHoy(dias, it) == dias[0].id }

    @Test fun tocaLaDeHoy() = assertEquals(dias[0].id, L.queToca(dias, hoy, emptySet())?.id)

    @Test fun despuesNoRepiteLaQueEstaEnCurso() {
        val despues = L.queToca(dias, hoy, emptySet(), enCurso = dias[0].id)
        assertEquals(dias[1].id, despues?.id)
    }

    @Test fun sinNadaDespuesNoHayLinea() {
        val hechos = dias.drop(1).map { it.id }.toSet()
        assertNull(L.queToca(dias, hoy, hechos, enCurso = dias[0].id))
    }
}

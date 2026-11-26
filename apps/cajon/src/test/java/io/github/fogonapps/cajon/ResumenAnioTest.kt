package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Lamina
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.ResumenAnio
import io.github.fogonapps.cajon.datos.Tipos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ResumenAnioTest {
    private fun obra(id: String, tipo: String = Tipos.PELICULA, fechas: List<String> = listOf("2026-03-01"), nota: Int = 0, meGusta: Boolean = false,
                     generos: List<String> = emptyList(), creador: String = "", minutos: Int? = 100, paginas: Int? = null, estado: String = Estados.TERMINADO) =
        Obra(id, tipo, "Obra $id", nota = nota, meGusta = meGusta, generos = generos, creador = creador, minutos = minutos, paginas = paginas,
            estado = estado, registros = fechas.mapIndexed { i, f -> Registro("r$id$i", f) }, terminada = fechas.lastOrNull())

    @Test fun cuentaSoloLoDelAnio() {
        val e = Estado(obras = listOf(
            obra("a", fechas = listOf("2026-01-10")), obra("b", fechas = listOf("2025-12-30")),
            obra("c", tipo = Tipos.LIBRO, fechas = listOf("2026-02-01"), paginas = 300, minutos = null),
            obra("d", tipo = Tipos.SERIE, estado = Estados.EN_CURSO, fechas = listOf("2026-02-02")),
        ))
        val r = ResumenAnio.de(e, 2026)
        assertEquals(2, r.total)
        // Lo empezado también cuenta en el diario, pero no como terminado.
        assertEquals(3, r.registros)
        assertEquals(listOf(Tipos.PELICULA to 1, Tipos.LIBRO to 1).toSet(), r.porTipo.toSet())
        assertEquals(300, r.paginas)
        assertEquals(100, r.minutos)
    }

    @Test fun conMenosDeCincoInvitaATraer() {
        val pocas = Estado(obras = (1..4).map { obra("p$it", fechas = listOf("2026-05-0$it")) })
        assertTrue(ResumenAnio.de(pocas, 2026).pocosDatos)
        val bastantes = Estado(obras = (1..5).map { obra("p$it", fechas = listOf("2026-05-0$it")) })
        assertFalse(ResumenAnio.de(bastantes, 2026).pocosDatos)
        // Lo de otro año no cuenta.
        assertTrue(ResumenAnio.de(bastantes, 2025).pocosDatos)
    }

    @Test fun mejoresFavoritasGeneroYCreador() {
        val e = Estado(obras = listOf(
            obra("a", nota = 10, meGusta = true, generos = listOf("Drama"), creador = "Ana"),
            obra("b", nota = 8, generos = listOf("Drama", "Comedia"), creador = "Ana"),
            obra("c", nota = 6, generos = listOf("Comedia"), creador = "Bea"),
            obra("d", nota = 4, meGusta = true),
            obra("e", meGusta = true),
        ))
        val r = ResumenAnio.de(e, 2026)
        assertEquals(listOf("a", "b", "c"), r.mejores.map { it.id })
        // Las favoritas no repiten lo mejor calificado.
        assertEquals(listOf("d", "e"), r.favoritas.map { it.id })
        assertEquals("Drama" to 2, r.genero)
        assertEquals("Ana" to 2, r.creador)
    }

    @Test fun mesMasActivoYRachaMasLarga() {
        val e = Estado(obras = listOf(
            obra("a", fechas = listOf("2026-03-01", "2026-03-02", "2026-03-03")),
            obra("b", fechas = listOf("2026-03-04", "2026-07-10")),
            obra("c", fechas = listOf("2026-07-11")),
            obra("viejo", fechas = listOf("2025-12-31")),
        ))
        val r = ResumenAnio.de(e, 2026)
        assertEquals(3, r.mes)
        assertEquals(4, r.enMes)
        assertEquals(4, r.racha)
        // El 31 de diciembre de 2025 no alarga la racha de 2026.
        assertEquals(1, ResumenAnio.rachaMasLarga(e, 2025))
    }

    @Test fun sinDiarioElMesSaleDeLoTerminado() {
        val importada = Obra("i", Tipos.LIBRO, "Importada", estado = Estados.TERMINADO, terminada = "2026-09-15")
        val r = ResumenAnio.de(Estado(obras = listOf(importada)), 2026)
        assertEquals(9, r.mes)
        assertEquals(0, r.racha)
    }

    @Test fun lasLaminasSinDatosSeSaltan() {
        val r = ResumenAnio.de(Estado(obras = listOf(obra("a", minutos = null))), 2026)
        assertEquals(listOf(Lamina.TOTAL, Lamina.MES, Lamina.CIERRE), r.laminas)
        val vacio = ResumenAnio.de(Estado(), 2026)
        assertNull(vacio.mes)
        assertEquals(listOf(Lamina.TOTAL, Lamina.CIERRE), vacio.laminas)
    }

    @Test fun conTodoSalenTodasLasLaminas() {
        val e = Estado(obras = listOf(
            obra("a", nota = 9, meGusta = true, generos = listOf("Drama"), creador = "Ana", fechas = listOf("2026-04-01", "2026-04-02")),
            obra("b", tipo = Tipos.LIBRO, paginas = 200, minutos = null, nota = 7, meGusta = true),
            obra("c", tipo = Tipos.LIBRO, paginas = 100, minutos = null, nota = 8),
            obra("d", nota = 6, meGusta = true),
        ))
        assertEquals(Lamina.entries.toList(), ResumenAnio.de(e, 2026).laminas)
    }

    @Test fun anioYTemporada() {
        assertEquals(2025, ResumenAnio.anioDelResumen(LocalDate.of(2026, 1, 20)))
        assertEquals(2026, ResumenAnio.anioDelResumen(LocalDate.of(2026, 12, 1)))
        assertTrue(ResumenAnio.esTemporada(LocalDate.of(2026, 12, 1)))
        assertTrue(ResumenAnio.esTemporada(LocalDate.of(2027, 1, 31)))
        assertFalse(ResumenAnio.esTemporada(LocalDate.of(2026, 11, 25)))
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.PersonaSocial
import io.github.fogonapps.cajon.datos.Sobre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatTest {
    private fun sobre(id: Long, cuando: Long = 100) = Sobre(id, PersonaSocial("ana"), cuando, "sobre", "firma")

    @Test fun reinicioProcesaIdsBajosYAjustaCursor() {
        val (nuevos, cursor) = decidirSobres(9, listOf(sobre(2), sobre(1)), emptyMap())
        assertEquals(listOf(1L, 2L), nuevos.map { it.id })
        assertEquals(2L, cursor)
    }

    @Test fun reinicioMixtoConservaTodosLosSobresSinDuplicar() {
        val nuevos = decidirSobres(9, listOf(sobre(1), sobre(10), sobre(1)), emptyMap())
        assertEquals(listOf(1L, 10L), nuevos.first.map { it.id })
        assertEquals(10L, nuevos.second)
    }

    @Test fun entregaRepetidaNoDuplicaNiReinicia() {
        val s = sobre(1)
        for (id in listOf("s1", claveSobre(s))) {
            val guardados = mapOf("ana" to listOf(MensajeChat(id, false, s.cuando)))
            val (nuevos, cursor) = decidirSobres(9, listOf(s), guardados)
            assertEquals(emptyList<Sobre>(), nuevos)
            assertEquals(9L, cursor)
        }
    }

    @Test fun sobresDistintosDelMismoMilisegundoSeConservanEnTandasSucesivas() {
        var lista = emptyList<MensajeChat>()
        var ultimo = 0L
        for (id in listOf(1L, 2L)) {
            val (nuevos, cursor) = decidirSobres(ultimo, listOf(sobre(id)), mapOf("ana" to lista))
            assertEquals(listOf(id), nuevos.map { it.id })
            for (s in nuevos) lista = agregarMensajeChat(lista, MensajeChat(claveSobre(s), false, s.cuando, k = "contenido$id"))
            ultimo = cursor
        }
        assertEquals(listOf("contenido1", "contenido2"), lista.map { it.k })
        assertEquals(2L, ultimo)
    }

    @Test fun sobreRenumeradoSeAbrePeroNoDuplicaLaMismaClaveEstable() {
        val nuevo = sobre(1_000_000)
        for (id in listOf("s1", claveSobre(sobre(1)))) {
            val lista = listOf(MensajeChat(id, false, nuevo.cuando, k = "estable"))
            val (nuevos, cursor) = decidirSobres(9, listOf(nuevo), mapOf("ana" to lista))
            assertEquals(listOf(nuevo), nuevos)
            assertEquals(nuevo.id, cursor)
            val repetido = MensajeChat(claveSobre(nuevos.single()), false, nuevo.cuando, k = "estable")
            assertEquals(lista, agregarMensajeChat(lista, repetido))
        }
    }

    @Test fun ilegiblesYClavesVaciasNoSeDescartanTrasRenumerar() {
        for (ilegible in listOf(false, true)) {
            val viejo = MensajeChat("s1", false, 100, noSeLee = ilegible)
            val nuevo = viejo.copy(id = "s1000000")
            assertEquals(listOf(viejo, nuevo), agregarMensajeChat(listOf(viejo), nuevo))
        }
    }

    @Test fun claveEstableDeUnMensajePropioNoDescartaUnoRecibido() {
        val propio = MensajeChat("propio", true, 100, k = "estable")
        val recibido = MensajeChat("s2", false, 100, k = "estable")
        assertEquals(listOf(propio, recibido), agregarMensajeChat(listOf(propio), recibido))
    }

    @Test fun mismaFechaDeOtraPersonaODeUnMensajePropioNoEsDuplicado() {
        val nuevo = sobre(10)
        for (guardados in listOf(
            mapOf("beto" to listOf(MensajeChat("s1", false, nuevo.cuando))),
            mapOf("ana" to listOf(MensajeChat("propio", true, nuevo.cuando))),
        )) {
            assertEquals(listOf(nuevo) to nuevo.id, decidirSobres(9, listOf(nuevo), guardados))
        }
    }

    @Test fun idReutilizadoConOtraFechaSeConserva() {
        val viejo = sobre(1, 50)
        val nuevo = sobre(1, 100)
        val guardados = mapOf("ana" to listOf(MensajeChat("s1", false, viejo.cuando)))
        assertEquals(listOf(nuevo), decidirSobres(9, listOf(nuevo), guardados).first)
        assertNotEquals(claveSobre(viejo), claveSobre(nuevo))
    }

    @Test fun reinicioPuedeRepetirExactamenteElUltimoId() {
        assertEquals(listOf(sobre(9)) to 9L, decidirSobres(9, listOf(sobre(9)), emptyMap()))
    }

    @Test fun flujoNormalYRespuestaVaciaConservanCursor() {
        assertEquals(listOf(sobre(10)) to 10L, decidirSobres(9, listOf(sobre(9), sobre(10)), emptyMap()))
        assertEquals(emptyList<Sobre>() to 9L, decidirSobres(9, emptyList(), emptyMap()))
    }
}

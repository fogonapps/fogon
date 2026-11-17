package io.github.fogonapps.social.ui

import org.junit.Assert.*
import org.junit.Test

class CapasMercadoTest {
    @Test fun deshacerConservaVeintePasosYUnGestoCuentaUnaVez() {
        val h = HistorialMercado(LienzoMercado())
        repeat(25) { h.aplicar(h.actual.agregar("circulo")) }
        repeat(20) { h.deshacer() }
        assertEquals(5,h.actual.capas.size)
        val inicio=h.actual
        repeat(50) { h.transformar(h.actual.copy(paso=8f+it)) }
        h.terminarGesto(inicio)
        assertEquals(inicio,h.deshacer())
    }
    @Test fun ordenYBorradoNoCambianLasOtrasCapas() {
        val l=LienzoMercado().agregar("circulo").agregar("pildora").agregar("estrella")
        val cambio=l.ordenar(1,true)
        assertEquals(listOf(2,1,3),cambio.capas.map { it.id })
        assertEquals(l,cambio.ordenar(1,false))
        assertEquals(listOf(2,3),cambio.borrar(1).capas.map { it.id })
        assertEquals(l,l.ordenar(1,false))
    }
    @Test fun borradorConTodasLasCapasViajaSinPerdidas() {
        val l=LienzoMercado("marco",2,3,true,.5f,32f)
            .agregar("texto","Árbol \"riso\"").agregar("imagen","foto-123.jpg")
            .agregar("personaje","celebrar_cucha").agregar("estrella")
        assertEquals(l,LienzoMercado.leer(l.serializar()))
    }
    @Test fun limiteDeTextosYCapas() {
        var l=LienzoMercado()
        repeat(5) { l=l.agregar("texto","Texto") }
        assertEquals(3,l.capas.size)
        repeat(50) { l=l.agregar("circulo") }
        assertEquals(40,l.capas.size)
    }
    @Test fun rechazaVersionDesconocida() {
        assertThrows(IllegalArgumentException::class.java) { LienzoMercado.leer(LienzoMercado().serializar().replace("\"version\":1","\"version\":99")) }
    }
}

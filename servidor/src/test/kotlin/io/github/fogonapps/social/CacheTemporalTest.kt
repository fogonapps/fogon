package io.github.fogonapps.social

import kotlin.test.*

class CacheTemporalTest {
    private val r = Cine.Respuesta(200, "[]")

    @Test fun topeDeEntradas() {
        var reloj = 0L
        val c = CacheTemporal.respuestas(1000, 3, 1_000_000, { reloj })
        listOf("a", "b", "c", "d").forEach { reloj++; c.guardar(it, r) }
        assertEquals(3, c.cantidad)
        // Se fue la más vieja.
        assertNull(c.leer("a"))
        assertNotNull(c.leer("d"))
    }

    @Test fun cuentaLlaveYCuerpo() {
        val c = CacheTemporal.respuestas(1000, 1000, 2_000, { 0L })
        // Búsquedas vacías con llaves grandes: antes no pesaban nada y crecían sin límite.
        repeat(100) { c.guardar("games|search \"${"x".repeat(500)}$it\";", r) }
        assertTrue(c.caracteres <= 2_000, "${c.caracteres}")
        assertTrue(c.cantidad in 1..4, "${c.cantidad}")
        // Una sola que no cabe no se guarda ni borra lo demás.
        val antes = c.cantidad
        c.guardar("y".repeat(3_000), r)
        assertNull(c.leer("y".repeat(3_000)))
        assertEquals(antes, c.cantidad)
    }

    @Test fun loVencidoSeBorra() {
        var reloj = 0L
        val c = CacheTemporal.respuestas(1000, 100, 1_000_000, { reloj })
        c.guardar("zelda", r)
        reloj = 500
        c.guardar("mario", r)
        // Al guardar otra cosa se va lo vencido, aunque nadie lo vuelva a leer.
        reloj = 1000
        c.guardar("kirby", r)
        assertEquals(2, c.cantidad)
        // Y el barrido periódico se lleva el resto sin que haga falta guardar nada.
        reloj = 2000
        c.barrer()
        assertEquals(0, c.cantidad)
        assertEquals(0, c.caracteres)
    }

    @Test fun guardarOtraVezNoDuplica() {
        var reloj = 0L
        val c = CacheTemporal.respuestas(1000, 100, 1_000_000, { reloj })
        c.guardar("a", r)
        reloj = 900
        c.guardar("a", Cine.Respuesta(200, "[1]"))
        assertEquals(1, c.cantidad)
        assertEquals(4L, c.caracteres)
        reloj = 1500
        assertEquals("[1]", c.leer("a")?.cuerpo)
    }
}

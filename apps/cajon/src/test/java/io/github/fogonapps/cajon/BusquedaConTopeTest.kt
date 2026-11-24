package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.ArtistaEncontrado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La búsqueda siempre termina: la barra se apaga y, si algo no llegó, sale el aviso. Cada prueba corre en un solo hilo,
 * como el hilo principal de la app.
 */
class BusquedaConTopeTest {
    private val director = ArtistaEncontrado("", "Denis Villeneuve", "", null, "director", "")

    private fun lanzar(estado: MutableStateFlow<Busqueda>, fuentes: CoroutineScope.(MutableStateFlow<Busqueda>) -> Unit, tope: Long = 2_000): Long =
        runBlocking {
            val inicio = System.nanoTime()
            busquedaConTope(estado, tope) { fuentes(estado) }
            (System.nanoTime() - inicio) / 1_000_000
        }

    /**
     * Lo que pasaba con «dune»: los directores tomaban la copia del estado antes de esperar su consulta y, al volver,
     * la escribían: las personas, que ya habían terminado, volvían a quedar «cargando» y la barra no se apagaba nunca.
     */
    @Test fun unaFuenteQueEscribeUnaCopiaViejaNoDejaLaBarraEncendida() {
        val estado = MutableStateFlow(Busqueda(texto = "dune"))
        lanzar(estado, { e ->
            fuenteDeBusqueda(e, "personas") { delay(50) }
            fuenteDeBusqueda(e, "directores") {
                // Copia vieja a propósito: se toma antes de esperar, como hacía la búsqueda de directores.
                val vieja = e.value
                delay(200)
                e.value = vieja.copy(directores = listOf(director))
            }
        })
        assertTrue("quedó cargando ${estado.value.cargando}", estado.value.cargando.isEmpty())
        assertEquals(listOf(director), estado.value.directores)
    }

    /** Una fuente que no suelta el hilo (no atiende la cancelación) no retiene la búsqueda más allá del tope. */
    @Test fun unaFuenteColgadaTerminaEnElTopeConAviso() {
        val estado = MutableStateFlow(Busqueda(texto = "inception"))
        val ms = lanzar(estado, { e ->
            fuenteDeBusqueda(e, "tmdb") { withContext(Dispatchers.IO) { Thread.sleep(2_000) } }
            fuenteDeBusqueda(e, "libros") { delay(20) }
        }, tope = 300)
        assertTrue("tardó $ms ms", ms < 1_500)
        assertTrue(estado.value.cargando.isEmpty())
        assertTrue(estado.value.avisos.isNotEmpty())
    }

    /** Muchas fuentes a la vez, unas lentas y otras que fallan: todas se desmarcan y la búsqueda acaba sin esperar el tope. */
    @Test fun muchasFuentesALaVezTerminanTodas() {
        val estado = MutableStateFlow(Busqueda(texto = "the matrix"))
        val ms = lanzar(estado, { e ->
            repeat(40) { i ->
                fuenteDeBusqueda(e, "f$i") {
                    delay((i % 7) * 10L)
                    if (i % 5 == 0) throw java.io.IOException("caída")
                    e.value = e.value.copy(cargando = e.value.cargando)
                }
            }
        })
        assertTrue("tardó $ms ms", ms < 1_500)
        assertTrue(estado.value.cargando.isEmpty())
        assertEquals(8, estado.value.errores.size)
    }
}

package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.EntrenoActivo
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.JsonGym
import io.github.fogonapps.forzudo.datos.Logica
import io.github.fogonapps.forzudo.datos.Rutina
import io.github.fogonapps.forzudo.datos.Sesion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/** Los nombres de fábrica de las rutinas siguen al idioma de la app; los que cambió la persona, no. */
class RutinasDeFabricaTest {
    private fun bib(idioma: String) = Biblioteca.cargar(idioma) { File("src/main/assets/datos/$it").readText() }
    private val enEspanol = Logica(bib("es"))
    private val enIngles = Logica(bib("en"))

    private val guardadoEnIngles = Estado(
        routines = listOf(Rutina("t1", "Upper A"), Rutina("p1", "Mi pierna"), Rutina("r1700000000000", "Upper A")),
        sessions = listOf(Sesion("s1", "2026-11-20", "t1", "Upper A"), Sesion("s2", "2026-11-21", "p1", "Lower A")),
        active = EntrenoActivo("a", "2026-11-24", "t1", "Upper A"),
        program = "vol4",
    )

    @Test fun pasaAlEspanol() {
        val e = enEspanol.traducirRutinasDeFabrica(guardadoEnIngles, bib("en"))
        assertEquals("Torso A", e.routines!![0].title)
        assertEquals("Torso A", e.sessions[0].title)
        assertEquals("Pierna A", e.sessions[1].title)
        assertEquals("Torso A", e.active!!.title)
    }

    @Test fun loDeLaPersonaNoSeToca() {
        val e = enEspanol.traducirRutinasDeFabrica(guardadoEnIngles, bib("en"))
        assertEquals("Mi pierna", e.routines!![1].title)
        // Una rutina propia que se llama igual que una de fábrica no es de fábrica.
        assertEquals("Upper A", e.routines!![2].title)
    }

    @Test fun yDeVueltaAlIngles() {
        val es = enEspanol.traducirRutinasDeFabrica(guardadoEnIngles, bib("en"))
        val en = enIngles.traducirRutinasDeFabrica(es, bib("es"))
        assertEquals("Upper A", en.routines!![0].title)
        assertSame(en, enIngles.traducirRutinasDeFabrica(en, bib("es")))
    }

    @Test fun unRespaldoViejoSeTraduce() {
        val json = """{"v":2,"sessions":[{"id":"s1","date":"2026-11-20","day":"t1","title":"Upper A"}],"routines":[{"id":"t1","title":"Upper A"}]}"""
        val e = enEspanol.traducirRutinasDeFabrica(enEspanol.normalizar(JsonGym.decodeFromString(Estado.serializer(), json)), bib("en"))
        assertEquals("Torso A", e.sessions.single().title)
        assertEquals("Torso A", e.routines!!.single().title)
    }
}

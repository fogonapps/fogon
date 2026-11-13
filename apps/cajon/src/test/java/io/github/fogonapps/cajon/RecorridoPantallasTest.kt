package io.github.fogonapps.cajon

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.ui.Acciones
import io.github.fogonapps.cajon.ui.App
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.revision.Hallazgo
import io.github.fogonapps.revision.revisar
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El recorrido de todas las pantallas de El Cajón con un cajón de ejemplo: cada una se abre y se revisa (botones
 * pegados o encimados, cosas difíciles de tocar o sin nombre para el lector de pantalla). La pantalla es muy alta para
 * que se vea todo lo de cada una sin desplazarse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w400dp-h3000dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class RecorridoPantallasTest {
    val regla = createComposeRule()
    // La escala debe estar aplicada antes de que la regla de Compose cree la actividad.
    @get:Rule val entorno: org.junit.rules.TestRule = org.junit.rules.RuleChain.outerRule(
        org.junit.rules.TestRule { base, descripcion ->
            object : org.junit.runners.model.Statement() {
                override fun evaluate() {
                    org.robolectric.RuntimeEnvironment.setFontScale(
                        if (descripcion.methodName == "espanolChicoLetraGrande") 1.3f else 1f
                    )
                    try { base.evaluate() } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
                }
            }
        }
    ).around(regla)

    private fun ejemplo() = Estado(
        listo = true,
        obras = listOf(
            Obra("p1", Tipos.PELICULA, "Dune", anio = 2021, creador = "Denis Villeneuve", fuente = "tmdb:movie:438631", estado = Estados.TERMINADO, nota = 8,
                registros = listOf(Registro("g1", "2026-11-01"))),
            Obra("s1", Tipos.SERIE, "Dark", anio = 2017, fuente = "tmdb:tv:70523", estado = Estados.EN_CURSO, temporadas = listOf(Temporada(1, 10))),
            Obra("l1", Tipos.LIBRO, "Cien años de soledad", anio = 1967, creador = "Gabriel García Márquez", fuente = "ol:OL274505W", paginas = 432, pagina = 120, estado = Estados.EN_CURSO),
            Obra("a1", Tipos.ALBUM, "Kind of Blue", anio = 1959, creador = "Miles Davis", estado = Estados.QUIERO),
        ),
        artistas = listOf(Artista("ar1", "Miles Davis")),
        listas = listOf(Lista("li1", "Favoritas", obras = listOf("p1", "l1"))),
    )

    @Test fun ningunaPantallaSeVeMal() = recorrer("inglés, 400 dp, letra normal, oscuro")

    @Test
    @Config(qualifiers = "es-w360dp-h3000dp-xxhdpi")
    fun espanolChicoLetraGrande() = recorrer("español, 360 dp, letra 130 %, oscuro")

    @Test
    @Config(qualifiers = "en-w360dp-h3000dp-xxhdpi")
    fun inglesClaro() = recorrer("inglés, 360 dp, letra normal, claro", apariencia = Apariencia(estilo = Estilo.CLARO))

    private fun recorrer(variante: String, apariencia: Apariencia = Apariencia()) {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        // La guía de primeros pasos, como ya vista (se probaría encima de todo).
        app.getSharedPreferences("guia", android.content.Context.MODE_PRIVATE).edit().putInt("vista.cajon", 99).commit()
        val vm = CajonViewModel(app)
        val acciones = Acciones({}, {}, {}, { _, _ -> })
        regla.setContent { FogonTema(Paleta.Cajon, apariencia) { App(vm, acciones) } }
        // El cajón de ejemplo se pone cuando terminó de cargar el guardado (si no, lo pisaría).
        regla.waitUntil(20_000) { vm.estado.value != null }
        @Suppress("UNCHECKED_CAST")
        val estado = CajonViewModel::class.java.getDeclaredField("_estado").apply { isAccessible = true }.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<Estado?>
        estado.value = ejemplo()
        regla.waitForIdle()
        val hallazgos = mutableListOf<Hallazgo>()
        Pestana.entries.forEach { p ->
            hallazgos += runCatching { vm.irA(p); regla.revisar("pestaña $p", barraAbajo = 140f) }
                .getOrElse { listOf(Hallazgo("pestaña $p", "falla", it.toString())) }
        }
        val pantallas: List<Pantalla> = listOf(
            Pantalla.Buscar(Secciones.TODO), Pantalla.Ficha("p1"), Pantalla.Ficha("s1"), Pantalla.Ficha("l1"), Pantalla.Ficha("a1"),
            Pantalla.CambiarPortada("p1"), Pantalla.EditarObra("p1", Tipos.PELICULA), Pantalla.EditarObra(null, Tipos.LIBRO), Pantalla.Registrar("p1"),
            Pantalla.VerArtista("ar1"), Pantalla.VerLista("li1"), Pantalla.EditarLista("li1"), Pantalla.EditarLista(null), Pantalla.AgregarALista("p1"),
            Pantalla.Diario, Pantalla.Listas, Pantalla.Cifras, Pantalla.Artistas, Pantalla.Retos, Pantalla.Ajustes, Pantalla.CajonEnCuenta,
            Pantalla.AcercaDe, Pantalla.Reporte, Pantalla.ElegirGustos, Pantalla.TusGustos, Pantalla.Perfil, Pantalla.EditarPerfil,
            Pantalla.PortadaPerfil, Pantalla.Manifiesto, Pantalla.MusicaEscuchada, Pantalla.Privacidad, Pantalla.CuentaRed, Pantalla.Red,
            Pantalla.Resenar("p1"), Pantalla.ElegirCancion, Pantalla.AjustesAvisos, Pantalla.PalabrasOcultas, Pantalla.Apariencia, Pantalla.Licencias,
            Pantalla.CifrasDe(Secciones.CINE),
        )
        pantallas.forEach { p ->
            hallazgos += runCatching { vm.irA(Pestana.INICIO); vm.abrir(p); regla.revisar(p.toString().take(60)) }.getOrElse { listOf(Hallazgo(p.toString().take(60), "falla", it.toString().take(200))) }
        }
        println("=== cajon | $variante ===\n" + hallazgos.joinToString("\n").ifEmpty { "Sin problemas" })
        val errores = hallazgos
        assertTrue("Pantallas con problemas ($variante):\n" + errores.joinToString("\n"), errores.isEmpty())
    }
}

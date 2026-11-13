package io.github.fogonapps.perfil

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.*

/** La app proporciona los nombres ya traducidos para el modo propio o ajeno. */
class SeccionPerfil(val id: Int, val icono: Dibujo, val nombreAccesible: String, val contenido: @Composable () -> Unit)

data class ContadorPerfil(val valor: String, val etiqueta: String, val abrir: (() -> Unit)? = null)

data class PerfilVista(
    val nombre: String, val usuario: String, val foto: String, val portada: String,
    val bio: String, val enlaces: List<String>, val privada: Boolean, val relacion: String,
    val contadores: List<ContadorPerfil> = emptyList(), val secciones: List<SeccionPerfil> = emptyList(),
    /** La insignia que da el servidor ("fogon", "verificada" o ""): ver [InsigniaCuenta]. */
    val insignia: String = "",
    /** El fondo del perfil (ver [TemasPerfil]); "" = el de siempre. */
    val tema: String = "",
    /** El marco de la foto (ver [MarcoFoto]); "" = sin marco. */
    val marco: String = "",
)

/** Conserva el encuadre y el avatar de cada fuente mediante huecos, sin depender de imágenes ni datos culturales. */
@Composable
fun PerfilCabecera(vista: PerfilVista, modifier: Modifier = Modifier, nombreSuave: Boolean = false, interlineado: Boolean = false,
    portada: @Composable BoxScope.() -> Unit, foto: @Composable () -> Unit,
    bajoNombre: @Composable ColumnScope.() -> Unit = {},
) {
    val f = FogonTema.actual
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(110.dp).clip(forma(24.dp))
            .tarjetaFogon(forma(24.dp), sombra = false).haloPuntos(f.tinta2.copy(alpha = 0.35f)), content = portada)
        Row(Modifier.padding(start = 28.dp, end = 16.dp, top = 62.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // El marco de la tienda, alrededor de la foto.
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { foto(); MarcoFoto(vista.marco, 96.dp) }
            Column(Modifier.weight(1f).padding(top = 54.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(vista.nombre, Modifier.weight(1f, fill = false), fontFamily = f.titulos, fontSize = 26.sp,
                        lineHeight = if (interlineado) 26.sp * 1.15f else androidx.compose.ui.unit.TextUnit.Unspecified,
                        color = if (nombreSuave) f.textoSuave else f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    InsigniaCuenta(vista.insignia, tamano = 22.dp)
                }
                bajoNombre()
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        vista.contadores.forEach { CifraPerfil(it.valor, it.etiqueta, it.abrir) }
    }
}

@Composable
fun PestanasPerfil(secciones: List<SeccionPerfil>, elegida: Int, alElegir: (Int) -> Unit) {
    val f = FogonTema.actual
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        val ancho = maxWidth
        Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = ancho), horizontalArrangement = Arrangement.SpaceEvenly) {
            secciones.forEach { seccion ->
                val seleccionada = elegida == seccion.id
                Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clip(FormaPildora)
                    .clickable { alElegir(seccion.id) }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Box(Modifier.clip(FormaPildora).background(if (seleccionada) f.tinta1 else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        IconoRiso(seccion.icono, seccion.nombreAccesible, tamano = 22.dp, tinta = if (seleccionada) f.sobreTinta1 else f.textoSuave)
                    }
                }
            }
        }
    }
}

/** El contenido es de la app: el módulo común no conoce obras ni cifras culturales. */
@Composable
fun ContenidoPerfil(vista: PerfilVista, elegida: Int) {
    vista.secciones.firstOrNull { it.id == elegida }?.contenido?.invoke()
}

/** Biografía y enlaces comunes, con las acciones y el indicador propio que aporta la app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerfilInformacion(vista: PerfilVista, enlace: @Composable (String) -> Unit,
    antesBio: @Composable () -> Unit = {}, acciones: @Composable RowScope.() -> Unit,
) {
    val f = FogonTema.actual
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        antesBio()
        if (vista.bio.isNotBlank()) Text(vista.bio, style = MaterialTheme.typography.bodyLarge, color = f.texto)
        if (vista.enlaces.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vista.enlaces.forEach { enlace(it) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = acciones)
}

@Composable
fun PerfilVacio(icono: Dibujo, texto: String) {
    val f = FogonTema.actual
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconoRiso(icono, null, tamano = 32.dp, tinta = f.textoSuave)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
    }
}

/** Estructura única para ambos modos. Las acciones y las secciones pertenecen a la app. */
@Composable
fun Perfil(vista: PerfilVista, elegida: Int, alElegir: (Int) -> Unit,
    modifier: Modifier = Modifier, nombreSuave: Boolean = false, interlineado: Boolean = false,
    portada: @Composable BoxScope.() -> Unit, foto: @Composable () -> Unit,
    bajoNombre: @Composable ColumnScope.() -> Unit = {}, antesBio: @Composable () -> Unit = {},
    enlace: @Composable (String) -> Unit, acciones: @Composable RowScope.() -> Unit,
    despuesAcciones: @Composable () -> Unit = {}, navegacion: @Composable () -> Unit = {},
) {
    val estilo = TemasPerfil.estilo(vista.tema)
    if (estilo == null) {
        navegacion()
        PerfilCabecera(vista, modifier, nombreSuave, interlineado, portada, foto, bajoNombre)
        PerfilInformacion(vista, enlace, antesBio, acciones)
        despuesAcciones()
        if (vista.secciones.isNotEmpty()) {
            PestanasPerfil(vista.secciones, elegida, alElegir)
            ContenidoPerfil(vista, elegida)
        }
        return
    }
    // Con un fondo de la tienda (como en Steam): la escena detrás de toda la página, lo demás en paneles
    // semitransparentes y con los colores del fondo.
    CompositionLocalProvider(LocalFogon provides FogonTema.actual.conEstilo(estilo)) {
        val f = FogonTema.actual
        Box(Modifier.fillMaxWidth()) {
            FondoTemaPerfil(vista.tema, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                navegacion()
                Column(Modifier.padding(horizontal = 10.dp).clip(forma(24.dp)).background(f.superficie).padding(vertical = 10.dp)) {
                    PerfilCabecera(vista, modifier, nombreSuave, interlineado, portada, foto, bajoNombre)
                    PerfilInformacion(vista, enlace, antesBio, acciones)
                    despuesAcciones()
                }
                if (vista.secciones.isNotEmpty()) Column(Modifier.padding(horizontal = 10.dp).clip(forma(24.dp)).background(f.superficie).padding(vertical = 6.dp)) {
                    PestanasPerfil(vista.secciones, elegida, alElegir)
                    ContenidoPerfil(vista, elegida)
                }
            }
        }
    }
}

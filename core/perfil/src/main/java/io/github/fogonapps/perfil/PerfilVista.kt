package io.github.fogonapps.perfil

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val nivel: Int = 0,
    val insigniasNivel: List<String> = emptyList(),
    val abrirNivel: (() -> Unit)? = null,
    val nivelLena: String = "",
    /** Tocar el marco (el borde de la foto): en tu perfil lleva a los marcos; en el ajeno abre «Lo quiero». */
    val tocarMarco: (() -> Unit)? = null,
    /** Lo que dicen los lectores de pantalla para [tocarMarco]. */
    val etiquetaMarco: String = "",
)

/**
 * La cabecera del perfil, compacta como en las redes de fotos: la portada arriba, la foto montada sobre su borde con
 * las cifras en una fila de ancho completo, y debajo el nombre y su información.
 */
@Composable
fun PerfilCabecera(vista: PerfilVista, modifier: Modifier = Modifier, nombreSuave: Boolean = false, interlineado: Boolean = false,
    portada: @Composable BoxScope.() -> Unit, foto: @Composable () -> Unit,
    bajoNombre: @Composable ColumnScope.() -> Unit = {},
) {
    if (Trama.apagada) { PerfilCabeceraLimpia(vista, modifier, nombreSuave, portada, foto, bajoNombre); return }
    val f = FogonTema.actual
    val lena = nivelLena(vista.nivelLena)
    var lenaAbierta by remember(vista.usuario, lena) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(96.dp).clip(forma(18.dp))
                .then(if (Trama.apagada) Modifier.background(f.fondo) else Modifier.tarjetaFogon(forma(18.dp), sombra = false).haloPuntos(f.tinta2.copy(alpha = 0.35f))),
                content = portada)
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // La foto (con el marco de la tienda), un poco más chica que antes.
                Box(Modifier.size(88.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Box(Modifier.graphicsLayer { scaleX = 88f / 106f; scaleY = 88f / 106f }.requiredSize(106.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        foto(); MarcoFoto(vista.marco, 96.dp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            vista.contadores.forEach { CifraPerfil(it.valor, it.etiqueta, it.abrir) }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(vista.nombre, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = if (nombreSuave) f.textoSuave else f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                InsigniaCuenta(vista.insignia, tamano = 16.dp)
                if (lena != null) InsigniaLena(lena, lenaAbierta) { lenaAbierta = !lenaAbierta }
                // El nombre se acorta con «…» antes de empujar el nivel: siempre queda a su lado y sin montarse.
                NivelChico(vista)
            }
            if (lenaAbierta && lena != null) TarjetaDetalleLena(lena)
            bajoNombre()
        }
    }
}

/** Proporción de la portada del perfil con el diseño nuevo (ancho : alto); el editor y el encuadre usan la misma. */
const val PROPORCION_PORTADA = 2.5f

/**
 * La cabecera con el diseño nuevo (la trama apagada): la portada a todo lo ancho, protagonista, con la foto (y su
 * marco) montada sobre su borde; debajo, el nombre grande en la letra de los títulos y el @usuario. Sin portada
 * propia va la predeterminada ([PortadaPredeterminada]), nunca un rectángulo vacío. Las cifras van con la biografía
 * (ver [PerfilInformacion]).
 */
@Composable
private fun PerfilCabeceraLimpia(vista: PerfilVista, modifier: Modifier, nombreSuave: Boolean,
    portada: @Composable BoxScope.() -> Unit, foto: @Composable () -> Unit, bajoNombre: @Composable ColumnScope.() -> Unit,
) {
    val f = FogonTema.actual
    val tamanoFoto = 96.dp
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Alto de la portada por su proporción; la foto baja un poco más de la mitad sobre el papel.
            val alto = maxWidth / PROPORCION_PORTADA
            Box(Modifier.fillMaxWidth().height(alto).clip(androidx.compose.ui.graphics.RectangleShape)) {
                if (vista.portada.isBlank()) PortadaPredeterminada(vista.nombre, Modifier.matchParentSize())
                portada()
            }
            Box(Modifier.padding(start = 16.dp, top = alto - tamanoFoto * 0.45f)) { FotoCabecera(vista, foto, tamanoFoto) }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(vista.nombre, Modifier.weight(1f, fill = false).semantics { heading() }, fontFamily = f.titulos,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp,
                    color = if (nombreSuave) f.textoSuave else f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                InsigniaCuenta(vista.insignia, tamano = 18.dp)
            }
            if (vista.usuario.isNotBlank()) Text("@" + vista.usuario, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            bajoNombre()
        }
    }
}

/**
 * La portada de quien todavía no eligió una: el papel apenas más hondo y la inicial del nombre, enorme y casi
 * transparente, cortada por el borde derecho. Gratis y de todos; el color queda para las portadas de verdad.
 */
@Composable
fun PortadaPredeterminada(nombre: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val inicial = nombre.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()
    val medidor = androidx.compose.ui.text.rememberTextMeasurer()
    val tinta = f.texto.copy(alpha = if (f.oscuro) 0.08f else 0.06f)
    // La inicial se dibuja (no es un texto que se lea): va cortada a propósito por el borde.
    Box(modifier.background(androidx.compose.ui.graphics.lerp(f.fondo, f.texto, if (f.oscuro) 0.07f else 0.045f))
        .clearAndSetSemantics {}.drawBehind {
            if (inicial.isEmpty()) return@drawBehind
            val tamano = (size.height * 1.5f).toSp()
            val medida = medidor.measure(inicial, androidx.compose.ui.text.TextStyle(fontFamily = f.titulos,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = tamano, lineHeight = tamano, color = tinta))
            drawText(medida, topLeft = androidx.compose.ui.geometry.Offset(size.width - medida.size.width * 0.82f,
                (size.height - medida.size.height) / 2f))
        })
}

/** La foto de la cabecera con el marco de la tienda, del tamaño pedido. */
@Composable
private fun FotoCabecera(vista: PerfilVista, foto: @Composable () -> Unit, tamano: androidx.compose.ui.unit.Dp) {
    // El marco se toca en el borde de la foto; el centro sigue siendo de la foto (historias). Lo mira el contenedor,
    // antes que la foto, porque un hermano encima taparía la foto entera.
    val toque = vista.tocarMarco?.takeIf { vista.marco.isNotBlank() }
    Box(Modifier.size(tamano).then(if (toque != null) Modifier.toqueEnElBorde(toque, vista.etiquetaMarco) else Modifier),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        Box(Modifier.graphicsLayer { val s = tamano.value / 106f; scaleX = s; scaleY = s }.requiredSize(106.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            foto(); MarcoFoto(vista.marco, 96.dp)
        }
    }
}

/**
 * Un toque cerca del borde de un círculo (fuera del [interior], en proporción al radio) dispara [accion]; el resto
 * pasa a lo que esté debajo. Para los lectores de pantalla queda como acción con [etiqueta].
 */
private fun Modifier.toqueEnElBorde(accion: () -> Unit, etiqueta: String, interior: Float = 0.72f): Modifier =
    pointerInput(accion) {
        awaitEachGesture {
            val abajo = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val centro = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            if ((abajo.position - centro).getDistance() < size.width / 2f * interior) return@awaitEachGesture
            abajo.consume()
            val arriba = waitForUpOrCancellation(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (arriba != null) { arriba.consume(); accion() }
        }
    }.semantics { customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(etiqueta) { accion(); true }) }

@Composable
fun PestanasPerfil(secciones: List<SeccionPerfil>, elegida: Int, alElegir: (Int) -> Unit) {
    val f = FogonTema.actual
    // Como en las redes de fotos: íconos livianos y una raya bajo la elegida.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val ancho = maxWidth
        Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = ancho), horizontalArrangement = Arrangement.SpaceEvenly) {
            secciones.forEach { seccion ->
                val seleccionada = elegida == seccion.id
                Column(Modifier.sizeIn(minWidth = 48.dp, minHeight = 44.dp).clickable { alElegir(seccion.id) }.padding(horizontal = 8.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 10.dp, bottom = if (Trama.apagada) 2.dp else 10.dp)) {
                        IconoRiso(seccion.icono, if (Trama.apagada) null else seccion.nombreAccesible, tamano = 22.dp, tinta = if (seleccionada) f.texto else f.textoSuave)
                    }
                    // Con el diseño nuevo, el nombre se lee debajo del ícono (cuatro íconos solos no se entendían).
                    if (Trama.apagada) Text(seccion.nombreAccesible, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = if (seleccionada) f.texto else f.textoSuave, maxLines = 1, modifier = Modifier.padding(bottom = 8.dp))
                    Box(Modifier.width(32.dp).height(2.dp).background(if (seleccionada) f.texto else Color.Transparent))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(f.borde).background(f.linea))
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
    Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(if (Trama.apagada) 8.dp else 6.dp)) {
        antesBio()
        // Con el diseño nuevo la biografía se lee un poco más grande, como el texto de una reseña.
        if (vista.bio.isNotBlank()) Text(vista.bio, style = if (Trama.apagada) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium, color = f.texto)
        if (vista.enlaces.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vista.enlaces.forEach { enlace(it) }
        }
    }
    // Con el diseño nuevo las cifras van aquí, después de la biografía y antes de los botones.
    if (Trama.apagada && vista.contadores.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top) {
        vista.contadores.forEach { CifraPerfil(it.valor, it.etiqueta, it.abrir) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = if (Trama.apagada) 20.dp else 16.dp, vertical = if (Trama.apagada) 14.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (Trama.apagada) 10.dp else 6.dp), content = acciones)
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
    // Con un fondo de la tienda (como en Steam): la escena detrás de toda la página, lo demás en paneles casi opacos
    // (la escena se asoma sin meterse en el texto) y con los colores del fondo; botones y tarjetas van encima.
    CompositionLocalProvider(LocalFogon provides FogonTema.actual.conEstilo(estilo)) {
        val f = FogonTema.actual
        // Al menos el alto de la pantalla: con poco contenido, el fondo igual la llena.
        Box(Modifier.fillMaxWidth().heightIn(min = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp)) {
            FondoTemaPerfil(vista.tema, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                navegacion()
                val panel = estilo.base.copy(alpha = 0.85f)
                Column(Modifier.padding(horizontal = 10.dp).clip(forma(24.dp)).background(panel).padding(vertical = 10.dp)) {
                    PerfilCabecera(vista, modifier, nombreSuave, interlineado, portada, foto, bajoNombre)
                    PerfilInformacion(vista, enlace, antesBio, acciones)
                    despuesAcciones()
                }
                if (vista.secciones.isNotEmpty()) Column(Modifier.padding(horizontal = 10.dp).clip(forma(24.dp)).background(panel).padding(vertical = 6.dp)) {
                    PestanasPerfil(vista.secciones, elegida, alElegir)
                    ContenidoPerfil(vista, elegida)
                }
            }
        }
    }
}

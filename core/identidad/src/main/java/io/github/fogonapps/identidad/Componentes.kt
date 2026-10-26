package io.github.fogonapps.identidad

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tarjetas: esquinas grandes y suaves. */
val FormaRiso: Shape
    @Composable @ReadOnlyComposable get() = forma(24.dp)

/** Botones: píldora. */
val FormaPildora: Shape
    @Composable @ReadOnlyComposable get() = RoundedCornerShape(50)

/** Una esquina redondeada de [radio]. */
@Composable @ReadOnlyComposable
fun forma(radio: Dp): Shape = RoundedCornerShape(radio)

/** Una forma redonda pero un poco torcida, como una gota. Se usa en las fichas de las categorías. */
val FormaMancha: Shape = GenericShape { tam, _ ->
    val sx = tam.width / 100f; val sy = tam.height / 100f
    moveTo(52f * sx, 4f * sy)
    cubicTo(72f * sx, 5f * sy, 92f * sx, 17f * sy, 95f * sx, 38f * sy)
    cubicTo(98f * sx, 57f * sy, 89f * sx, 78f * sy, 70f * sx, 89f * sy)
    cubicTo(53f * sx, 98f * sy, 31f * sx, 96f * sy, 17f * sx, 83f * sy)
    cubicTo(3f * sx, 71f * sy, (-2f) * sx, 50f * sy, 6f * sx, 32f * sy)
    cubicTo(13f * sx, 14f * sy, 31f * sx, 3f * sy, 52f * sx, 4f * sy)
    close()
}

/** Antes era la tinta corrida detrás de las formas; en el vidrio no dibuja nada (queda por compatibilidad). */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
@Composable
fun Modifier.brillo(color: Color, forma: Shape = FormaRiso, intensidad: Dp = 18.dp): Modifier = this

/** Una sombra plana de la misma forma, corrida abajo a la derecha. */
fun Modifier.sombraDura(forma: Shape, desplazamiento: Dp = 4.dp, color: Color = Tintas.Tinta): Modifier = drawBehind {
    val d = desplazamiento.toPx()
    translate(d, d) { drawOutline(forma.createOutline(size, layoutDirection, this), color) }
}

/** Tarjeta de vidrio: blanco con poca opacidad sobre el papel y un borde fino. [sombra] ya no hace nada. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.tarjetaFogon(forma: Shape = FormaRiso, sombra: Boolean = true): Modifier {
    val f = FogonTema.actual
    return clip(forma).background(f.superficie).border(f.borde, f.linea, forma)
}

/** La tarjeta destacada de cada pantalla: un bloque lleno de la primera tinta. Lo de encima va en [Fogon.sobreTinta1]. */
@Composable
fun Modifier.tarjetaDestacada(forma: Shape = FormaRiso): Modifier {
    val f = FogonTema.actual
    return clip(forma).background(f.tinta1)
}

/**
 * La luz del tema: un círculo suave de [Fogon.brillo] arriba de la pantalla, que se apaga hacia afuera. Es
 * la única luz de cada pantalla; con "Menos adornos" o en Contraste no hay.
 */
fun Modifier.decoracion(f: Fogon): Modifier = if (f.brillo.alpha <= 0f) this else drawWithCache {
    val centro = Offset(size.width * 0.5f, -size.width * 0.15f)
    val radio = size.width * 1.05f
    val pincel = Brush.radialGradient(listOf(f.brillo, f.brillo.copy(alpha = 0f)), centro, radio)
    onDrawBehind { drawRect(pincel) }
}

/** El fondo de cada pantalla: el papel del tema y su luz. */
fun Modifier.fondoFogon(f: Fogon): Modifier = background(f.fondo).decoracion(f)

/**
 * El halo de puntos: una trama que se apaga hacia el borde, detrás del número grande. Es la trama riso de
 * Fogón convertida en luz. [centro] en fracciones del ancho y del alto.
 */
fun Modifier.haloPuntos(color: Color, paso: Dp = 10.dp, centro: Offset = Offset(0.35f, 0.45f)): Modifier = drawWithCache {
    val p = paso.toPx(); val r = p * 0.15f
    val ancho = size.width; val alto = size.height
    val c = Offset(ancho * centro.x, alto * centro.y)
    val alcance = maxOf(ancho, alto) * 0.6f
    val puntos = buildList {
        var y = p / 2
        while (y < alto) {
            var x = p / 2
            while (x < ancho) {
                val t = 1f - (Offset(x, y) - c).getDistance() / alcance
                if (t > 0f) add(Offset(x, y) to t)
                x += p
            }
            y += p
        }
    }
    onDrawBehind { puntos.forEach { (o, t) -> drawCircle(color.copy(alpha = color.alpha * 0.55f * t), r, o) } }
}

/** Un número o palabra grande, hecho de puntos (Doto), con las cifras que ruedan al cambiar. [fantasma] queda por compatibilidad. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun NumeroRiso(
    texto: String,
    tamano: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = FogonTema.actual.numero,
    familia: FontFamily = FogonTema.actual.puntos,
    alto: TextUnit = tamano * 0.95f,
    fantasma: Boolean = true,
    maxLineas: Int = 1,
) {
    if (maxLineas > 1) Text(texto, modifier, fontFamily = familia, fontSize = tamano, lineHeight = alto, color = color, maxLines = maxLineas)
    else CifrasRodantes(texto, familia, tamano, color, modifier, alto)
}

/** Botón principal: una píldora llena de la primera tinta. */
@Composable
fun BotonRiso(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val f = FogonTema.actual
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = FormaPildora,
        colors = ButtonDefaults.buttonColors(containerColor = f.tinta1, contentColor = f.sobreTinta1),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier.rebote().heightIn(min = 50.dp),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

/** Botón secundario: una píldora de vidrio con borde fino. */
@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    peligro: Boolean = false,
) {
    val f = FogonTema.actual
    val color = if (peligro) MaterialTheme.colorScheme.error else f.texto
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        shape = FormaPildora,
        border = BorderStroke(f.borde, if (peligro) color.copy(alpha = 0.6f) else f.linea),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = f.superficie, contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.rebote().heightIn(min = 50.dp),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

/** Título de sección en la letra de los títulos. */
@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleLarge,
        color = FogonTema.actual.texto,
        modifier = modifier.semantics { heading() },
    )
}

/** Etiqueta pequeña, con la tinta de la app. */
@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = FogonTema.actual.acentoTexto,
        modifier = modifier,
    )
}

/** Cifra grande, en la letra de las cifras. */
@Composable
fun Cifra(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = FogonTema.actual.textoSuave)
        Text(valor, style = MaterialTheme.typography.displaySmall, color = FogonTema.actual.texto)
    }
}

/** Los personajes hechos con el muñeco (identidad/muneco): el cuerpo en semitono y la cabeza (la chispa). */
private val CAPAS = mapOf(
    R.drawable.personaje_el_forzudo to (R.drawable.personaje_el_forzudo_cuerpo to R.drawable.personaje_el_forzudo_cabeza),
    R.drawable.personaje_el_ahorrador to (R.drawable.personaje_el_ahorrador_cuerpo to R.drawable.personaje_el_ahorrador_cabeza),
    R.drawable.personaje_la_cucha to (R.drawable.personaje_la_cucha_cuerpo to R.drawable.personaje_la_cucha_cabeza),
    R.drawable.personaje_el_cajon to (R.drawable.personaje_el_cajon_cuerpo to R.drawable.personaje_el_cajon_cabeza),
)

/**
 * El personaje de la app. Si es del muñeco (identidad/muneco), toma los colores del tema: el cuerpo en las
 * letras y la cabeza en la tinta (ver [PersonajeFogon]).
 */
@Composable
fun Personaje(@DrawableRes recurso: Int, descripcion: String?, modifier: Modifier = Modifier, tamano: Dp = 160.dp) {
    val capas = CAPAS[recurso]
    if (capas == null) {
        Image(painterResource(recurso), contentDescription = descripcion, modifier = modifier.size(tamano))
        return
    }
    PersonajeFogon(capas.first, capas.second, descripcion, modifier, tamano)
}

/**
 * La tinta de la cabeza del muñeco: el color propio de la app (también en los temas neutros, como Grafito), o
 * la del tema si ese color no se distingue del fondo.
 */
fun tintaCabeza(f: Fogon): Color = when {
    contraste(f.marca, f.fondo) >= 2.0 -> f.marca
    contraste(f.tinta1, f.fondo) >= 2.0 -> f.tinta1
    else -> f.acentoTexto
}

/**
 * El muñeco de Fogón (identidad/muneco): el mismo cuerpo hace la acción de cada app. El cuerpo, en semitono,
 * va en el color de las letras del tema (oscuro en los claros, claro en los oscuros) y la cabeza, la chispa, en
 * la tinta de la app.
 */
@Composable
fun PersonajeFogon(
    @DrawableRes cuerpo: Int, @DrawableRes cabeza: Int,
    descripcion: String?, modifier: Modifier = Modifier, tamano: Dp = 160.dp,
) {
    val f = FogonTema.actual
    val sem = if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier
    Box(modifier.size(tamano).then(sem)) {
        Image(painterResource(cuerpo), null, Modifier.matchParentSize(), colorFilter = ColorFilter.tint(f.texto))
        Image(painterResource(cabeza), null, Modifier.matchParentSize(), colorFilter = ColorFilter.tint(tintaCabeza(f)))
    }
}

/**
 * Una ilustración 3D (identidad/vidrio/ilustraciones): viene en gris y se tiñe con [tinta], así la misma imagen
 * sirve en todos los temas. Las luces del gris quedan como brillos de la tinta.
 */
@Composable
fun Ilustracion(@DrawableRes recurso: Int, modifier: Modifier = Modifier, tamano: Dp = 120.dp, tinta: Color = FogonTema.actual.tintaIlustracion) {
    // Cada canal = el gris por la tinta; a las tintas oscuras se les suma algo de gris para que no pierdan
    // las luces y se siga viendo el volumen.
    val extra = (1f - tinta.luminance()) * 0.35f
    fun c(v: Float) = v * 1.3f + extra
    val matriz = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        c(tinta.red), 0f, 0f, 0f, 0f,
        0f, c(tinta.green), 0f, 0f, 0f,
        0f, 0f, c(tinta.blue), 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
    Image(painterResource(recurso), null, modifier.aparecerConRebote().size(tamano), colorFilter = ColorFilter.colorMatrix(matriz))
}

@Composable
fun Chispa(modifier: Modifier = Modifier, tamano: Dp = 18.dp, descripcion: String? = null) {
    Image(painterResource(R.drawable.chispa), contentDescription = descripcion, modifier = modifier.size(tamano))
}

/** Pantalla vacía con el personaje de la app. */
@Composable
fun EstadoVacio(@DrawableRes personaje: Int, titulo: String, texto: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Personaje(personaje, null, tamano = 140.dp)
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = FogonTema.actual.texto)
        Text(texto, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = FogonTema.actual.textoSuave)
    }
}

/** Marca de logro: la chispa junto a un texto. */
@Composable
fun Logro(texto: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chispa(tamano = 20.dp)
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = FogonTema.actual.texto)
    }
}

/** Pantalla "Acerca de", igual en todas las apps de Fogón. */
@Composable
fun AcercaDeFogon(
    @DrawableRes personaje: Int,
    nombreApp: String,
    nombrePersonaje: String,
    version: String,
    creditos: List<String>,
    onVerManifiesto: () -> Unit,
    onVerLicencias: () -> Unit,
    onAbrirEnlace: (String) -> Unit,
    modifier: Modifier = Modifier,
    avisoSalud: String? = null,
    avisoTitulo: String? = null,
) {
    val f = FogonTema.actual
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Personaje(personaje, nombrePersonaje, tamano = 150.dp)
        Text(nombreApp, style = MaterialTheme.typography.headlineMedium, color = f.texto)
        Text(if (nombrePersonaje == nombreApp) stringResource(R.string.fogon_version, version) else "$nombrePersonaje · " + stringResource(R.string.fogon_version, version), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        Image(
            painterResource(if (f.oscuro) R.drawable.fogon_logotipo_claro else R.drawable.fogon_logotipo),
            contentDescription = stringResource(R.string.fogon_hecha),
            modifier = Modifier.padding(top = 8.dp).size(width = 180.dp, height = 82.dp),
        )
        Text(stringResource(R.string.fogon_hecha), style = MaterialTheme.typography.titleLarge, color = f.texto)
        Text(
            stringResource(R.string.fogon_sin_anuncios),
            style = MaterialTheme.typography.labelLarge, color = f.acentoTexto, textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.fogon_lema),
            style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, textAlign = TextAlign.Center,
        )
        BotonRiso(stringResource(R.string.fogon_leer_manifiesto), onVerManifiesto)
        BotonSecundario(stringResource(R.string.fogon_licencias), onVerLicencias)
        URL_CODIGO?.let { url -> BotonSecundario(stringResource(R.string.fogon_ver_codigo), { onAbrirEnlace(url) }) }
        creditos.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center)
        }
        Text(
            stringResource(R.string.fogon_licencia_gpl),
            style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center,
        )
        avisoSalud?.let {
            Text(avisoTitulo ?: stringResource(R.string.fogon_aviso_salud), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(it, style = MaterialTheme.typography.bodyMedium, color = f.texto, textAlign = TextAlign.Center)
        }
    }
}

/** Dirección del código público (null si una app todavía no lo tiene). */
val URL_CODIGO: String? = "https://github.com/fogonapps/fogon"

/** Correo de Fogón para reportes y sugerencias (null = las apps solo ofrecen GitHub). */
val CORREO_FOGON: String? = "fogonapps@gmail.com"

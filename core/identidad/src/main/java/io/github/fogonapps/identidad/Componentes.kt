package io.github.fogonapps.identidad

import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tarjetas: esquinas grandes y suaves, o casi rectas si la Apariencia lo pide. */
val FormaRiso: Shape
    @Composable @ReadOnlyComposable get() = forma(22.dp)

/** Botones: píldora (o casi rectos en el estilo Rebelde y con esquinas rectas). */
val FormaPildora: Shape
    @Composable @ReadOnlyComposable get() = if (FogonTema.actual.rectas) RoundedCornerShape(4.dp) else RoundedCornerShape(50)

/** Una esquina redondeada de [radio] que respeta la Apariencia: con esquinas rectas se queda en 4 dp. */
@Composable @ReadOnlyComposable
fun forma(radio: Dp): Shape = RoundedCornerShape(if (FogonTema.actual.rectas) minOf(radio, 4.dp) else radio)

/**
 * Brillo de color alrededor de una forma, como una luz que se escapa por los bordes. Cambia con el
 * estilo: en Riso es una sombra dura de tinta corrida; en Rebelde y Contraste, y con "menos
 * movimiento", no hay brillo. En Android 8 el brillo sale como sombra gris.
 */
@Composable
fun Modifier.brillo(color: Color, forma: Shape = FormaRiso, intensidad: Dp = 18.dp): Modifier {
    val f = FogonTema.actual
    return when {
        f.sombraDura -> sombraDura(forma)
        f.conBrillo -> resplandor(color, forma, intensidad)
        else -> this
    }
}

/**
 * El brillo dibujado a mano: la misma forma, cada vez un poco más grande y más transparente.
 * No usa las sombras de elevación de Android (en Android 8 salen grises y en el emulador tumbaban
 * el gráfico), así que se ve igual en todos los teléfonos.
 */
fun Modifier.resplandor(color: Color, forma: Shape, intensidad: Dp): Modifier = drawBehind {
    val capas = 6
    val total = intensidad.toPx() * 0.6f
    for (i in capas downTo 1) {
        val d = total * i / capas
        val tam = androidx.compose.ui.geometry.Size(size.width + d * 2, size.height + d * 2)
        translate(-d, -d) {
            drawOutline(forma.createOutline(tam, layoutDirection, this), color.copy(alpha = color.alpha * 0.16f * (1f - (i - 1f) / capas)))
        }
    }
}

/** Sombra riso: la misma forma en tinta, corrida abajo a la derecha. */
fun Modifier.sombraDura(forma: Shape, desplazamiento: Dp = 4.dp, color: Color = Tintas.Tinta): Modifier = drawBehind {
    val d = desplazamiento.toPx()
    translate(d, d) { drawOutline(forma.createOutline(size, layoutDirection, this), color) }
}

/** Tarjeta de la identidad: fondo de tarjeta, borde y, en Riso, sombra dura. */
@Composable
fun Modifier.tarjetaFogon(forma: Shape = FormaRiso): Modifier {
    val f = FogonTema.actual
    return then(if (f.sombraDura) Modifier.sombraDura(forma) else Modifier)
        .clip(forma).background(f.superficie).border(f.borde, f.linea, forma)
}

/**
 * Lo que dibuja cada estilo detrás de las pantallas, con los dos colores de degradado
 * ([Paleta.segunda] y [Paleta.luz2]) y el acento:
 * - Noche: orbes difuminados (uno grande arriba a la derecha, otro abajo a la izquierda);
 * - Aura: un orbe cromático grande arriba y uno cálido abajo;
 * - Riso: un círculo de trama de puntos (semitono) en la segunda tinta;
 * - Rebelde: rayos de cómic que salen de arriba a la derecha.
 */
fun Modifier.decoracion(f: Fogon): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val p = f.paleta
    val puntos = if (f.decoracion == Decoracion.SEMITONO) {
        val paso = 9.dp.toPx(); val r = 2.6.dp.toPx()
        val c = Offset(w * 0.92f, h * 0.16f); val radio = w * 0.42f
        buildList {
            var y = c.y - radio
            while (y < c.y + radio) {
                var x = c.x - radio
                while (x < c.x + radio) { if ((Offset(x, y) - c).getDistance() < radio) add(Offset(x, y)); x += paso }
                y += paso
            }
        } to r
    } else null
    val rayos = if (f.decoracion == Decoracion.RAYOS) {
        val c = Offset(w * 0.78f, h * 0.2f); val largo = maxOf(w, h) * 1.5f
        (0 until 24).map { i ->
            val a0 = Math.toRadians(i * 15.0); val a1 = Math.toRadians(i * 15.0 + 4.0)
            Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + (largo * kotlin.math.cos(a0)).toFloat(), c.y + (largo * kotlin.math.sin(a0)).toFloat())
                lineTo(c.x + (largo * kotlin.math.cos(a1)).toFloat(), c.y + (largo * kotlin.math.sin(a1)).toFloat())
                close()
            }
        }
    } else emptyList()
    onDrawBehind {
        when (f.decoracion) {
            Decoracion.ORBES -> {
                orbe(Offset(w * 0.95f, h * 0.05f), w * 0.85f, listOf(p.tinta.copy(alpha = 0.32f), p.segunda.copy(alpha = 0.5f), p.luz2.copy(alpha = 0.22f)))
                orbe(Offset(0f, h * 0.62f), w * 0.8f, listOf(p.luz2.copy(alpha = 0.28f)))
            }
            Decoracion.AURA -> {
                orbe(Offset(w * 0.55f, h * 0.18f), w * 0.7f, listOf(p.luz2.copy(alpha = 0.8f), p.segunda.copy(alpha = 0.6f), Tintas.Rosa.copy(alpha = 0.3f)))
                orbe(Offset(0f, h * 0.8f), w * 0.6f, listOf(Tintas.Naranja.copy(alpha = 0.35f)))
            }
            Decoracion.SEMITONO -> puntos?.let { (lista, r) -> lista.forEach { drawCircle(p.luz2.copy(alpha = 0.5f), r, it) } }
            Decoracion.RAYOS -> rayos.forEach { drawPath(it, p.segunda.copy(alpha = 0.22f)) }
            Decoracion.NADA -> Unit
        }
    }
}

/**
 * Un orbe difuminado: círculos concéntricos de color sólido, del borde al centro, que van tomando los
 * [colores] (del centro hacia afuera) y se desvanecen hacia el borde. Se dibuja así, y no con un degradado
 * radial, porque el degradado grande junto con el grano tumbaba el gráfico del emulador.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.orbe(centro: Offset, radio: Float, colores: List<Color>) {
    val pasos = 64
    for (k in 0 until pasos) {
        val t = k / (pasos - 1f) // 0 = borde, 1 = centro
        val r = radio * (1f - t * 0.97f)
        val pos = (1f - t) * (colores.size) // de qué color va: el centro toma el primero
        val i = pos.toInt().coerceIn(0, colores.size - 1)
        val c = if (i + 1 < colores.size) androidx.compose.ui.graphics.lerp(colores[i], colores[i + 1], pos - i) else colores[i]
        // Cada círculo suma un poco: el borde queda casi transparente y el centro con el color pedido.
        drawCircle(c.copy(alpha = c.alpha * 2.2f / pasos), r, centro)
    }
}

/** El fondo de cada pantalla: el color de fondo del estilo, su decoración y el grano. */
fun Modifier.fondoFogon(f: Fogon): Modifier = background(f.fondo).decoracion(f).granoPapel(f.oscuro, f.grano)

private const val GRANO_AGSL = """
uniform float intensidad;
uniform float claro;
half4 main(float2 p) {
    float n = fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453);
    if (claro > 0.5) {
        half v = half(1.0 - intensidad * n);
        return half4(v, v, v, 1.0);
    }
    half w = half(intensidad * n);
    return half4(w, w, w, 1.0);
}
"""

/** Grano de papel (Android 13+). En versiones anteriores, o con intensidad 0, no hace nada. */
fun Modifier.granoPapel(oscuro: Boolean, intensidad: Float = 0.07f): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || intensidad <= 0f) this
    else drawWithCache {
        val shader = RuntimeShader(GRANO_AGSL)
        shader.setFloatUniform("intensidad", intensidad)
        shader.setFloatUniform("claro", if (oscuro) 0f else 1f)
        val pincel = ShaderBrush(shader)
        onDrawWithContent {
            drawContent()
            drawRect(pincel, blendMode = if (oscuro) BlendMode.Screen else BlendMode.Multiply)
        }
    }

/** Botón principal: del acento, con su brillo (o su sombra dura en Riso). */
@Composable
fun BotonRiso(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val f = FogonTema.actual
    val p = f.paleta
    val forma = FormaPildora
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = forma,
        colors = ButtonDefaults.buttonColors(containerColor = p.tinta, contentColor = p.sobreTinta),
        border = if (f.borde > 1.dp) BorderStroke(f.borde, f.linea) else null,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier
            .heightIn(min = 50.dp)
            .then(if (habilitado) Modifier.brillo(p.tinta.copy(alpha = 0.6f), forma, 14.dp) else Modifier),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

/** Botón secundario: con el fondo de las tarjetas y contorno. */
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
        border = BorderStroke(if (f.borde > 1.dp) f.borde else 1.5.dp, if (peligro) color else if (f.borde > 1.dp) f.linea else f.texto.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = f.superficie, contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 50.dp),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

/** Título de sección en Bagel Fat One. */
@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleLarge,
        color = FogonTema.actual.texto,
        modifier = modifier.semantics { heading() },
    )
}

/** Etiqueta pequeña en mayúsculas, con la tinta de la app. */
@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FogonTema.actual.acentoTexto,
        modifier = modifier,
    )
}

/** Cifra grande en Anton. */
@Composable
fun Cifra(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = FogonTema.actual.textoSuave)
        Text(valor, style = MaterialTheme.typography.displaySmall, color = FogonTema.actual.texto)
    }
}

@Composable
fun Personaje(@DrawableRes recurso: Int, descripcion: String?, modifier: Modifier = Modifier, tamano: Dp = 160.dp) {
    Image(painterResource(recurso), contentDescription = descripcion, modifier = modifier.size(tamano))
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

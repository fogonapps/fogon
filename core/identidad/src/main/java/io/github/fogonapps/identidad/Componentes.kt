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
 * La mancha de tinta: una forma redonda pero torcida, como una gota que cayó en el papel. Marca la pestaña
 * activa y el botón del perfil (en lugar del círculo perfecto de siempre).
 */
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

/**
 * Antes era un brillo de color; en el sistema riso es la segunda tinta corrida detrás de la forma, como
 * cuando la imprenta no calza las dos pasadas. [color] e [intensidad] se ignoran (quedan por compatibilidad).
 * Con corrimiento 0 (Contraste) no dibuja nada.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.brillo(color: Color, forma: Shape = FormaRiso, intensidad: Dp = 18.dp): Modifier {
    val f = FogonTema.actual
    return if (f.desfase > 0.dp) sombraDura(forma, f.desfase, f.tinta2) else this
}

/** La sombra riso: la misma forma, en [color], corrida abajo a la derecha. */
fun Modifier.sombraDura(forma: Shape, desplazamiento: Dp = 4.dp, color: Color = Tintas.Tinta): Modifier = drawBehind {
    val d = desplazamiento.toPx()
    translate(d, d) { drawOutline(forma.createOutline(size, layoutDirection, this), color) }
}

/** Tarjeta de la identidad: papel de tarjeta, borde de tinta y, si [sombra], la segunda tinta corrida detrás. */
@Composable
fun Modifier.tarjetaFogon(forma: Shape = FormaRiso, sombra: Boolean = true): Modifier {
    val f = FogonTema.actual
    return then(if (sombra && f.desfase > 0.dp) Modifier.sombraDura(forma, f.desfase, f.tinta2) else Modifier)
        .clip(forma).background(f.superficie).border(f.borde, f.linea, forma)
}

/**
 * La tarjeta destacada de cada pantalla: un bloque lleno de la primera tinta, con su borde y la segunda
 * tinta bien corrida detrás. Lo de encima va en [Fogon.sobreTinta1].
 */
@Composable
fun Modifier.tarjetaDestacada(forma: Shape = FormaRiso): Modifier {
    val f = FogonTema.actual
    return then(if (f.desfase > 0.dp) Modifier.sombraDura(forma, f.desfase * 2, f.tinta2) else Modifier)
        .clip(forma).background(f.tinta1).border(f.borde, f.linea, forma)
}

/** El destello de cuatro puntas del estilo Chicle (24×24). */
private const val DESTELLO = "M12 1.5c.8 6.2 3.9 9.6 10.5 10.5-6.6.9-9.7 4.3-10.5 10.5-.8-6.2-3.9-9.6-10.5-10.5C8.1 11.1 11.2 7.7 12 1.5z"

/** Dónde van los destellos: x (fracción del ancho), y (dp desde arriba), tamaño (dp), si van en la primera tinta. */
private val DESTELLOS = listOf(
    listOf(0.08f, 210f, 22f, 1f), listOf(0.85f, 70f, 16f, 0f), listOf(0.46f, 120f, 12f, 1f),
    listOf(0.9f, 300f, 20f, 1f), listOf(0.05f, 630f, 14f, 0f), listOf(0.91f, 640f, 12f, 1f),
)

/**
 * Lo que imprime cada estilo detrás de las pantallas (nunca degradados):
 * - Trama (Noche y Riso): un círculo de puntos en la segunda tinta, gordos al centro y finos al borde,
 *   como el semitono de una riso;
 * - Rayos (Rebelde): rayos de cómic en la primera tinta, que se van apagando;
 * - Destellos (Chicle): la trama y destellos de cuatro puntas en las dos tintas.
 */
fun Modifier.decoracion(f: Fogon): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val conTrama = f.decoracion == Decoracion.TRAMA || f.decoracion == Decoracion.ESTRELLAS
    val trama = if (conTrama) {
        val paso = (if (f.oscuro) 10 else 9).dp.toPx()
        val radio = 145.dp.toPx()
        val c = Offset(w - 70.dp.toPx(), minOf(h * 0.22f, 175.dp.toPx()))
        val maximo = paso * 0.46f
        buildList {
            var y = c.y - radio
            while (y < c.y + radio) {
                var x = c.x - radio
                while (x < c.x + radio) {
                    val t = 1f - (Offset(x, y) - c).getDistance() / radio
                    if (t > 0.04f) add(Offset(x, y) to maximo * kotlin.math.sqrt(minOf(1f, t * 1.6f)))
                    x += paso
                }
                y += paso
            }
        }
    } else emptyList()
    val rayos = if (f.decoracion == Decoracion.RAYOS) {
        val c = Offset(w * 0.74f, minOf(h * 0.24f, 190.dp.toPx())); val largo = 300.dp.toPx()
        val tramos = 8
        (0 until 24).flatMap { i ->
            val a0 = Math.toRadians(i * 15.0); val a1 = Math.toRadians(i * 15.0 + 3.0)
            (0 until tramos).map { k ->
                val r0 = largo * k / tramos; val r1 = largo * (k + 1) / tramos
                val alfa = if (k < 2) 0.5f else 0.5f * (1f - (k - 1f) / (tramos - 1f))
                fun p(r: Float, a: Double) = Offset(c.x + (r * kotlin.math.cos(a)).toFloat(), c.y + (r * kotlin.math.sin(a)).toFloat())
                Path().apply {
                    val q0 = p(r0, a0); moveTo(q0.x, q0.y)
                    p(r1, a0).let { lineTo(it.x, it.y) }; p(r1, a1).let { lineTo(it.x, it.y) }; p(r0, a1).let { lineTo(it.x, it.y) }
                    close()
                } to alfa
            }
        }
    } else emptyList()
    val destellos = if (f.decoracion == Decoracion.ESTRELLAS) {
        val base = PathParser().parsePathString(DESTELLO).toPath()
        DESTELLOS.map { (x, y, t, primera) ->
            val escala = t.dp.toPx() / 24f
            Path().apply {
                addPath(base)
                transform(androidx.compose.ui.graphics.Matrix().apply { scale(escala, escala) })
                translate(Offset(w * x, minOf(y.dp.toPx(), h - t.dp.toPx())))
            } to (if (primera > 0f) f.tinta1 else f.tinta2)
        }
    } else emptyList()
    onDrawBehind {
        // Suave, para que las letras que caigan encima se sigan leyendo.
        trama.forEach { (o, r) -> drawCircle(f.tinta2.copy(alpha = 0.6f), r, o) }
        rayos.forEach { (camino, alfa) -> drawPath(camino, f.tinta1.copy(alpha = alfa)) }
        destellos.forEach { (camino, c) -> drawPath(camino, c) }
    }
}

/** El fondo de cada pantalla: el papel del estilo, su adorno impreso y el grano. */
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

/**
 * Un número o palabra gigante impreso en dos pasadas: la segunda tinta corrida detrás y [color] encima.
 * El corrimiento crece con el tamaño de la letra. [fantasma] = false lo deja en una sola tinta.
 */
@Composable
fun NumeroRiso(
    texto: String,
    tamano: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = FogonTema.actual.numero,
    familia: FontFamily = Anton,
    alto: TextUnit = tamano * 0.86f,
    fantasma: Boolean = true,
    maxLineas: Int = 1,
) {
    val f = FogonTema.actual
    val corre = f.desfase * (tamano.value / 108f)
    Box(modifier) {
        if (fantasma && corre > 0.dp) Text(
            texto, fontFamily = familia, fontSize = tamano, lineHeight = alto, color = f.tinta2, maxLines = maxLineas, softWrap = maxLineas > 1,
            modifier = Modifier.offset(corre * 1.1f, corre).clearAndSetSemantics { },
        )
        Text(texto, fontFamily = familia, fontSize = tamano, lineHeight = alto, color = color, maxLines = maxLineas, softWrap = maxLineas > 1)
    }
}

/** Botón principal: de la primera tinta, con borde de tinta y la segunda tinta corrida detrás. */
@Composable
fun BotonRiso(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val f = FogonTema.actual
    val forma = FormaPildora
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = forma,
        colors = ButtonDefaults.buttonColors(containerColor = f.tinta1, contentColor = f.sobreTinta1),
        border = BorderStroke(f.borde, f.linea),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier
            .heightIn(min = 50.dp)
            .then(if (habilitado) Modifier.brillo(f.tinta1, forma) else Modifier),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

/** Botón secundario: papel de tarjeta con borde de tinta. */
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
        border = BorderStroke(f.borde, if (peligro) color else f.linea),
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

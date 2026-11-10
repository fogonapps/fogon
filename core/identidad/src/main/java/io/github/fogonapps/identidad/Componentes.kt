package io.github.fogonapps.identidad

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.res.imageResource
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
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
    val parametros = parametrosLuz(f)
    val centro = Offset(size.width * parametros.centroX, size.width * parametros.centroY)
    val radio = size.width * parametros.radio
    val pincel = Brush.radialGradient(parametros.colores, centro, radio)
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

/** Los personajes de Fogón: uno por app. */
enum class Quien { FORZUDO, AHORRADOR, CUCHA, CAJON }

/**
 * Las caras de los personajes. Cada momento de la app tiene la suya: [DORMIDO] en los vacíos, [FELIZ] en los
 * logros, [SORPRESA] en los récords, [ENAMORADO] en lo favorito, [TRISTE] cuando algo no se pudo, [MIEDO], [DECIDIDO]
 * antes de empezar y [GUINO] cuando algo quedó listo. [PARPADEO] (los ojos cerrados en una rayita) es solo para parpadear.
 */
enum class Cara { NORMAL, FELIZ, DORMIDO, SORPRESA, ENAMORADO, TRISTE, MIEDO, DECIDIDO, GUINO, PARPADEO }

/** Las imágenes de cada personaje con cada cara. */
private val IMAGENES: Map<Quien, Map<Cara, Int>> = mapOf(
    Quien.FORZUDO to mapOf(
        Cara.NORMAL to R.drawable.personaje_forzudo_normal,
        Cara.FELIZ to R.drawable.personaje_forzudo_feliz,
        Cara.DORMIDO to R.drawable.personaje_forzudo_dormido,
        Cara.SORPRESA to R.drawable.personaje_forzudo_sorpresa,
        Cara.ENAMORADO to R.drawable.personaje_forzudo_enamorado,
        Cara.TRISTE to R.drawable.personaje_forzudo_triste,
        Cara.MIEDO to R.drawable.personaje_forzudo_miedo,
        Cara.DECIDIDO to R.drawable.personaje_forzudo_decidido,
        Cara.GUINO to R.drawable.personaje_forzudo_guino,
        Cara.PARPADEO to R.drawable.personaje_forzudo_parpadeo,
    ),
    Quien.AHORRADOR to mapOf(
        Cara.NORMAL to R.drawable.personaje_ahorrador_normal,
        Cara.FELIZ to R.drawable.personaje_ahorrador_feliz,
        Cara.DORMIDO to R.drawable.personaje_ahorrador_dormido,
        Cara.SORPRESA to R.drawable.personaje_ahorrador_sorpresa,
        Cara.ENAMORADO to R.drawable.personaje_ahorrador_enamorado,
        Cara.TRISTE to R.drawable.personaje_ahorrador_triste,
        Cara.MIEDO to R.drawable.personaje_ahorrador_miedo,
        Cara.DECIDIDO to R.drawable.personaje_ahorrador_decidido,
        Cara.GUINO to R.drawable.personaje_ahorrador_guino,
        Cara.PARPADEO to R.drawable.personaje_ahorrador_parpadeo,
    ),
    Quien.CUCHA to mapOf(
        Cara.NORMAL to R.drawable.personaje_cucha_normal,
        Cara.FELIZ to R.drawable.personaje_cucha_feliz,
        Cara.DORMIDO to R.drawable.personaje_cucha_dormido,
        Cara.SORPRESA to R.drawable.personaje_cucha_sorpresa,
        Cara.ENAMORADO to R.drawable.personaje_cucha_enamorado,
        Cara.TRISTE to R.drawable.personaje_cucha_triste,
        Cara.MIEDO to R.drawable.personaje_cucha_miedo,
        Cara.DECIDIDO to R.drawable.personaje_cucha_decidido,
        Cara.GUINO to R.drawable.personaje_cucha_guino,
        Cara.PARPADEO to R.drawable.personaje_cucha_parpadeo,
    ),
    Quien.CAJON to mapOf(
        Cara.NORMAL to R.drawable.personaje_cajon_normal,
        Cara.FELIZ to R.drawable.personaje_cajon_feliz,
        Cara.DORMIDO to R.drawable.personaje_cajon_dormido,
        Cara.SORPRESA to R.drawable.personaje_cajon_sorpresa,
        Cara.ENAMORADO to R.drawable.personaje_cajon_enamorado,
        Cara.TRISTE to R.drawable.personaje_cajon_triste,
        Cara.MIEDO to R.drawable.personaje_cajon_miedo,
        Cara.DECIDIDO to R.drawable.personaje_cajon_decidido,
        Cara.GUINO to R.drawable.personaje_cajon_guino,
        Cara.PARPADEO to R.drawable.personaje_cajon_parpadeo,
    ),
)

@DrawableRes fun imagenPersonaje(quien: Quien, cara: Cara = Cara.NORMAL): Int = IMAGENES.getValue(quien).getValue(cara)

/**
 * El personaje de la app con la [cara] del momento: blanco, con su sombra y sus ojitos. En los temas claros lleva
 * detrás un círculo suave del color de la app, para que no se pierda sobre el papel claro.
 *
 * Está vivo: aparece con un rebote, respira (sube y baja muy poquito), parpadea cada tanto y, al cambiar de cara,
 * pasa de una a otra con un fundido. Con "Menos movimiento" se queda quieto.
 */
@Composable
fun Personaje(quien: Quien, descripcion: String?, modifier: Modifier = Modifier, cara: Cara = Cara.NORMAL, tamano: Dp = 160.dp) {
    val f = FogonTema.actual
    val quieto = f.menosMovimiento
    // El parpadeo: los ojos cerrados (una rayita) un instante, cada 3 a 6 segundos, de golpe y sin fundido. No en las
    // caras que ya tienen los ojos cerrados (ni en La Cucha, que siempre los tiene en arquito).
    var parpado by remember { mutableStateOf(false) }
    val cerrados = cara in setOf(Cara.DORMIDO, Cara.FELIZ, Cara.GUINO, Cara.PARPADEO) || (quien == Quien.CUCHA && cara == Cara.NORMAL)
    if (!quieto && !cerrados) {
        LaunchedEffect(cara) {
            while (true) {
                kotlinx.coroutines.delay(3000L + (kotlin.random.Random.nextFloat() * 3000).toLong())
                parpado = true; kotlinx.coroutines.delay(130); parpado = false
            }
        }
    }
    val entrada = remember { androidx.compose.animation.core.Animatable(if (quieto) 1f else 0.6f) }
    LaunchedEffect(Unit) { if (entrada.value < 1f) entrada.animateTo(1f, resorteAlegre()) }
    val respira = androidx.compose.animation.core.rememberInfiniteTransition(label = "respira")
    val r by respira.animateFloat(
        0f, if (quieto) 0f else 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween<Float>(1800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "respira",
    )
    Box(modifier.size(tamano), contentAlignment = Alignment.Center) {
        if (!f.oscuro) Box(Modifier.fillMaxWidth(0.86f).aspectRatio(1f).background(f.marca.copy(alpha = 0.28f), androidx.compose.foundation.shape.CircleShape))
        androidx.compose.animation.Crossfade(cara, Modifier.matchParentSize().graphicsLayer {
            scaleX = entrada.value * (1f - r * 0.015f); scaleY = entrada.value * (1f + r * 0.03f)
            translationY = -r * tamano.toPx() * 0.015f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }, animationSpec = androidx.compose.animation.core.tween(220), label = "cara") { c ->
            Image(painterResource(imagenPersonaje(quien, if (parpado && c == cara) Cara.PARPADEO else c)), contentDescription = descripcion, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * La tarjeta del personaje, la principal de cada app: a la izquierda lo importante (la cifra en puntos, una línea,
 * una píldora) y a la derecha el personaje en su propio lugar, centrado de alto. Los textos nunca le pasan por
 * encima: tienen su columna y el personaje la suya. Sobre la trama de puntos del tema.
 */
@Composable
fun TarjetaPersonaje(
    quien: Quien, cara: Cara, modifier: Modifier = Modifier, tamano: Dp = 96.dp, celebrar: Boolean = false,
    onClick: (() -> Unit)? = null, personaje: Boolean = true,
    contenido: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val f = FogonTema.actual
    val toque = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).tarjetaFogon(forma(28.dp))
            .haloPuntos(f.tinta2.copy(alpha = if (f.oscuro) 0.6f else 0.4f), centro = Offset(0.25f, 0.45f))
            .then(toque).padding(start = 20.dp, top = 18.dp, bottom = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = contenido)
        // En el inicio el personaje vive en el círculo del perfil (X-07); aquí sale solo para celebrar.
        if (celebrar) Celebracion(quien, null, tamano = tamano) else if (personaje) Personaje(quien, null, cara = cara, tamano = tamano)
    }
}

/**
 * El círculo del perfil, arriba a la derecha del inicio (siempre el último): la foto de la persona si tiene, o la carita
 * de la mascota de la app con su ánimo (X-07).
 */
@Composable
fun BotonPerfilMascota(quien: Quien, descripcion: String, cara: Cara = Cara.NORMAL, foto: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val f = FogonTema.actual
    Box(
        Modifier.rebote(0.9f).size(48.dp).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = descripcion; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { if (foto != null) foto() else Personaje(quien, null, cara = cara, tamano = 40.dp) }
}

/** La celebración de cada personaje: 24 cuadros en una tira (salen de Blender). */
private val TIRAS = mapOf(
    Quien.FORZUDO to R.drawable.celebrar_forzudo, Quien.AHORRADOR to R.drawable.celebrar_ahorrador,
    Quien.CUCHA to R.drawable.celebrar_cucha, Quien.CAJON to R.drawable.celebrar_cajon,
)
private const val CUADROS = 24

/**
 * El personaje celebra cuando terminas algo: salta con la cara feliz (El Forzudo levanta los bracitos, El Cajón da
 * una vuelta en el aire) un par de veces y después se queda feliz. Con "Menos movimiento", solo feliz.
 */
@Composable
fun Celebracion(quien: Quien, descripcion: String?, modifier: Modifier = Modifier, tamano: Dp = 96.dp, veces: Int = 2) {
    val f = FogonTema.actual
    var termino by remember { mutableStateOf(f.menosMovimiento) }
    if (termino) { Personaje(quien, descripcion, modifier, cara = Cara.FELIZ, tamano = tamano); return }
    val tira = androidx.compose.ui.graphics.ImageBitmap.imageResource(TIRAS.getValue(quien))
    var cuadro by remember { mutableStateOf(0) }
    LaunchedEffect(quien) {
        repeat(veces) { for (k in 0 until CUADROS) { cuadro = k; kotlinx.coroutines.delay(45) } }
        termino = true
    }
    val lado = tira.width / CUADROS
    val pintor = remember(cuadro, tira) {
        androidx.compose.ui.graphics.painter.BitmapPainter(tira, androidx.compose.ui.unit.IntOffset(cuadro * lado, 0), androidx.compose.ui.unit.IntSize(lado, tira.height))
    }
    Box(modifier.size(tamano), contentAlignment = Alignment.Center) {
        if (!f.oscuro) Box(Modifier.fillMaxWidth(0.86f).aspectRatio(1f).background(f.marca.copy(alpha = 0.28f), androidx.compose.foundation.shape.CircleShape))
        // Cada cuadro es más alto que ancho (aire arriba para el salto): el cuadro de abajo coincide con el personaje.
        val alto = tira.height.toFloat() / lado
        Image(pintor, descripcion, Modifier.requiredSize(tamano, tamano * alto).offset(y = -tamano * (alto - 1f) / 2f))
    }
}

/**
 * Una ilustración 3D: viene en gris y se tiñe con [tinta], así la misma imagen
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

/** Pantalla vacía con el personaje de la app, dormido. */
@Composable
fun EstadoVacio(quien: Quien, titulo: String, texto: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Personaje(quien, null, cara = Cara.DORMIDO, tamano = 90.dp)
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
    quien: Quien,
    nombreApp: String,
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
        Personaje(quien, nombreApp, cara = Cara.FELIZ, tamano = 96.dp)
        Text(nombreApp, style = MaterialTheme.typography.headlineMedium, color = f.texto)
        Text(stringResource(R.string.fogon_version, version), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
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

/** Elegir una opción entre varias, como píldoras (la elegida, en la tinta del tema). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> SelectorPildoras(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    androidx.compose.foundation.layout.FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEach { (v, t) ->
            androidx.compose.material3.FilterChip(v == elegido, { onElegir(v) }, { androidx.compose.material3.Text(t) }, shape = FormaPildora,
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = f.paleta.tinta, selectedLabelColor = f.paleta.sobreTinta))
        }
    }
}

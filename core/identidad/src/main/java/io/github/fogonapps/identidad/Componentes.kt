package io.github.fogonapps.identidad

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

/** Tarjetas: esquinas grandes y suaves. */
val FormaRiso = RoundedCornerShape(22.dp)

/** Botones: píldora. */
val FormaPildora = RoundedCornerShape(50)

/**
 * Brillo de color alrededor de una forma, como una luz que se escapa por los bordes.
 * Reemplaza a la sombra riso del estilo de día. En Android 8 sale como sombra gris.
 */
fun Modifier.brillo(color: Color, forma: Shape = FormaRiso, intensidad: Dp = 18.dp): Modifier =
    shadow(intensidad, forma, clip = false, ambientColor = color, spotColor = color)

/**
 * Las luces difuminadas del fondo, con los colores de la app: una grande arriba a la izquierda
 * ([Paleta.segunda]) y otra a la derecha, más abajo ([Paleta.luz2]).
 */
fun Modifier.luces(p: Paleta): Modifier = drawBehind {
    val w = size.width; val h = size.height
    drawRect(Brush.radialGradient(
        listOf(p.segunda.copy(alpha = 0.55f), p.segunda.copy(alpha = 0.18f), Color.Transparent),
        center = Offset(w * 0.05f, h * 0.06f), radius = w * 1.05f,
    ))
    drawRect(Brush.radialGradient(
        listOf(p.luz2.copy(alpha = 0.32f), Color.Transparent),
        center = Offset(w * 1.0f, h * 0.58f), radius = w * 0.8f,
    ))
}

/** El fondo de cada pantalla: el negro de la app, sus luces y un grano muy sutil (el guiño riso). */
fun Modifier.fondoFogon(f: Fogon): Modifier = background(f.fondo).luces(f.paleta).granoPapel(f.oscuro, 0.035f)

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

/** Grano de papel muy sutil (Android 13+). En versiones anteriores no hace nada. */
fun Modifier.granoPapel(oscuro: Boolean, intensidad: Float = 0.07f): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) this
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

/** Botón principal: píldora del acento de la app, con su brillo. */
@Composable
fun BotonRiso(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val p = FogonTema.actual.paleta
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = FormaPildora,
        colors = ButtonDefaults.buttonColors(containerColor = p.tinta, contentColor = p.sobreTinta),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier
            .heightIn(min = 50.dp)
            .then(if (habilitado) Modifier.brillo(p.tinta.copy(alpha = 0.6f), FormaPildora, 14.dp) else Modifier),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

/** Botón secundario: píldora de vidrio con contorno claro. */
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
        border = BorderStroke(1.5.dp, if (peligro) color else f.texto.copy(alpha = 0.35f)),
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

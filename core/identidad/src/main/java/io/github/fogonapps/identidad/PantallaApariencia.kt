package io.github.fogonapps.identidad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Estilo.nombre get() = when (this) {
    Estilo.NOCHE -> R.string.fogon_estilo_noche
    Estilo.RISO -> R.string.fogon_estilo_riso
    Estilo.REBELDE -> R.string.fogon_estilo_rebelde
    Estilo.AURA -> R.string.fogon_estilo_aura
    Estilo.CONTRASTE -> R.string.fogon_estilo_contraste
}

val Estilo.explicacion get() = when (this) {
    Estilo.NOCHE -> R.string.fogon_estilo_noche_texto
    Estilo.RISO -> R.string.fogon_estilo_riso_texto
    Estilo.REBELDE -> R.string.fogon_estilo_rebelde_texto
    Estilo.AURA -> R.string.fogon_estilo_aura_texto
    Estilo.CONTRASTE -> R.string.fogon_estilo_contraste_texto
}

val Ranura.nombre get() = when (this) {
    Ranura.FONDO -> R.string.fogon_color_fondo
    Ranura.TARJETAS -> R.string.fogon_color_tarjetas
    Ranura.TEXTO -> R.string.fogon_color_texto
    Ranura.TEXTO_SUAVE -> R.string.fogon_color_texto_suave
    Ranura.ACENTO -> R.string.fogon_color_acento
    Ranura.SOBRE_ACENTO -> R.string.fogon_color_sobre_acento
    Ranura.DEGRADADO_1 -> R.string.fogon_color_degradado_1
    Ranura.DEGRADADO_2 -> R.string.fogon_color_degradado_2
    Ranura.NUMERO -> R.string.fogon_color_numero
    Ranura.BORDES -> R.string.fogon_color_bordes
}

/** Los colores que se ofrecen de una vez: las tintas riso y unos neutros. */
private val MUESTRAS = listOf(
    Tintas.Rojo, Tintas.Naranja, Tintas.Amarillo, Tintas.Lima, Tintas.Verde, Tintas.Cian, Tintas.Azul, Tintas.Violeta, Tintas.Rosa,
    Color(0xFFF03E33), Color(0xFF5CE1E6), Color(0xFF9D7BFF), Color(0xFFFF7A5C), Color(0xFFE2B33C),
    Color.White, Tintas.Papel, Color(0xFFF1F0EE), Color(0xFFB9B5C0), Tintas.Gris, Color(0xFF2E2823), Color(0xFF0C0A12), Tintas.Tinta, Color.Black,
)

/**
 * Perfil → Apariencia, igual en todas las apps. Los cambios se ven al momento en toda la app, porque
 * la Activity escucha [AlmacenApariencia.estado]. Cada app pone su barra de arriba.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaApariencia(paleta: Paleta, almacen: AlmacenApariencia, modifier: Modifier = Modifier) {
    val a by almacen.estado.collectAsState()
    val f = FogonTema.actual
    val e = a.estilo
    var editando by remember { mutableStateOf<Ranura?>(null) }

    Column(modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        VistaPrevia()

        Encabezado(stringResource(R.string.fogon_ap_estilo))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Estilo.entries.forEach { s ->
                MuestraEstilo(fogonDe(paleta, a.copy(estilo = s)), stringResource(s.nombre), s == e, Modifier.weight(1f)) { almacen.elegirEstilo(s) }
            }
        }
        Text(stringResource(e.explicacion), style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        Encabezado(stringResource(R.string.fogon_ap_colores, stringResource(e.nombre)))
        val poco = contraste(f.texto, f.fondo) < 4.5 || contraste(f.paleta.sobreTinta, f.paleta.tinta) < 4.5
        if (poco) Text(stringResource(R.string.fogon_ap_poco_contraste), style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Ranura.entries.forEach { r ->
                val c = a.color(paleta, r)
                val cambiado = r in a.de().colores
                Row(
                    Modifier.fillMaxWidth().tarjetaFogon(forma(18.dp)).clickable { editando = r }.heightIn(min = 52.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(c).border(1.dp, f.texto.copy(alpha = 0.4f), CircleShape))
                    Text(stringResource(r.nombre), color = f.texto, modifier = Modifier.weight(1f))
                    if (cambiado) Text(stringResource(R.string.fogon_ap_cambiado), style = MaterialTheme.typography.labelSmall, color = f.acentoTexto)
                    Text(hex(c), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                }
            }
        }

        Encabezado(stringResource(R.string.fogon_ap_transparencia))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fogon_ap_transparencia_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
                Text("${a.transparencia()} %", fontFamily = Anton, fontSize = 18.sp, color = f.acentoTexto)
            }
            var t by remember(e, a.transparencia()) { mutableFloatStateOf(a.transparencia().toFloat()) }
            Slider(t, { t = it }, onValueChangeFinished = { almacen.ponerTransparencia(e, t.toInt()) }, valueRange = 0f..100f, steps = 19)
        }

        Encabezado(stringResource(R.string.fogon_ap_textura))
        Opciones(Grano.entries.map { it to stringResource(when (it) { Grano.NADA -> R.string.fogon_ap_nada; Grano.SUAVE -> R.string.fogon_ap_suave; Grano.FUERTE -> R.string.fogon_ap_fuerte }) },
            a.grano()) { almacen.ponerGrano(e, it) }

        Encabezado(stringResource(R.string.fogon_ap_esquinas))
        Opciones(listOf(false to stringResource(R.string.fogon_ap_redondas), true to stringResource(R.string.fogon_ap_rectas)), a.rectas()) { almacen.ponerRectas(e, it) }

        BotonSecundario(stringResource(R.string.fogon_ap_restablecer, stringResource(e.nombre)), { almacen.restablecer(e) },
            Modifier.fillMaxWidth().padding(16.dp))

        Encabezado(stringResource(R.string.fogon_ap_comodidad))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().tarjetaFogon(forma(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.fogon_ap_tamano_texto), color = f.texto)
                val nombres = listOf(R.string.fogon_ap_pequeno, R.string.fogon_ap_normal, R.string.fogon_ap_grande, R.string.fogon_ap_muy_grande).map { stringResource(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    nombres.forEachIndexed { i, n ->
                        val elegido = a.tamanoTexto == i
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(forma(14.dp)).background(if (elegido) f.pildora else f.superficie)
                                .border(1.dp, f.linea, forma(14.dp)).clickable { almacen.ponerTamanoTexto(i) }
                                .semantics { contentDescription = n; selected = elegido; role = Role.RadioButton },
                            contentAlignment = Alignment.Center,
                        ) { Text("A", fontWeight = FontWeight.Bold, fontSize = listOf(13, 16, 20, 24)[i].sp, color = if (elegido) f.sobrePildora else f.texto) }
                    }
                }
            }
            Interruptor(stringResource(R.string.fogon_ap_menos_movimiento), stringResource(R.string.fogon_ap_menos_movimiento_texto), a.menosMovimiento, almacen::ponerMenosMovimiento)
            Interruptor(stringResource(R.string.fogon_ap_mas_contraste), stringResource(R.string.fogon_ap_mas_contraste_texto), a.masContraste, almacen::ponerMasContraste)
        }
        Text(stringResource(R.string.fogon_ap_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }

    editando?.let { r ->
        DialogoColor(stringResource(r.nombre), a.color(paleta, r), e.colorBase(paleta, r),
            onListo = { almacen.ponerColor(e, r, it); editando = null }, onCerrar = { editando = null })
    }
}

@Composable
private fun Encabezado(texto: String) {
    Text(texto.uppercase(), fontFamily = Anton, fontSize = 18.sp, letterSpacing = 0.06.sp * 10, color = FogonTema.actual.texto,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp).semantics { heading() })
}

/** La vista previa: un número gigante, una tarjeta y el botón, con los colores en uso. */
@Composable
private fun VistaPrevia() {
    val f = FogonTema.actual
    Box(Modifier.fillMaxWidth().padding(16.dp).height(170.dp).tarjetaFogon(forma(26.dp)).fondoFogon(f)) {
        Text(stringResource(R.string.fogon_ap_vista_previa).uppercase(), style = MaterialTheme.typography.labelSmall, color = f.textoSuave,
            modifier = Modifier.padding(14.dp))
        Text("12", fontFamily = Anton, fontSize = 120.sp, lineHeight = 120.sp, color = f.numero, modifier = Modifier.padding(start = 12.dp, top = 22.dp))
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).width(170.dp).tarjetaFogon(forma(18.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.fogon_ap_muestra_titulo), fontFamily = Bagel, fontSize = 18.sp, color = f.texto)
            Text(stringResource(R.string.fogon_ap_muestra_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            Box(Modifier.brillo(f.paleta.tinta.copy(alpha = 0.6f), FormaPildora, 10.dp).clip(FormaPildora).background(f.paleta.tinta)
                .padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(stringResource(R.string.fogon_ap_muestra_boton), style = MaterialTheme.typography.labelLarge, color = f.paleta.sobreTinta)
            }
        }
    }
}

/** Miniatura de un estilo, con sus propios colores. */
@Composable
private fun MuestraEstilo(m: Fogon, nombre: String, elegido: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val f = FogonTema.actual
    Column(
        modifier.clip(forma(16.dp)).border(if (elegido) 2.dp else 1.dp, if (elegido) f.acentoTexto else f.linea, forma(16.dp))
            .clickable(onClick = onClick).semantics { selected = elegido; role = Role.RadioButton }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(if (m.rectas) 2.dp else 10.dp)).background(m.fondo)) {
            Box(Modifier.offset(18.dp, (-10).dp).size(44.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(m.paleta.segunda, m.paleta.luz2, m.fondo))))
            Box(Modifier.align(Alignment.BottomStart).padding(5.dp).size(width = 26.dp, height = 10.dp)
                .clip(RoundedCornerShape(if (m.rectas) 1.dp else 5.dp)).background(m.paleta.tinta))
            Text("A", fontFamily = Anton, fontSize = 16.sp, color = m.numero, modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp))
        }
        Text(nombre, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Opciones(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit) {
    val f = FogonTema.actual
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (v, t) ->
            FilterChip(v == elegido, { onElegir(v) }, { Text(t) }, shape = FormaPildora,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.pildora, selectedLabelColor = f.sobrePildora, labelColor = f.texto))
        }
    }
}

@Composable
private fun Interruptor(titulo: String, pista: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(
        Modifier.fillMaxWidth().tarjetaFogon(forma(18.dp)).clickable { onCambio(!valor) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(titulo, color = f.texto, fontWeight = FontWeight.Medium)
            Text(pista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        Switch(valor, onCambio)
    }
}

fun hex(c: Color): String = "#%06X".format(c.toArgb() and 0xFFFFFF)

private fun deHex(s: String): Color? = s.trim().removePrefix("#").takeIf { it.length == 6 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
    ?.let { Color(0xFF000000 or it.toLong(16)) }

/** Elegir un color: muestras, tono / saturación / brillo, o su código. "Por defecto" vuelve al del estilo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoColor(titulo: String, inicial: Color, base: Color, onListo: (Color?) -> Unit, onCerrar: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(inicial.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val actual = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    var codigo by remember(actual) { mutableStateOf(hex(actual)) }
    fun poner(c: Color) {
        val x = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), x); h = x[0]; s = x[1]; v = x[2]
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(actual).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)))
                    OutlinedTextField(codigo, { t -> codigo = t.take(7); deHex(t)?.let { poner(it) } }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.fogon_ap_codigo)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(base) + MUESTRAS).distinct().forEach { c ->
                        val nombre = hex(c)
                        Box(Modifier.size(34.dp).clip(CircleShape).background(c)
                            .border(if (c == actual) 3.dp else 1.dp, if (c == actual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { poner(c) }.semantics { contentDescription = nombre; selected = c == actual })
                    }
                }
                Barra(stringResource(R.string.fogon_ap_tono), h / 360f, Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })) { h = it * 360f }
                Barra(stringResource(R.string.fogon_ap_saturacion), s, Brush.horizontalGradient(listOf(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 0f, v))), Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, v)))))) { s = it }
                Barra(stringResource(R.string.fogon_ap_brillo), v, Brush.horizontalGradient(listOf(
                    Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f)))))) { v = it }
            }
        },
        confirmButton = { TextButton({ onListo(actual) }) { Text(stringResource(R.string.fogon_ap_listo), fontWeight = FontWeight.Bold) } },
        dismissButton = {
            Row {
                TextButton({ onListo(null) }) { Text(stringResource(R.string.fogon_ap_por_defecto)) }
                TextButton(onCerrar) { Text(stringResource(R.string.fogon_ap_cancelar)) }
            }
        },
    )
}

/** Una barra de color con su deslizador encima. */
@Composable
private fun Barra(nombre: String, valor: Float, pincel: Brush, onCambio: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(nombre, style = MaterialTheme.typography.labelMedium)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(pincel))
            Slider(valor, onCambio, Modifier.semantics { stateDescription = "${(valor * 100).toInt()} %" })
        }
    }
}

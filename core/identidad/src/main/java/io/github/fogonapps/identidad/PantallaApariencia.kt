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

/** El nombre del tema: el propio de la app se llama como en su [Paleta] (Gimnasio, Vino…). */
@Composable
fun Estilo.nombre(paleta: Paleta): String = stringResource(when (this) {
    Estilo.GRAFITO -> R.string.fogon_estilo_grafito
    Estilo.MARCA -> paleta.nombreTema
    Estilo.CLARO -> R.string.fogon_estilo_claro
    Estilo.CONTRASTE -> R.string.fogon_estilo_contraste
})

val Estilo.explicacion get() = when (this) {
    Estilo.GRAFITO -> R.string.fogon_estilo_grafito_texto
    Estilo.MARCA -> R.string.fogon_estilo_marca_texto
    Estilo.CLARO -> R.string.fogon_estilo_claro_texto
    Estilo.CONTRASTE -> R.string.fogon_estilo_contraste_texto
}

val Ranura.nombre get() = when (this) {
    Ranura.FONDO -> R.string.fogon_color_fondo
    Ranura.TINTA_1 -> R.string.fogon_color_tinta_1
    Ranura.TINTA_2 -> R.string.fogon_color_tinta_2
}

/** Los colores que se ofrecen en "otro color". */
private val MUESTRAS = listOf(
    Color(0xFFEDEDEF), Color(0xFFCDEB3A), Color(0xFFF2384D), Color(0xFF6E8BFF), Color(0xFFFFB547), Color(0xFF3DD9A0), Color(0xFFB98CFF), Color(0xFFFF7A1A),
    Color(0xFF5E5E66), Color(0xFF5C7A12), Color(0xFFF5B547), Color(0xFF2F4AA8), Color(0xFF1F8A62), Color(0xFF7A4FD0), Color(0xFFDAD6D0), Color(0xFF19181A),
    Color(0xFF0E0E10), Color(0xFF0C0D09), Color(0xFF0D0809), Color(0xFF0B0F1A), Color(0xFFF3F2EE), Color(0xFFEFF1F4), Color.White, Color.Black,
)

/** Las muestras rápidas de papel y de cada tinta: pocas y buenas. */
private val PAPELES = listOf(Color(0xFF0E0E10), Color(0xFF0C0D09), Color(0xFF0D0809), Color(0xFF0B0F1A), Color(0xFF12100C), Color(0xFFF3F2EE), Color(0xFFEFF1F4), Color(0xFFF4EEE8))
private val PRIMERAS = listOf(Color(0xFFEDEDEF), Color(0xFFCDEB3A), Color(0xFFF2384D), Color(0xFF6E8BFF), Color(0xFFFFB547), Color(0xFF3DD9A0), Color(0xFFB98CFF), Color(0xFF19181A))
private val SEGUNDAS = listOf(Color(0xFF5E5E66), Color(0xFF5C7A12), Color(0xFFF5B547), Color(0xFF2F4AA8), Color(0xFFE0663A), Color(0xFF1F8A62), Color(0xFF7A4FD0), Color(0xFFDAD6D0))

/**
 * Perfil → Apariencia, igual en todas las apps. Los cambios se ven al momento en toda la app, porque
 * la Activity escucha [AlmacenApariencia.estado]. Cada app pone su barra de arriba. Con [conEstilo] = false
 * no muestra la vista previa ni los temas (el paso "¡Dale vida!" del tutorial ya los tiene arriba).
 */
@Composable
fun PantallaApariencia(paleta: Paleta, almacen: AlmacenApariencia, modifier: Modifier = Modifier, conEstilo: Boolean = true) {
    val a by almacen.estado.collectAsState()
    val f = FogonTema.actual
    val e = a.estilo
    var editando by remember { mutableStateOf<Ranura?>(null) }

    Column(modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        if (conEstilo) {
            VistaPrevia(paleta)
            Encabezado(stringResource(R.string.fogon_ap_estilo))
            SelectorEstilos(paleta, almacen)
        }

        Encabezado(stringResource(R.string.fogon_ap_tintas))
        Text(stringResource(R.string.fogon_ap_tintas_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        listOf(Ranura.FONDO to PAPELES, Ranura.TINTA_1 to PRIMERAS, Ranura.TINTA_2 to SEGUNDAS).forEach { (r, lista) ->
            FilaMuestras(stringResource(r.nombre), a.color(paleta, r), (listOf(e.colorBase(paleta, r)) + lista).distinct(),
                onElegir = { almacen.ponerColor(e, r, if (it == e.colorBase(paleta, r)) null else it) }, onOtro = { editando = r })
        }

        Encabezado(stringResource(R.string.fogon_ap_transparencia))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fogon_ap_transparencia_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
                Text("${a.transparencia()} %", style = MaterialTheme.typography.titleMedium, color = f.acentoTexto)
            }
            var t by remember(e, a.transparencia()) { mutableFloatStateOf(a.transparencia().toFloat()) }
            Slider(t, { t = it }, onValueChangeFinished = { almacen.ponerTransparencia(e, t.toInt()) }, valueRange = 0f..100f, steps = 19)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Interruptor(stringResource(R.string.fogon_ap_luz), stringResource(R.string.fogon_ap_luz_texto), a.adorno() == Decoracion.LUZ) {
                almacen.ponerAdorno(e, if (it) Decoracion.LUZ else Decoracion.NADA)
            }
        }

        BotonSecundario(stringResource(R.string.fogon_ap_restablecer, e.nombre(paleta)), { almacen.restablecer(e) },
            Modifier.fillMaxWidth().padding(16.dp))

        Encabezado(stringResource(R.string.fogon_ap_comodidad))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().tarjetaFogon(forma(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.fogon_ap_tamano_texto), color = f.texto)
                val nombres = listOf(R.string.fogon_ap_pequeno, R.string.fogon_ap_normal, R.string.fogon_ap_grande, R.string.fogon_ap_muy_grande).map { stringResource(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    nombres.forEachIndexed { i, n ->
                        val elegido = a.tamanoTexto == i
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(FormaPildora).background(if (elegido) f.tinta1 else f.superficie)
                                .border(f.borde, f.linea, FormaPildora).clickable { almacen.ponerTamanoTexto(i) }
                                .semantics { contentDescription = n; selected = elegido; role = Role.RadioButton },
                            contentAlignment = Alignment.Center,
                        ) { Text("A", fontWeight = FontWeight.Bold, fontSize = listOf(13, 16, 20, 24)[i].sp, color = if (elegido) f.sobreTinta1 else f.texto) }
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

/** Los cuatro temas en dos filas de dos, cada uno con su papel, su luz y sus tintas, y la explicación del elegido. */
@Composable
fun SelectorEstilos(paleta: Paleta, almacen: AlmacenApariencia, modifier: Modifier = Modifier) {
    val a by almacen.estado.collectAsState()
    val f = FogonTema.actual
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Estilo.entries.chunked(2).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { s ->
                    MuestraEstilo(fogonDe(paleta, a.copy(estilo = s)), s.nombre(paleta), s == a.estilo, Modifier.weight(1f)) { almacen.elegirEstilo(s) }
                }
            }
        }
        Text(stringResource(a.estilo.explicacion), style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/**
 * El último paso del tutorial: las apps vienen en Grafito y aquí la persona elige su tema. Se ve al momento
 * en toda la app. "Más opciones" abre lo demás de la Apariencia (tintas, vidrio, texto, comodidad).
 */
@Composable
fun PasoDaleVida(paleta: Paleta, almacen: AlmacenApariencia, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    var mas by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.fogon_dale_vida), style = MaterialTheme.typography.headlineMedium, color = f.texto,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() })
        Text(stringResource(R.string.fogon_dale_vida_texto), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave,
            modifier = Modifier.padding(horizontal = 20.dp))
        SelectorEstilos(paleta, almacen)
        TextButton({ mas = !mas }, Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(if (mas) R.string.fogon_menos_opciones else R.string.fogon_mas_opciones), color = f.acentoTexto, fontWeight = FontWeight.Bold)
        }
        if (mas) PantallaApariencia(paleta, almacen, conEstilo = false)
    }
}

@Composable
private fun Encabezado(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleLarge, color = FogonTema.actual.texto,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp).semantics { heading() })
}

/** Una fila de muestras de un color (papel o tinta): círculos y uno más para elegir cualquier otro. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaMuestras(titulo: String, actual: Color, lista: List<Color>, onElegir: (Color) -> Unit, onOtro: () -> Unit) {
    val f = FogonTema.actual
    val elegidoTexto = stringResource(R.string.fogon_ap_elegido)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge, color = f.texto)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val enLista = actual in lista
            (if (enLista) lista else listOf(actual) + lista).forEach { c ->
                val elegido = c == actual
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c).border(if (elegido) 3.dp else 1.dp, if (elegido) f.texto else f.linea, CircleShape)
                        .clickable { onElegir(c) }
                        .semantics { contentDescription = hex(c) + if (elegido) ", $elegidoTexto" else ""; selected = elegido; role = Role.RadioButton },
                )
            }
            val otro = stringResource(R.string.fogon_ap_otro_color)
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(f.superficie).border(1.dp, f.linea, CircleShape).clickable(onClick = onOtro)
                    .semantics { contentDescription = otro; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { IconoRiso(IconosFogon.Mas, null, tamano = 18.dp) }
        }
    }
}

/** La vista previa: el papel con su luz, el número de puntos, una tarjeta de vidrio con su botón y el personaje. */
@Composable
private fun VistaPrevia(paleta: Paleta) {
    val f = FogonTema.actual
    Box(Modifier.fillMaxWidth().padding(16.dp).height(206.dp).clip(forma(28.dp)).fondoFogon(f).border(f.borde, f.linea, forma(28.dp))) {
        Column(Modifier.padding(start = 18.dp, top = 16.dp).haloPuntos(f.tinta2)) {
            Text(stringResource(R.string.fogon_ap_vista_previa), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            NumeroRiso("12", 88.sp)
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(14.dp).width(170.dp).tarjetaFogon(forma(20.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.fogon_ap_muestra_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Text(stringResource(R.string.fogon_ap_muestra_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            Box(Modifier.padding(top = 2.dp).clip(FormaPildora).background(f.tinta1).padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(stringResource(R.string.fogon_ap_muestra_boton), style = MaterialTheme.typography.labelLarge, color = f.sobreTinta1)
            }
        }
        Personaje(PERSONAJE_DE[paleta] ?: R.drawable.personaje_la_minga, null, Modifier.align(Alignment.TopEnd).padding(10.dp), tamano = 84.dp)
    }
}

/** El personaje de cada app, para la vista previa. */
private val PERSONAJE_DE = mapOf(Paleta.Forzudo to R.drawable.personaje_el_forzudo, Paleta.Ahorrador to R.drawable.personaje_el_ahorrador, Paleta.Cucha to R.drawable.personaje_la_cucha, Paleta.Cajon to R.drawable.personaje_el_cajon)

/** Miniatura de un tema: su papel con su luz, una tarjeta de vidrio y sus dos tintas. */
@Composable
private fun MuestraEstilo(m: Fogon, nombre: String, elegido: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val f = FogonTema.actual
    val forma = forma(20.dp)
    Column(
        modifier.semantics(mergeDescendants = true) { selected = elegido; role = Role.RadioButton }.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(84.dp).clip(forma).fondoFogon(m).border(if (elegido) 2.dp else 1.dp, if (elegido) f.texto else f.linea, forma)
                .padding(10.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.7f).height(30.dp).clip(forma(12.dp)).background(m.superficie).border(1.dp, m.linea, forma(12.dp)))
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(m.tinta2))
                Box(Modifier.size(18.dp).clip(CircleShape).background(m.tinta1))
            }
            Text("Aa", Modifier.align(Alignment.TopStart), style = MaterialTheme.typography.titleMedium, color = m.texto)
        }
        Text(nombre, style = MaterialTheme.typography.labelMedium, fontWeight = if (elegido) FontWeight.Bold else FontWeight.Normal, color = f.texto, maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun Interruptor(titulo: String, pista: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(
        Modifier.fillMaxWidth().tarjetaFogon(forma(20.dp)).clickable { onCambio(!valor) }.padding(horizontal = 14.dp, vertical = 10.dp),
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

/** Elegir un color: muestras, tono / saturación / brillo, o su código. "Por defecto" vuelve al del tema. */
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

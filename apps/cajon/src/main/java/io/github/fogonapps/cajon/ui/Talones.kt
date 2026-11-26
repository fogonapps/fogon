package io.github.fogonapps.cajon.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Insignia
import io.github.fogonapps.cajon.datos.Insignias
import io.github.fogonapps.identidad.BarrasDelDialogo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.perfil.TalonInsignia
import io.github.fogonapps.perfil.TextosTalon
import io.github.fogonapps.perfil.iconoInsignia
import io.github.fogonapps.perfil.menosMovimientoBanner
import io.github.fogonapps.perfil.tintasTalon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Las insignias como talones de cine (el dibujo está en core/perfil, TalonInsignia.kt): ganadas, tinta llena;
 * por ganar, el troquel vacío que se va perforando. Cada nivel apila otro talón. Al tocar una se abre su hoja con
 * el talón grande; al ganarla (o subir de nivel), entra con un resorte una vez.
 */

@Composable
internal fun nombreInsignia(id: String) = stringResource(when (id) {
    "cine" -> R.string.insignia_cine; "series" -> R.string.insignia_series; "anime" -> R.string.insignia_anime; "lector" -> R.string.insignia_lector
    "discos" -> R.string.insignia_discos; "constancia" -> R.string.insignia_constancia; "critico" -> R.string.insignia_critico
    "explorador" -> R.string.insignia_explorador; else -> R.string.insignia_reto
})

/** Qué se cuenta en cada insignia, para el talón grande («películas»). */
@Composable
private fun unidadInsignia(id: String) = stringResource(when (id) {
    "cine" -> R.string.insignia_unidad_cine; "series" -> R.string.insignia_unidad_series; "anime" -> R.string.insignia_unidad_anime
    "lector" -> R.string.insignia_unidad_lector; "discos" -> R.string.insignia_unidad_discos; "constancia" -> R.string.insignia_unidad_constancia
    "critico" -> R.string.insignia_unidad_critico; "explorador" -> R.string.insignia_unidad_explorador; else -> R.string.insignia_unidad_reto
})

/** Qué pide cada insignia, con la meta que se muestra en el talón (la del nivel alcanzado si ya se ganó). */
@Composable
private fun descripcionInsignia(id: String, meta: Int = Insignias.meta(id)): String {
    return when (id) {
        "cine" -> stringResource(R.string.insignia_desc_cine, meta)
        "series" -> stringResource(R.string.insignia_desc_series, meta)
        "anime" -> stringResource(R.string.insignia_desc_anime, meta)
        "lector" -> stringResource(R.string.insignia_desc_lector, meta)
        "discos" -> stringResource(R.string.insignia_desc_discos, meta)
        "constancia" -> stringResource(R.string.insignia_desc_constancia, meta)
        "critico" -> stringResource(R.string.insignia_desc_critico, meta)
        "explorador" -> stringResource(R.string.insignia_desc_explorador, meta)
        else -> stringResource(R.string.insignia_desc_reto)
    }
}

/** Lo que dice el lector de pantalla de un talón. */
@Composable
private fun descripcionTalon(ins: Insignia, propias: Boolean): String {
    val nombre = nombreInsignia(ins.id).lowercase(LocalConfiguration.current.locales[0])
    return when {
        ins.ganada && propias && ins.niveles.size > 1 -> stringResource(R.string.insignia_voz_nivel, nombre, ins.nivel, ins.niveles.size)
        ins.ganada -> stringResource(R.string.insignia_voz_ganada, nombre)
        propias && ins.lleva > 0 -> stringResource(R.string.insignia_voz_progreso, nombre, ins.lleva, ins.meta)
        else -> stringResource(R.string.insignia_voz_pendiente, nombre)
    }
}

/** Lo que va escrito abajo del talón: la meta del nivel alcanzado, o «lleva/meta». El reto (meta 1) lleva un chulo. */
private fun numeroTalon(ins: Insignia): String? = when {
    ins.meta <= 1 -> null
    ins.ganada -> ins.alcanzada.toString()
    else -> "${ins.lleva.coerceAtLeast(0)}/${ins.meta}"
}

/** Ganadas primero; luego las que van en camino, de la más avanzada a la menos; al final, las sin empezar. */
internal fun ordenarInsignias(lista: List<Insignia>): List<Insignia> = lista.sortedWith(
    compareBy<Insignia>({ if (it.ganada) 0 else if (it.lleva > 0) 1 else 2 }, { -it.lleva.toFloat() / it.meta.coerceAtLeast(1) }),
)

/** El talón de una insignia, con sus niveles apilados (solo en tu perfil: del ajeno no llegan). */
@Composable
internal fun TalonDe(ins: Insignia, lado: Dp, modifier: Modifier = Modifier, propias: Boolean = true, grande: Boolean = false, invertido: Boolean = false, nivel: Int? = null) {
    val alcanzada = nivel?.let { ins.niveles.getOrNull(it - 1) } ?: ins.alcanzada
    val numero = if (nivel != null && ins.meta > 1) alcanzada.toString() else numeroTalon(ins)
    val textos = if (grande) {
        val idioma = LocalConfiguration.current.locales[0]
        TextosTalon(stringResource(R.string.app_name).uppercase(idioma), stringResource(R.string.talon_admite).uppercase(idioma), unidadInsignia(ins.id).uppercase(idioma))
    } else null
    TalonInsignia(iconoInsignia(ins.id), ins.ganada, modifier, numero = numero, lleva = ins.lleva, meta = ins.meta,
        apilados = if (propias) nivel ?: ins.nivel else 1, lado = lado, grande = textos, tintas = tintasTalon(invertido))
}

// ── La fila del perfil ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * Las insignias en fila, a 72 dp: en tu perfil todas (ganadas, en camino y sin empezar), en el de otra persona
 * solo las ganadas y más chicas. Al tocar una se abre su hoja.
 */
@Composable
internal fun EstanteInsignias(insignias: List<Insignia>, propias: Boolean) {
    val f = FogonTema.actual
    val lista = remember(insignias, propias) { if (propias) ordenarInsignias(insignias) else insignias.filter { it.ganada } }
    if (lista.isEmpty()) return
    var abierta by rememberSaveable { mutableStateOf<String?>(null) }
    val ganadas = lista.count { it.ganada }
    val total = if (propias) insignias.size else Insignias.IDS.size
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.insignias_titulo), fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, color = f.texto,
            modifier = Modifier.weight(1f).semantics { heading() })
        Text(stringResource(R.string.insignias_cuenta, ganadas, total), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
    }
    val lado = if (propias) 72.dp else 56.dp
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(lista.size, key = { lista[it].id }) { i ->
            val ins = lista[i]
            ItemTalon(ins, propias, lado) { abierta = ins.id }
        }
    }
    abierta?.let { id -> lista.firstOrNull { it.id == id } }?.let { DetalleInsignia(it, propias) { abierta = null } }
}

@Composable
private fun ItemTalon(ins: Insignia, propias: Boolean, lado: Dp, alTocar: () -> Unit) {
    val f = FogonTema.actual
    val descripcion = descripcionTalon(ins, propias)
    Column(
        // Al menos 84 dp: en el perfil ajeno (56 dp) los nombres salían en «Melóm…» o «Explora…».
        Modifier.width(maxOf(lado + 12.dp, 84.dp)).clickable(role = Role.Button, onClick = alTocar)
            .clearAndSetSemantics { contentDescription = descripcion; role = Role.Button; onClick { alTocar(); true } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TalonDe(ins, lado, propias = propias)
        Spacer(Modifier.height(4.dp))
        // Una palabra: una línea que se achica un poco. Dos o más («Reto cumplido»): hasta dos líneas en vez de letra diminuta.
        val nombre = nombreInsignia(ins.id)
        val estilo = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
        val color = if (ins.ganada) f.texto else f.textoSuave
        if (' ' in nombre.trim()) Text(nombre, style = estilo, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        else Text(nombre, style = estilo, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp))
    }
}

// ── La hoja ─────────────────────────────────────────────────────────────────────────────────────────────────────

/** La hoja de una insignia: el talón grande, qué pide, y la fecha en que se ganó o cuánto falta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalleInsignia(ins: Insignia, propias: Boolean, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val acciones = LocalAcciones.current
    val alcance = rememberCoroutineScope()
    val capa = rememberGraphicsLayer()
    val fecha = remember(ins.id, ins.nivel, propias) { if (propias) RegistroInsignias(ctx).fecha(Insignias.claveNivel(ins.id, ins.nivel)) else null }
    val textoCompartir = stringResource(R.string.insignia_compartir_texto, nombreInsignia(ins.id), stringResource(R.string.app_name))
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = f.fondo,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        HojaInsignia(ins, propias, fecha, capa,
            compartir = if (acciones != null) { { alcance.launch { acciones.compartir(capa.toImageBitmap().asAndroidBitmap(), textoCompartir) } } } else null)
    }
}

/** Lo de adentro de la hoja (aparte, para probarlo sin la hoja). Lo que se comparte como imagen va en [capa]. */
@Composable
internal fun HojaInsignia(ins: Insignia, propias: Boolean, fecha: String?, capa: GraphicsLayer?, compartir: (() -> Unit)?) {
    val f = FogonTema.actual
    val idioma = LocalConfiguration.current.locales[0]
    val descripcion = descripcionTalon(ins, propias)
    val cuerpo = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.then(if (capa != null) Modifier.drawWithContent { capa.record { this@drawWithContent.drawContent() }; drawLayer(capa) } else Modifier)
            .background(f.fondo).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TalonDe(ins, 250.dp, Modifier.clearAndSetSemantics { contentDescription = descripcion }, propias = propias, grande = true)
            Spacer(Modifier.height(16.dp))
            Text(nombreInsignia(ins.id), fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, color = f.texto, textAlign = TextAlign.Center)
        }
        Text(descripcionInsignia(ins.id, if (ins.ganada && ins.alcanzada > 1) ins.alcanzada else Insignias.meta(ins.id)), style = cuerpo, color = f.textoSuave, textAlign = TextAlign.Center)
        if (propias && !ins.ganada) {
            val faltan = (ins.meta - ins.lleva).coerceAtLeast(0)
            Spacer(Modifier.height(6.dp))
            Text(pluralStringResource(R.plurals.insignia_faltan, faltan, faltan), style = cuerpo.copy(fontWeight = FontWeight.Medium), color = f.texto)
        }
        if (propias && ins.ganada) {
            fecha?.takeIf { it.isNotBlank() }?.let { iso ->
                runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(idioma)) }.getOrNull()?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.insignia_ganada_el, it), style = cuerpo.copy(fontWeight = FontWeight.Medium), color = f.texto)
                }
            }
            if (ins.niveles.size > 1) {
                Spacer(Modifier.height(6.dp))
                val siguiente = ins.siguiente
                Text(if (siguiente != null) stringResource(R.string.insignia_proximo_nivel, ins.nivel, ins.niveles.size, siguiente)
                    else stringResource(R.string.insignia_nivel, ins.nivel, ins.niveles.size),
                    style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, textAlign = TextAlign.Center)
            }
            if (compartir != null) {
                Spacer(Modifier.height(20.dp))
                Button(compartir, Modifier.fillMaxWidth().height(52.dp), shape = FormaPildora,
                    colors = ButtonDefaults.buttonColors(containerColor = f.pildora, contentColor = f.sobrePildora)) {
                    Text(stringResource(R.string.compartir), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.insignias_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center)
    }
}

// ── La celebración ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * El día en que se vio ganado cada nivel de cada insignia (claves de [Insignias.claveNivel]), en este teléfono. Es
 * local a propósito: no viaja en el respaldo sincronizado ni toca el estado del cajón (la app lo calcula sola).
 */
internal class RegistroInsignias(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("fabs_insignias", Context.MODE_PRIVATE)

    fun fecha(clave: String): String? = prefs.getString(CLAVE + clave, null)

    /** Anota las [ganadas] (claves de nivel) y devuelve las que hay que celebrar. */
    @Synchronized
    fun anotar(ganadas: List<String>, hoy: String = LocalDate.now().toString()): List<String> {
        val antes = if (prefs.getBoolean(INICIADO, false)) {
            prefs.all.filterKeys { it.startsWith(CLAVE) }.map { (k, v) -> k.removePrefix(CLAVE) to (v as? String).orEmpty() }.toMap()
        } else null
        val r = Insignias.anotar(antes, ganadas, hoy)
        if (antes == null || r.fechas != antes) {
            prefs.edit().apply {
                antes?.keys?.forEach { remove(CLAVE + it) }
                r.fechas.forEach { (id, dia) -> putString(CLAVE + id, dia) }
                putBoolean(INICIADO, true)
            }.apply()
        }
        return r.celebrar
    }

    private companion object {
        const val CLAVE = "ganada_"
        const val INICIADO = "iniciado"
    }
}

/**
 * Celebra cada insignia (o nivel) que se gana mientras se usa la app, una tras otra. Va una sola vez en la raíz de
 * la app, con el estado ya cargado. La primera vez solo anota las que ya estaban, sin celebrarlas.
 */
@Composable
fun CelebracionInsignias(e: Estado) {
    val ctx = LocalContext.current
    val registro = remember { RegistroInsignias(ctx) }
    var cola by remember { mutableStateOf(emptyList<Pair<Insignia, Int>>()) }
    LaunchedEffect(e) {
        delay(600) // los cambios seguidos (una importación) se cuentan juntos
        val ganadas = withContext(Dispatchers.Default) { Insignias.de(e, LocalDate.now().year).filter { it.ganada } }
        val celebrar = withContext(Dispatchers.IO) { registro.anotar(Insignias.clavesGanadas(ganadas)) }
        // De cada insignia, solo el nivel más alto que llegó.
        val nuevas = celebrar.map(Insignias::deClave).groupBy({ it.first }, { it.second }).mapNotNull { (id, niveles) ->
            ganadas.firstOrNull { it.id == id }?.let { it to niveles.max() }
        }
        if (nuevas.isNotEmpty()) cola = cola + nuevas.filter { (ins, _) -> cola.none { it.first.id == ins.id } }
    }
    val actual = cola.firstOrNull() ?: return
    key(actual.first.id) { CapaCelebracion(actual.first, actual.second) { cola = cola.drop(1) } }
}

/**
 * El talón entra con un resorte, un poco girado, sobre una cortina del papel del tema (casi opaca, sin panel):
 * clara en el tema claro y oscura en el oscuro. Con «menos movimiento», todo aparece de una vez, sin animar.
 */
@Composable
internal fun CapaCelebracion(ins: Insignia, nivel: Int, onListo: () -> Unit) {
    val f = FogonTema.actual
    val quieto = menosMovimientoBanner() || f.menosMovimiento
    val haptica = LocalHapticFeedback.current
    val acciones = LocalAcciones.current
    val alcance = rememberCoroutineScope()
    val capa = rememberGraphicsLayer()
    val escala = remember { Animatable(if (quieto) 1f else 0.6f) }
    val giro = remember { Animatable(if (quieto) 0f else -10f) }
    val subida = remember { Animatable(if (quieto) 0f else 1f) }
    val opacidad = remember { Animatable(if (quieto) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!quieto) {
            launch { opacidad.animateTo(1f, tween(180)) }
            launch { subida.animateTo(0f, spring(0.7f, 260f)) }
            launch { giro.animateTo(0f, spring(0.4f, 220f)) }
            escala.animateTo(1f, spring(0.5f, 300f))
        }
        haptica.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val nombre = nombreInsignia(ins.id)
    val textoCompartir = stringResource(R.string.insignia_compartir_texto, nombre, stringResource(R.string.app_name))
    Dialog(onListo, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BarrasDelDialogo(f.fondo)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacidad.value }.background(f.fondo.copy(alpha = 0.96f)).systemBarsPadding(),
            contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Lo que se comparte: el talón y su nombre, sin fondo en pantalla (el papel se pone al compartir).
                Column(Modifier.drawWithContent { capa.record { this@drawWithContent.drawContent() }; drawLayer(capa) }.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    TalonDe(ins, 260.dp,
                        Modifier.graphicsLayer {
                            scaleX = escala.value; scaleY = escala.value; rotationZ = giro.value
                            translationY = subida.value * 80.dp.toPx()
                        }.semantics { contentDescription = nombre },
                        grande = true, nivel = nivel)
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(if (nivel > 1) R.string.insignia_nuevo_nivel else R.string.insignia_nueva),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = f.textoSuave)
                    Spacer(Modifier.height(6.dp))
                    Text(nombre, fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, color = f.texto, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                Button(onListo, Modifier.fillMaxWidth().height(52.dp), shape = FormaPildora,
                    colors = ButtonDefaults.buttonColors(containerColor = f.texto, contentColor = f.fondo)) {
                    Text(stringResource(R.string.listo), style = MaterialTheme.typography.labelLarge)
                }
                if (acciones != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton({ alcance.launch { acciones.compartir(conPapel(capa.toImageBitmap().asAndroidBitmap(), f.fondo.toArgb()), textoCompartir) } },
                        Modifier.fillMaxWidth().height(52.dp), shape = FormaPildora, border = BorderStroke(1.dp, f.texto),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = f.texto)) {
                        Text(stringResource(R.string.compartir), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** La imagen de [imagen] (con fondo transparente) sobre el papel del tema, para compartirla. */
internal fun conPapel(imagen: android.graphics.Bitmap, papel: Int): android.graphics.Bitmap {
    val salida = android.graphics.Bitmap.createBitmap(imagen.width, imagen.height, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(salida).apply {
        drawColor(papel)
        drawBitmap(imagen.copy(android.graphics.Bitmap.Config.ARGB_8888, false), 0f, 0f, null)
    }
    return salida
}

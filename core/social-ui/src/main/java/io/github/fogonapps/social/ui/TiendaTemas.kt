package io.github.fogonapps.social.ui

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import io.github.fogonapps.perfil.MuestraTema
import io.github.fogonapps.perfil.MuestraMarco
import io.github.fogonapps.perfil.TemasPerfil

/** El fondo y el marco que tienes puestos (para dibujar tu propio perfil). */
@Composable
fun temaPropio(vm: InterfazSocial): String {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.cargarTemas() }
    return if (usuario == null) "" else temas.puesto
}

@Composable
fun marcoPropio(vm: InterfazSocial): String {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    return if (usuario == null) "" else temas.marco
}

/** La chispa (la moneda de Fogón), neutra como un ícono más: un destello de cuatro puntas dentro de un hexágono. */
private val Chispa = dibujo("chispa",
    trazo("M12 2.6l8.2 4.7v9.4L12 21.4l-8.2-4.7V7.3z", 2.2f),
    relleno("M12 7.2l1.3 3.5 3.5 1.3-3.5 1.3L12 16.8l-1.3-3.5L7.2 12l3.5-1.3z"))

@Composable
private fun IconoChispa(tamano: androidx.compose.ui.unit.Dp) = IconoRiso(Chispa, null, tamano = tamano, tinta = FogonTema.actual.texto)

/**
 * La tienda en la barra de arriba, al lado de tu foto (como la moneda de los juegos): un botón redondo con la chispa,
 * sin el número (tus chispas se ven al abrirla). Un puntito avisa que puedes reclamar las del día. Solo con cuenta.
 */
@Composable
fun ChispasArriba(vm: InterfazSocial, modifier: Modifier = Modifier) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    if (usuario == null) return
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.cargarTemas() }
    val f = FogonTema.actual
    var abierta by remember { mutableStateOf(false) }
    val texto = stringResource(R.string.social_temas_tienda)
    Box(modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(f.superficie).border(f.borde, f.linea, androidx.compose.foundation.shape.CircleShape)
        .clickable { abierta = true }.semantics { contentDescription = texto }, contentAlignment = Alignment.Center) {
        IconoChispa(24.dp)
        if (temas.diario) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(f.tinta1))
    }
    if (abierta) TiendaTemas(vm) { abierta = false }
}

/** El botón del perfil propio que abre la tienda (solo con cuenta: lo que te pones lo ven los demás). */
@Composable
fun BotonTiendaTemas(vm: InterfazSocial, modifier: Modifier = Modifier) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    if (usuario == null) return
    var abierta by remember { mutableStateOf(false) }
    Box(modifier) { Pildora(stringResource(R.string.social_temas_tienda), IconosFogon.Estrella) { abierta = true } }
    if (abierta) TiendaTemas(vm) { abierta = false }
}

/**
 * La tienda dentro de «Editar perfil»: una tarjeta con tus chispas que abre la tienda (fondo y marco son parte de cómo
 * se ve tu perfil). Solo con cuenta.
 */
@Composable
fun TiendaEnEditarPerfil(vm: InterfazSocial, modifier: Modifier = Modifier) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    if (usuario == null) return
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.cargarTemas() }
    val f = FogonTema.actual
    var abierta by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(forma(20.dp)).background(f.superficie).clickable { abierta = true }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconoChispa(28.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.social_temas_tienda), style = MaterialTheme.typography.titleSmall, color = f.texto)
            Text(stringResource(R.string.social_chispas_n, temas.chispas), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        if (temas.diario) Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(f.tinta1))
        IconoRiso(IconosFogon.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    if (abierta) TiendaTemas(vm) { abierta = false }
}

/**
 * La tienda del perfil: fondos (escenas con su paleta, como los de Steam) y marcos de foto, que se compran con chispas.
 * Las chispas se ganan usando las apps (la bienvenida y las del día) y solo sirven aquí.
 */
@Composable
fun TiendaTemas(vm: InterfazSocial, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    var pestana by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.red.cargarTemas() }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(stringResource(R.string.social_temas_tienda), onCerrar)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Tus chispas y las del día.
                Row(Modifier.fillMaxWidth().clip(forma(20.dp)).background(f.superficie).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconoChispa(28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.social_chispas_n, temas.chispas), style = MaterialTheme.typography.titleMedium, color = f.texto)
                        Text(stringResource(R.string.social_chispas_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    if (temas.diario) BotonRiso(stringResource(R.string.social_chispas_diario), { vm.red.cobrarDiario() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pildora(stringResource(R.string.social_tienda_fondos), acento = pestana == 0) { pestana = 0 }
                    Pildora(stringResource(R.string.social_tienda_marcos), acento = pestana == 1) { pestana = 1 }
                }
                val lista = if (pestana == 0) listOf("") + TemasPerfil.FONDOS else listOf("marco:") + TemasPerfil.MARCOS.map { "marco:$it" }
                lista.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { item ->
                            val quitar = item.isEmpty() || item == "marco:"
                            val id = item.removePrefix("marco:")
                            val tiene = quitar || item in temas.reclamados
                            val puesto = if (pestana == 0) temas.puesto == item else temas.marco == id
                            val precio = TemasPerfil.PRECIOS[item] ?: 0
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (pestana == 0) MuestraTema(item, Modifier.fillMaxWidth()) else MuestraMarco(id, Modifier.fillMaxWidth())
                                Text(if (quitar) stringResource(if (pestana == 0) R.string.social_tienda_sin_fondo else R.string.social_tienda_sin_marco) else TemasPerfil.nombre(id),
                                    style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1)
                                when {
                                    puesto -> BotonSecundario(stringResource(R.string.social_temas_en_uso), {}, Modifier.fillMaxWidth(), habilitado = false)
                                    tiene -> BotonSecundario(stringResource(R.string.social_temas_usar), { vm.red.ponerTema(item) }, Modifier.fillMaxWidth())
                                    else -> BotonRiso(stringResource(R.string.social_tienda_comprar, precio), { vm.red.reclamarTema(item) }, Modifier.fillMaxWidth(), habilitado = temas.chispas >= precio)
                                }
                            }
                        }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                PistaSocial(stringResource(R.string.social_chispas_reglas))
            }
        }
    }
}

/** La portada (banner) de un perfil ajeno, llenando su marco (para las apps sin cargador de imágenes propio). */
@Composable
fun PortadaPerfilRed(url: String, modifier: Modifier = Modifier) {
    if (url.isNotBlank()) coil3.compose.AsyncImage(url, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}

/** Las cifras sociales de un perfil ajeno (seguidores y seguidos), las mismas en las cuatro apps. */
@Composable
fun cifrasSociales(p: io.github.fogonapps.social.PerfilSocial): List<io.github.fogonapps.perfil.ContadorPerfil> = listOf(
    io.github.fogonapps.perfil.ContadorPerfil("${p.seguidores}", stringResource(R.string.social_cifra_seguidores)),
    io.github.fogonapps.perfil.ContadorPerfil("${p.siguiendo}", stringResource(R.string.social_cifra_siguiendo)),
)

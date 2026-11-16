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
import io.github.fogonapps.perfil.BannerPerfil
import io.github.fogonapps.perfil.nombreBanner
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
 * sin el número (tus chispas se ven al abrirla). Solo con cuenta.
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
        IconoRiso(IconosFogon.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    if (abierta) TiendaTemas(vm) { abierta = false }
}

/**
 * La tienda del perfil: fondos (escenas con su paleta, como los de Steam) y marcos de foto, que se compran con chispas.
 * Las chispas se ganan usando las apps (bienvenida y aportes verificados) y solo sirven aquí.
 */
@Composable
fun TiendaTemas(vm: InterfazSocial, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val paquetes by vm.red.paquetesWompi.collectAsStateWithLifecycle()
    var comprando by remember { mutableStateOf(false) }
    var errorPago by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.red.cargarPaquetesWompi() }
    DisposableEffect(ciclo) {
        val observador = androidx.lifecycle.LifecycleEventObserver { _, evento ->
            if (evento == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.red.cargarTemas()
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    var regalo by remember { mutableStateOf<String?>(null) }
    var regalando by remember { mutableStateOf(false) }
    var errorRegalo by remember { mutableStateOf<String?>(null) }
    var destinatario by remember { mutableStateOf("") }
    var pestana by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.red.cargarTemas() }
    if (regalo != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = { regalo = null },
        title = { Text(stringResource(R.string.social_regalar)) },
        text = { Column {
            androidx.compose.material3.OutlinedTextField(destinatario, { destinatario = it }, label = { Text(stringResource(R.string.social_regalo_para)) }, singleLine = true, enabled = !regalando)
            errorRegalo?.let { error -> Text(stringResource(when (error) {
                "ya_lo_tiene" -> R.string.social_regalo_ya_tiene
                "tope_regalos" -> R.string.social_regalo_tope
                "bloqueado" -> R.string.social_regalo_bloqueado
                "a_si_mismo" -> R.string.social_regalo_propio
                "no_existe" -> R.string.social_regalo_no_existe
                "sin_chispas" -> R.string.social_regalo_sin_chispas
                else -> R.string.social_regalo_error
            }), color = f.texto) }
        } },
        confirmButton = { BotonRiso(stringResource(R.string.social_regalar), { regalo?.let { item ->
            regalando = true; errorRegalo = null
            vm.red.regalar(item, destinatario) { error ->
                regalando = false; errorRegalo = error
                if (error == null) { regalo = null; destinatario = "" }
            }
        } }, habilitado = destinatario.isNotBlank() && !regalando) },
        dismissButton = { BotonSecundario(stringResource(R.string.social_regalo_cancelar), { regalo = null }) })
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(stringResource(R.string.social_temas_tienda), onCerrar)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Saldo de la cuenta.
                Row(Modifier.fillMaxWidth().clip(forma(20.dp)).background(f.superficie).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconoChispa(28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.social_chispas_n, temas.chispas), style = MaterialTheme.typography.titleMedium, color = f.texto)
                        Text(stringResource(R.string.social_chispas_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                }
                if (paquetes.isNotEmpty()) {
                    Text(stringResource(R.string.social_wompi_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
                    paquetes.forEach { paquete ->
                        BotonRiso(stringResource(R.string.social_wompi_paquete, formatoColombiano(paquete), formatoColombiano(paquete * TemasPerfil.PESOS_POR_CHISPA)), {
                            comprando = true
                            errorPago = false
                            vm.red.comprarChispas(paquete) { url ->
                                comprando = false
                                errorPago = url == null || runCatching {
                                    val uri = android.net.Uri.parse(url)
                                    require(uri.scheme == "https" && uri.host == "checkout.wompi.co")
                                    contexto.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                }.isFailure
                            }
                        }, Modifier.fillMaxWidth(), habilitado = !comprando)
                    }
                    Text(stringResource(R.string.social_wompi_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    if (errorPago) Text(stringResource(R.string.social_wompi_error), color = f.texto)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pildora(stringResource(R.string.social_tienda_fondos), acento = pestana == 0) { pestana = 0 }
                    Pildora(stringResource(R.string.social_tienda_marcos), acento = pestana == 1) { pestana = 1 }
                    Pildora(stringResource(R.string.social_tienda_banners), acento = pestana == 2) { pestana = 2 }
                }
                val lista = when (pestana) { 0 -> listOf("") + TemasPerfil.FONDOS; 1 -> listOf("marco:") + TemasPerfil.MARCOS.map { "marco:$it" }; else -> TemasPerfil.BANNERS.keys.map { "banner:$it" } }
                lista.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { item ->
                            val quitar = item.isEmpty() || item == "marco:"
                            val id = item.removePrefix("marco:").removePrefix("banner:")
                            val tiene = quitar || item in temas.reclamados || TemasPerfil.PRECIOS[item] == 0
                            val puesto = if (pestana == 0) temas.puesto == item else if (pestana == 1) temas.marco == id else temas.banner == item
                            val precio = TemasPerfil.PRECIOS[item] ?: 0
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (pestana == 0) MuestraTema(item, Modifier.fillMaxWidth()) else if (pestana == 1) MuestraMarco(id, Modifier.fillMaxWidth()) else BannerPerfil(id, Modifier.fillMaxWidth().height(100.dp))
                                Text(if (quitar) stringResource(if (pestana == 0) R.string.social_tienda_sin_fondo else R.string.social_tienda_sin_marco) else if (pestana == 2) nombreBanner(id) else TemasPerfil.nombre(id),
                                    style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1)
                                if (!quitar) Text(stringResource(R.string.social_precio_pesos, formatoColombiano(precio), formatoColombiano(precio * TemasPerfil.PESOS_POR_CHISPA)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                                when {
                                    puesto -> BotonSecundario(stringResource(R.string.social_temas_en_uso), {}, Modifier.fillMaxWidth(), habilitado = false)
                                    tiene -> BotonSecundario(stringResource(R.string.social_temas_usar), { vm.red.ponerTema(item) }, Modifier.fillMaxWidth())
                                    else -> BotonRiso(stringResource(R.string.social_tienda_comprar, precio), { vm.red.reclamarTema(item) }, Modifier.fillMaxWidth(), habilitado = temas.chispas >= precio)
                                }
                                if (precio > 0) BotonSecundario(stringResource(R.string.social_regalar), { regalo = item; errorRegalo = null }, Modifier.fillMaxWidth(), habilitado = temas.chispas >= precio)
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
    if (url.startsWith("banner:")) { BannerPerfil(url.removePrefix("banner:"), modifier); return }
    if (url.isNotBlank()) coil3.compose.AsyncImage(url, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}

/** Las cifras sociales de un perfil ajeno (seguidores y seguidos), las mismas en las cuatro apps. */
@Composable
fun cifrasSociales(p: io.github.fogonapps.social.PerfilSocial): List<io.github.fogonapps.perfil.ContadorPerfil> = listOf(
    io.github.fogonapps.perfil.ContadorPerfil("${p.seguidores}", stringResource(R.string.social_cifra_seguidores)),
    io.github.fogonapps.perfil.ContadorPerfil("${p.siguiendo}", stringResource(R.string.social_cifra_siguiendo)),
)

internal fun formatoColombiano(valor: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-CO")).format(valor)

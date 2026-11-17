package io.github.fogonapps.social.ui

import coil3.network.httpHeaders
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import io.github.fogonapps.perfil.R as PerfilR
import io.github.fogonapps.perfil.Escaparates
import io.github.fogonapps.perfil.EscaparateVista
import io.github.fogonapps.perfil.ElementoEscaparate
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
    var mercado by remember { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(forma(20.dp)).background(f.superficie).rebote().clickable { abierta = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoChispa(28.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.social_temas_tienda), style = MaterialTheme.typography.titleSmall, color = f.texto)
                Text(stringResource(R.string.social_chispas_n, numeroSocial(temas.chispas)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            IconoRiso(IconosFogon.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
        }
        Row(Modifier.fillMaxWidth().clip(forma(20.dp)).background(f.superficie).rebote().clickable { mercado = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRiso(IconosFogon.Moneda, null, tamano = 28.dp)
            Text(stringResource(R.string.social_mercado), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = f.texto)
            IconoRiso(IconosFogon.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
        }
    }
    EquiparComprados(vm, Modifier.padding(top = 12.dp))
    if (mercado) Mercado(vm) { mercado = false }
    if (abierta) TiendaTemas(vm) { abierta = false }
}

/**
 * La tienda del perfil: fondos (escenas con su paleta, como los de Steam) y marcos de foto, que se compran con chispas.
 * Las chispas se ganan usando las apps (bienvenida y aportes verificados) y solo sirven aquí.
 */
@Composable
fun TiendaTemas(vm: InterfazSocial, escaparatesExtra: Boolean = false, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val paquetes by vm.red.paquetesWompi.collectAsStateWithLifecycle()
    val chispasDeRegalo by vm.red.regaloWompi.collectAsStateWithLifecycle()
    val desplazamiento = rememberScrollState()
    var posicionEscaparates by remember { mutableIntStateOf(-1) }
    var alinearEntrada by remember(escaparatesExtra, paquetes) { mutableStateOf(escaparatesExtra) }
    LaunchedEffect(alinearEntrada, posicionEscaparates, desplazamiento.maxValue) {
        if (alinearEntrada && posicionEscaparates >= 0 && desplazamiento.maxValue > 0) {
            // Espera la medida; si llegan los paquetes, vuelve a alinear la sección una vez.
            withFrameNanos { }
            desplazamiento.scrollTo(posicionEscaparates)
            alinearEntrada = false
        }
    }
    var comprando by remember { mutableStateOf(false) }
    var errorPago by remember { mutableStateOf(false) }
    val pagosExternos = ofrecePagosExternos(BuildConfig.TIENDA_PLAY)
    LaunchedEffect(pagosExternos) {
        if (pagosExternos) vm.red.cargarPaquetesWompi()
    }
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
    var mercado by remember { mutableStateOf(false) }
    if (mercado) Mercado(vm) { mercado = false }
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
            // Deslizar hacia abajo recarga el saldo (y, en el servidor, concilia los pagos pendientes con Wompi).
            io.github.fogonapps.identidad.DeslizarParaRecargar({ vm.red.cargarTemas(); if (pagosExternos) vm.red.cargarPaquetesWompi() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(desplazamiento).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BotonSecundario(stringResource(R.string.social_mercado), { mercado = true }, Modifier.fillMaxWidth())
                // Saldo de la cuenta.
                Row(Modifier.fillMaxWidth().clip(forma(20.dp)).background(f.superficie).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconoChispa(28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.social_chispas_n, numeroSocial(temas.chispas)), style = MaterialTheme.typography.titleMedium, color = f.texto)
                        Text(stringResource(R.string.social_chispas_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                }
                if (pagosExternos && paquetes.isNotEmpty()) {
                    Text(stringResource(R.string.social_wompi_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
                    paquetes.forEach { paquete ->
                        BotonRiso(chispasDeRegalo[paquete]?.let { extra -> stringResource(R.string.social_wompi_paquete_regalo, numeroSocial(paquete), numeroSocial(extra), numeroSocial(paquete * TemasPerfil.PESOS_POR_CHISPA)) }
                            ?: stringResource(R.string.social_wompi_paquete, numeroSocial(paquete), numeroSocial(paquete * TemasPerfil.PESOS_POR_CHISPA)), {
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
                Text(stringResource(PerfilR.string.tienda_repisas_titulo), Modifier.onGloballyPositioned { posicionEscaparates = it.positionInParent().y.roundToInt() }, color = f.texto, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(PerfilR.string.tienda_repisas_intro), color = f.textoSuave)
                TemasPerfil.EXTRAS.forEachIndexed { indice, item ->
                    val tiene = item in temas.reclamados
                    val nombres = listOf(PerfilR.string.tienda_repisa_discos, PerfilR.string.tienda_repisa_frase, PerfilR.string.tienda_repisa_numero)
                    val lineas = listOf(PerfilR.string.tienda_repisa_discos_linea, PerfilR.string.tienda_repisa_frase_linea, PerfilR.string.tienda_repisa_numero_linea)
                    Column(Modifier.fillMaxWidth().tarjetaFogon().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(nombres[indice]), style = MaterialTheme.typography.titleMedium, color = f.texto)
                        Text(stringResource(lineas[indice]), color = f.textoSuave)
                        MuestraRepisa(indice)
                        Text(stringResource(PerfilR.string.tienda_repisa_aclara), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        val precio = TemasPerfil.PRECIOS.getValue(item)
                        Text(stringResource(R.string.social_precio_pesos, numeroSocial(precio), numeroSocial(precio * TemasPerfil.PESOS_POR_CHISPA)), color = f.textoSuave)
                        BotonSecundario(stringResource(if (tiene) PerfilR.string.escaparates_comprado else R.string.social_tienda_comprar, numeroSocial(precio)),
                            { vm.red.reclamarTema(item) }, Modifier.fillMaxWidth(), habilitado = !tiene && temas.chispas >= precio)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pildora(stringResource(R.string.social_tienda_fondos), acento = pestana == 0) { pestana = 0 }
                    Pildora(stringResource(R.string.social_tienda_marcos), acento = pestana == 1) { pestana = 1 }
                    Pildora(stringResource(R.string.social_tienda_banners), acento = pestana == 2) { pestana = 2 }
                }
                if (pestana == 2) {
                    val item = "portada:propia"
                    Text(TemasPerfil.nombre(item), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.social_precio_pesos, numeroSocial(10000), numeroSocial(100000)))
                    if (item in temas.reclamados) PortadaPropia(vm)
                    else BotonRiso(stringResource(R.string.social_tienda_comprar, numeroSocial(10000)), { vm.red.reclamarTema(item) }, habilitado = temas.chispas >= 10000)
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
                                if (!quitar) Text(stringResource(R.string.social_precio_pesos, numeroSocial(precio), numeroSocial(precio * TemasPerfil.PESOS_POR_CHISPA)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                                when {
                                    puesto -> BotonSecundario(stringResource(R.string.social_temas_en_uso), {}, Modifier.fillMaxWidth(), habilitado = false)
                                    tiene -> BotonSecundario(stringResource(R.string.social_temas_usar), { vm.red.ponerTema(item) }, Modifier.fillMaxWidth())
                                    else -> BotonRiso(stringResource(R.string.social_tienda_comprar, numeroSocial(precio)), { vm.red.reclamarTema(item) }, Modifier.fillMaxWidth(), habilitado = temas.chispas >= precio)
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
}

/** La portada (banner) de un perfil ajeno, llenando su marco (para las apps sin cargador de imágenes propio). */
@Composable
fun PortadaPerfilRed(url: String, modifier: Modifier = Modifier, vm: InterfazSocial? = null) {
    if (url.startsWith("banner:")) { BannerPerfil(url.removePrefix("banner:"), modifier); return }
    if (url.isBlank()) return
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val propia = Regex("[A-Za-z0-9_-]{22}").matches(url)
    val cabeceras = if (propia) vm?.cabeceras().orEmpty() else emptyMap()
    val solicitud = remember(url, cabeceras) {
        coil3.request.ImageRequest.Builder(contexto)
            .data(if (propia) io.github.fogonapps.social.ClienteSocial.imagen(url) else url)
            .httpHeaders(coil3.network.NetworkHeaders.Builder().apply { cabeceras.forEach { (k, v) -> set(k, v) } }.build())
            .memoryCachePolicy(coil3.request.CachePolicy.DISABLED).diskCachePolicy(coil3.request.CachePolicy.DISABLED).build()
    }
    coil3.compose.AsyncImage(solicitud, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}

/** Las cifras sociales de un perfil ajeno (seguidores y seguidos), las mismas en las cuatro apps. */
@Composable
fun cifrasSociales(p: io.github.fogonapps.social.PerfilSocial): List<io.github.fogonapps.perfil.ContadorPerfil> = listOf(
    io.github.fogonapps.perfil.ContadorPerfil("${p.seguidores}", stringResource(R.string.social_cifra_seguidores)),
    io.github.fogonapps.perfil.ContadorPerfil("${p.siguiendo}", stringResource(R.string.social_cifra_siguiendo)),
)

internal fun formatearMiles(valor: Int, idioma: java.util.Locale): String =
    java.text.NumberFormat.getIntegerInstance(if (idioma.language == "en") java.util.Locale.US else java.util.Locale.forLanguageTag("es-CO")).format(valor)

@Composable
internal fun numeroSocial(valor: Int): String = formatearMiles(valor,
    androidx.compose.ui.platform.LocalConfiguration.current.locales[0])

/** En Google Play no se ofrecen pagos fuera de la tienda. */
internal fun ofrecePagosExternos(tiendaPlay: Boolean): Boolean = !tiendaPlay

/** Accesos a lo adquirido, junto a la tienda en todos los editores. */
@Composable
private fun EquiparComprados(vm: InterfazSocial, modifier: Modifier = Modifier) {
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.social_tienda_fondos to (listOf("") + temas.reclamados.filter { it in TemasPerfil.FONDOS }),
            R.string.social_tienda_marcos to (listOf("marco:") + temas.reclamados.filter { it.startsWith("marco:") }),
        ).forEach { (titulo, items) ->
            Text(stringResource(titulo), style = MaterialTheme.typography.titleSmall, color = FogonTema.actual.texto)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    val id = item.removePrefix("banner:").removePrefix("marco:")
                    val puesto = item == temas.banner || (!item.startsWith("banner:") && !item.startsWith("marco:") && item == temas.puesto) || (item.startsWith("marco:") && id == temas.marco)
                    Column(Modifier.width(128.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box {
                            when {
                                item.startsWith("banner:") -> BannerPerfil(id, Modifier.fillMaxWidth().height(48.dp))
                                item.startsWith("marco:") -> MuestraMarco(id, Modifier.fillMaxWidth())
                                else -> MuestraTema(id, Modifier.fillMaxWidth())
                            }
                            if (item in temas.reclamados) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(forma(10.dp)).background(FogonTema.actual.fondo).padding(4.dp)) {
                                IconoRiso(IconosFogon.Chulo, stringResource(PerfilR.string.perfil_articulo_comprado), tamano = 16.dp)
                            }
                        }
                        val nombre = if (item.startsWith("banner:")) nombreBanner(id) else TemasPerfil.nombre(id)
                        BotonSecundario(nombre, { vm.red.ponerTema(item) }, Modifier.fillMaxWidth(), habilitado = !puesto)
                    }
                }
            }
        }
        if ("portada:propia" in temas.reclamados) PortadaPropia(vm)
    }
}

/** Ejemplos locales: no consultan catálogos ni representan contenido de la cuenta. */
@Composable
internal fun MuestraRepisa(indice: Int) {
    val f = FogonTema.actual
    val muestra = when (indice) {
        0 -> EscaparateVista("cajon:albumes", elementos = listOf(
            ElementoEscaparate(stringResource(PerfilR.string.muestra_disco_1), portada = "0"),
            ElementoEscaparate(stringResource(PerfilR.string.muestra_disco_2), portada = "1"),
            ElementoEscaparate(stringResource(PerfilR.string.muestra_disco_3), portada = "2")))
        1 -> EscaparateVista("texto", texto = stringResource(PerfilR.string.muestra_frase))
        else -> EscaparateVista("numero", numero = "128", etiqueta = stringResource(PerfilR.string.muestra_numero))
    }
    Escaparates(listOf(muestra)) { id, modifier ->
        Box(modifier.background(listOf(f.tinta1, f.tinta2, f.superficie)[id.toInt()]))
    }
}

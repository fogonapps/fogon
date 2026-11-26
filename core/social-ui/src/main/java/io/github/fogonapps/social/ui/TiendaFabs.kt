package io.github.fogonapps.social.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.BannerPerfil
import io.github.fogonapps.perfil.ColeccionFabs
import io.github.fogonapps.perfil.MuestraMarco
import io.github.fogonapps.perfil.MuestraTema
import io.github.fogonapps.perfil.PROPORCION_PORTADA
import io.github.fogonapps.perfil.PerfilVista
import io.github.fogonapps.perfil.TemasPerfil
import io.github.fogonapps.perfil.nombreBanner
import io.github.fogonapps.social.ErrorSocial
import io.github.fogonapps.social.PersonaSocial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * La tienda de Fabs (el diseño nuevo): arriba la vitrina con la colección de la semana, en grande; debajo el
 * catálogo oficial por tipo; cada artículo se prueba en tu propio perfil antes de comprarlo. Casi blanco y negro: el
 * color lo ponen los artículos.
 */

/** "banner", "animado", "marco" o "fondo". */
internal fun tipoArticulo(item: String): String = when {
    item.startsWith("marco:") -> "marco"
    item.startsWith("banner:") -> if (item.removePrefix("banner:").substringBefore('~') in TemasPerfil.BANNERS_ANIMADOS) "animado" else "banner"
    else -> "fondo"
}

/** El artículo de una portada ajena ("banner:…", sin el color); null si es una foto o una portada subida. */
fun articuloDePortada(portada: String): String? = portada.takeIf { it.startsWith("banner:") }?.substringBefore('~')

private val DEL_MERCADO = Regex("(banner|marco):m([1-9][0-9]*)")

@Composable
internal fun nombreArticulo(item: String): String {
    val id = item.removePrefix("banner:").removePrefix("marco:").substringBefore('~')
    return when {
        DEL_MERCADO.matches(item) -> stringResource(if (item.startsWith("marco:")) R.string.social_tipo_marco else R.string.social_tipo_banner)
        item.startsWith("banner:") -> nombreBanner(id)
        else -> TemasPerfil.nombre(id)
    }
}

@Composable
private fun tipoLegible(item: String): String = stringResource(when (tipoArticulo(item)) {
    "marco" -> R.string.social_tipo_marco; "fondo" -> R.string.social_tipo_fondo
    "animado" -> R.string.social_tipo_animado; else -> R.string.social_tipo_banner
})

/** La muestra de un artículo: el banner a la proporción del perfil, el marco sobre una foto vacía o el fondo. */
@Composable
internal fun MuestraArticulo(item: String, modifier: Modifier = Modifier) {
    val id = item.removePrefix("banner:").removePrefix("marco:")
    when (tipoArticulo(item)) {
        "marco" -> MuestraMarco(id, modifier.fillMaxWidth())
        "fondo" -> MuestraTema(id, modifier.fillMaxWidth())
        else -> BannerPerfil(id, modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA).clip(forma(10.dp)))
    }
}

/** Un artículo del catálogo: su muestra, el nombre y el precio (o si ya es tuyo), sin caja alrededor. */
@Composable
private fun TarjetaArticulo(item: String, reclamados: List<String>, enUso: Boolean, modifier: Modifier, abrir: () -> Unit) {
    val f = FogonTema.actual
    val precio = TemasPerfil.PRECIOS[item] ?: 0
    Column(modifier.clip(forma(12.dp)).clickable(role = Role.Button, onClick = abrir).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MuestraArticulo(item)
        Text(nombreArticulo(item), style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(when {
            enUso -> stringResource(R.string.social_temas_en_uso)
            item in reclamados || precio == 0 -> stringResource(R.string.social_probador_tuyo)
            else -> stringResource(R.string.social_precio_chispas, numeroSocial(precio))
        }, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

private fun enUso(item: String, puesto: String, marco: String, banner: String): Boolean = when (tipoArticulo(item)) {
    "marco" -> item.removePrefix("marco:") == marco
    "fondo" -> item == puesto
    else -> banner.substringBefore('~') == item
}

/** La vitrina: la colección de la semana en grande, con sus cinco artículos debajo. */
@Composable
internal fun VitrinaFabs(c: ColeccionFabs, temas: io.github.fogonapps.social.TemasCuentaSocial, abrir: (String) -> Unit) {
    val f = FogonTema.actual
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.social_vitrina_titulo).uppercase(), style = MaterialTheme.typography.labelMedium, color = f.textoSuave, letterSpacing = 1.2.sp)
        BannerPerfil(c.animado, Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA).clip(forma(14.dp))
            .clickable(role = Role.Button) { abrir("banner:${c.animado}") })
        Text(stringResource(c.nombre), fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp, color = f.texto)
        Text(stringResource(c.lema), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            c.articulos.forEach { item ->
                TarjetaArticulo(item, temas.reclamados, enUso(item, temas.puesto, temas.marco, temas.banner), Modifier.width(when (tipoArticulo(item)) { "fondo" -> 64.dp; "marco" -> 100.dp; else -> 168.dp })) { abrir(item) }
            }
        }
    }
}

/** El catálogo oficial por tipo (banners, animados, marcos y fondos) en dos columnas. */
@Composable
internal fun CatalogoFabs(vm: InterfazSocial, temas: io.github.fogonapps.social.TemasCuentaSocial, tipo: String, elegirTipo: (String) -> Unit, abrir: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("banner" to R.string.social_tienda_banners, "animado" to R.string.social_tienda_animados,
            "marco" to R.string.social_tienda_marcos, "fondo" to R.string.social_tienda_fondos).forEach { (clave, texto) ->
            PildoraSocial(stringResource(texto), acento = tipo == clave) { elegirTipo(clave) }
        }
    }
    if (tipo == "banner") PortadasPropias(vm)
    val lista = TemasPerfil.COLECCIONES.flatMap { it.articulos }.filter { tipoArticulo(it) == tipo }
    lista.chunked(2).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            fila.forEach { item -> TarjetaArticulo(item, temas.reclamados, enUso(item, temas.puesto, temas.marco, temas.banner), Modifier.weight(1f)) { abrir(item) } }
            if (fila.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * El probador: pone el artículo en TU perfil, a pantalla completa, solo como vista previa. Abajo, «Comprar» (o lo
 * que falta, con el paquete justo a un toque: al llegar las chispas se compra solo) y «Quitar», que cierra sin
 * cambiar nada. [comprar] lanza el error del servidor si falla.
 */
@Composable
fun Probador(vm: InterfazSocial, item: String, nombre: String, precio: Int, tiene: Boolean, comprar: suspend () -> Unit, cerrar: () -> Unit) {
    val f = FogonTema.actual
    val scope = rememberCoroutineScope()
    val contadores = contadoresDeLaApp()
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val actividad = LocalActivity.current
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    var ocupada by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var listo by remember { mutableStateOf(false) }
    // Compra en espera: se pidió un paquete para este artículo; al llegar las chispas se compra solo.
    var enEspera by rememberSaveable(item) { mutableStateOf(false) }
    val faltan = (precio - temas.chispas).coerceAtLeast(0)
    val puesto = enUso(item, temas.puesto, temas.marco, temas.banner)
    val pagosExternos = ofrecePagosExternos(BuildConfig.TIENDA_PLAY)
    val tiendaPlay = vm.red.tiendaPlay.takeIf { ofrecePagosPlay(BuildConfig.TIENDA_PLAY, it != null) }
    val paquetesWompi by vm.red.paquetesWompi.collectAsStateWithLifecycle()
    val regaloWompi by vm.red.regaloWompi.collectAsStateWithLifecycle()
    val paquetesPlay = tiendaPlay?.paquetes?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(faltan > 0) {
        if (faltan > 0) { if (pagosExternos && paquetesWompi.isEmpty()) vm.red.cargarPaquetesWompi(); tiendaPlay?.cargar() }
    }
    fun comprarYPoner() { scope.launch {
        ocupada = true; error = null
        try {
            comprar()
            vm.red.ponerArticuloMercado(item)
            contadores.contar("tienda.tema_comprado")
            listo = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = (e as? ErrorSocial)?.error ?: "conexion" }
        finally { ocupada = false }
    } }
    LaunchedEffect(enEspera, temas.chispas) {
        if (enEspera && !tiene && temas.chispas >= precio) { enEspera = false; comprarYPoner() }
    }
    // Al volver del pago (Wompi abre el navegador) se mira el saldo de nuevo.
    DisposableEffect(ciclo, enEspera) {
        val observador = androidx.lifecycle.LifecycleEventObserver { _, evento ->
            if (evento == androidx.lifecycle.Lifecycle.Event.ON_RESUME && enEspera) vm.red.cargarTemas()
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    val tipo = tipoArticulo(item)
    val id = item.removePrefix("marco:")
    val vista = PerfilVista(
        nombre = cuenta?.nombre.orEmpty().ifBlank { usuario.orEmpty() }, usuario = usuario.orEmpty(), foto = "",
        portada = if (tipo == "banner" || tipo == "animado") item else temas.banner, bio = "", enlaces = emptyList(), privada = false, relacion = "",
        tema = if (tipo == "fondo") item else temas.puesto, marco = if (tipo == "marco") id else temas.marco,
    )
    Dialog(cerrar, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BarrasDelDialogo()
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(nombre, cerrar)
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                io.github.fogonapps.perfil.Perfil(vista, 0, {},
                    portada = { if (vista.portada.isNotBlank()) PortadaPerfilRed(vista.portada, Modifier.matchParentSize(), vm) },
                    foto = { AvatarRed(PersonaSocial(usuario.orEmpty(), cuenta?.nombre.orEmpty(), foto = !usuario.isNullOrEmpty()), 96.dp) },
                    enlace = {}, acciones = {})
            }
            Column(Modifier.fillMaxWidth().background(f.fondo).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(when { listo -> R.string.social_compra_lista; enEspera -> R.string.social_compra_en_espera; else -> R.string.social_probador_aviso }),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                error?.let { Text(stringResource(when (it) { "sin_chispas" -> R.string.social_regalo_sin_chispas; "deuda_chispas" -> R.string.social_deuda_chispas; else -> R.string.social_mercado_error }), color = f.texto) }
                val quitar = @Composable { m: Modifier -> BotonSecundario(stringResource(R.string.social_probador_quitar), cerrar, m) }
                when {
                    tiene || listo -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        quitar(Modifier.weight(1f))
                        BotonRiso(stringResource(if (puesto) R.string.social_temas_en_uso else R.string.social_temas_usar), { scope.launch {
                            ocupada = true
                            try { vm.red.ponerArticuloMercado(item) } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "conexion" }
                            finally { ocupada = false }
                        } }, Modifier.weight(1f), habilitado = !puesto && !ocupada)
                    }
                    faltan == 0 -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        quitar(Modifier.weight(1f))
                        BotonRiso(stringResource(R.string.social_probador_comprar, numeroSocial(precio)), { comprarYPoner() }, Modifier.weight(1.4f), habilitado = !ocupada)
                    }
                    else -> {
                        // La versión de Play no vende artículos sueltos: el paquete más chico que alcanza, a un toque.
                        val play = paquetesPlay.orEmpty().filterNot { it.unico }.sortedBy { it.chispas }
                        val paquetePlay = play.firstOrNull { it.chispas + it.regalo >= faltan } ?: play.lastOrNull()
                        val wompi = paquetesWompi.sorted()
                        val paqueteWompi = wompi.firstOrNull { it + (regaloWompi[it] ?: 0) >= faltan } ?: wompi.lastOrNull()
                        val chispasPaquete = paquetePlay?.chispas ?: paqueteWompi
                        Text(if (chispasPaquete != null) stringResource(R.string.social_faltan_con_paquete, numeroSocial(faltan), numeroSocial(chispasPaquete))
                            else stringResource(R.string.social_mercado_faltan, numeroSocial(faltan)), color = f.texto)
                        if (tiendaPlay != null && paquetePlay != null) BotonRiso(stringResource(R.string.social_comprar_paquete_play, numeroSocial(paquetePlay.chispas), paquetePlay.precio), {
                            val a = actividad ?: return@BotonRiso
                            contadores.contar("tienda.paquete_justo"); enEspera = true
                            tiendaPlay.comprar(a, paquetePlay.id)
                        }, Modifier.fillMaxWidth(), habilitado = !ocupada)
                        else if (pagosExternos && paqueteWompi != null) BotonRiso(stringResource(R.string.social_comprar_paquete_wompi, numeroSocial(paqueteWompi),
                            numeroSocial(paqueteWompi * TemasPerfil.PESOS_POR_CHISPA)), {
                            contadores.contar("tienda.paquete_justo"); ocupada = true
                            vm.red.comprarChispas(paqueteWompi) { url ->
                                ocupada = false
                                val abierta = url != null && runCatching {
                                    val uri = android.net.Uri.parse(url)
                                    require(uri.scheme == "https" && uri.host == "checkout.wompi.co")
                                    contexto.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                }.isSuccess
                                if (abierta) enEspera = true else error = "conexion"
                            }
                        }, Modifier.fillMaxWidth(), habilitado = !ocupada)
                        quitar(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** El probador de un artículo del catálogo oficial (se compra con chispas en la tienda). */
@Composable
internal fun ProbadorCatalogo(vm: InterfazSocial, item: String, cerrar: () -> Unit) {
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    val precio = TemasPerfil.PRECIOS[item] ?: 0
    Probador(vm, item, nombreArticulo(item), precio, tiene = precio == 0 || item in temas.reclamados,
        comprar = { vm.red.comprarTema(item) }, cerrar = cerrar)
}

/**
 * «Lo quiero»: al tocar el banner o el marco de otra persona, el artículo en una hoja. Si es del catálogo, llevarlo a
 * la tienda (con el probador abierto) o regalarlo; si es de un creador, el enlace al mercado.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HojaLoQuiero(vm: InterfazSocial, item: String, cerrar: () -> Unit) {
    var tienda by remember { mutableStateOf(false) }
    var mercado by remember { mutableStateOf<Long?>(null) }
    ContarEntrada("tienda.lo_quiero")
    // Abierta entera: a medio abrir, «Regalar» quedaba debajo de la barra de navegación.
    ModalBottomSheet(cerrar, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FogonTema.actual.fondo, contentColor = FogonTema.actual.texto) {
        ContenidoLoQuiero(vm, item, { tienda = true }, { mercado = it }, cerrar)
    }
    if (tienda) TiendaTemas(vm, inicio = item) { tienda = false; cerrar() }
    mercado?.let { AbrirFichaMercado(vm, it) { mercado = null; cerrar() } }
}

/** Lo de adentro de [HojaLoQuiero]: la muestra del artículo y qué se puede hacer con él. */
@Composable
fun ContenidoLoQuiero(vm: InterfazSocial, item: String, abrirTienda: () -> Unit, abrirMercado: (Long) -> Unit, cerrar: () -> Unit) {
    val f = FogonTema.actual
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.cargarTemas() }
    var regalar by remember { mutableStateOf(false) }
    var destino by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupada by remember { mutableStateOf(false) }
    val delMercado = DEL_MERCADO.matchEntire(item)?.groupValues?.get(2)?.toLongOrNull()
    val id = item.removePrefix("banner:").removePrefix("marco:")
    val precio = TemasPerfil.PRECIOS[item]
    Column(Modifier.fillMaxWidth().background(f.fondo).verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
        .padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (delMercado == null) MuestraArticulo(item, if (tipoArticulo(item) == "fondo") Modifier.width(140.dp) else Modifier)
            Text(tipoLegible(item).uppercase(), style = MaterialTheme.typography.labelMedium, color = f.textoSuave, letterSpacing = 1.2.sp)
            if (delMercado == null) Text(nombreArticulo(item), fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = f.texto)
            TemasPerfil.coleccionDe(item)?.let { Text(stringResource(it.nombre), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave) }
            when {
                delMercado != null -> {
                    Text(stringResource(R.string.social_lo_quiero_creador), color = f.texto)
                    BotonRiso(stringResource(R.string.social_ver_en_mercado), { abrirMercado(delMercado) }, Modifier.fillMaxWidth())
                }
                usuario == null -> Text(stringResource(R.string.social_lo_quiero_sin_cuenta), color = f.texto)
                precio == null -> Text(stringResource(R.string.social_lo_quiero_retirado), color = f.texto)
                precio == 0 -> {
                    Text(stringResource(R.string.social_lo_quiero_gratis), color = f.texto)
                    BotonRiso(stringResource(R.string.social_temas_usar), { vm.red.ponerTema(item); cerrar() }, Modifier.fillMaxWidth())
                }
                item !in temas.reclamados && !TemasPerfil.seOfrece(id) -> Text(stringResource(R.string.social_lo_quiero_retirado), color = f.texto)
                else -> {
                    Text(stringResource(R.string.social_precio_chispas, numeroSocial(precio)), style = MaterialTheme.typography.titleMedium, color = f.texto)
                    BotonRiso(stringResource(if (item in temas.reclamados) R.string.social_probador_tuyo else R.string.social_lo_quiero), abrirTienda, Modifier.fillMaxWidth())
                    BotonSecundario(stringResource(R.string.social_regalar), { regalar = !regalar }, Modifier.fillMaxWidth())
                    if (regalar) {
                        OutlinedTextField(destino, { destino = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_regalo_para)) }, singleLine = true, enabled = !ocupada)
                        BotonSecundario(stringResource(R.string.social_regalar), {
                            ocupada = true; mensaje = null
                            vm.red.regalar(item, destino) { e -> ocupada = false; mensaje = e ?: "listo"; if (e == null) { destino = ""; regalar = false } }
                        }, Modifier.fillMaxWidth(), habilitado = destino.isNotBlank() && !ocupada)
                    }
                    mensaje?.let { m -> Text(stringResource(when (m) {
                        "listo" -> R.string.social_regalo_enviado
                        "ya_lo_tiene" -> R.string.social_regalo_ya_tiene
                        "tope_regalos" -> R.string.social_regalo_tope
                        "bloqueado" -> R.string.social_regalo_bloqueado
                        "a_si_mismo" -> R.string.social_regalo_propio
                        "no_existe" -> R.string.social_regalo_no_existe
                        "sin_chispas" -> R.string.social_regalo_sin_chispas
                        "deuda_chispas" -> R.string.social_deuda_chispas
                        else -> R.string.social_regalo_error
                    }), color = f.texto) }
                }
            }
    }
}

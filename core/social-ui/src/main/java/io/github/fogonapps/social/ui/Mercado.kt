package io.github.fogonapps.social.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.network.httpHeaders
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** La misma pantalla en las cuatro apps: catálogo, publicaciones propias y cola del equipo. */
@Composable
fun Mercado(vm: InterfazSocial, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val scope = rememberCoroutineScope()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    var tipo by remember { mutableStateOf("banner") }
    var orden by remember { mutableStateOf("nuevo") }
    var oficial by remember { mutableStateOf(false) }
    var modo by remember { mutableStateOf("") }
    var pagina by remember { mutableStateOf(PaginaMercado()) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var publicar by remember { mutableStateOf(false) }
    var crear by remember { mutableStateOf(false) }
    var arteEditor by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var elegido by remember { mutableStateOf<ArticuloMercado?>(null) }
    suspend fun cargar(numero: Int) {
        cargando = true; error = null
        try {
            val nueva = vm.red.mercado(tipo, orden, oficial, numero, modo)
            pagina = if (numero == 0) nueva else nueva.copy(articulos = pagina.articulos + nueva.articulos)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = (e as? ErrorSocial)?.error ?: "conexion" }
        finally { cargando = false }
    }
    LaunchedEffect(usuario, tipo, orden, oficial, modo, revision) { pagina = PaginaMercado(); vm.red.cargarTemas(); cargar(0) }
    if (publicar) PublicarMercadoPantalla(vm, { publicar = false }, arteEditor) { publicar = false; modo = "mios"; revision++ }
    if (crear) EditorMercado(tipo, ClienteSocial.foto(usuario.orEmpty()), { crear = false }) { formato, bytes ->
        arteEditor = formato to bytes; crear = false; publicar = true
    }
    elegido?.let { articulo -> FichaMercado(vm, articulo, { elegido = null }) { elegido = null; revision++ } }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(stringResource(R.string.social_mercado), onCerrar)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.social_chispas_n, numeroSocial(temas.chispas)), color = f.texto, style = MaterialTheme.typography.titleLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PildoraSocial(stringResource(R.string.social_tienda_banners), acento = tipo == "banner") { tipo = "banner" }
                    PildoraSocial(stringResource(R.string.social_tienda_marcos), acento = tipo == "marco") { tipo = "marco" }
                    PildoraSocial(stringResource(R.string.social_mercado_oficial), acento = oficial) { oficial = !oficial }
                    PildoraSocial(stringResource(R.string.social_mercado_nuevo), acento = orden == "nuevo") { orden = "nuevo" }
                    PildoraSocial(stringResource(R.string.social_mercado_vendidos), acento = orden == "vendidos") { orden = "vendidos" }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PildoraSocial(stringResource(R.string.social_mercado_catalogo), acento = modo.isEmpty()) { modo = "" }
                    PildoraSocial(stringResource(R.string.social_mercado_adquiridos), acento = modo == "adquiridos") { modo = "adquiridos" }
                    PildoraSocial(stringResource(R.string.social_mercado_mios), acento = modo == "mios") { modo = "mios" }
                    if ("revisar_mercado" in cuenta?.permisos.orEmpty()) PildoraSocial(stringResource(R.string.social_mercado_revisar), acento = modo == "revision") { modo = "revision" }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonRiso(stringResource(R.string.social_editor_crear), { crear = true }, Modifier.weight(1f), habilitado = usuario != null)
                    BotonSecundario(stringResource(R.string.social_mercado_publicar), { arteEditor = null; publicar = true }, Modifier.weight(1f), habilitado = usuario != null)
                }
                if (error != null) { ErrorMercado(error!!); BotonSecundario(stringResource(R.string.social_mercado_reintentar), { revision++ }, Modifier.fillMaxWidth()) }
                if (pagina.articulos.isEmpty() && !cargando && error == null) Text(stringResource(R.string.social_mercado_vacio), color = f.textoSuave)
                pagina.articulos.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { a -> Column(Modifier.weight(1f).tarjetaFogon().clickable { elegido = a }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ImagenMercado(vm, a, Modifier.fillMaxWidth().height(90.dp))
                            if (a.oficial) Text(stringResource(R.string.social_mercado_oficial), color = f.tinta1, style = MaterialTheme.typography.labelSmall)
                            Text(a.nombre, color = f.texto, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.social_mercado_por, a.creador), color = f.textoSuave, style = MaterialTheme.typography.bodySmall)
                            Text(numeroSocial(a.precio), color = f.tinta1, fontFamily = f.puntos, style = MaterialTheme.typography.titleMedium)
                            if (modo.isNotEmpty()) { EstadoMercado(a.estado); Text(stringResource(R.string.social_mercado_ventas, numeroSocial(a.ventas)), color = f.textoSuave); if (a.motivo.isNotBlank()) Text(a.motivo, color = f.texto) }
                        } }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (cargando) Text(stringResource(R.string.social_mercado_cargando), color = f.textoSuave)
                pagina.siguiente?.let { n -> BotonSecundario(stringResource(R.string.social_mercado_mas), { scope.launch { cargar(n) } }, Modifier.fillMaxWidth(), habilitado = !cargando) }
            }
        }
    }
}

@Composable
private fun ImagenMercado(vm: InterfazSocial, a: ArticuloMercado, modifier: Modifier) {
    val ctx = LocalContext.current
    val cabeceras = vm.cabeceras()
    val solicitud = remember(a.id, cabeceras) { coil3.request.ImageRequest.Builder(ctx).data("${ClienteSocial.SERVIDOR}/v1/mercado/${a.id}/imagen")
        .httpHeaders(coil3.network.NetworkHeaders.Builder().apply { cabeceras.forEach { (k,v) -> set(k,v) } }.build())
        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED).diskCachePolicy(coil3.request.CachePolicy.DISABLED).build() }
    coil3.compose.AsyncImage(solicitud, a.nombre, modifier, contentScale = if (a.tipo == "marco") ContentScale.Fit else ContentScale.Crop)
}

@Composable private fun EstadoMercado(estado: String) { Text(stringResource(when (estado) {
    "aprobado" -> R.string.social_mercado_aprobado; "rechazado" -> R.string.social_mercado_rechazado
    "retirado" -> R.string.social_mercado_retirado; "derechos" -> R.string.social_mercado_derechos
    else -> R.string.social_mercado_revision
}), color = FogonTema.actual.textoSuave) }

@Composable private fun ErrorMercado(error: String) { Text(stringResource(when (error) {
    "sin_chispas" -> R.string.social_regalo_sin_chispas
    "tope_publicaciones" -> R.string.social_mercado_tope
    "mfa_requerida", "elevacion_requerida" -> R.string.social_mercado_elevar
    "marco_sin_alfa", "imagen", "dimensiones" -> R.string.social_mercado_imagen_error
    else -> R.string.social_mercado_error
}), color = FogonTema.actual.texto) }

@Composable
private fun FichaMercado(vm: InterfazSocial, a: ArticuloMercado, cerrar: () -> Unit, cambiado: () -> Unit) {
    val f = FogonTema.actual
    val scope = rememberCoroutineScope()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    var ocupada by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var destino by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var regalar by remember { mutableStateOf(false) }
    val clave = remember(a.id) { UUID.randomUUID().toString() }
    fun accion(bloque: suspend () -> Unit) { scope.launch {
        ocupada = true; error = null
        try { bloque(); cambiado() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = (e as? ErrorSocial)?.error ?: "conexion" }
        finally { ocupada = false }
    } }
    Dialog(cerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(a.nombre, cerrar)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Vista previa: el arte sobre la cabecera y foto de mi perfil.
                Box(Modifier.fillMaxWidth().height(160.dp).tarjetaFogon()) {
                    if (a.tipo == "banner") ImagenMercado(vm,a,Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            coil3.compose.AsyncImage(ClienteSocial.foto(usuario.orEmpty()), null, Modifier.size(58.dp), contentScale = ContentScale.Crop)
                            if (a.tipo == "marco") ImagenMercado(vm,a,Modifier.fillMaxSize())
                        }
                        Text("@${usuario.orEmpty()}", color = f.texto)
                    }
                }
                Text(stringResource(R.string.social_mercado_por,a.creador), color = f.textoSuave)
                Text(numeroSocial(a.precio), fontFamily = f.puntos, color = f.tinta1, style = MaterialTheme.typography.headlineLarge)
                EstadoMercado(a.estado)
                if (a.motivo.isNotBlank()) Text(a.motivo, color = f.texto)
                Text(stringResource(R.string.social_mercado_ventas,numeroSocial(a.ventas)), color = f.textoSuave)
                error?.let { ErrorMercado(it) }
                if ((a.adquirido || a.propio) && a.estado != "derechos") BotonRiso(stringResource(R.string.social_temas_usar), { accion { vm.red.ponerArticuloMercado(a.item) } }, modifier = Modifier.fillMaxWidth(), habilitado = !ocupada)
                if (!a.adquirido && !a.propio && a.estado == "aprobado") BotonRiso(stringResource(R.string.social_tienda_comprar,numeroSocial(a.precio)), { accion { vm.red.comprarMercado(a.id,clave) } }, modifier = Modifier.fillMaxWidth(), habilitado = !ocupada && temas.chispas >= a.precio)
                if (!a.propio && a.estado == "aprobado") BotonSecundario(stringResource(R.string.social_regalar), { regalar = !regalar }, modifier = Modifier.fillMaxWidth(), habilitado = !ocupada)
                if (regalar) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(destino,{ destino=it },label={ Text(stringResource(R.string.social_regalo_para)) }, modifier = Modifier.fillMaxWidth(), enabled = !ocupada)
                    BotonSecundario(stringResource(R.string.social_regalar), { ocupada=true; vm.red.regalar(a.item,destino) { resultado -> ocupada=false; error=resultado; if (resultado==null) cambiado() } }, modifier = Modifier.fillMaxWidth(), habilitado = destino.isNotBlank() && !ocupada)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(motivo,{ motivo=it.take(2000) }, label={ Text(stringResource(R.string.social_mercado_motivo)) }, modifier=Modifier.fillMaxWidth(), enabled = !ocupada)
                    if (a.estado == "aprobado" && !a.propio) BotonSecundario(stringResource(R.string.social_mercado_reportar), { accion { vm.red.reportarArticuloMercado(a.id,motivo) } }, modifier = Modifier.fillMaxWidth(), peligro = true, habilitado = !ocupada && motivo.isNotBlank())
                    if (!a.propio && "revisar_mercado" in cuenta?.permisos.orEmpty()) {
                        if (a.estado == "revision") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BotonRiso(stringResource(R.string.social_mercado_aprobar), { accion { vm.red.decidirMercado(a.id,"aprobar",motivo) } }, modifier = Modifier.weight(1f), habilitado = !ocupada)
                            BotonSecundario(stringResource(R.string.social_mercado_rechazar), { accion { vm.red.decidirMercado(a.id,"rechazar",motivo) } }, modifier = Modifier.weight(1f), habilitado = !ocupada && motivo.isNotBlank())
                        }
                        if (a.estado in setOf("aprobado","retirado")) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BotonSecundario(stringResource(R.string.social_mercado_retirar), { accion { vm.red.decidirMercado(a.id,"retirar",motivo) } }, modifier = Modifier.weight(1f), peligro = true, habilitado = !ocupada && motivo.isNotBlank())
                            BotonSecundario(stringResource(R.string.social_mercado_quitar_derechos), { accion { vm.red.decidirMercado(a.id,"derechos",motivo) } }, modifier = Modifier.weight(1f), peligro = true, habilitado = !ocupada && motivo.isNotBlank())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicarMercadoPantalla(vm: InterfazSocial, cerrar: () -> Unit, arte: Pair<String, ByteArray>? = null, publicado: () -> Unit) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val oficial = cuenta?.rol == "principal"
    var tipo by remember { mutableStateOf(arte?.first ?: "banner") }
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    var usarEditor by remember { mutableStateOf(arte != null) }
    var nombre by remember { mutableStateOf("") }
    var precio by remember { mutableFloatStateOf(600f) }
    var ocupada by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Se conserva la misma intención y la imagen subida si falla la respuesta de publicación.
    var borrador by remember { mutableStateOf<PublicarMercado?>(null) }
    val clave = remember { UUID.randomUUID().toString() }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) { uri=it; usarEditor=false; error=null } }
    Dialog(cerrar, DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !ocupada, dismissOnClickOutside = !ocupada)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(stringResource(R.string.social_mercado_publicar), { if (!ocupada) cerrar() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PildoraSocial(stringResource(R.string.social_tienda_banners), acento=tipo=="banner") { if (!ocupada && borrador==null && !usarEditor) tipo="banner" }
                    PildoraSocial(stringResource(R.string.social_tienda_marcos), acento=tipo=="marco") { if (!ocupada && borrador==null && !usarEditor) tipo="marco" }
                }
                if (usarEditor) coil3.compose.AsyncImage(arte?.second,null,Modifier.fillMaxWidth().height(140.dp),contentScale=ContentScale.Fit)
                uri?.let { coil3.compose.AsyncImage(it,null,Modifier.fillMaxWidth().height(140.dp),contentScale=ContentScale.Fit) }
                BotonSecundario(stringResource(R.string.social_mercado_elegir), { selector.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth(), habilitado = !ocupada && borrador==null)
                Text(stringResource(R.string.social_mercado_limites), color=f.textoSuave)
                OutlinedTextField(nombre,{ nombre=it.take(40) },label={ Text(stringResource(R.string.social_mercado_nombre)) }, modifier=Modifier.fillMaxWidth(),enabled = !ocupada && borrador==null)
                Text(numeroSocial(precio.toInt()),fontFamily=f.puntos,color=f.tinta1,style=MaterialTheme.typography.headlineLarge)
                Slider(precio,{ precio=it },valueRange=100f..2000f,steps=1899,enabled = !ocupada && borrador==null)
                Text(stringResource(R.string.social_mercado_rango),color=f.textoSuave)
                Row(Modifier.fillMaxWidth().height(16.dp)) { Box(Modifier.weight(1f).fillMaxHeight().background(f.tinta1)); Box(Modifier.weight(1f).fillMaxHeight().background(f.linea)) }
                Text(stringResource(R.string.social_mercado_reparto,numeroSocial(precio.toInt()/2),numeroSocial(precio.toInt()-precio.toInt()/2)),color=f.texto)
                Text(stringResource(if (oficial) R.string.social_mercado_publicacion_oficial else R.string.social_mercado_aviso),color=f.textoSuave)
                error?.let { ErrorMercado(it) }
                BotonRiso(stringResource(if (oficial) R.string.social_mercado_publicar else R.string.social_mercado_pagar), { scope.launch {
                    ocupada=true; error=null
                    try {
                        val d = borrador ?: run {
                            val bytes = if (usarEditor) requireNotNull(arte).second else prepararArteMercado(ctx,uri!!,tipo=="marco") ?: throw IllegalArgumentException("imagen")
                            val imagen = vm.red.subirImagenEscaparate(bytes)
                            PublicarMercado(clave,imagen,nombre.trim(),tipo,precio.toInt()).also { borrador=it }
                        }
                        vm.red.publicarMercado(d); publicado()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error=if (e is IllegalArgumentException) "imagen" else (e as? ErrorSocial)?.error ?: "conexion" }
                    finally { ocupada=false }
                } },modifier = Modifier.fillMaxWidth(), habilitado = !ocupada && (usarEditor || uri!=null) && nombre.isNotBlank())
            }
        }
    }
}

/** Abre el artículo desde la cola de reportes, con la autorización específica del mercado. */
@Composable
internal fun ReporteMercado(vm: InterfazSocial, caso: CasoModeracion) {
    var articulo by remember(caso.objeto) { mutableStateOf<ArticuloMercado?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var abierto by remember { mutableStateOf(false) }
    LaunchedEffect(caso.objeto) {
        try { articulo = vm.red.articuloMercado(caso.objeto.toLong()) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "conexion" }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp).tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.social_mercado), color = FogonTema.actual.texto)
        Text(caso.contenido, color = FogonTema.actual.textoSuave)
        Text(caso.motivos.entries.joinToString { (codigo, cantidad) -> "$codigo ×$cantidad" }, color = FogonTema.actual.textoSuave)
        error?.let { ErrorMercado(it) }
        articulo?.let { a ->
            ImagenMercado(vm,a,Modifier.fillMaxWidth().height(100.dp))
            BotonSecundario(stringResource(R.string.social_mercado_revisar), { abierto = true })
            if (abierto) FichaMercado(vm,a,{ abierto=false }) { abierto=false; vm.red.cargarCasos() }
        }
    }
}

package io.github.fogonapps.social.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.prepararImagen
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Editor local: ninguna capa ni borrador se sube hasta confirmar en Publicar. */
@Composable
internal fun EditorMercado(tipoInicial: String, fotoEjemplo: String?, cerrar: () -> Unit, publicar: (String, ByteArray) -> Unit) {
    val ctx = LocalContext.current
    val recursos = LocalResources.current
    val f = FogonTema.actual
    val marca = listOf(Paleta.Forzudo,Paleta.Ahorrador,Paleta.Cucha,Paleta.Cajon).firstOrNull { it.nombreTema == f.paleta.nombreTema } ?: f.paleta
    val colores = listOf(f.fondo, f.tinta1, f.tinta2, f.texto, marca.tinta, marca.segunda)
    val tintas = colores.map { it.toArgb() }
    val scope = rememberCoroutineScope()
    val archivo = remember(tipoInicial) { File(carpetaEditor(ctx), "$tipoInicial.json") }
    var lienzo by remember { mutableStateOf(LienzoMercado(tipoInicial)) }
    var historial by remember { mutableStateOf(HistorialMercado(lienzo)) }
    var cargado by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var seleccion by remember { mutableStateOf<Int?>(null) }
    var gestoFinalizado by remember { mutableIntStateOf(0) }
    var herramienta by remember { mutableStateOf("fondo") }
    var color by remember { mutableIntStateOf(1) }
    val dibujante = remember { DibujoMercado(ctx) }
    // Se libera al salir, después de que los trabajos del scope de composición se cancelen.
    DisposableEffect(dibujante) { onDispose { dibujante.cerrar() } }
    fun guardarAhora(): Boolean = runCatching {
        val a = android.util.AtomicFile(archivo); val salida = a.startWrite()
        try { salida.write(lienzo.serializar().toByteArray()); a.finishWrite(salida) }
        catch (e: Exception) { a.failWrite(salida); throw e }
    }.isSuccess
    fun salir() { if (!ocupado && cargado) { if (guardarAhora()) cerrar() else error = true } }
    fun cambiar(nuevo: LienzoMercado) { historial.aplicar(nuevo); lienzo = historial.actual }
    LaunchedEffect(archivo) {
        val guardado = withContext(Dispatchers.IO) {
            if (archivo.exists()) runCatching { LienzoMercado.leer(android.util.AtomicFile(archivo).openRead().bufferedReader().use { it.readText() }) } else null
        }
        guardado?.onSuccess { lienzo = it; historial = HistorialMercado(it) }?.onFailure { error = true }
        cargado = true
    }
    LaunchedEffect(lienzo, cargado) {
        if (cargado && !guardarAhora()) error = true
    }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            ocupado = true
            try {
                val bytes = prepararImagen(ctx,uri) ?: throw IllegalArgumentException("imagen")
                val nombre = "foto-${UUID.randomUUID()}.jpg"
                withContext(Dispatchers.IO) { archivoImagenEditor(ctx,nombre).writeBytes(bytes) }
                val nuevo = lienzo.agregar("imagen",nombre,color); cambiar(nuevo); seleccion = nuevo.capas.lastOrNull()?.id
            } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { ocupado = false }
        }
    }
    // El mismo bitmap de exportación se escala en pantalla: sin divergencia de texto o recortes.
    val vista = remember(lienzo,tintas,cargado) { if (cargado) runCatching { dibujante.dibujar(lienzo,tintas) }.getOrNull() else null }
    val actual by rememberUpdatedState(lienzo)
    val descripcionLienzo = stringResource(R.string.social_editor_gestos)
    val capa = lienzo.capas.find { it.id == seleccion }
    val puedeDeshacer = remember(lienzo, gestoFinalizado) { historial.puedeDeshacer }
    fun agregar(clase: String, contenido: String = "") {
        val nuevo = lienzo.agregar(clase,contenido,color)
        cambiar(nuevo); seleccion = nuevo.capas.lastOrNull()?.id
    }
    Dialog({ salir() }, DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !ocupado, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(stringResource(if (tipoInicial == "marco") R.string.social_editor_marco else R.string.social_editor_banner), { salir() })
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(lienzo.ancho.toFloat()/lienzo.alto)
                .background(f.superficie).semantics { contentDescription = descripcionLienzo }
                .pointerInput(cargado,ocupado) {
                    if (!cargado || ocupado) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val inicio = actual
                        val x = down.position.x/size.width*inicio.ancho; val y = down.position.y/size.height*inicio.alto
                        val hueco = inicio.tipo == "marco" && kotlin.math.hypot(x-256f,y-256f) < 180f
                        val tocada = if (hueco) null else inicio.capas.lastOrNull { dibujante.limites(it,inicio).contains(x,y) }
                        seleccion = tocada?.id
                        var movido = false
                        do {
                            val evento = awaitPointerEvent()
                            val pan = evento.calculatePan(); val zoom = evento.calculateZoom()
                            val c = actual.capas.find { it.id == tocada?.id }
                            if (c != null && (pan.getDistance() > 0 || zoom != 1f)) {
                                val nueva = c.copy(x=(c.x+pan.x/size.width).coerceIn(0f,1f), y=(c.y+pan.y/size.height).coerceIn(0f,1f), tamano=(c.tamano*zoom).coerceIn(.05f,3f))
                                lienzo = actual.cambiar(nueva); historial.transformar(lienzo); movido = true
                                evento.changes.forEach { it.consume() }
                            }
                        } while (evento.changes.any { it.pressed })
                        if (movido) { historial.terminarGesto(inicio); gestoFinalizado++ }
                    }
                }, contentAlignment = Alignment.Center) {
                if (tipoInicial == "marco") coil3.compose.AsyncImage(fotoEjemplo, stringResource(R.string.social_editor_foto_ejemplo),
                    Modifier.fillMaxSize(.703125f).clip(CircleShape).background(f.tinta2), contentScale = ContentScale.Crop)
                vista?.let { Image(it.asImageBitmap(), descripcionLienzo, Modifier.fillMaxSize()) }
                // El borde se dibuja solo en la vista previa, jamás en la exportación.
                capa?.let { c -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val r = dibujante.limites(c,lienzo)
                    Box(Modifier.offset(maxWidth*(r.left/lienzo.ancho),maxHeight*(r.top/lienzo.alto))
                        .size(maxWidth*(r.width()/lienzo.ancho),maxHeight*(r.height()/lienzo.alto)).border(2.dp,f.texto))
                } }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.social_editor_gestos), color=f.textoSuave)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("fondo" to R.string.social_editor_fondo,"trama" to R.string.social_editor_trama,"texto" to R.string.social_editor_texto,
                        "personaje" to R.string.social_editor_personajes,"formas" to R.string.social_editor_formas,"plantillas" to R.string.social_editor_plantillas).forEach { (id,t) ->
                        PildoraSocial(stringResource(t),acento=herramienta==id) { herramienta=id }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconoRiso(IconosFogon.Imagen, stringResource(R.string.social_editor_imagen), Modifier.size(24.dp), tinta = f.tinta1)
                    BotonSecundario(stringResource(R.string.social_editor_imagen), { selector.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, habilitado=cargado && !ocupado && lienzo.capas.size<40)
                }
                Text(stringResource(R.string.social_editor_colores),color=f.texto)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    colores.forEachIndexed { i,tinta ->
                        val nombre = stringResource(R.string.social_editor_color,i+1)
                        Box(Modifier.size(48.dp).clip(CircleShape).background(tinta).border(if(color==i) 4.dp else 1.dp,f.texto,CircleShape)
                            .semantics { contentDescription=nombre; selected=color==i }.clickable {
                                color=i
                                if (herramienta=="fondo") cambiar(lienzo.copy(fondo=i))
                                else if (capa!=null) cambiar(lienzo.cambiar(capa.copy(color=i)))
                            }, contentAlignment=Alignment.Center) { if(color==i) IconoRiso(IconosFogon.Chulo,null,tinta=legibleSobre(tinta,listOf(f.texto,f.fondo))) }
                    }
                }
                when(herramienta) {
                    "fondo" -> {
                        Row(verticalAlignment=Alignment.CenterVertically) { Switch(lienzo.degradado,{ cambiar(lienzo.copy(degradado=it)) },modifier=Modifier.semantics { contentDescription=recursos.getString(R.string.social_editor_degradado) }); Text(stringResource(R.string.social_editor_degradado),color=f.texto) }
                        BotonSecundario(stringResource(R.string.social_editor_segundo),{ cambiar(lienzo.copy(segundo=color)) })
                    }
                    "trama" -> {
                        Text(stringResource(R.string.social_editor_opacidad),color=f.texto)
                        Slider(lienzo.trama,{ cambiar(lienzo.copy(trama=it)) },modifier=Modifier.semantics { contentDescription=recursos.getString(R.string.social_editor_opacidad) })
                        Text(stringResource(R.string.social_editor_tamano),color=f.texto)
                        Slider(lienzo.paso,{ cambiar(lienzo.copy(paso=it)) },valueRange=8f..64f,modifier=Modifier.semantics { contentDescription=recursos.getString(R.string.social_editor_tamano) })
                    }
                    "texto" -> BotonSecundario(stringResource(R.string.social_editor_anadir_texto),{ agregar("texto",recursos.getString(R.string.social_editor_texto_inicial)) },habilitado=lienzo.capas.count { it.clase=="texto" }<3)
                    "personaje" -> {
                        // Recursos explícitos: todas las caras, celebraciones y la chispa, sin reflexión.
                        piezasMercado.entries.chunked(4).forEach { fila -> Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            fila.forEach { (nombre,recurso) ->
                                val descripcion=stringResource(R.string.social_editor_pieza,stringResource(nombresPiezasMercado.getValue(nombre)))
                                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(recurso),descripcion,
                                    Modifier.weight(1f).height(64.dp).clickable { agregar("personaje",nombre) })
                            }
                        } }
                    }
                    "formas" -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("circulo" to R.string.social_editor_circulo,"pildora" to R.string.social_editor_pildora,"estrella" to R.string.social_editor_estrella).forEach { (id,t) -> BotonSecundario(stringResource(t),{ agregar(id) }) }
                    }
                    "plantillas" -> {
                        listOf(R.string.social_editor_cancha,R.string.social_editor_noche,R.string.social_editor_riso,R.string.social_editor_grande).forEachIndexed { i,t ->
                            BotonSecundario(stringResource(t), {
                                val base=LienzoMercado(tipoInicial,fondo=if(i==1) 0 else 2,segundo=1,degradado=i<2,trama=if(i==2) 1f else .35f)
                                cambiar(when(i) {
                                    0 -> base.agregar("circulo",color=3).agregar("texto",recursos.getString(t),3).let { it.cambiar(it.capas.last().copy(x=.7f)) }
                                    1 -> base.agregar("estrella",color=1).agregar("texto",recursos.getString(t),3)
                                    2 -> base.agregar("pildora",color=1).agregar("texto",recursos.getString(t),0)
                                    else -> base.agregar("personaje","personaje_cajon_feliz").let { it.cambiar(it.capas.last().copy(tamano=1.2f)) }
                                }); seleccion=null
                            })
                        }
                    }
                }
                capa?.let { c ->
                    Text(stringResource(R.string.social_editor_capa,c.id),color=f.texto)
                    if(c.clase=="texto") OutlinedTextField(c.contenido,{ cambiar(lienzo.cambiar(c.copy(contenido=it.replace('\n',' ').take(100)))) },label={ Text(stringResource(R.string.social_editor_texto)) })
                    Text(stringResource(R.string.social_editor_tamano),color=f.texto)
                    Slider(c.tamano,{ cambiar(lienzo.cambiar(c.copy(tamano=it))) },valueRange=.05f..3f,modifier=Modifier.semantics { contentDescription=recursos.getString(R.string.social_editor_tamano) })
                    Text(stringResource(R.string.social_editor_horizontal),color=f.texto)
                    Slider(c.x,{ cambiar(lienzo.cambiar(c.copy(x=it))) },modifier=Modifier.semantics { contentDescription=recursos.getString(R.string.social_editor_horizontal) })
                    Text(stringResource(R.string.social_editor_vertical),color=f.texto)
                    Slider(c.y,{ cambiar(lienzo.cambiar(c.copy(y=it))) },modifier=Modifier.semantics { contentDescription=recursos.getString(R.string.social_editor_vertical) })
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        BotonSecundario(stringResource(R.string.social_editor_atras),{ cambiar(lienzo.ordenar(c.id,false)) })
                        BotonSecundario(stringResource(R.string.social_editor_adelante),{ cambiar(lienzo.ordenar(c.id,true)) })
                        BotonSecundario(stringResource(R.string.social_editor_borrar),{ cambiar(lienzo.borrar(c.id)); seleccion=null })
                    }
                }
                // Lista accesible: permite recuperar capas tapadas por otras y ordenar sin gestos.
                lienzo.capas.asReversed().forEach { c -> PildoraSocial(stringResource(R.string.social_editor_capa,c.id),acento=seleccion==c.id) { seleccion=c.id } }
                BotonSecundario(stringResource(R.string.social_editor_deshacer),{ lienzo=historial.deshacer(); seleccion=null },habilitado=puedeDeshacer)
                if(error) Text(stringResource(R.string.social_editor_error),color=f.texto)
            }
            BotonRiso(stringResource(R.string.social_editor_siguiente),{
                scope.launch { ocupado=true; error=false
                    try { check(guardarAhora()); val bytes=dibujante.exportar(lienzo,tintas); publicar(lienzo.tipo,bytes) }
                    catch(e: CancellationException) { throw e } catch(_: Exception) { error=true }
                    finally { ocupado=false }
                }
            },modifier=Modifier.padding(16.dp),habilitado=cargado && !ocupado && vista!=null)
        }
    }
}

private val nombresPiezasMercado = mapOf(
    "personaje_ahorrador_decidido" to R.string.social_editor_personaje_ahorrador_decidido,
    "personaje_ahorrador_dormido" to R.string.social_editor_personaje_ahorrador_dormido,
    "personaje_ahorrador_enamorado" to R.string.social_editor_personaje_ahorrador_enamorado,
    "personaje_ahorrador_feliz" to R.string.social_editor_personaje_ahorrador_feliz,
    "personaje_ahorrador_guino" to R.string.social_editor_personaje_ahorrador_guino,
    "personaje_ahorrador_miedo" to R.string.social_editor_personaje_ahorrador_miedo,
    "personaje_ahorrador_normal" to R.string.social_editor_personaje_ahorrador_normal,
    "personaje_ahorrador_parpadeo" to R.string.social_editor_personaje_ahorrador_parpadeo,
    "personaje_ahorrador_sorpresa" to R.string.social_editor_personaje_ahorrador_sorpresa,
    "personaje_ahorrador_triste" to R.string.social_editor_personaje_ahorrador_triste,
    "personaje_cajon_decidido" to R.string.social_editor_personaje_cajon_decidido,
    "personaje_cajon_dormido" to R.string.social_editor_personaje_cajon_dormido,
    "personaje_cajon_enamorado" to R.string.social_editor_personaje_cajon_enamorado,
    "personaje_cajon_feliz" to R.string.social_editor_personaje_cajon_feliz,
    "personaje_cajon_guino" to R.string.social_editor_personaje_cajon_guino,
    "personaje_cajon_miedo" to R.string.social_editor_personaje_cajon_miedo,
    "personaje_cajon_normal" to R.string.social_editor_personaje_cajon_normal,
    "personaje_cajon_parpadeo" to R.string.social_editor_personaje_cajon_parpadeo,
    "personaje_cajon_sorpresa" to R.string.social_editor_personaje_cajon_sorpresa,
    "personaje_cajon_triste" to R.string.social_editor_personaje_cajon_triste,
    "personaje_cucha_decidido" to R.string.social_editor_personaje_cucha_decidido,
    "personaje_cucha_dormido" to R.string.social_editor_personaje_cucha_dormido,
    "personaje_cucha_enamorado" to R.string.social_editor_personaje_cucha_enamorado,
    "personaje_cucha_feliz" to R.string.social_editor_personaje_cucha_feliz,
    "personaje_cucha_guino" to R.string.social_editor_personaje_cucha_guino,
    "personaje_cucha_miedo" to R.string.social_editor_personaje_cucha_miedo,
    "personaje_cucha_normal" to R.string.social_editor_personaje_cucha_normal,
    "personaje_cucha_parpadeo" to R.string.social_editor_personaje_cucha_parpadeo,
    "personaje_cucha_sorpresa" to R.string.social_editor_personaje_cucha_sorpresa,
    "personaje_cucha_triste" to R.string.social_editor_personaje_cucha_triste,
    "personaje_forzudo_decidido" to R.string.social_editor_personaje_forzudo_decidido,
    "personaje_forzudo_dormido" to R.string.social_editor_personaje_forzudo_dormido,
    "personaje_forzudo_enamorado" to R.string.social_editor_personaje_forzudo_enamorado,
    "personaje_forzudo_feliz" to R.string.social_editor_personaje_forzudo_feliz,
    "personaje_forzudo_guino" to R.string.social_editor_personaje_forzudo_guino,
    "personaje_forzudo_miedo" to R.string.social_editor_personaje_forzudo_miedo,
    "personaje_forzudo_normal" to R.string.social_editor_personaje_forzudo_normal,
    "personaje_forzudo_parpadeo" to R.string.social_editor_personaje_forzudo_parpadeo,
    "personaje_forzudo_sorpresa" to R.string.social_editor_personaje_forzudo_sorpresa,
    "personaje_forzudo_triste" to R.string.social_editor_personaje_forzudo_triste,
    "celebrar_ahorrador" to R.string.social_editor_celebrar_ahorrador,
    "celebrar_cajon" to R.string.social_editor_celebrar_cajon,
    "celebrar_cucha" to R.string.social_editor_celebrar_cucha,
    "celebrar_forzudo" to R.string.social_editor_celebrar_forzudo,
    "chispa" to R.string.social_editor_chispa,
)

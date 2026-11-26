package io.github.fogonapps.cajon.ui

import io.github.fogonapps.identidad.destinoGuia
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.fogonapps.social.Frases
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.Pestana
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.R as RId

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
fun Inicio(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val anio = vm.hoy().year
    val c = vm.L.cifras(e, anio)

    // Arriba: tu nombre en grande con una frase cercana, publicar y tu foto (abre tu perfil).
    // La frase: se elige con el momento, los votos de la comunidad y lo que dijo la persona (ver `Frases`), y se queda
    // toda la franja del día (mañana, tarde o noche) aunque se cierre la app. Tocarla pasa a otra.
    val comunidad by vm.frasesComunidad.collectAsStateWithLifecycle()
    val deLaGente by vm.frasesDeLaGente.collectAsStateWithLifecycle()
    val momento by vm.momento.collectAsStateWithLifecycle()
    val ocultas by vm.frasesOcultas.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarFrases(); vm.cargarMomento() }
    var vuelta by rememberSaveable { mutableStateOf(0) }
    var menuFrase by remember { mutableStateOf(false) }
    val hora = java.time.LocalTime.now().hour
    val franja = "${vm.hoy()}-${franjaDe(hora)}"
    val frase = remember(franja, vuelta, e.ajustes.frasesNo, deLaGente, ocultas) {
        val guardada = if (vuelta == 0) vm.fraseGuardada(franja)?.takeIf { it.first !in e.ajustes.frasesNo && it.first !in ocultas }?.let { (c, o) -> fraseDeClave(c, o, deLaGente) } else null
        guardada ?: fraseDelDia(e, vm.hoy().dayOfYear, hora, comunidad, vuelta, momento, deLaGente, ocultas).also { vm.guardarFrase(franja, it.clave, it.obra) }
    }
    val textoDeFrase = textoFrase(frase)
    val gusta = frase.clave in e.ajustes.frasesGusta
    // Arriba, lo empezado en grande sobre el color de su portada (ver InicioVivo.kt); la cabecera queda encima del mismo fondo.
    val enCurso = e.vivas.filter { it.estado == Estados.EN_CURSO }.sortedByDescending { it.cambio }
    var enfocada by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxWidth()) {
    FondoDePortada(enfocada?.takeIf { u -> enCurso.any { it.portada == u } }?.let { io.github.fogonapps.cajon.datos.Imagenes.url(it, io.github.fogonapps.cajon.datos.Imagenes.Lugar.LISTA, true) }, Modifier.matchParentSize())
    Column(Modifier.fillMaxWidth()) {
    io.github.fogonapps.perfil.CabeceraInicio(e.ajustes.nombre.ifBlank { stringResource(R.string.hola) }, textoDeFrase,
        onMantener = { menuFrase = true },
        adorno = { if (gusta) IconoRiso(Iconos.Corazon, stringResource(R.string.frase_te_gusta), tamano = 14.dp, tinta = f.texto) },
        acciones = { BotonIcono(Iconos.Lapiz, stringResource(R.string.red_publicar), grande = true, lleno = true) { vm.abrir(if (vm.red.usuario.value == null) Pantalla.CuentaRed else Pantalla.Publicar()) } },
        perfil = { androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) { io.github.fogonapps.social.ui.ChispasArriba(interfazSocial(vm)); androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.destinoGuia("perfil")) {
            io.github.fogonapps.identidad.BotonPerfilMascota(io.github.fogonapps.identidad.Quien.CAJON, stringResource(R.string.tu_perfil),
                foto = e.perfil.foto.takeIf { it.isNotBlank() }?.let { { FotoPerfil(it, 48.dp, e.perfil.encuadreFoto) } }) { vm.abrirMiPerfil() }
        }}})

    AvisoCajonSinGuardar(vm)
    if (enCurso.isNotEmpty()) EmpezadoVivo(enCurso, vm) { enfocada = it.portada }
    }
    }

    if (menuFrase) io.github.fogonapps.social.ui.MenuFrase(textoDeFrase, gusta, cerrar = { menuFrase = false },
        onGusta = { vm.votarFrase(frase.clave, if (gusta) 0 else 1) },
        onNoMostrar = { vm.votarFrase(frase.clave, -1); vuelta++ },
        onCompartido = { vm.votarFrase(frase.clave, 1) },
        // Proponer una frase propia: el equipo la revisa y, si va, sale para todos.
        onProponer = { propia -> if (vm.red.usuario.value == null) vm.abrir(Pantalla.CuentaRed) else vm.proponerFrase(propia) })

    if (e.vivas.isEmpty()) {
        Vacio(Iconos.Cajon, stringResource(R.string.vacio_titulo), stringResource(R.string.vacio_texto), boton = stringResource(R.string.agregar_lo_primero)) {
            vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.TODO))
        }
        // O traer de una vez lo que ya se lleva en otra app.
        TraerHistorial(LocalAcciones.current, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        return
    }

    // Recién traído de otra app: mirar los números del año.
    val traidas by vm.recienImportado.collectAsStateWithLifecycle()
    traidas?.let { n -> AvisoImportado(n, { vm.olvidarImportado(); abrirResumen(vm) }, vm::olvidarImportado) }
    // En diciembre y enero, el resumen del año a la vista (si el año tiene con qué).
    val anioResumen = anioDelResumen(vm)
    val resumen = remember(e, anioResumen) { if (io.github.fogonapps.cajon.datos.ResumenAnio.esTemporada(vm.hoy())) io.github.fogonapps.cajon.datos.ResumenAnio.de(e, anioResumen) else null }
    if (traidas == null && resumen != null && !resumen.pocosDatos) AccesoResumen(anioResumen, resumen.recientes, { abrirResumen(vm, anioResumen) }, Modifier.aparecer(0))

    // Debajo de lo empezado, sin tarjetas: lo terminado este año (la cifra grande en la letra de títulos, con las
    // portadas de lo último en abanico) y «¿Qué te pareció?» con lo último sin nota, su portada y las estrellas.
    val total = c.porTipo.values.sum()
    val sinNota = e.vivas.filter { (it.estado == Estados.TERMINADO || it.estado == Estados.EN_CURSO) && it.nota == 0 }.maxByOrNull { it.cambio }
    Row(Modifier.aparecer(0).fillMaxWidth().clip(forma(20.dp)).clickable { vm.abrir(Pantalla.Cifras) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val enCajon = total == 0
        NumeroQueRueda(if (enCajon) e.vivas.size else total) {
            Text("$it", fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = 54.sp, lineHeight = 56.sp, color = f.texto)
        }
        Text(if (enCajon) stringResource(R.string.en_tu_cajon) else pluralStringResource(R.plurals.terminadas_en, total, total, anio),
            style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.weight(1f),
            // Una línea que se achica: junto a las portadas, el año bajaba solo a otra línea.
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = MaterialTheme.typography.titleSmall.fontSize))
        val ultimas = e.vivas.filter { it.estado == Estados.TERMINADO }.sortedByDescending { it.terminada }.take(4)
        if (ultimas.isNotEmpty()) Box(Modifier.width((40 + 18 * (ultimas.size - 1)).dp).height(64.dp)) {
            ultimas.reversed().forEachIndexed { i, o ->
                val giro = (i - (ultimas.size - 1) / 2f) * 5f
                Portada(o.portada, o.tipo, o.titulo, Modifier.width(40.dp).align(Alignment.CenterStart).offset(x = (18 * i).dp)
                    .graphicsLayer { rotationZ = giro }.shadow(4.dp, forma(5.dp), clip = false), esquina = 5.dp, conTitulo = false)
            }
        }
    }
    if (sinNota != null) {
        val o = sinNota
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(64.dp).clip(forma(8.dp)).clickable { vm.abrir(Pantalla.Ficha(o.id)) }, esquina = 8.dp, conTitulo = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.que_te_parecio), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                Text(o.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // (No es tocable: la portada de al lado abre la ficha; un título tocable quedaba chico y pegado al lápiz.)
                )
                // La nota se ve primero (las estrellas se encienden y, con la máxima, sueltan chispas) y un momento
                // después se guarda; entonces pasa a lo siguiente sin calificar.
                var notaVista by remember(o.id) { mutableStateOf(0) }
                LaunchedEffect(o.id, notaVista) {
                    if (notaVista > 0) { kotlinx.coroutines.delay(if (f.menosMovimiento) 250 else 1100); vm.calificarRapido(o.id, notaVista) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ElegirEstrellas(notaVista, { n -> notaVista = n }, tamano = 30.dp, descripcion = stringResource(R.string.que_te_parecio))
                    Spacer(Modifier.weight(1f))
                    BotonIcono(Iconos.Lapiz, stringResource(R.string.escribir_resena)) { vm.abrir(Pantalla.Registrar(o.id)) }
                }
            }
        }
    }

    // Lo último de tu gente en la red (o la invitación a entrar).
    FeedInicio(vm)

    // «Para ti» se mudó a Descubrir.

    // El diario: lo último.
    val diario = vm.L.diario(e).take(5)
    if (diario.isNotEmpty()) {
        Seccion(stringResource(R.string.tu_diario)) { Pildora(stringResource(R.string.ver_todo)) { vm.abrir(Pantalla.Diario) } }
        diario.forEachIndexed { i, d ->
            FilaVidrio({ vm.abrir(Pantalla.Ficha(d.obra.id)) }, Modifier.aparecer(i + 1)) {
                Portada(d.obra.portada, d.obra.tipo, d.obra.titulo, Modifier.width(40.dp), esquina = 6.dp, conTitulo = false)
                Column(Modifier.weight(1f)) {
                    Text(d.obra.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fechaCorta(d.registro.fecha, vm.idioma()), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        Estrellas(d.registro.nota.takeIf { it > 0 } ?: d.obra.nota, 12.sp)
                        if (d.registro.otraVez) IconoRiso(Iconos.Repetir, stringResource(R.string.otra_vez), tamano = 14.dp, tinta = f.textoSuave)
                    }
                }
                if (d.obra.meGusta) IconoRiso(Iconos.Corazon, stringResource(R.string.me_gusta), tamano = 18.dp, tinta = f.texto, relleno = f.texto)
            }
        }
    }

    // Los pendientes más recientes.
    val pendientes = e.vivas.filter { it.estado == Estados.QUIERO }.sortedByDescending { it.agregada }
    if (pendientes.isNotEmpty()) {
        Seccion(stringResource(R.string.pendientes))
        Carrusel(pendientes.take(12), vm, conProgreso = false)
    }

    // Sus listas.
    val listas = e.listasVivas.sortedByDescending { it.cambio }
    // «Ver todo» lleva siempre a Listas (ahí están también las listas en común, aunque no tengas listas propias).
    Seccion(stringResource(R.string.pestana_listas)) {
        if (listas.isEmpty()) Pildora(stringResource(R.string.nueva)) { vm.abrir(Pantalla.EditarLista(null)) }
        Pildora(stringResource(R.string.ver_todo)) { vm.abrir(Pantalla.Listas) }
    }
    if (listas.isEmpty()) Pista(stringResource(R.string.listas_vacio))
    else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(listas, key = { it.id }) { l ->
            val obras = obrasDe(e, l)
            // Una lista es una pila de sus portadas, la primera delante y las otras asomando detrás.
            Column(Modifier.width(150.dp).clip(forma(12.dp)).clickable { vm.abrir(Pantalla.VerLista(l.id)) }.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    val pila = obras.take(3)
                    pila.reversed().forEachIndexed { i, it ->
                        val atras = pila.size - 1 - i
                        Portada(it.portada, it.tipo, it.titulo, Modifier.width(96.dp).align(Alignment.BottomStart).offset(x = (22 * atras).dp, y = (-6 * atras).dp)
                            .graphicsLayer { alpha = 1f - 0.18f * atras }.shadow(if (atras == 0) 8.dp else 2.dp, forma(8.dp), clip = false), esquina = 8.dp, conTitulo = false)
                    }
                    if (obras.isEmpty()) Box(Modifier.size(96.dp, 144.dp).align(Alignment.BottomStart).clip(forma(8.dp)).background(androidx.compose.ui.graphics.lerp(f.fondo, f.texto, 0.07f)), contentAlignment = Alignment.Center) {
                        IconoRiso(Iconos.Lista, null, tamano = 28.dp, tinta = f.textoSuave)
                    }
                }
                Text(l.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
        }
    }


    // Los retos del año, si tiene.
    if (e.ajustes.retos.isNotEmpty()) {
        Seccion(stringResource(R.string.retos_del_anio, anio)) { Pildora(stringResource(R.string.cambiar)) { vm.abrir(Pantalla.Retos) } }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            e.ajustes.retos.forEach { (t, meta) ->
                val lleva = c.porTipo[t] ?: 0
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconoRiso(Iconos.de(t), null, tamano = 18.dp, tinta = f.texto)
                        Text(nombreTipo(t), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text("$lleva / $meta", fontFamily = f.cifras, color = f.texto)
                    }
                    Barra(lleva.toFloat() / meta.coerceAtLeast(1), f.tinta1)
                }
            }
        }
    }
}

/**
 * Una fila de portadas que se desliza; con [conProgreso], cada una lleva su barra y lo que sigue. Mantener apretada
 * una portada abre el registro en dos toques ([RegistroRapido]).
 */
@Composable
fun Carrusel(obras: List<Obra>, vm: CajonViewModel, conProgreso: Boolean) {
    val f = FogonTema.actual
    var rapida by rememberSaveable { mutableStateOf<String?>(null) }
    val registrarRapido = stringResource(R.string.rapido_registrar)
    rapida?.let { id -> obras.firstOrNull { it.id == id }?.let { o -> RegistroRapido(vm, o) { rapida = null } } }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(obras, key = { it.id }) { o ->
            Column(Modifier.width(112.dp).clip(forma(12.dp))
                .combinedClickable(onLongClickLabel = registrarRapido, onLongClick = { rapida = o.id }) { vm.abrir(Pantalla.Ficha(o.id)) }.animateItem(),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (conProgreso) {
                    vm.L.progreso(o)?.let { Barra(it, f.tinta1) }
                    val sigue = if (o.tipo == Tipos.LIBRO && o.pagina > 0) stringResource(R.string.va_en_pagina, o.pagina) else null
                    if (sigue != null) Text(sigue, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                } else Text(nombreTipo(o.tipo, plural = false), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
            }
        }
    }
}



/** La frase del saludo: una de la casa, o una sobre lo que tienes en curso o acabas de terminar. */
/** La frase del saludo: su [clave] (para los votos), su texto y, si habla de algo, el nombre de eso. */
data class Frase(val clave: String, val texto: Int, val obra: String? = null, val literal: String? = null)

/**
 * Elige la frase del saludo. Las candidatas salen del momento (mañana, tarde, noche) y de lo que la persona tiene:
 * lo que va viendo, lo último que terminó (y si le encantó), lo que acaba de guardar para después, el disco que
 * escucha. Cada una con su peso, y [Frases.elegir] las mezcla con los votos de la comunidad y los suyos. [vuelta]
 * cambia el sorteo (tocar la frase).
 */
fun fraseDelDia(
    e: Estado, dia: Int, hora: Int, comunidad: Map<String, Frases.Votos> = emptyMap(), vuelta: Int = 0,
    momento: CajonViewModel.DelMomento = CajonViewModel.DelMomento(), deLaGente: List<io.github.fogonapps.cajon.datos.FraseComunidad> = emptyList(),
    ocultas: Set<String> = emptySet(),
): Frase {
    val ahora = System.currentTimeMillis()
    val dias = { d: Int -> ahora - d * 86_400_000L }
    val enCurso = e.vivas.filter { it.estado == Estados.EN_CURSO }.maxByOrNull { it.cambio }
    val terminada = e.vivas.filter { it.estado == Estados.TERMINADO }.maxByOrNull { it.cambio }
    val encanto = e.vivas.filter { it.estado == Estados.TERMINADO && it.nota >= 4 && it.cambio > dias(14) }.maxByOrNull { it.cambio }
    val nueva = e.vivas.filter { it.estado == Estados.QUIERO && it.cambio > dias(7) }.maxByOrNull { it.cambio }
    val disco = e.vivas.filter { it.tipo == Tipos.ALBUM && it.creador.isNotBlank() && it.estado != Estados.QUIERO }.maxByOrNull { it.cambio }
    // Lo del momento hace frases nuevas solas: lo que todos ven, escuchan o leen esta semana (lo que no tenga ya).
    val propias = e.vivas.map { it.titulo.lowercase() }.toSet()
    fun delMomento(t: String) = (momento.obras[t].orEmpty().map { it.titulo } + momento.buscados[t].orEmpty().map { it.titulo })
        .firstOrNull { it.lowercase() !in propias }
    val discoDeLaSemana = momento.obras[Tipos.ALBUM].orEmpty().firstOrNull { it.titulo.lowercase() !in propias }
    val obras = buildMap {
        enCurso?.let { put("frase_seguimos", it.titulo) }
        terminada?.takeIf { it.nota == 0 }?.let { put("frase_que_tal", it.titulo) }
        encanto?.let { put("frase_te_gusto", it.titulo) }
        nueva?.let { put("frase_play", it.titulo) }
        disco?.let { put("frase_artista", it.creador) }
        delMomento(Tipos.PELICULA)?.let { put("frase_momento_peli", it) }
        delMomento(Tipos.SERIE)?.let { put("frase_momento_serie", it) }
        delMomento(Tipos.ANIME)?.let { put("frase_momento_anime", it) }
        discoDeLaSemana?.let { put("frase_momento_disco", if (it.creador.isBlank()) it.titulo else "${it.titulo} (${it.creador})") }
        delMomento(Tipos.LIBRO)?.let { put("frase_momento_libro", it) }
    }
    val candidatas = buildMap {
        obras.keys.forEach { put(it, 1.5) }
        (when (hora) { in 5..11 -> FRASES_MANANA; in 12..18 -> FRASES_TARDE; else -> FRASES_NOCHE }).forEach { put(it, 1.3) }
        FRASES_SIEMPRE.forEach { put(it, 1.0) }
        // Las de la gente y del equipo: las de todo el día y las de esta franja (que pesan más).
        val ahoraFranja = franjaDe(hora)
        deLaGente.filter { it.franja == "todo" || it.franja == ahoraFranja }.forEach { put(it.clave, if (it.franja == "todo") 1.0 else 1.3) }
    }
    val clave = Frases.elegir(candidatas, comunidad, e.ajustes.frasesGusta.toSet(), e.ajustes.frasesNo.toSet() + ocultas, (dia * 24L + hora) * 7919 + vuelta)
        ?: FRASES_SIEMPRE.first()
    return fraseDeClave(clave, obras[clave], deLaGente) ?: Frase(FRASES_SIEMPRE.first(), TEXTOS_FRASES.getValue(FRASES_SIEMPRE.first()))
}

/** Arma la frase de una clave (de la app, con su obra, o de la gente); null si ya no existe. */
fun fraseDeClave(clave: String, obra: String?, deLaGente: List<io.github.fogonapps.cajon.datos.FraseComunidad>): Frase? {
    TEXTOS_FRASES[clave]?.let { r -> return if (clave in CON_OBRA && obra == null) null else Frase(clave, r, obra) }
    return deLaGente.firstOrNull { it.clave == clave }?.let { Frase(clave, 0, literal = it.texto) }
}

private val CON_OBRA = setOf("frase_seguimos", "frase_que_tal", "frase_te_gusto", "frase_play", "frase_artista",
    "frase_momento_peli", "frase_momento_serie", "frase_momento_anime", "frase_momento_disco", "frase_momento_libro")
/** La franja del día de una hora: madrugada (0 a 4), mañana (5 a 11), tarde (12 a 18) o noche (19 a 23). */
fun franjaDe(hora: Int) = when (hora) { in 0..4 -> "madrugada"; in 5..11 -> "manana"; in 12..18 -> "tarde"; else -> "noche" }

/** La franja de una frase de la app ("todo" si sale a cualquier hora; las de noche también salen de madrugada). */
fun franjaDeFrase(clave: String) = when (clave) { in FRASES_MANANA -> "manana"; in FRASES_TARDE -> "tarde"; in FRASES_NOCHE -> "noche"; else -> "todo" }

private val FRASES_MANANA = listOf("frase_manana_1", "frase_manana_2")
private val FRASES_TARDE = listOf("frase_tarde_1", "frase_tarde_2")
private val FRASES_NOCHE = listOf("frase_noche_1", "frase_noche_2", "frase_noche_3")
private val FRASES_SIEMPRE = (1..22).map { "frase_$it" }
internal val TEXTOS_FRASES = mapOf(
    "frase_seguimos" to R.string.frase_seguimos, "frase_que_tal" to R.string.frase_que_tal, "frase_te_gusto" to R.string.frase_te_gusto,
    "frase_play" to R.string.frase_play, "frase_artista" to R.string.frase_artista,
    "frase_momento_peli" to R.string.frase_momento_peli, "frase_momento_serie" to R.string.frase_momento_serie,
    "frase_momento_anime" to R.string.frase_momento_anime, "frase_momento_disco" to R.string.frase_momento_disco,
    "frase_momento_libro" to R.string.frase_momento_libro,
    "frase_manana_1" to R.string.frase_manana_1, "frase_manana_2" to R.string.frase_manana_2,
    "frase_tarde_1" to R.string.frase_tarde_1, "frase_tarde_2" to R.string.frase_tarde_2,
    "frase_noche_1" to R.string.frase_noche_1, "frase_noche_2" to R.string.frase_noche_2, "frase_noche_3" to R.string.frase_noche_3,
    "frase_1" to R.string.frase_1, "frase_2" to R.string.frase_2, "frase_3" to R.string.frase_3, "frase_4" to R.string.frase_4,
    "frase_5" to R.string.frase_5, "frase_6" to R.string.frase_6, "frase_7" to R.string.frase_7, "frase_8" to R.string.frase_8,
    "frase_9" to R.string.frase_9, "frase_10" to R.string.frase_10, "frase_11" to R.string.frase_11, "frase_12" to R.string.frase_12,
    "frase_13" to R.string.frase_13, "frase_14" to R.string.frase_14, "frase_15" to R.string.frase_15, "frase_16" to R.string.frase_16,
    "frase_17" to R.string.frase_17, "frase_18" to R.string.frase_18, "frase_19" to R.string.frase_19, "frase_20" to R.string.frase_20,
    "frase_21" to R.string.frase_21, "frase_22" to R.string.frase_22,
)

@Composable
fun textoFrase(f: Frase): String = f.literal ?: if (f.obra != null) stringResource(f.texto, f.obra) else stringResource(f.texto)

/**
 * D-033: desde esta versión, entrar con el código ya pide la contraseña. Esto queda solo para quien entró antes así
 * (cajón bloqueado) o tiene una cuenta sin contraseña: se resuelve ahí mismo, escribiendo la contraseña, sin ir a otra
 * pantalla. Sin correo no se puede poner contraseña, y eso sigue en Ajustes → «Tu cajón en tu cuenta».
 */
@Composable
private fun AvisoCajonSinGuardar(vm: CajonViewModel) {
    val f = FogonTema.actual
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val bloqueado by vm.red.cajonBloqueado.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    // La llave no es un flujo: se mira cada segundo mientras Inicio está a la vista, y al abrirse la tarjeta se va.
    val conLlave by androidx.compose.runtime.produceState(vm.hayLlaveCajon(), usuario, bloqueado) {
        while (true) { value = vm.hayLlaveCajon(); kotlinx.coroutines.delay(1000) }
    }
    val c = cuenta
    if (usuario == null || c == null || c.correo.isBlank() || (!bloqueado && conLlave)) return
    val crear = !bloqueado && !c.conClave
    var clave by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.nube_aviso_titulo), fontFamily = f.titulos, fontSize = 20.sp, color = f.texto)
        Text(stringResource(if (crear) R.string.nube_aviso_sin_clave else R.string.nube_aviso_bloqueado), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        androidx.compose.material3.OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(if (crear) R.string.red_clave_nueva else R.string.red_clave)) }, singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            supportingText = if (crear) ({ Text(stringResource(R.string.red_clave_regla)) }) else null)
        BotonRiso(stringResource(if (crear) R.string.nube_activar else R.string.nube_abrir), {
            if (crear) vm.red.ponerClave(clave, "") { clave = "" } else { vm.red.desbloquearCajon(clave); clave = "" }
        }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8 && sync.estado != io.github.fogonapps.cajon.EstadoSync.SINCRONIZANDO)
    }
}

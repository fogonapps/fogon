package io.github.fogonapps.cajon.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import io.github.fogonapps.identidad.tarjetaFogon
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.fogonapps.cajon.datos.Frases
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
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
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos
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
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(e.ajustes.nombre.ifBlank { stringResource(R.string.hola) }, fontFamily = f.titulos, fontSize = 30.sp, color = f.texto, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(textoDeFrase, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false).clip(forma(8.dp))
                        .combinedClickable(onClick = {}, onLongClick = { menuFrase = true }))
                if (gusta) IconoRiso(Iconos.Corazon, stringResource(R.string.frase_te_gusta), tamano = 14.dp, tinta = f.acentoTexto)
            }
        }
        BotonIcono(Iconos.Lapiz, stringResource(R.string.red_publicar), grande = true, lleno = true) { vm.abrir(if (vm.red.usuario.value == null) Pantalla.CuentaRed else Pantalla.Publicar()) }
        FotoPerfil(e.perfil.foto, 48.dp, descripcion = stringResource(R.string.tu_perfil)) { vm.abrirMiPerfil() }
    }

    if (menuFrase) {
        val contexto = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.ModalBottomSheet({ menuFrase = false }, containerColor = f.fondo) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(textoDeFrase, style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                FilaVidrio({ vm.votarFrase(frase.clave, if (gusta) 0 else 1); menuFrase = false }) {
                    IconoRiso(Iconos.Corazon, null, tamano = 20.dp, tinta = f.acentoTexto)
                    Text(stringResource(if (gusta) R.string.frase_ya_no_gusta else R.string.frase_me_gusta), color = f.texto, modifier = Modifier.weight(1f))
                }
                FilaVidrio({
                    vm.votarFrase(frase.clave, 1)
                    contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, textoDeFrase), null))
                    menuFrase = false
                }) {
                    IconoRiso(Iconos.Compartir, null, tamano = 20.dp, tinta = f.texto)
                    Text(stringResource(R.string.compartir), color = f.texto, modifier = Modifier.weight(1f))
                }
                FilaVidrio({ vm.votarFrase(frase.clave, -1); vuelta++; menuFrase = false }) {
                    IconoRiso(Iconos.Cerrar, null, tamano = 20.dp, tinta = f.texto)
                    Text(stringResource(R.string.frase_no_mostrar), color = f.texto, modifier = Modifier.weight(1f))
                }
                // Proponer una frase propia: el equipo la revisa y, si va, sale para todos.
                var propia by rememberSaveable { mutableStateOf("") }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(propia, { propia = it.take(90) }, Modifier.weight(1f), singleLine = true, shape = forma(22.dp),
                        placeholder = { Text(stringResource(R.string.frase_proponer)) })
                    BotonIcono(Iconos.Enviar, stringResource(R.string.frase_proponer), activo = propia.trim().length >= 6, lleno = true) {
                        if (vm.red.usuario.value == null) vm.abrir(Pantalla.CuentaRed) else vm.proponerFrase(propia)
                        propia = ""; menuFrase = false
                    }
                }
            }
        }
    }

    if (e.vivas.isEmpty()) {
        EstadoVacio(io.github.fogonapps.identidad.Quien.CAJON, stringResource(R.string.vacio_titulo), stringResource(R.string.vacio_texto))
        Column(Modifier.padding(horizontal = 24.dp)) {
            BotonRiso(stringResource(R.string.agregar_lo_primero), { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.TODO)) }, Modifier.fillMaxWidth())
        }
        return
    }

    // La tarjeta grande: lo terminado este año, en puntos sobre su halo, con El Cajón.
    val total = c.porTipo.values.sum()
    Box(Modifier.aparecer(0).fillMaxWidth().padding(16.dp).vidrio(forma(28.dp)).haloPuntos(f.tinta2.copy(alpha = 0.35f), centro = androidx.compose.ui.geometry.Offset(0.25f, 0.45f))
        .clickable { vm.abrir(Pantalla.Cifras) }) {
        Column(Modifier.padding(20.dp).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val enCajon = total == 0
            Text(if (enCajon) stringResource(R.string.en_tu_cajon) else stringResource(R.string.este_anio, anio), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            NumeroRiso(if (enCajon) "${e.vivas.size}" else "$total", 56.sp)
            if (!enCajon) Text(pluralStringResource(R.plurals.terminadas, total, total), style = MaterialTheme.typography.bodyMedium, color = f.texto)
            // Cuántas de cada tipo, en una línea de íconos.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Tipos.TODOS.filter { (c.porTipo[it] ?: 0) > 0 }.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        IconoRiso(Iconos.de(t), null, tamano = 16.dp, tinta = f.textoSuave)
                        Text("${c.porTipo[t]}", fontFamily = f.cifras, fontSize = 14.sp, color = f.texto)
                    }
                }
            }
        }
        // El Cajón pone la cara de lo que estás viendo o escuchando: la obra de la frase o lo último que tocaste.
        val cara = remember(frase.obra, e.obras) { caraDe(io.github.fogonapps.cajon.datos.Caras.de(io.github.fogonapps.cajon.datos.Caras.obra(e, frase.obra))) }
        Personaje(io.github.fogonapps.identidad.Quien.CAJON, null, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), cara = cara, tamano = 96.dp)
    }

    // ¿Qué te pareció? Lo último que terminaste sin nota: calificarlo con un toque o escribir la reseña.
    e.vivas.filter { (it.estado == Estados.TERMINADO || it.estado == Estados.EN_CURSO) && it.nota == 0 }.maxByOrNull { it.cambio }?.let { o ->
        Column(Modifier.fillMaxWidth().padding(16.dp).tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Portada(o.portada, o.tipo, o.titulo, Modifier.width(52.dp).clickable { vm.abrir(Pantalla.Ficha(o.id)) }, conTitulo = false)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.que_te_parecio), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                    Text(o.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 2)
                }
            }
            ElegirEstrellas(0, { n -> vm.calificarRapido(o.id, n) }, tamano = 34.dp, descripcion = stringResource(R.string.que_te_parecio))
            Pildora(stringResource(R.string.escribir_resena), Iconos.Lapiz) { vm.abrir(Pantalla.Registrar(o.id)) }
        }
    }

    // Lo que está viendo, leyendo o escuchando.
    val enCurso = e.vivas.filter { it.estado == Estados.EN_CURSO }.sortedByDescending { it.cambio }
    if (enCurso.isNotEmpty()) {
        Seccion(stringResource(R.string.en_curso))
        Carrusel(enCurso, vm, conProgreso = true)
    }

    // Lo último de tu gente en la red (o la invitación a entrar).
    FeedInicio(vm)

    ParaTi(vm, e)

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
                if (d.obra.meGusta) IconoRiso(Iconos.Corazon, stringResource(R.string.me_gusta), tamano = 18.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
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
    Seccion(stringResource(R.string.pestana_listas)) { Pildora(stringResource(if (listas.isEmpty()) R.string.nueva else R.string.ver_todo)) { vm.abrir(if (listas.isEmpty()) Pantalla.EditarLista(null) else Pantalla.Listas) } }
    if (listas.isEmpty()) Pista(stringResource(R.string.listas_vacio))
    else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(listas, key = { it.id }) { l ->
            val obras = obrasDe(e, l)
            Column(Modifier.width(150.dp).vidrio().clickable { vm.abrir(Pantalla.VerLista(l.id)) }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                    obras.take(3).forEach { Portada(it.portada, it.tipo, it.titulo, Modifier.width(48.dp), esquina = 6.dp, conTitulo = false) }
                    if (obras.isEmpty()) Ficha(f.tinta1, Iconos.Lista)
                }
                Text(l.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
        }
    }

    // El diario completo (las cifras se abren desde la tarjeta grande de arriba).
    Seccion(stringResource(R.string.mas))
    FilaVidrio({ vm.abrir(Pantalla.Diario) }) {
        Ficha(f.tinta2, Iconos.Diario)
        Text(stringResource(R.string.tu_diario), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }

    // Los retos del año, si tiene.
    if (e.ajustes.retos.isNotEmpty()) {
        Seccion(stringResource(R.string.retos_del_anio, anio)) { Pildora(stringResource(R.string.cambiar)) { vm.abrir(Pantalla.Retos) } }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            e.ajustes.retos.forEach { (t, meta) ->
                val lleva = c.porTipo[t] ?: 0
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconoRiso(Iconos.de(t), null, tamano = 18.dp, tinta = f.textoSuave)
                        Text(nombreTipo(t), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text("$lleva / $meta", fontFamily = f.cifras, color = f.texto)
                    }
                    Barra(lleva.toFloat() / meta.coerceAtLeast(1), f.tinta1)
                }
            }
        }
    }
}

/** Una fila de portadas que se desliza; con [conProgreso], cada una lleva su barra y lo que sigue. */
@Composable
fun Carrusel(obras: List<Obra>, vm: CajonViewModel, conProgreso: Boolean) {
    val f = FogonTema.actual
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(obras, key = { it.id }) { o ->
            Column(Modifier.width(112.dp).clip(forma(12.dp)).clickable { vm.abrir(Pantalla.Ficha(o.id)) }.animateItem(),
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

/** La cara (de `Caras`) como la del personaje. */
fun caraDe(nombre: String): io.github.fogonapps.identidad.Cara = when (nombre) {
    "feliz" -> io.github.fogonapps.identidad.Cara.FELIZ
    "enamorado" -> io.github.fogonapps.identidad.Cara.ENAMORADO
    "sorpresa" -> io.github.fogonapps.identidad.Cara.SORPRESA
    "triste" -> io.github.fogonapps.identidad.Cara.TRISTE
    "miedo" -> io.github.fogonapps.identidad.Cara.MIEDO
    "decidido" -> io.github.fogonapps.identidad.Cara.DECIDIDO
    "guino" -> io.github.fogonapps.identidad.Cara.GUINO
    else -> io.github.fogonapps.identidad.Cara.NORMAL
}

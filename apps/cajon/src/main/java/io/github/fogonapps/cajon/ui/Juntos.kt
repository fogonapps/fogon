package io.github.fogonapps.cajon.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.identidad.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

/*
 * «Juntos» con alguien que sigues: ¿qué vemos? (lo que los dos, o cada uno, tienen por ver, con un filtro de duración
 * y «me apunto» o «paso»), lo que van viendo a la vez, su maratón (lo que ya terminaron los dos) y sus listas en
 * común. Lo tuyo sale de tu cajón; lo suyo, de las partes de su perfil y de su diario compartido. Los votos se guardan
 * en el teléfono (sirven sin red) y, si se siguen los dos, también en el servidor, donde cada uno ve los del otro. Lo
 * elegido también se le puede mandar por mensaje.
 */

/** El filtro de duración de «¿Qué vemos?». */
internal enum class DuracionJuntos { TODO, MENOS_DE_2H, UNA_TARDE, TEMPORADA_CORTA }

/** Lo que dijiste de una propuesta. */
internal enum class VotoJuntos { ME_APUNTO, PASO }

/** Algo que podrían ver juntos: de tu cajón, del suyo o de los dos. */
internal data class Propuesta(
    val clave: String,
    val titulo: String,
    val tipo: String,
    val portada: String,
    val fuente: String,
    val anio: Int?,
    val creador: String,
    /** Cuánto dura entera, en minutos (null si no se sabe). */
    val minutos: Int?,
    /** Episodios de la primera temporada, en series y animes (null si no se sabe). */
    val episodiosPrimera: Int?,
    val mia: Boolean,
    val suya: Boolean,
) {
    val deLosDos get() = mia && suya
}

/** Una obra que los dos tienen en curso, con por dónde va cada uno (solo el capítulo o la página). */
internal data class EnCursoJuntos(val obra: ObraParte, val miAvance: String, val suAvance: String)

/** Una obra que terminaron los dos, con la última fecha de cada uno (AAAA-MM-DD; null si no se sabe). */
internal data class TerminadaJuntos(val obra: ObraParte, val mia: String?, val suya: String?)

object Juntos {
    /** La clave de una obra para los votos: su fuente o, sin ella, el tipo y el título limpio. */
    internal fun clave(tipo: String, titulo: String, fuente: String) = fuente.ifBlank { "$tipo|" + Logica.tituloLimpio(titulo) }

    private fun duracion(o: Obra): Int? = when {
        o.tipo == Tipos.PELICULA -> o.minutos?.takeIf { it > 0 }
        Tipos.porEpisodios(o.tipo) -> o.minutos?.takeIf { it > 0 }?.let { m -> Logica.totalEpisodios(o).takeIf { it > 0 }?.let { m * it } }
        else -> null
    }

    private fun primeraTemporada(o: Obra): Int? = if (!Tipos.porEpisodios(o.tipo)) null
        else o.temporadas.filter { it.numero >= 1 }.minByOrNull { it.numero }?.episodios?.takeIf { it > 0 }

    /**
     * Lo que los dos tienen por ver, junto: lo tuyo (más reciente primero) y después lo que solo tiene la otra
     * persona. Sin álbumes ni lo que entró en silencio de una importación. La duración sale de tu cajón; de lo suyo
     * solo se sabe la de las películas.
     */
    internal fun queVemos(mias: List<Obra>, suyas: List<ObraParte>): List<Propuesta> {
        val porVer = mias.filter { it.estado == Estados.QUIERO && it.tipo != Tipos.ALBUM && !it.silenciosa && !it.borrada }.sortedByDescending { it.agregada }
        val suyasVivas = suyas.filter { it.tipo != Tipos.ALBUM }
        val usadas = mutableSetOf<Int>()
        val deMi = porVer.map { o ->
            val i = suyasVivas.indices.firstOrNull { it !in usadas && o.esLaMisma(suyasVivas[it]) }
            if (i != null) usadas += i
            val s = i?.let { suyasVivas[it] }
            Propuesta(clave(o.tipo, o.titulo, o.fuente.ifBlank { s?.fuente.orEmpty() }), o.titulo, o.tipo, o.portada.ifBlank { s?.portada.orEmpty() },
                o.fuente.ifBlank { s?.fuente.orEmpty() }, o.anio ?: s?.anio, o.creador.ifBlank { s?.creador.orEmpty() },
                duracion(o) ?: s?.let { minutosDe(it) }, primeraTemporada(o), mia = true, suya = s != null)
        }
        val soloSuyas = suyasVivas.filterIndexed { i, _ -> i !in usadas }.distinctBy { clave(it.tipo, it.titulo, it.fuente) }.map { s ->
            Propuesta(clave(s.tipo, s.titulo, s.fuente), s.titulo, s.tipo, s.portada, s.fuente, s.anio, s.creador, minutosDe(s), null, mia = false, suya = true)
        }
        return (deMi + soloSuyas).distinctBy { it.clave }
    }

    /** Los minutos que llegan con una obra suya: solo valen enteros en las películas (en series son por episodio). */
    private fun minutosDe(o: ObraParte): Int? = if (o.tipo != Tipos.PELICULA) null
        else (o.extra["minutos"] as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it > 0 }

    /** Si una propuesta cabe en el tiempo elegido. Lo que no se sabe cuánto dura solo sale en «Todo». */
    internal fun cabe(p: Propuesta, d: DuracionJuntos): Boolean = when (d) {
        DuracionJuntos.TODO -> true
        DuracionJuntos.MENOS_DE_2H -> (p.minutos ?: 0) in 1..120
        DuracionJuntos.UNA_TARDE -> (p.minutos ?: 0) in 1..300
        DuracionJuntos.TEMPORADA_CORTA -> Tipos.porEpisodios(p.tipo) && (p.episodiosPrimera ?: 0) in 1..13
    }

    /**
     * El orden: primero a lo que se apuntaron los dos; después lo que tienen los dos y alguno se apuntó, lo que solo
     * uno se apuntó, lo que tienen los dos, lo demás, lo que la otra persona pasó y al final lo que pasaste tú. Dentro
     * de cada grupo se respeta el orden de llegada.
     */
    internal fun ordenar(lista: List<Propuesta>, votos: Map<String, VotoJuntos>, suyos: Map<String, VotoJuntos> = emptyMap()): List<Propuesta> = lista.sortedBy { p ->
        val v = votos[p.clave]
        val s = suyos[p.clave]
        val alguno = v == VotoJuntos.ME_APUNTO || s == VotoJuntos.ME_APUNTO
        when {
            v == VotoJuntos.PASO -> 6
            v == VotoJuntos.ME_APUNTO && s == VotoJuntos.ME_APUNTO -> 0
            s == VotoJuntos.PASO -> 5
            alguno && p.deLosDos -> 1
            alguno -> 2
            p.deLosDos -> 3
            else -> 4
        }
    }

    /** Si los dos se apuntaron a [clave]. */
    internal fun losDosSeApuntaron(clave: String, votos: Map<String, VotoJuntos>, suyos: Map<String, VotoJuntos>) =
        votos[clave] == VotoJuntos.ME_APUNTO && suyos[clave] == VotoJuntos.ME_APUNTO

    /** Un voto como lo guarda el servidor ("si", "no") y al revés. */
    internal fun aServidor(v: VotoJuntos?): String? = when (v) { VotoJuntos.ME_APUNTO -> "si"; VotoJuntos.PASO -> "no"; null -> null }
    internal fun deServidor(m: Map<String, String>): Map<String, VotoJuntos> =
        m.mapNotNull { (k, v) -> when (v) { "si" -> k to VotoJuntos.ME_APUNTO; "no" -> k to VotoJuntos.PASO; else -> null } }.toMap()

    /**
     * Junta tus votos del teléfono con los del servidor: lo que cambiaste sin poder mandarlo ([pendientes]) gana; lo
     * demás es lo del servidor, que puede venir de otro teléfono tuyo.
     */
    internal fun fusionar(locales: Map<String, VotoJuntos>, pendientes: Set<String>, servidor: Map<String, VotoJuntos>): Map<String, VotoJuntos> =
        servidor.filterKeys { it !in pendientes } + locales.filterKeys { it in pendientes }

    /**
     * La que se propone en grande: la primera a la que se apuntaron los dos (ya es la respuesta a «¿qué vemos?»); si no
     * hay, la primera sin tu voto o, si ya votaste todo, la primera del orden.
     */
    internal fun propuesta(ordenadas: List<Propuesta>, votos: Map<String, VotoJuntos>, suyos: Map<String, VotoJuntos> = emptyMap()): Propuesta? =
        ordenadas.firstOrNull { losDosSeApuntaron(it.clave, votos, suyos) } ?: ordenadas.firstOrNull { it.clave !in votos } ?: ordenadas.firstOrNull()

    /** Lo que los dos tienen en curso, con por dónde va cada uno (sin decir nada de lo que pasa). */
    internal fun enCurso(mias: List<Obra>, suyas: List<ObraParte>): List<EnCursoJuntos> = suyas.mapNotNull { s ->
        val mia = mias.firstOrNull { it.estado == Estados.EN_CURSO && !it.borrada && it.esLaMisma(s) } ?: return@mapNotNull null
        EnCursoJuntos(s, io.github.fogonapps.cajon.RedSocial.avanceDe(mia), (s.extra["avance"] as? JsonPrimitive)?.content.orEmpty())
    }

    /** Lo que la otra persona terminó, según su diario y sus reseñas compartidas (con la última fecha de cada obra). */
    private fun terminadasSuyas(publicaciones: List<PublicacionSocial>): List<Pair<ObraParte, String?>> =
        publicaciones.filter { (it.tipo == "diario" || it.tipo == "resena") && (it.datos["titulo"] as? JsonPrimitive)?.content.orEmpty().isNotBlank() }
            .map { p ->
                fun t(k: String) = (p.datos[k] as? JsonPrimitive)?.content.orEmpty()
                val fecha = t("fecha").takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
                    ?: runCatching { java.time.Instant.ofEpochMilli(p.cuando).atZone(ZoneOffset.UTC).toLocalDate().toString() }.getOrNull()
                ObraParte(t("titulo"), t("tipoObra").ifBlank { Tipos.PELICULA }, t("portada"), t("fuente"), t("anio").toIntOrNull(), t("creador"), p.datos) to fecha
            }
            .groupBy { (o, _) -> clave(o.tipo, o.titulo, o.fuente) }
            .map { (_, l) -> l.first().first to l.mapNotNull { it.second }.maxOrNull() }

    /** Lo que terminaron los dos, lo más reciente primero. */
    internal fun terminadasEnComun(mias: List<Obra>, publicaciones: List<PublicacionSocial>): List<TerminadaJuntos> {
        val mias2 = mias.filter { !it.borrada && it.tipo != Tipos.ALBUM && (it.estado == Estados.TERMINADO || it.registros.isNotEmpty()) }
        return terminadasSuyas(publicaciones).filter { it.first.tipo != Tipos.ALBUM }.mapNotNull { (s, fecha) ->
            val mia = mias2.firstOrNull { it.esLaMisma(s) } ?: return@mapNotNull null
            TerminadaJuntos(s, Logica.ultimaVez(mia), fecha)
        }.sortedByDescending { maxOf(it.mia.orEmpty(), it.suya.orEmpty()) }
    }

    /** Si la terminaron casi a la vez (a menos de un mes): el mes en que coincidieron. */
    internal fun coincidieron(t: TerminadaJuntos): LocalDate? {
        val a = t.mia?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val b = t.suya?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return if (kotlin.math.abs(ChronoUnit.DAYS.between(a, b)) <= 31) maxOf(a, b) else null
    }

    /** Por tipo, cuántas obras terminaron los dos dentro de [anio], a partir de una lista ya cruzada. */
    internal fun delAnio(lista: List<TerminadaJuntos>, anio: Int): Map<String, Int> = lista
        .filter { it.mia?.take(4)?.toIntOrNull() == anio && it.suya?.take(4)?.toIntOrNull() == anio }
        .groupingBy { it.obra.tipo }.eachCount()

    /**
     * Cuántas obras terminaron los dos en [anio], por tipo («23 películas en común este año»). Lo suyo sale del
     * diario y las reseñas que comparte (las últimas que trae su perfil). Para usarlo desde otras pantallas (el resumen
     * del año).
     */
    fun enComunDelAnio(e: Estado, perfil: PerfilSocial, anio: Int): Map<String, Int> =
        delAnio(terminadasEnComun(e.vivas, perfil.publicaciones), anio)
}

/**
 * Los votos de «¿Qué vemos?» en este teléfono (por persona): los tuyos, los suyos que llegaron la última vez y lo que
 * cambiaste sin poder mandarlo todavía. Es la caché: sin red, la pantalla sigue funcionando con esto.
 */
private class VotosJuntos(ctx: Context, private val usuario: String) {
    private val prefs = ctx.getSharedPreferences("juntos_votos", Context.MODE_PRIVATE)
    private val suyosPrefs = ctx.getSharedPreferences("juntos_votos_suyos", Context.MODE_PRIVATE)
    private val pendientesPrefs = ctx.getSharedPreferences("juntos_votos_pendientes", Context.MODE_PRIVATE)
    private val prefijo = "$usuario\u0000"
    init {
        // Los votos de antes de compartirlos quedan por mandar (una sola vez), para que el servidor no los pise.
        val migrado = "\u0001$usuario"
        if (!pendientesPrefs.contains(migrado)) pendientesPrefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(prefijo) }.forEach { putBoolean(it, true) }
            putBoolean(migrado, true)
        }.apply()
    }
    private fun deMapa(p: android.content.SharedPreferences): Map<String, VotoJuntos> = p.all.mapNotNull { (k, v) ->
        if (!k.startsWith(prefijo)) null
        else runCatching { VotoJuntos.valueOf(v.toString()) }.getOrNull()?.let { k.removePrefix(prefijo) to it }
    }.toMap()
    private fun guardar(p: android.content.SharedPreferences, votos: Map<String, VotoJuntos>) = p.edit().apply {
        p.all.keys.filter { it.startsWith(prefijo) }.forEach { remove(it) }
        votos.forEach { (k, v) -> putString(prefijo + k, v.name) }
    }.apply()
    fun leer(): Map<String, VotoJuntos> = deMapa(prefs)
    fun suyos(): Map<String, VotoJuntos> = deMapa(suyosPrefs)
    fun pendientes(): Set<String> = pendientesPrefs.all.keys.filter { it.startsWith(prefijo) }.map { it.removePrefix(prefijo) }.toSet()
    fun poner(clave: String, voto: VotoJuntos?) {
        prefs.edit().apply { if (voto == null) remove(prefijo + clave) else putString(prefijo + clave, voto.name) }.apply()
        pendientesPrefs.edit().putBoolean(prefijo + clave, true).apply()
    }
    fun enviado(clave: String) = pendientesPrefs.edit().remove(prefijo + clave).apply()
    fun ponerTodo(mios: Map<String, VotoJuntos>, suyos: Map<String, VotoJuntos>) { guardar(prefs, mios); guardar(suyosPrefs, suyos) }
}

/** La pantalla «Juntos» con [usuario]. */
@Composable
fun Juntos(vm: CajonViewModel, e: Estado, usuario: String) {
    val f = FogonTema.actual
    val perfiles by vm.red.perfiles.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.verPerfil(usuario) }
    val p = perfiles[usuario]
    // El nombre de pila (o el usuario), para decir de quién es cada cosa sin pronombres.
    val nombre = p?.persona?.nombre?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: usuario
    Cabecera(stringResource(R.string.juntos_titulo, nombre), vm::cerrar)
    if (p == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.texto, trackColor = f.linea); return }
    QueVemos(vm, e, p, usuario, nombre)
    VanJuntos(vm, e, p, nombre)
    SuMaraton(vm, e, p, nombre)
    ListasComunesCon(vm, usuario)
    Espacio(48.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueVemos(vm: CajonViewModel, e: Estado, p: PerfilSocial, usuario: String, nombre: String) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val almacen = remember(usuario) { VotosJuntos(ctx.applicationContext, usuario) }
    val votos = remember(usuario) { mutableStateMapOf<String, VotoJuntos>().apply { putAll(almacen.leer()) } }
    val suyos = remember(usuario) { mutableStateMapOf<String, VotoJuntos>().apply { putAll(almacen.suyos()) } }
    // Si los votos se comparten (se siguen los dos y hay red); mientras no se sepa, como la última vez que hubo datos.
    var compartidos by remember(usuario) { mutableStateOf(suyos.isNotEmpty()) }
    val alcance = rememberCoroutineScope()
    // Al abrir: se trae lo de los dos, se manda lo que quedó sin mandar y se juntan (lo no mandado gana).
    suspend fun sincronizar() {
        // Si no se pueden leer (sin red, o no se siguen los dos), todo sigue en el teléfono y se manda otro día.
        val r = vm.red.votosJuntos(usuario) ?: return
        val pendientes = almacen.pendientes()
        val locales = almacen.leer()
        pendientes.forEach { clave -> if (vm.red.votarJuntos(usuario, clave, Juntos.aServidor(locales[clave]))) almacen.enviado(clave) }
        val mios = Juntos.fusionar(almacen.leer(), pendientes + almacen.pendientes(), Juntos.deServidor(r.mios))
        val deEl = Juntos.deServidor(r.suyos)
        almacen.ponerTodo(mios, deEl)
        votos.clear(); votos.putAll(mios); suyos.clear(); suyos.putAll(deEl)
        compartidos = true
    }
    LaunchedEffect(usuario) { sincronizar() }
    var duracion by rememberSaveable(usuario) { mutableStateOf(DuracionJuntos.TODO) }
    var elegida by rememberSaveable(usuario) { mutableStateOf<String?>(null) }
    val parte = p.partes[PartesPerfil.POR_VER]
    val todas = remember(e.vivas, parte) { Juntos.queVemos(e.vivas, obrasDe(parte)) }
    val lista = Juntos.ordenar(todas.filter { Juntos.cabe(it, duracion) }, votos, suyos)
    fun votar(clave: String, v: VotoJuntos?) {
        if (v == null) votos.remove(clave) else votos[clave] = v
        almacen.poner(clave, v); elegida = null
        // Sin red queda pendiente y se manda la próxima vez.
        alcance.launch { if (vm.red.votarJuntos(usuario, clave, Juntos.aServidor(v))) almacen.enviado(clave) }
    }

    TituloPerfil(stringResource(R.string.juntos_que_vemos))
    Pista(if (parte == null) stringResource(R.string.juntos_sin_por_ver, nombre) else stringResource(R.string.juntos_que_vemos_pista))
    Selector(listOf(DuracionJuntos.TODO to stringResource(R.string.juntos_duracion_todo), DuracionJuntos.MENOS_DE_2H to stringResource(R.string.juntos_menos_2h),
        DuracionJuntos.UNA_TARDE to stringResource(R.string.juntos_una_tarde), DuracionJuntos.TEMPORADA_CORTA to stringResource(R.string.juntos_temporada_corta)),
        duracion, { duracion = it; elegida = null }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    if (lista.isEmpty()) {
        Pista(stringResource(if (todas.isEmpty()) R.string.juntos_nada_por_ver else R.string.juntos_nada_duracion), Modifier.padding(vertical = 8.dp))
        return
    }
    val grande = lista.firstOrNull { it.clave == elegida } ?: Juntos.propuesta(lista, votos, suyos) ?: return
    val voto = votos[grande.clave]
    val suyo = suyos[grande.clave]
    // La propuesta grande: portada, título, de quién es y cuánto dura; abajo, «me apunto» o «paso».
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(190.dp).clip(forma(14.dp)).clickable(role = Role.Button) {
            vm.abrirObraDePerfil(grande.titulo, grande.tipo, grande.portada, grande.fuente, grande.anio, grande.creador)
        }) { Portada(grande.portada, grande.tipo, grande.titulo, Modifier.fillMaxWidth(), esquina = 14.dp, conTitulo = false, lugar = Imagenes.Lugar.FICHA) }
        Text(grande.titulo, fontFamily = f.titulos, fontSize = 26.sp, lineHeight = 30.sp, color = f.texto, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(nombreTipo(grande.tipo, plural = false), grande.minutos?.let { duracionTexto(it) },
            when { grande.deLosDos -> stringResource(R.string.juntos_los_dos); grande.mia -> stringResource(R.string.juntos_la_tienes)
                else -> stringResource(R.string.juntos_la_tiene, nombre) }).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, textAlign = TextAlign.Center)
        // La marca de que la otra persona también se apuntó (o de que se apuntaron los dos).
        if (suyo == VotoJuntos.ME_APUNTO) Row(Modifier.clip(FormaPildora).background(f.texto).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconoRiso(Iconos.Gente, null, tamano = 16.dp, tinta = f.fondo)
            Text(if (voto == VotoJuntos.ME_APUNTO) stringResource(R.string.juntos_se_apuntaron_los_dos) else stringResource(R.string.juntos_se_apunto, nombre),
                style = MaterialTheme.typography.labelLarge, color = f.fondo)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (voto == VotoJuntos.ME_APUNTO) BotonRiso(stringResource(R.string.juntos_te_apuntaste), { votar(grande.clave, null) }, Modifier.weight(1f))
            else BotonRiso(stringResource(R.string.juntos_me_apunto), { votar(grande.clave, VotoJuntos.ME_APUNTO) }, Modifier.weight(1f))
            BotonSecundario(stringResource(if (voto == VotoJuntos.PASO) R.string.juntos_pasaste else R.string.juntos_paso),
                { votar(grande.clave, if (voto == VotoJuntos.PASO) null else VotoJuntos.PASO) }, Modifier.weight(1f))
        }
    }
    // Los demás, chicos: tocar uno lo sube a la propuesta grande.
    val demas = lista.filter { it.clave != grande.clave }
    if (demas.isNotEmpty()) {
        Text(stringResource(R.string.juntos_los_demas), style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        val apuntada = stringResource(R.string.juntos_te_apuntaste)
        val pasada = stringResource(R.string.juntos_pasaste)
        val losDos = stringResource(R.string.juntos_los_dos)
        val seApuntaronLosDos = stringResource(R.string.juntos_se_apuntaron_los_dos)
        val seApunto = stringResource(R.string.juntos_se_apunto, nombre)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(demas.size, key = { demas[it].clave }) { i ->
                val o = demas[i]
                val v = votos[o.clave]
                val suyoAqui = suyos[o.clave] == VotoJuntos.ME_APUNTO
                val ambos = suyoAqui && v == VotoJuntos.ME_APUNTO
                val estado = listOfNotNull(if (o.deLosDos) losDos else null, when (v) { VotoJuntos.ME_APUNTO -> apuntada; VotoJuntos.PASO -> pasada; null -> null },
                    when { ambos -> seApuntaronLosDos; suyoAqui -> seApunto; else -> null }).joinToString(" · ")
                Column(Modifier.width(100.dp).clip(forma(10.dp)).clickable(role = Role.Button) { elegida = o.clave }
                    .semantics { if (estado.isNotBlank()) stateDescription = estado },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 10.dp, conTitulo = false)
                        if (v == VotoJuntos.ME_APUNTO) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(FormaPildora).background(f.texto),
                            contentAlignment = Alignment.Center) { IconoRiso(Iconos.Chulo, null, tamano = 14.dp, tinta = f.fondo) }
                        // La otra persona también se apuntó.
                        if (suyoAqui) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).clip(FormaPildora).background(f.texto),
                            contentAlignment = Alignment.Center) { IconoRiso(Iconos.Gente, null, tamano = 14.dp, tinta = f.fondo) }
                    }
                    Text(o.titulo, style = MaterialTheme.typography.labelMedium, color = if (v == VotoJuntos.PASO) f.textoSuave else f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (ambos) Text(seApuntaronLosDos, style = MaterialTheme.typography.labelSmall, color = f.texto)
                    else if (suyoAqui) Text(seApunto, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else if (o.deLosDos) Text(stringResource(R.string.juntos_de_los_dos_corto), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                }
            }
        }
    }
    // Si se siguen los dos, lo ve en su «Juntos»; si no, se queda aquí. Siempre se le puede mandar por mensaje.
    val apuntadas = todas.filter { votos[it.clave] == VotoJuntos.ME_APUNTO }
    Pista(if (compartidos) stringResource(R.string.juntos_voto_compartido, nombre) else stringResource(R.string.juntos_voto_local), Modifier.padding(top = 8.dp))
    if (apuntadas.isNotEmpty()) {
        val texto = stringResource(R.string.juntos_mensaje, apuntadas.take(12).joinToString(", ") { it.titulo })
        BotonSecundario(stringResource(R.string.juntos_mandar), {
            vm.chat.enviar(usuario, texto, devolverSiFalla = true)
            vm.abrir(Pantalla.Conversacion(usuario))
        }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
    }
}

@Composable
private fun duracionTexto(minutos: Int): String = if (minutos < 60) stringResource(R.string.juntos_minutos, minutos)
    else if (minutos % 60 == 0) stringResource(R.string.juntos_horas, minutos / 60)
    else stringResource(R.string.juntos_horas_minutos, minutos / 60, minutos % 60)

@Composable
private fun VanJuntos(vm: CajonViewModel, e: Estado, p: PerfilSocial, nombre: String) {
    val f = FogonTema.actual
    val parte = p.partes[PartesPerfil.VIENDO]
    val lista = remember(e.vivas, parte) { Juntos.enCurso(e.vivas, obrasDe(parte)) }
    TituloPerfil(stringResource(R.string.juntos_van_juntos))
    if (parte == null) { Pista(stringResource(R.string.juntos_sin_viendo, nombre)); return }
    if (lista.isEmpty()) { Pista(stringResource(R.string.juntos_nada_a_la_vez)); return }
    val enCurso = stringResource(R.string.juntos_en_curso)
    lista.forEach { j ->
        val o = j.obra
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.abrirObraDePerfil(o.titulo, o.tipo, o.portada, o.fuente, o.anio, o.creador) }
            .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(52.dp), esquina = 8.dp, conTitulo = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(o.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.juntos_avances, j.miAvance.ifBlank { enCurso }, nombre, j.suAvance.ifBlank { enCurso }),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
    }
}

@Composable
private fun SuMaraton(vm: CajonViewModel, e: Estado, p: PerfilSocial, nombre: String) {
    val f = FogonTema.actual
    val lista = remember(e.vivas, p.publicaciones) { Juntos.terminadasEnComun(e.vivas, p.publicaciones) }
    val anio = LocalDate.now().year
    TituloPerfil(stringResource(R.string.juntos_maraton))
    if (lista.isEmpty()) { Pista(stringResource(R.string.juntos_nada_en_comun)); Pista(stringResource(R.string.juntos_maraton_pista, nombre)); return }
    val delAnio = Juntos.delAnio(lista, anio)
    if (delAnio.isNotEmpty()) {
        // El número grande, como las cifras de «Tú»: lo que más vieron juntos primero.
        val etiquetas = delAnio.entries.sortedByDescending { it.value }.map { (t, n) -> "$n" to stringResource(R.string.juntos_en_comun_tipo, nombreTipo(t, plural = n != 1).lowercase()) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            etiquetas.forEach { (n, t) ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$n $t" }) {
                    NumeroRiso(n, 44.sp, color = f.texto, familia = f.titulos)
                    Text(t, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }
    }
    val idioma = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    LazyRow(Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val mostrar = lista.take(30)
        items(mostrar.size) { i ->
            val t = mostrar[i]
            PortadaChica(vm, t.obra) {
                Juntos.coincidieron(t)?.let { d ->
                    Text(stringResource(R.string.juntos_coincidieron, d.month.getDisplayName(TextStyle.SHORT, idioma) + " " + d.year),
                        style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                }
            }
        }
    }
    Pista(stringResource(R.string.juntos_maraton_pista, nombre), Modifier.padding(top = 6.dp))
}

package io.github.fogonapps.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Eventos semánticos; cada app decide su traducción. */
enum class EventoSocial { SIN_CONEXION, PUBLICACION_GUARDADA, SOLICITUD_ENVIADA, SIGUIENDO, CUENTA_BLOQUEADA, CUENTA_DESBLOQUEADA, REPORTE_ENVIADO, CORREO_VINCULADO, CUENTA_BORRADA }

/** Estado e interacciones de una sesión local; el adaptador conserva los efectos propios de la app. */
abstract class ControladorSocial(
    protected val scope: CoroutineScope,
    protected val sesion: SesionCuenta,
    usuarioInicial: String?,
) {
    protected abstract fun cliente(): ClienteSocial
    protected abstract suspend fun intentar(
        avisar: Boolean = true,
        marca: SesionCuenta.Marca = sesion.actual(),
        trabajo: suspend () -> Unit,
    ): Boolean
    protected fun lanzar(trabajo: suspend () -> Unit) = scope.launch { intentar(trabajo = trabajo) }
    protected abstract fun avisar(evento: EventoSocial)
    protected abstract fun olvidarCuenta()
    protected abstract fun ponerCuenta(cuenta: MiCuenta)
    protected abstract fun alContarAvisos(cantidad: Int)
    abstract fun cargarCasos(): Job

    protected val _usuario = MutableStateFlow(usuarioInicial)
    /** El @usuario de la cuenta en este teléfono, o null si no hay cuenta. */
    val usuario: StateFlow<String?> = _usuario

    protected val _feed = MutableStateFlow<List<PublicacionSocial>?>(null)
    val feed: StateFlow<List<PublicacionSocial>?> = _feed
    protected val _explorar = MutableStateFlow<List<PublicacionSocial>?>(null)
    val explorar: StateFlow<List<PublicacionSocial>?> = _explorar
    protected val _perfiles = MutableStateFlow<Map<String, PerfilSocial>>(emptyMap())
    val perfiles: StateFlow<Map<String, PerfilSocial>> = _perfiles
    protected val _vistas = MutableStateFlow<Map<Long, PublicacionSocial>>(emptyMap())
    val vistas: StateFlow<Map<Long, PublicacionSocial>> = _vistas
    protected val _comentarios = MutableStateFlow<Map<Long, List<ComentarioSocial>>>(emptyMap())
    val comentarios: StateFlow<Map<Long, List<ComentarioSocial>>> = _comentarios
    protected val _avisos = MutableStateFlow<List<AvisoSocial>?>(null)
    val avisos: StateFlow<List<AvisoSocial>?> = _avisos
    protected val _solicitudes = MutableStateFlow<List<PersonaSocial>>(emptyList())
    val solicitudes: StateFlow<List<PersonaSocial>> = _solicitudes
    protected val _sinLeer = MutableStateFlow(0)
    val sinLeer: StateFlow<Int> = _sinLeer
    protected val _guardados = MutableStateFlow<List<PublicacionSocial>?>(null)
    val guardados: StateFlow<List<PublicacionSocial>?> = _guardados
    /** "seguidores:<usuario>" o "siguiendo:<usuario>" (o "bloqueados") → la lista. */
    protected val _gente = MutableStateFlow<Map<String, List<PersonaSocial>>>(emptyMap())
    val gente: StateFlow<Map<String, List<PersonaSocial>>> = _gente

    protected val _cuenta = MutableStateFlow<MiCuenta?>(null)
    /** Lo de la cuenta en el servidor: correo, si tiene contraseña, desde cuándo puede cambiar el usuario. */
    val cuenta: StateFlow<MiCuenta?> = _cuenta

    protected val _sesiones = MutableStateFlow<List<SesionMostrada>?>(null)
    val sesiones: StateFlow<List<SesionMostrada>?> = _sesiones
    fun cargarSesiones() = lanzar {
        val marca = sesion.actual()
        val lista = cliente().sesiones().map { it.paraPantalla() }
        if (sesion.vigente(marca)) _sesiones.value = lista
    }
    fun cerrarSesion(id: String, actual: Boolean) = lanzar {
        val marca = sesion.actual()
        cliente().cerrarSesion(id)
        if (sesion.vigente(marca)) { if (actual) olvidarCuenta() else cargarSesiones() }
    }
    fun cerrarOtrasSesiones() = lanzar {
        val marca = sesion.actual()
        cliente().cerrarOtrasSesiones()
        if (sesion.vigente(marca)) cargarSesiones()
    }

    fun cargarFeed(avisar: Boolean = true) {
        if (_usuario.value == null) return
        scope.launch { intentar(avisar = avisar) { _feed.value = cliente().feed() } }
    }

    /** X-09: al deslizar hacia abajo, el feed y Explorar de nuevo (sin avisar errores: el gesto ya dice que intentó). */
    open suspend fun recargarFeed() {
        if (_usuario.value != null) intentar(avisar = false) { _feed.value = cliente().feed() }
        intentar(avisar = false) { _explorar.value = cliente().explorar() }
        revisarSinLeer()
    }

    fun masFeed() = lanzar {
        val ya = _feed.value ?: return@lanzar
        val mas = cliente().feed(ya.lastOrNull()?.cuando)
        _feed.value = (ya + mas).distinctBy { it.id }
    }

    fun cargarExplorar() = lanzar { _explorar.value = cliente().explorar() }

    /** Los @usuarios que se buscaron y no existen (para decirlo en vez de quedarse cargando). */
    protected val _noEstan = MutableStateFlow<Set<String>>(emptySet())
    val noEstan: StateFlow<Set<String>> = _noEstan

    fun verPerfil(usuario: String) = scope.launch {
        val marca = sesion.actual()
        try {
            val perfil = cliente().perfil(usuario)
            if (sesion.vigente(marca)) _perfiles.value = _perfiles.value + (usuario to perfil)
        } catch (x: ErrorSocial) {
            if (sesion.vigente(marca) && x.codigo == 404) _noEstan.value = _noEstan.value + usuario
            else intentar(marca = marca) { throw x }
        } catch (x: IOException) { if (sesion.vigente(marca)) avisar(EventoSocial.SIN_CONEXION) }
    }

    fun cargarPublicacion(id: Long) = lanzar {
        _vistas.value = _vistas.value + (id to cliente().publicacion(id))
        _comentarios.value = _comentarios.value + (id to cliente().comentarios(id))
    }

    /** Cambia una publicación en todas las listas donde esté (para que el corazón responda al instante). */
    protected fun tocar(id: Long, f: (PublicacionSocial) -> PublicacionSocial) {
        fun l(x: List<PublicacionSocial>?) = x?.map { if (it.id == id) f(it) else it }
        _feed.value = l(_feed.value); _explorar.value = l(_explorar.value); _guardados.value = l(_guardados.value)
        _vistas.value = _vistas.value.mapValues { if (it.key == id) f(it.value) else it.value }
        _perfiles.value = _perfiles.value.mapValues { (_, p) -> p.copy(publicaciones = l(p.publicaciones)!!) }
        _listas.value = _listas.value.mapValues { (_, xs) -> l(xs)!! }
    }

    /** Personas por nombre o @usuario (X-07). */
    suspend fun buscarPersonas(q: String): List<PersonaSocial> = cliente().buscar(q)

    fun buscarPublicacion(id: Long): PublicacionSocial? =
        _vistas.value[id] ?: (_feed.value.orEmpty() + _explorar.value.orEmpty() + _guardados.value.orEmpty() + _perfiles.value.values.flatMap { it.publicaciones }).firstOrNull { it.id == id }

    fun meGusta(p: PublicacionSocial) {
        val si = !p.yoMeGusta
        tocar(p.id) { it.copy(yoMeGusta = si, megusta = it.megusta + if (si) 1 else -1) }
        scope.launch { if (!intentar { cliente().meGusta(p.id, si) }) tocar(p.id) { it.copy(yoMeGusta = !si, megusta = it.megusta + if (si) -1 else 1) } }
    }

    fun guardar(p: PublicacionSocial) {
        val si = !p.yoGuardo
        tocar(p.id) { it.copy(yoGuardo = si) }
        scope.launch {
            if (intentar { cliente().guardar(p.id, si) }) { if (si) avisar(EventoSocial.PUBLICACION_GUARDADA) }
            else tocar(p.id) { it.copy(yoGuardo = !si) }
        }
    }

    fun cargarGuardados() = lanzar { _guardados.value = cliente().guardados() }

    /** Las guardadas que alguien muestra en su perfil, y lo que lleva una #etiqueta (clave → lista). */
    protected val _listas = MutableStateFlow<Map<String, List<PublicacionSocial>>>(emptyMap())
    val listas: StateFlow<Map<String, List<PublicacionSocial>>> = _listas
    fun cargarGuardadasDe(usuario: String) = lanzar { _listas.value = _listas.value + ("guardadas:$usuario" to cliente().guardadasDe(usuario)) }

    fun cargarEtiqueta(etiqueta: String) = lanzar { _listas.value = _listas.value + ("#$etiqueta" to cliente().conEtiqueta(etiqueta)) }

    fun comentar(id: Long, texto: String, padre: Long? = null) = lanzar {
        cliente().comentar(id, texto.trim(), padre)
        _comentarios.value = _comentarios.value + (id to cliente().comentarios(id))
        tocar(id) { it.copy(comentarios = it.comentarios + 1) }
    }

    fun meGustaComentario(pub: Long, comentario: ComentarioSocial) = lanzar {
        cliente().meGustaComentario(comentario.id, !comentario.yoMeGusta)
        _comentarios.value = _comentarios.value + (pub to cliente().comentarios(pub))
    }

    fun borrarComentario(pub: Long, id: Long) = lanzar {
        cliente().borrarComentario(id)
        _comentarios.value = _comentarios.value + (pub to cliente().comentarios(pub))
        tocar(pub) { it.copy(comentarios = (it.comentarios - 1).coerceAtLeast(0)) }
    }

    /* ----- Seguir ----- */

    fun seguir(usuario: String) = lanzar {
        val r = cliente().seguir(usuario)
        avisar(if (r == "pendiente") EventoSocial.SOLICITUD_ENVIADA else EventoSocial.SIGUIENDO)
        _perfiles.value = _perfiles.value + (usuario to cliente().perfil(usuario))
        cargarFeed()
    }

    fun dejarDeSeguir(usuario: String) = lanzar {
        cliente().dejarDeSeguir(usuario)
        _perfiles.value = _perfiles.value + (usuario to cliente().perfil(usuario))
        cargarFeed()
    }

    fun aceptar(usuario: String) = lanzar { cliente().aceptar(usuario); _solicitudes.value = cliente().solicitudes() }
    fun rechazar(usuario: String) = lanzar { cliente().rechazar(usuario); _solicitudes.value = cliente().solicitudes() }

    fun quitarSeguidor(usuario: String) = lanzar {
        cliente().quitarSeguidor(usuario)
        _usuario.value?.let { yo -> _gente.value = _gente.value + ("seguidores:$yo" to cliente().seguidores(yo)) }
    }

    fun cargarGente(usuario: String, seguidores: Boolean) = lanzar {
        val clave = (if (seguidores) "seguidores:" else "siguiendo:") + usuario
        _gente.value = _gente.value + (clave to if (seguidores) cliente().seguidores(usuario) else cliente().siguiendo(usuario))
    }

    fun bloquear(usuario: String, si: Boolean) = lanzar {
        cliente().bloquear(usuario, si)
        if (si) {
            _gente.value = quitarCuentaBloqueada(_gente.value, usuario)
            _feed.value = quitarPublicacionesBloqueadas(_feed.value, usuario)
        }
        avisar(if (si) EventoSocial.CUENTA_BLOQUEADA else EventoSocial.CUENTA_DESBLOQUEADA)
        _perfiles.value = _perfiles.value - usuario
        _gente.value = _gente.value + ("bloqueados" to cliente().bloqueados())
        cargarFeed()
    }

    fun cargarBloqueados() = lanzar { _gente.value = _gente.value + ("bloqueados" to cliente().bloqueados()) }

    fun reportar(que: String, objeto: String, motivo: String) = lanzar { cliente().reportar(que, objeto, motivo); avisar(EventoSocial.REPORTE_ENVIADO) }

    protected val _ocultas = MutableStateFlow<Set<Long>>(emptySet())
    /** Publicaciones que escondió (o reportó) en esta sesión: dejan de verse al momento. */
    val ocultas: StateFlow<Set<Long>> = _ocultas
    fun ocultar(id: Long) { _ocultas.value = _ocultas.value + id }

    protected val _equipoBloqueado = MutableStateFlow(false)
    val equipoBloqueado: StateFlow<Boolean> = _equipoBloqueado
    protected val _equipoOcupado = MutableStateFlow(false)
    val equipoOcupado: StateFlow<Boolean> = _equipoOcupado
    suspend fun <T> operarEquipo(trabajo: suspend (ClienteSocial) -> T): T? {
        if (_equipoOcupado.value) return null
        _equipoOcupado.value = true
        var resultado: T? = null
        val marca = sesion.actual()
        var ok = false
        try { ok = intentar(marca = marca) { resultado = trabajo(cliente()) } } finally { _equipoOcupado.value = false }
        return if (ok) resultado else null
    }
    suspend fun actualizarEquipo() {
        val marca = sesion.actual()
        val c = cliente().miCuenta()
        if (sesion.vigente(marca)) {
            ponerCuenta(c)
            _equipoBloqueado.value = false
        }
    }
    fun restaurar(c: CasoModeracion) = lanzar { cliente().restaurar(c); cargarCasos() }

    protected val _estadoCuenta = MutableStateFlow<EstadoCuentaSocial?>(null)
    val estadoCuenta: StateFlow<EstadoCuentaSocial?> = _estadoCuenta
    fun cargarEstadoCuenta() = lanzar { _estadoCuenta.value = cliente().estadoCuenta() }

    protected val _casos = MutableStateFlow<List<CasoModeracion>?>(null)
    val casos: StateFlow<List<CasoModeracion>?> = _casos

    fun decidir(c: CasoModeracion, decision: String, regla: String = c.motivos.maxByOrNull { it.value }?.key.orEmpty(), mensaje: String = "") = lanzar {
        cliente().decidir(c.que, c.objeto, decision, regla, mensaje.trim())
        _casos.value = _casos.value?.filterNot { it.que == c.que && it.objeto == c.objeto }
    }

    /* Sanciones recientes y apelaciones (equipo). */
    protected val _sanciones = MutableStateFlow<List<SancionReciente>?>(null)
    val sanciones: StateFlow<List<SancionReciente>?> = _sanciones
    protected val _apelaciones = MutableStateFlow<List<ApelacionSocial>?>(null)
    val apelaciones: StateFlow<List<ApelacionSocial>?> = _apelaciones
    fun cargarSanciones() = lanzar { _sanciones.value = cliente().sancionesRecientes() }
    fun cargarApelaciones() = lanzar { _apelaciones.value = cliente().apelaciones() }
    fun sancionar(usuario: String, decision: String, regla: String, mensaje: String) = lanzar {
        cliente().sancionar(usuario.trim().removePrefix("@").lowercase(), decision, regla, mensaje.trim())
        _sanciones.value = cliente().sancionesRecientes()
    }
    fun levantar(usuario: String, motivo: String) = lanzar {
        cliente().levantarSanciones(usuario, motivo.trim())
        _sanciones.value = cliente().sancionesRecientes()
    }
    fun decidirApelacion(a: ApelacionSocial, aceptar: Boolean, respuesta: String) = lanzar {
        cliente().decidirApelacion(a.id, aceptar, respuesta.trim())
        _apelaciones.value = _apelaciones.value?.filterNot { it.id == a.id }
    }
    /** La persona apela su última sanción desde Estado de la cuenta. */
    fun apelar(texto: String) = lanzar {
        cliente().apelar(texto.trim())
        _estadoCuenta.value = cliente().estadoCuenta()
    }

    /* ----- Historias y notas (24 horas) ----- */
    protected val _historias = MutableStateFlow<List<GrupoHistoriasSocial>>(emptyList())
    /** Las historias de la gente que sigues en esta app (la tuya primero; las no vistas antes). */
    val historias: StateFlow<List<GrupoHistoriasSocial>> = _historias
    protected val _notasGente = MutableStateFlow<List<NotaGente>>(emptyList())
    val notasGente: StateFlow<List<NotaGente>> = _notasGente
    /** Dónde guardar la última barra de historias y notas, para mostrarla al instante y sin conexión (null: no se guarda). */
    protected open val archivoHistorias: java.io.File? = null
    @kotlinx.serialization.Serializable private class BarraGuardada(val historias: List<GrupoHistoriasSocial> = emptyList(), val notas: List<NotaGente> = emptyList(), val cuando: Long = 0)

    fun cargarHistorias() {
        if (_usuario.value == null) return
        scope.launch {
            // Primero lo guardado (si es de las últimas 24 horas), después lo nuevo del servidor.
            if (_historias.value.isEmpty() && _notasGente.value.isEmpty()) archivoHistorias?.let { a ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { JsonSocial.decodeFromString(BarraGuardada.serializer(), a.readText()) }.getOrNull() }
                    ?.takeIf { System.currentTimeMillis() - it.cuando < 24 * 3600_000L }
                    ?.let { if (_historias.value.isEmpty()) _historias.value = it.historias; if (_notasGente.value.isEmpty()) _notasGente.value = it.notas }
            }
            intentar(avisar = false) {
                val h = cliente().historias(); val n = cliente().notasGente()
                _historias.value = h; _notasGente.value = n
                archivoHistorias?.let { a -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { a.writeText(JsonSocial.encodeToString(BarraGuardada.serializer(), BarraGuardada(h, n, System.currentTimeMillis()))) }
                } }
            }
        }
    }
    /** Sube una historia ([jpeg]: la foto ya achicada, si hay) y avisa con [listo]. */
    fun subirHistoria(datos: kotlinx.serialization.json.JsonObject, jpeg: ByteArray?, video: java.io.File? = null, listo: () -> Unit = {}) = lanzar {
        val c = cliente()
        var conFoto = if (jpeg != null) kotlinx.serialization.json.JsonObject(datos + ("imagen" to kotlinx.serialization.json.JsonPrimitive(c.subirImagen(jpeg)))) else datos
        if (video != null) conFoto = kotlinx.serialization.json.JsonObject(conFoto + ("video" to kotlinx.serialization.json.JsonPrimitive(c.subirVideo(video))))
        c.subirHistoria(conFoto)
        video?.delete()
        _historias.value = c.historias()
        listo()
    }
    /** Marca una historia como vista (aquí al instante; en el servidor, de fondo). */
    fun verHistoria(h: HistoriaSocial) {
        if (h.vista) return
        _historias.value = _historias.value.map { g -> g.copy(historias = g.historias.map { if (it.id == h.id) it.copy(vista = true) else it }) }
        scope.launch { runCatching { cliente().verHistoria(h.id) } }
    }
    /** Amigos: quienes se siguen contigo mutuamente. */
    protected val _amigos = MutableStateFlow<List<PersonaSocial>>(emptyList())
    val amigos: StateFlow<List<PersonaSocial>> = _amigos
    fun cargarAmigos() { if (_usuario.value != null) scope.launch { intentar(avisar = false) { _amigos.value = cliente().amigos() } } }
    /** Tu cumpleaños ("MM-DD" o vacío) y el de tus amigos (para «hoy cumple años»). */
    protected val _cumple = MutableStateFlow("")
    val cumple: StateFlow<String> = _cumple
    protected val _cumplesHoy = MutableStateFlow<Set<String>>(emptySet())
    val cumplesHoy: StateFlow<Set<String>> = _cumplesHoy
    fun cargarCumples() {
        if (_usuario.value == null) return
        scope.launch { intentar(avisar = false) {
            _cumple.value = cliente().cumple()
            val hoy = java.time.LocalDate.now().let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }
            _cumplesHoy.value = cliente().cumplesAmigos().filter { it.fecha == hoy }.map { it.persona.usuario }.toSet()
        } }
    }
    /** Los temas de perfil (tienda): los reclamados y el puesto. */
    protected val _temas = MutableStateFlow(TemasCuentaSocial())
    val temas: StateFlow<TemasCuentaSocial> = _temas
    fun cargarTemas() { if (_usuario.value != null) scope.launch { intentar(avisar = false) { _temas.value = cliente().temas() } } }
    fun reclamarTema(tema: String) = lanzar { cliente().reclamarTema(tema); _temas.value = cliente().temas() }
    fun ponerTema(tema: String) = lanzar {
        cliente().ponerTema(tema)
        _temas.value = if (tema.startsWith("marco:")) _temas.value.copy(marco = tema.removePrefix("marco:")) else _temas.value.copy(puesto = tema)
    }
    /** Las chispas del día. */
    fun cobrarDiario() = lanzar { _temas.value = cliente().cobrarDiario() }
    fun ponerCumple(fecha: String) = lanzar { cliente().ponerCumple(fecha); _cumple.value = fecha }
    suspend fun mejoresAmigos(): List<String> = runCatching { cliente().mejoresAmigos() }.getOrDefault(emptyList())

    /* Listas en común (entre amigos). Devuelven null si falló (sin internet o sin permiso). */
    suspend fun listasComunes(): List<ListaComunSocial>? = runCatching { cliente().listasComunes() }.getOrNull()
    suspend fun crearListaComun(nombre: String, miembros: List<String>): ListaComunSocial? = runCatching { cliente().crearListaComun(nombre, miembros) }.getOrNull()
    suspend fun listaComun(id: Long): DetalleListaComunSocial? = runCatching { cliente().listaComun(id) }.getOrNull()
    suspend fun agregarAListaComun(id: Long, datos: kotlinx.serialization.json.JsonObject) = runCatching { cliente().agregarAListaComun(id, datos) }.isSuccess
    suspend fun quitarDeListaComun(id: Long, item: Long) = runCatching { cliente().quitarDeListaComun(id, item) }.isSuccess
    suspend fun votarListaComun(id: Long, item: Long, si: Boolean) = runCatching { cliente().votarListaComun(id, item, si) }.isSuccess
    suspend fun sumarAListaComun(id: Long, usuario: String) = runCatching { cliente().sumarAListaComun(id, usuario) }.isSuccess
    suspend fun salirDeListaComun(id: Long) = runCatching { cliente().salirDeListaComun(id) }.isSuccess
    suspend fun amigosParaListas(): List<PersonaSocial> = runCatching { cliente().amigos() }.getOrDefault(emptyList())
    fun ponerMejoresAmigos(l: List<String>) = lanzar { cliente().ponerMejoresAmigos(l) }
    fun silenciarHistorias(usuario: String, si: Boolean) = lanzar {
        cliente().silenciarHistorias(usuario, si)
        _historias.value = cliente().historias()
    }
    /** Votar en la encuesta de una historia; devuelve la historia con los votos. */
    suspend fun votarHistoria(h: HistoriaSocial, opcion: Int): HistoriaSocial? = runCatching { cliente().votarHistoria(h.id, opcion) }.getOrNull()?.also { v ->
        _historias.value = _historias.value.map { g -> g.copy(historias = g.historias.map { if (it.id == h.id) it.copy(votos = v.votos, miVoto = v.miVoto) else it }) }
    }
    fun responderPregunta(h: HistoriaSocial, texto: String) = lanzar { cliente().responderPregunta(h.id, texto) }
    suspend fun respuestasPregunta(h: HistoriaSocial) = runCatching { cliente().respuestasPregunta(h.id) }.getOrDefault(emptyList())
    fun fijarPublicacion(id: Long, si: Boolean) = lanzar { cliente().fijarPublicacion(id, si) }
    fun destacarHistoria(h: HistoriaSocial, titulo: String) = lanzar { cliente().destacarHistoria(h.id, titulo) }
    /** Tus historias de los últimos 90 días (también las vencidas), para volver a verlas o destacarlas. */
    suspend fun archivoHistorias(): List<HistoriaSocial> = runCatching { cliente().archivoHistorias() }.getOrDefault(emptyList())
    fun portadaDestacada(h: HistoriaSocial) = lanzar { cliente().portadaDestacada(h.id) }
    suspend fun destacadas(usuario: String): List<GrupoDestacadasSocial> = runCatching { cliente().destacadas(usuario) }.getOrDefault(emptyList())
    fun meGustaHistoria(h: HistoriaSocial, si: Boolean) {
        _historias.value = _historias.value.map { g -> g.copy(historias = g.historias.map { if (it.id == h.id) it.copy(meGusta = si) else it }) }
        scope.launch { runCatching { cliente().meGustaHistoria(h.id, si) } }
    }
    fun borrarHistoria(h: HistoriaSocial) = lanzar {
        cliente().borrarHistoria(h.id)
        _historias.value = _historias.value.map { g -> g.copy(historias = g.historias.filterNot { it.id == h.id }) }.filter { it.historias.isNotEmpty() }
    }
    suspend fun vistasHistoria(h: HistoriaSocial): List<VistaHistoriaSocial> = runCatching { cliente().vistasHistoria(h.id) }.getOrDefault(emptyList())
    suspend fun historiasDe(usuario: String): GrupoHistoriasSocial? =
        _historias.value.firstOrNull { it.persona.usuario == usuario } ?: runCatching { cliente().historiasDe(usuario) }.getOrNull()?.takeIf { it.historias.isNotEmpty() }
    /** Pone (o quita, si está vacía y sin adjunto) tu nota de 24 horas. */
    fun ponerNotaGente(texto: String, adjunto: kotlinx.serialization.json.JsonObject?, adjunto2: kotlinx.serialization.json.JsonObject? = null) = lanzar {
        val datos = kotlinx.serialization.json.JsonObject(listOfNotNull(adjunto?.let { "adjunto" to it }, adjunto2?.let { "adjunto2" to it }).toMap())
        cliente().ponerNotaGente(texto.trim().take(60), datos)
        _notasGente.value = cliente().notasGente()
    }
    suspend fun canciones(q: String): List<CancionSocial> = runCatching { cliente().canciones(q, java.util.Locale.getDefault().country.ifBlank { "CO" }) }.getOrDefault(emptyList())

    fun cargarAvisos() = lanzar {
        _avisos.value = cliente().avisos()
        _solicitudes.value = cliente().solicitudes()
        cliente().leidos()
        _sinLeer.value = 0
    }

    fun borrarAviso(id: Long?) = lanzar {
        cliente().borrarAvisos(id)
        _avisos.value = if (id == null) emptyList() else _avisos.value?.filter { it.id != id }
    }

    fun revisarSinLeer() {
        if (_usuario.value == null) return
        scope.launch { runCatching { _sinLeer.value = cliente().sinLeer(); alContarAvisos(_sinLeer.value) } }
    }

}

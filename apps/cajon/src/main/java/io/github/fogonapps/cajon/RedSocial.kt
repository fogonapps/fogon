package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.paraPantalla

import android.app.Application
import io.github.fogonapps.cajon.datos.AvisoSocial
import io.github.fogonapps.cajon.datos.CajaFuerte
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.cajon.datos.ComentarioSocial
import io.github.fogonapps.cajon.datos.CasoModeracion
import io.github.fogonapps.cajon.datos.necesitaElevar
import io.github.fogonapps.cajon.datos.ErrorSocial
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.llaveParaRecuperacion
import io.github.fogonapps.cajon.datos.SubidasPerfil
import io.github.fogonapps.social.SesionCuenta
import io.github.fogonapps.cajon.datos.SincronizacionesAgrupadas
import io.github.fogonapps.cajon.datos.prepararSubidaPerfil
import io.github.fogonapps.cajon.datos.RecuperacionCajon
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.PartesPerfil
import io.github.fogonapps.cajon.datos.ParteSubida
import io.github.fogonapps.cajon.datos.PerfilSocial
import io.github.fogonapps.cajon.datos.PerfilSubido
import io.github.fogonapps.cajon.datos.PersonaSocial
import io.github.fogonapps.cajon.datos.PublicacionPropia
import io.github.fogonapps.cajon.datos.PublicacionSocial
import io.github.fogonapps.cajon.datos.PublicacionSubida
import io.github.fogonapps.cajon.datos.Visibilidad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

typealias PasoAcceso = io.github.fogonapps.social.PasoAcceso
typealias MotivoCodigo = io.github.fogonapps.social.MotivoCodigo
typealias Acceso = io.github.fogonapps.social.EstadoAcceso

/**
 * La red de El Cajón (Fogón Social): la cuenta, el feed, los perfiles de otras personas, seguir, me gusta,
 * comentarios, guardados, avisos y lo que se publica. Todo es opcional: sin cuenta, nada de esto sale del teléfono.
 * Lo del cajón que la persona marcó como público o para sus seguidores se sube solo ([sincronizar]).
 */
class RedSocial(
    private val app: Application,
    scope: CoroutineScope,
    private val caja: CajaFuerte,
    private val estado: () -> Estado?,
    private val cambiar: ((Estado) -> Estado) -> Unit,
    private val decir: (Int) -> Unit,
    /** Espera a descargar, descifrar, unir y guardar el cajón; false mantiene bloqueada la publicación. */
    private val sincronizarCajon: suspend () -> Boolean = { false },
    private val directorio: java.io.File = app.filesDir,
    private val cuentaDuplicada: suspend (io.github.fogonapps.cajon.datos.Acceso) -> Boolean = { false },
    guardarIdCuenta: (String) -> Unit = {},
    leerIdCuenta: () -> String = { "" },
    private val almacenSesion: io.github.fogonapps.social.AlmacenSesionSocial =
        io.github.fogonapps.cajon.datos.AlmacenSesionCajon(caja, leerIdCuenta, guardarIdCuenta),
) : io.github.fogonapps.social.ControladorCuentaSocial(
    scope, almacenSesion.sesion, caja.usuarioSocial.takeIf { almacenSesion.token != null },
) {
    override fun alContarAvisos(cantidad: Int) { AvisosRed.vistoAvisos(app, cantidad, caja.perfilId) }
    override fun avisar(evento: io.github.fogonapps.social.EventoSocial) = decir(when (evento) {
        io.github.fogonapps.social.EventoSocial.SIN_CONEXION -> R.string.red_sin_conexion
        io.github.fogonapps.social.EventoSocial.PUBLICACION_GUARDADA -> R.string.red_guardada
        io.github.fogonapps.social.EventoSocial.SOLICITUD_ENVIADA -> R.string.red_solicitud_enviada
        io.github.fogonapps.social.EventoSocial.SIGUIENDO -> R.string.red_ahora_sigues
        io.github.fogonapps.social.EventoSocial.CUENTA_BLOQUEADA -> R.string.red_bloqueado
        io.github.fogonapps.social.EventoSocial.CUENTA_DESBLOQUEADA -> R.string.red_desbloqueado
        io.github.fogonapps.social.EventoSocial.REPORTE_ENVIADO -> R.string.red_reportado
        io.github.fogonapps.social.EventoSocial.CORREO_VINCULADO -> R.string.red_correo_vinculado
        io.github.fogonapps.social.EventoSocial.CUENTA_BORRADA -> R.string.red_cuenta_borrada
    })

    fun detenerPerfil() {
        sesion.invalidar()
        recuperacionCajon.reiniciar()
        sincronizacionesCajon.cancelar()
    }
    init { caja.redPermitida = false; caja.migrarPerfilRed() }

    /** El cajón guardado en la cuenta necesita la contraseña para abrirse en este teléfono (entró con el código). */
    private val _cajonBloqueado = MutableStateFlow(false)
    private val recuperacionCajon = RecuperacionCajon()
    private val sincronizacionesCajon = SincronizacionesAgrupadas(scope)
    private val recuperandoPerfil = Mutex()
    private var aplicandoPerfil = false
    val cajonBloqueado: StateFlow<Boolean> = _cajonBloqueado

    /**
     * Arma o abre la llave del cajón con la contraseña: si el servidor ya tiene una llave envuelta, la abre; si no,
     * crea una (o usa la de este teléfono) y la sube envuelta. Luego sincroniza.
     */
    protected override suspend fun llaveConClave(clave: String) {
        val marca = sesion.actual()
        val token = almacenSesion.token ?: return
        revisarCajon {
            val cliente = caja.clienteSocial(token)
            val enServidor = cliente.cajon().llave
            if (!sesion.vigente(marca)) return@revisarCajon
            val llave = if (enServidor.isNotBlank()) withContext(Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.desenvolver(enServidor, clave) }
            else (caja.llaveCajon ?: io.github.fogonapps.cajon.datos.Nube.llaveNueva()).also {
                val envuelta = withContext(Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.envolver(it, clave) }
                if (!sesion.vigente(marca)) return@revisarCajon
                cliente.guardarLlaveCajon(envuelta)
            }
            if (sesion.vigente(marca)) caja.llaveCajon = llave
        }
    }

    /** Con la contraseña, abre en este teléfono el cajón guardado en la cuenta. */
    fun desbloquearCajon(clave: String) = scope.launch {
        try { intentar { llaveConClave(clave) } }
        catch (x: kotlinx.coroutines.CancellationException) { throw x }
        catch (x: Exception) { decir(R.string.red_clave_mala) }
    }

    /** Después de entrar: si la cuenta ya tiene el cajón guardado y este teléfono no tiene la llave, pide la contraseña. */
    private suspend fun revisarCajon(prepararLlave: suspend () -> Unit = {}) {
        if (!caja.redPermitida) return
        val marca = sesion.actual()
        val token = almacenSesion.token ?: return
        recuperacionCajon.revisar(
            sesionValida = { sesion.vigente(marca) },
            prepararLlave = prepararLlave,
            tieneLlave = { caja.llaveCajon != null },
            hayCajon = { caja.clienteSocial(token).cajon().let { it.llave.isNotBlank() || it.sobre.isNotBlank() } },
            unir = sincronizarCajon,
            bloquear = { _cajonBloqueado.value = it },
        )
    }

    /** Sincronización pedida por el ViewModel; comparte la exclusión con comprobar y desbloquear. */
    fun recuperarCajon(): Job {
        if (!caja.redPermitida || _borrarEl.value > 0) return scope.launch {}
        val marca = sesion.actual()
        return sincronizacionesCajon.solicitar {
            if (sesion.vigente(marca)) intentar(avisar = false) { revisarCajon() }
        }
    }

    val permitida: StateFlow<Boolean> = caja.permisoRed
    protected override fun cliente() = caja.clienteSocial()
    protected override fun clienteAcceso() = ClienteSocial()
    protected override fun permitirRed(permitir: Boolean) { caja.redPermitida = permitir }
    protected override fun guardarPolitica(version: String) { almacenSesion.politicaAceptada = version }
    protected override fun pausarPublicaciones() { subida?.cancel(); sincronizacionesCajon.cancelar() }
    protected override fun reanudarPublicaciones() { recuperarCajon(); sincronizarYa(); cargarFeed(avisar = false) }

    /** Si el servidor puede mandar códigos por correo (null mientras no se sabe), y la versión de la política. */
    private val _correo = MutableStateFlow<Boolean?>(null)
    val correoDisponible: StateFlow<Boolean?> = _correo

    private val _borrarEl = MutableStateFlow(0L)
    val borrarEl: StateFlow<Long> = _borrarEl
    private var decisionBorrado: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
    override fun ponerCuenta(cuenta: io.github.fogonapps.cajon.datos.MiCuenta) {
        almacenSesion.guardarIdCuenta(cuenta.idCuenta)
        _cuenta.value = cuenta
        almacenSesion.politicaAceptada = cuenta.politica
        val pendiente = io.github.fogonapps.cajon.datos.debeAceptarPolitica(almacenSesion.token != null, cuenta.politica, politica)
        caja.redPermitida = !pendiente
        if (pendiente && !_politicaPendiente.value) _hojaPolitica.value = true
        _politicaPendiente.value = pendiente
    }
    private val _publicando = MutableStateFlow(false)
    val publicando: StateFlow<Boolean> = _publicando
    private val _progresoVideo = MutableStateFlow<Float?>(null)
    val progresoVideo: StateFlow<Float?> = _progresoVideo
    private var publicacionEnCurso: Job? = null
    fun cancelarPublicacion() { publicacionEnCurso?.cancel() }
    fun cabecerasVideo(): Map<String, String> {
        check(caja.redPermitida) { "politica_pendiente" }
        return almacenSesion.token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
    }

    init {
        scope.launch {
            ClienteSocial.cuentasPorBorrar.collect { (token, desde) ->
                if (token == almacenSesion.token) {
                    val fecha = if (desde > 0) desde else runCatching { caja.clienteSocial(token).miCuenta().borrarEl }.getOrDefault(0L)
                    if (token == almacenSesion.token) {
                        sincronizacionesCajon.cancelar(); subida?.cancel()
                        _borrarEl.value = fecha
                    }
                }
            }
        }
    }

    /** Hace [trabajo]; en segundo plano omite avisos salvo el cierre de sesión por 401. */
    protected override suspend fun intentar(avisar: Boolean, marca: SesionCuenta.Marca, trabajo: suspend () -> Unit): Boolean =
        sesion.intentar(marca, trabajo) { x ->
            if (x is ErrorSocial) {
                if (x.codigo == 401) { olvidarCuenta(); decir(R.string.red_sesion_vencida) }
                else if (x.codigo == 403 && x.error in setOf("mfa_requerida", "sesion_no_elevada", "elevacion_requerida")) {
                    _cuenta.value = _cuenta.value?.copy(mfa = x.error != "mfa_requerida", elevadaHasta = 0)
                    _equipoBloqueado.value = true
                }
                else if (x.error == "por_borrar") {
                    sincronizacionesCajon.cancelar(); subida?.cancel()
                    if (x.desde > 0) _borrarEl.value = x.desde
                }
                else if (avisar) decir(when (x.error) {
                    "video_formato", "video_duracion", "video_grande", "video_y_fotos", "poster" -> R.string.video_preparacion_error
                    "codigo_mfa", "recuperacion" -> R.string.equipo_codigo_error
                    "sin_permiso" -> R.string.equipo_sin_permiso
                    "equipo_propio", "solo_principal", "nivel_protegido" -> R.string.equipo_protegido
                    "reporte_propio" -> R.string.mod_reporte_propio
                    "usuario_tomado" -> R.string.red_usuario_tomado
                    "usuario" -> R.string.red_usuario_malo
                    "correo" -> R.string.red_correo_malo
                    "codigo" -> R.string.red_codigo_malo
                    "codigo_vencido", "sin_codigo" -> R.string.red_codigo_vencido
                    "actualiza_app" -> R.string.red_actualiza_app
                    "cuenta_protegida" -> if (_equipoOcupado.value) R.string.equipo_protegido else R.string.red_cuenta_protegida
                    "por_borrar" -> R.string.red_por_borrar
                    "correo_tomado" -> R.string.red_correo_tomado
                    "despacio" -> R.string.red_despacio
                    "clave_mala" -> R.string.red_clave_mala
                    "clave_comun" -> R.string.red_clave_comun
                    "sin_correo" -> R.string.red_codigo_no_disponible
                    "clave_corta" -> R.string.red_clave_corta
                    "usuario_espera" -> R.string.red_usuario_espera
                    "suspendida" -> R.string.red_suspendida
                    "cerrada" -> R.string.red_cerrada
                    "restringida" -> R.string.red_restringida
                    "privada" -> R.string.red_es_privada
                    "no_existe" -> R.string.red_no_existe
                    else -> R.string.red_error
                })
            } else if (avisar) decir(R.string.red_sin_conexion)
        }


    /* ----- La cuenta ----- */

    override fun revisarServidor() = scope.launch {
        val revision = revisionPolitica
        val marca = sesion.actual()
        if (caja.perfilRedPendiente) intentar(avisar = false) { traerPerfilPropio() }
        if (!sesion.vigente(marca)) return@launch
        if (!sesion.vigente(marca)) return@launch
        val info = runCatching { ClienteSocial().info() }.getOrNull()
        if (!sesion.vigente(marca)) return@launch
        info?.let { i ->
            _correo.value = i.correo == "true"
            if (i.politica.isNotBlank()) politica = i.politica
        }
        // Cuentas creadas antes de que existieran estas partes: lo que hace que el perfil tenga sentido queda a la vista, una sola vez
        // (lo que la persona ya eligió se respeta).
        if (_usuario.value != null && !caja.partesRedPuestas) {
            cambiar { e -> e.copy(perfil = e.perfil.copy(visibilidad = PartesPerfil.AL_CREAR_CUENTA + e.perfil.visibilidad, cambio = System.currentTimeMillis())) }
            caja.partesRedPuestas = true
        }
        if (_usuario.value != null) intentar(avisar = false) {
            val c = cliente().miCuenta()
            if (!sesion.vigente(marca)) return@intentar
            if (revision != revisionPolitica) return@intentar
            _privada.value = c.privada; _correoCuenta.value = c.correo; ponerCuenta(c)
            if (caja.redPermitida) {
                // El perfil es uno solo para las cuatro apps: si se cambió desde otra (foto, nombre, bio, portada),
                // se trae antes de subir nada, para no mostrar ni volver a subir uno viejo.
                val r = runCatching { cliente().revisionPerfil() }.getOrNull()
                if (r != null && sesion.vigente(marca)) {
                    val vista = caja.revisionPerfilVista
                    if (vista == 0L) caja.revisionPerfilVista = r
                    else if (r != vista) caja.perfilRedPendiente = true
                }
                if (caja.perfilRedPendiente) traerPerfilPropio()
                if (r != null && !caja.perfilRedPendiente && sesion.vigente(marca)) caja.revisionPerfilVista = r
                recuperarCajon(); sincronizarYa()
            }
        }
    }

    /** La llave del cajón envuelta con una contraseña recién puesta (la de este teléfono, o una nueva). */
    protected override suspend fun llaveNueva(clave: String) {
        val marca = sesion.actual()
        val token = almacenSesion.token ?: return
        revisarCajon {
            val recuperada = llaveParaRecuperacion(caja.llaveCajon)
            val llave = recuperada.llave
            val envuelta = withContext(Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.envolver(llave, clave) }
            if (!sesion.vigente(marca)) return@revisarCajon
            caja.clienteSocial(token).guardarLlaveCajon(envuelta)
            if (sesion.vigente(marca)) caja.guardarLlaveRecuperada(recuperada)
        }
    }

    /** Cuenta nueva con el correo ya comprobado y su contraseña (con ella el cajón se guarda en la cuenta desde ya). */
    fun crearConCorreo(usuario: String, nombre: String, clave: String, foto: String? = null) = lanzar {
        val a = _acceso.value
        _acceso.value = a.copy(ocupado = true)
        try {
            if (entro(ClienteSocial().entrar(a.correo, a.codigo, usuario.trim().lowercase(), nombre.trim(), politica, clave), nombre.trim())) {
                val marca = sesion.actual()
                if (foto != null) intentar {
                    val imagen = jpeg(foto, 512, 400_000) ?: throw IOException("foto")
                    if (!sesion.vigente(marca)) return@intentar
                    cliente().subirFoto(imagen)
                    val ruta = withContext(Dispatchers.IO) {
                        val carpeta = java.io.File(directorio, "portadas").apply { mkdirs() }
                        val archivo = java.io.File(carpeta, "perfil-red-${System.currentTimeMillis()}.jpg")
                        archivo.writeBytes(imagen)
                        android.net.Uri.fromFile(archivo).toString()
                    }
                    if (sesion.vigente(marca)) {
                        ultimaFoto = ruta
                        cambiar { e -> e.copy(perfil = e.perfil.copy(foto = ruta, cambio = System.currentTimeMillis())) }
                    }
                }
                if (sesion.vigente(marca)) intentar { llaveNueva(clave) }
            }
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Crea o cambia la contraseña; con ella, la llave del cajón se vuelve a envolver (y si no había, se crea). */
    fun ponerClave(nueva: String, actual: String, listo: () -> Unit) = lanzar {
        val marca = sesion.actual()
        val token = almacenSesion.token ?: return@lanzar
        revisarCajon {
            val llaveAntes = caja.llaveCajon ?: actual.takeIf { it.isNotBlank() }?.let { a ->
                runCatching { caja.clienteSocial(token).cajon().llave.takeIf { it.isNotBlank() }?.let { io.github.fogonapps.cajon.datos.Nube.desenvolver(it, a) } }.getOrNull()
            }
            if (!sesion.vigente(marca)) return@revisarCajon
            caja.clienteSocial(token).ponerClave(nueva, actual)
            if (!sesion.vigente(marca)) return@revisarCajon
            val llave = llaveAntes ?: io.github.fogonapps.cajon.datos.Nube.llaveNueva()
            val envuelta = withContext(Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.envolver(llave, nueva) }
            if (!sesion.vigente(marca)) return@revisarCajon
            caja.clienteSocial(token).guardarLlaveCajon(envuelta)
            if (sesion.vigente(marca)) caja.llaveCajon = llave
        }
        if (sesion.vigente(marca)) { decir(R.string.red_clave_lista); revisarServidor(); listo() }
    }

    /** Guarda el resto del perfil incluso cuando el límite de apodo rechaza el nombre. */
    fun guardarApodo(nombre: String, guardar: (Boolean) -> Unit) = scope.launch {
        var aceptado = false
        val marca = sesion.actual()
        intentar {
            try { cliente().cambiarApodo(nombre); aceptado = true }
            catch (x: ErrorSocial) {
                if (x.error != "apodo_espera") throw x
                val cuenta = cliente().miCuenta()
                if (sesion.vigente(marca)) {
                    ponerCuenta(cuenta)
                    val fecha = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(cuenta.apodoDesde))
                    android.widget.Toast.makeText(app, io.github.fogonapps.identidad.Idioma.envolver(app).getString(R.string.red_apodo_desde, fecha), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        if (sesion.vigente(marca)) guardar(aceptado)
    }

    fun cambiarUsuario(nuevo: String, listo: () -> Unit) = lanzar {
        val u = cliente().cambiarUsuario(nuevo.trim().lowercase())
        caja.usuarioSocial = u; _usuario.value = u
        cambiar { e -> e.copy(perfil = e.perfil.copy(usuario = u, cambio = System.currentTimeMillis())) }
        decir(R.string.red_usuario_cambiado); revisarServidor(); listo()
    }

    protected override suspend fun entro(a: io.github.fogonapps.cajon.datos.Acceso, nombreNuevo: String): Boolean {
        if (cuentaDuplicada(a)) return false
        sesion.invalidar()
        sincronizacionesCajon.cancelar()
        almacenSesion.token = a.token
        val marca = sesion.actual()
        caja.usuarioSocial = a.usuario
        almacenSesion.politicaAceptada = if (a.nueva) politica else ""
        caja.redPermitida = a.nueva
        caja.partesRedPuestas = true
        _acceso.value = Acceso()
        if (io.github.fogonapps.cajon.datos.debePreguntarBorrado(a.borrarEl)) {
            subida?.cancel()
            val decision = kotlinx.coroutines.CompletableDeferred<Boolean>()
            decisionBorrado = decision
            _borrarEl.value = a.borrarEl
            if (!decision.await() || !sesion.vigente(marca)) return false
        }
        _usuario.value = a.usuario
        AvisosRed.programar(app)
        subida?.cancel()
        subidasPerfil.reiniciar(); ultimaFoto = null
        caja.borrarFotoRed = false
        caja.perfilRedPendiente = !a.nueva
        recuperacionCajon.reiniciar(cuentaNueva = a.nueva)
        _cajonBloqueado.value = false
        if (!a.nueva) intentar {
            val info = ClienteSocial().info()
            if (!sesion.vigente(marca)) return@intentar
            if (info.politica.isNotBlank()) politica = info.politica
            val c = cliente().miCuenta()
            if (sesion.vigente(marca)) ponerCuenta(c)
        }
        if (a.nueva) {
            val plan = estado()?.let { planEntrada(it, a.usuario, nueva = true, nombre = nombreNuevo) }
            if (plan != null) {
                cambiar { plan.estado }
                if (plan.subir) sincronizarYa()
            }
        } else if (caja.redPermitida) {
            intentar { traerPerfilPropio() }
        }
        if (!sesion.vigente(marca)) return false
        decir(if (a.nueva) R.string.red_bienvenida else R.string.red_entraste)
        cargarFeed(avisar = false)
        revisarServidor()
        return sesion.vigente(marca)
    }

    private suspend fun traerPerfilPropio() {
        val marca = sesion.actual()
        recuperandoPerfil.withLock {
            if (!sesion.vigente(marca) || !caja.perfilRedPendiente) return@withLock
            val usuario = _usuario.value ?: return@withLock
            val cliente = caja.clienteSocial()
            val remoto = cliente.perfil(usuario)
            if (!sesion.vigente(marca)) return@withLock
            val foto = if (remoto.persona.foto) {
                try {
                    val bytes = cliente.descargarFoto(usuario)
                    if (!sesion.vigente(marca)) return@withLock
                    withContext(Dispatchers.IO) {
                        val carpeta = java.io.File(directorio, "portadas").apply { mkdirs() }
                        val archivo = java.io.File(carpeta, "perfil-red-${System.currentTimeMillis()}.jpg")
                        archivo.writeBytes(bytes)
                        android.net.Uri.fromFile(archivo).toString()
                    }
                } catch (x: IOException) {
                    if (x is ErrorSocial && x.codigo == 401) throw x
                    ClienteSocial.foto(usuario)
                }
            } else ""
            if (!sesion.vigente(marca)) return@withLock
            aplicandoPerfil = true
            try { cambiar { planEntrada(it, usuario, nueva = false, remoto = remoto, foto = foto).estado } }
            finally { aplicandoPerfil = false }
            if (estado() == null) return@withLock
            // Bajar identidad y foto no confirma ninguna publicación local en el servidor.
            ultimaFoto = foto
            caja.perfilRedPendiente = false
        }
    }

    fun quitarFotoPropia() { if (_usuario.value != null) caja.borrarFotoRed = true }

    /** Ponerle correo a una cuenta hecha sin él: primero [pedirCodigo] y luego esto con el código. */
    fun vincularCorreo(codigo: String) = lanzar {
        cliente().vincularCorreo(_acceso.value.correo, codigo.trim())
        _correoCuenta.value = _acceso.value.correo
        _acceso.value = Acceso()
        decir(R.string.red_correo_vinculado)
    }

    fun salir() = scope.launch {
        val marca = sesion.actual()
        runCatching { cliente().salir() }
        if (sesion.vigente(marca)) olvidarCuenta()
    }

    fun decidirBorrado(conservar: Boolean) = seguridad {
        val marca = sesion.actual()
        val cliente = cliente()
        if (conservar) {
            cliente.conservarCuenta()
            if (!sesion.vigente(marca)) return@seguridad
            _borrarEl.value = 0
            if (decisionBorrado != null) {
                decisionBorrado?.complete(true); decisionBorrado = null
            } else {
                traerPerfilPropio(); recuperarCajon(); cargarFeed()
            }
        } else {
            runCatching { cliente.salir() }
            if (sesion.vigente(marca)) olvidarCuenta()
        }
    }

    override fun olvidarCuenta() {
        caja.redPermitida = false
        _politicaPendiente.value = false; _hojaPolitica.value = false
        decisionBorrado?.complete(false); decisionBorrado = null
        _borrarEl.value = 0; _correoPendiente.value = ""
        sesion.invalidar()
        sincronizacionesCajon.cancelar()
        subida?.cancel(); subidasPerfil.reiniciar(); ultimaFoto = null
        caja.perfilRedPendiente = false; caja.borrarFotoRed = false; recuperacionCajon.reiniciar()
        almacenSesion.token = null; caja.usuarioSocial = null; caja.publicacionesSubidas = emptySet(); caja.llaveCajon = null; _cajonBloqueado.value = false
        _sesiones.value = null
        _usuario.value = null
        AvisosRed.programar(app)
        _feed.value = null; _avisos.value = null; _guardados.value = null; _sinLeer.value = 0
        _perfiles.value = emptyMap(); _gente.value = emptyMap(); _correoCuenta.value = ""; _cuenta.value = null; _equipoBloqueado.value = false
        _casos.value = null; _frasesPendientes.value = emptyList()
    }

    /* ----- Subir lo del cajón ----- */

    private var subida: Job? = null
    /** Lo último que se subió, para no subir lo mismo cada vez que cambia algo que no se comparte. */
    private val subidasPerfil = SubidasPerfil()
    private var ultimaFoto: String? = null

    /** Después de un cambio en el cajón: sube lo compartido (perfil y publicaciones) a los pocos segundos. */
    fun sincronizar() {
        if (!caja.redPermitida || _borrarEl.value > 0 || _usuario.value == null || aplicandoPerfil) return
        subida?.cancel()
        subida = scope.launch { delay(5000); subirAhora() }
    }

    fun sincronizarYa() { if (_borrarEl.value > 0) return; subida?.cancel(); subida = scope.launch { subirAhora() } }

    private suspend fun subirAhora() {
        if (!caja.redPermitida || _borrarEl.value > 0 || _usuario.value == null) return
        val marca = sesion.actual()
        val preparacion = io.github.fogonapps.social.PreparacionPublicacion { vigente -> prepararSubidaPerfil(
            pendiente = { caja.perfilRedPendiente },
            recuperarPerfil = { intentar(avisar = false) { traerPerfilPropio() } },
            recuperarCajon = {
                if (!recuperacionCajon.recuperado) intentar(avisar = false) { revisarCajon() }
                puedeSubirPerfil(caja.perfilRedPendiente, recuperacionCajon.recuperado, _cajonBloqueado.value)
            },
            vigente = vigente,
        ) }
        if (!preparacion.preparar { sesion.vigente(marca) }) return
        val e = estado() ?: return
        intentar(avisar = false) {
            val aporte = object : io.github.fogonapps.social.AporteSocialApp {
                override suspend fun perfil() = perfilDe(e).copy(portada = portadaEnLaRed(e.perfil.portada))
                override fun publicaciones() = publicacionesDe(e)
            }
            val cliente by lazy { caja.clienteSocial() }
            val subido = io.github.fogonapps.social.subirAporteSocial(
                aporte, subidasPerfil,
                vigente = { sesion.vigente(marca) },
                clavesAnteriores = { caja.publicacionesSubidas },
                cliente = { cliente },
                confirmar = { caja.publicacionesSubidas = it },
            )
            if (!subido || !sesion.vigente(marca)) return@intentar
            val foto = e.perfil.foto
            // Lo que se acaba de subir ya es lo de este teléfono: al final se anota su revisión para no traerlo de vuelta.
            when (accionFoto(foto, ultimaFoto, caja.borrarFotoRed)) {
                "borrar" -> {
                    cliente.quitarFoto()
                    if (sesion.vigente(marca)) { caja.borrarFotoRed = false; ultimaFoto = foto }
                }
                "subir" -> jpeg(foto, 512, 400_000)?.let {
                    if (!sesion.vigente(marca)) return@intentar
                    cliente.subirFoto(it)
                    if (sesion.vigente(marca)) { ultimaFoto = foto; caja.borrarFotoRed = false }
                }
            }
            if (sesion.vigente(marca)) runCatching { caja.revisionPerfilVista = cliente.revisionPerfil() }
        }
    }

    /**
     * La portada como la ve la red: si es una imagen del teléfono, se sube una vez (JPEG de hasta 1600 px) y queda su
     * dirección en el servidor; si ya es de internet, esa misma.
     */
    private suspend fun portadaEnLaRed(portada: String): String {
        val marca = sesion.actual()
        val cliente = cliente()
        if (portada.isBlank() || portada.startsWith("https://")) return portada.takeIf { it.startsWith("https://") }.orEmpty()
        caja.portadaSubida?.split('|')?.takeIf { it.size == 2 && it[0] == portada }?.let { return it[1] }
        val jpg = jpeg(portada, 1600, 900_000) ?: return ""
        if (!sesion.vigente(marca)) return ""
        val url = ClienteSocial.imagen(cliente.subirImagen(jpg))
        if (sesion.vigente(marca)) caja.portadaSubida = "$portada|$url"
        return url
    }

    /* ----- Feed, perfiles y publicaciones ----- */

    /** Reseñas por fuente, parte y orden; las páginas de cada consulta se conservan separadas. */
    private val _cargandoResenas = MutableStateFlow<Set<String>>(emptySet())
    val cargandoResenas: StateFlow<Set<String>> = _cargandoResenas
    private val _finResenas = MutableStateFlow<Set<String>>(emptySet())
    val finResenas: StateFlow<Set<String>> = _finResenas
    private val consultasResenas = mutableMapOf<String, Long>()
    fun claveResenas(fuente: String, parte: String = "", orden: String = "utiles") = "resenas:$fuente|$parte|$orden"
    fun cargarResenas(fuente: String, parte: String = "", orden: String = "utiles", mas: Boolean = false) {
        val clave = claveResenas(fuente, parte, orden)
        if (clave in _cargandoResenas.value) return
        val turno = System.nanoTime()
        consultasResenas[clave] = turno
        _cargandoResenas.value += clave
        scope.launch {
            try {
                intentar {
                    val anteriores = if (mas) _listas.value[clave].orEmpty() else emptyList()
                    val pagina = caja.clienteSocial().resenas(fuente, parte, orden, anteriores.minOfOrNull { it.cuando })
                    if (consultasResenas[clave] == turno) {
                        _listas.value = _listas.value + (clave to (anteriores + pagina).distinctBy { it.id })
                        _finResenas.value = if (pagina.size < 50) _finResenas.value + clave else _finResenas.value - clave
                    }
                }
            } finally { _cargandoResenas.value -= clave }
        }
    }

    private val votandoUtil = mutableSetOf<Long>()
    fun util(p: PublicacionSocial) {
        if (_usuario.value == null || p.autor.usuario == _usuario.value || !votandoUtil.add(p.id)) return
        val si = !p.yoUtil
        tocar(p.id) { it.copy(yoUtil = si, utiles = (it.utiles + if (si) 1 else -1).coerceAtLeast(0)) }
        scope.launch {
            try {
                if (!intentar { cliente().util(p.id, quitar = !si) }) tocar(p.id) { it.copy(yoUtil = p.yoUtil, utiles = p.utiles) }
            } finally { votandoUtil.remove(p.id) }
        }
    }

    private val _subiendoFotoResena = MutableStateFlow(false)
    val subiendoFotoResena: StateFlow<Boolean> = _subiendoFotoResena
    fun subirFotoResena(uri: android.net.Uri, lista: (String) -> Unit) {
        if (_subiendoFotoResena.value) return
        _subiendoFotoResena.value = true
        scope.launch {
            try { intentar {
                val jpg = jpeg(uri.toString(), 1600, 900_000) ?: throw IOException("sin imagen")
                lista(cliente().subirImagen(jpg))
            } } finally { _subiendoFotoResena.value = false }
        }
    }

    fun borrarResena(clave: String, eliminarEnRed: Boolean = true) {
        val cargadas = _vistas.value.values + _feed.value.orEmpty() + _explorar.value.orEmpty() +
            _guardados.value.orEmpty() + _perfiles.value.values.flatMap { it.publicaciones } + _listas.value.values.flatten()
        val p = cargadas.firstOrNull { it.autor.usuario == _usuario.value && (it.datos["clave"] as? JsonPrimitive)?.content == clave }
        _listas.value = _listas.value.mapValues { (_, xs) -> xs.filterNot { it.autor.usuario == _usuario.value && (it.datos["clave"] as? JsonPrimitive)?.content == clave } }
        fun quitar(xs: List<PublicacionSocial>?) = xs?.filterNot { it.autor.usuario == _usuario.value && (it.datos["clave"] as? JsonPrimitive)?.content == clave }
        _feed.value = quitar(_feed.value); _explorar.value = quitar(_explorar.value); _guardados.value = quitar(_guardados.value)
        _vistas.value = _vistas.value.filterValues { x -> x.autor.usuario != _usuario.value || (x.datos["clave"] as? JsonPrimitive)?.content != clave }
        _perfiles.value = _perfiles.value.mapValues { (_, perfil) -> perfil.copy(publicaciones = quitar(perfil.publicaciones).orEmpty()) }
        if (p != null && eliminarEnRed) lanzar { cliente().borrarPublicacion(p.id) }
        sincronizar()
    }

    suspend fun portadas(fuente: String) = cliente().portadas(fuente)
    fun votarPortada(fuente: String, url: String?, terminado: suspend () -> Unit) = lanzar {
        cliente().votarPortada(fuente, url); terminado()
    }

    /* ----- Lo que la persona dice de lo que ve (todo en el teléfono, como sus gustos) ----- */

    /** "Me interesa" / "No me interesa": se guarda como un gusto de la obra o del creador de la publicación. */
    fun interesa(p: PublicacionSocial, si: Boolean) {
        val fuente = (p.datos["fuente"] as? JsonPrimitive)?.content.orEmpty()
        val creador = (p.datos["creador"] as? JsonPrimitive)?.content.orEmpty()
        val titulo = (p.datos["titulo"] as? JsonPrimitive)?.content.orEmpty()
        val (clave, nombre) = when {
            fuente.isNotBlank() -> "obra:$fuente" to titulo
            creador.isNotBlank() -> "artista:${creador.lowercase()}" to creador
            else -> "red:${p.autor.usuario}" to "@${p.autor.usuario}"
        }
        val senal = if (si) io.github.fogonapps.cajon.datos.Senales.GUSTA else io.github.fogonapps.cajon.datos.Senales.NO_INTERESA
        cambiar { e -> e.copy(gustos = e.gustos.filter { it.clave != clave } + io.github.fogonapps.cajon.datos.Gusto(clave, nombre, senal, cambio = System.currentTimeMillis())) }
        if (!si) ocultar(p.id)
        decir(if (si) R.string.menu_anotado_interesa else R.string.menu_anotado_no)
    }

    /** Silenciar a alguien: sus publicaciones no salen en Explorar ni en el feed (sin que se entere). */
    fun silenciar(usuario: String) {
        cambiar { e -> e.copy(gustos = e.gustos.filter { it.clave != "red:$usuario" } +
            io.github.fogonapps.cajon.datos.Gusto("red:$usuario", "@$usuario", io.github.fogonapps.cajon.datos.Senales.EXCLUIR, cambio = System.currentTimeMillis())) }
        decir(R.string.menu_silenciado)
    }

    /* ----- Vistas y notas ----- */

    private val porContar = java.util.Collections.synchronizedSet(LinkedHashSet<Long>())
    private var contando: Job? = null
    /** Una publicación que se vio en pantalla: se juntan y se mandan juntas a los 3 segundos. */
    fun vista(id: Long) {
        if (_usuario.value == null || !porContar.add(id)) return
        contando?.cancel()
        contando = scope.launch { delay(3000); val ids = synchronized(porContar) { porContar.toList() }; runCatching { cliente().vio(ids) } }
    }

    /** Las notas y las historias de 24 horas viven en el controlador común ([cargarHistorias]). */
    override suspend fun recargarFeed() {
        super.recargarFeed()
        cargarHistorias()
    }

    /** Quita de la red una entrada del diario o una lista (la vuelve privada en el cajón). */
    fun quitarDeLaRed(clave: String) {
        when {
            clave.startsWith("r:") -> { val (_, obra, reg) = clave.split(":", limit = 3)
                cambiar { e -> e.copy(obras = e.obras.map { o -> if (o.id != obra) o else o.copy(cambio = System.currentTimeMillis(),
                    registros = o.registros.map { if (it.id == reg) it.copy(visibilidad = Visibilidad.PRIVADA) else it }) }) } }
            clave.startsWith("l:") -> { val id = clave.removePrefix("l:")
                cambiar { e -> e.copy(listas = e.listas.map { if (it.id == id) it.copy(visibilidad = Visibilidad.PRIVADA, cambio = System.currentTimeMillis()) else it }) } }
            clave.startsWith("f:") -> { borrarPropia(clave.removePrefix("f:")); return }
        }
        sincronizarYa(); cargarFeed(); decir(R.string.red_quitada)
    }

    /* ----- Moderación ----- */

    override fun cargarCasos() = lanzar {
        val c = _cuenta.value ?: return@lanzar
        if (necesitaElevar(c, System.currentTimeMillis())) return@lanzar
        if ("ver_reportes" in c.permisos) _casos.value = cliente().casos()
        if ("gestionar_frases" in c.permisos) _frasesPendientes.value = cliente().frasesPendientes()
    }
    private val _frasesPendientes = MutableStateFlow<List<io.github.fogonapps.cajon.datos.FraseComunidad>>(emptyList())
    /** Las frases que propuso la gente y esperan que alguien del equipo las apruebe. */
    val frasesPendientes: StateFlow<List<io.github.fogonapps.cajon.datos.FraseComunidad>> = _frasesPendientes
    fun decidirFrase(clave: String, aprobar: Boolean) = lanzar {
        cliente().decidirFrase(clave, aprobar); _frasesPendientes.value = _frasesPendientes.value.filter { it.clave != clave }
    }
    /* ----- Avisos ----- */

    /* ----- Publicar a mano ----- */

    /** Publica texto con fotos o un vídeo preparado en el teléfono, subiendo los archivos primero. */
    fun publicar(texto: String, obra: String?, fotos: List<android.net.Uri>, visibilidad: String, spoiler: Boolean, video: android.net.Uri? = null, listo: () -> Unit): Job {
        if (publicacionEnCurso?.isActive == true) return publicacionEnCurso!!
        return scope.launch {
            if (!caja.redPermitida) { decir(R.string.politica_pausa); return@launch }
            _publicando.value = true
            var preparado: io.github.fogonapps.cajon.datos.VideoPreparado? = null
            try {
                require(video == null || fotos.isEmpty())
                val ok = intentar {
                    val marca = sesion.actual()
                    val clientePublicacion = cliente()
                    if (video != null) {
                        _progresoVideo.value = 0f
                        preparado = try {
                            io.github.fogonapps.cajon.datos.prepararVideo(app, video, { _progresoVideo.value = it }, { decir(R.string.video_recortado) })
                        } catch (x: kotlinx.coroutines.CancellationException) { throw x }
                        catch (_: Exception) { throw ErrorSocial(400, "video_formato") }
                    }
                    val nombreVideo = preparado?.let { clientePublicacion.subirVideo(it.archivo) }.orEmpty()
                    val poster = preparado?.let { clientePublicacion.subirImagen(it.poster) }.orEmpty()
                    if (!sesion.vigente(marca)) return@intentar
                    val imagenes = fotos.take(10).map { u -> jpeg(u.toString(), 1600, 900_000)?.let { cliente().subirImagen(it) } ?: throw IOException("sin imagen") }
                    val ahora = System.currentTimeMillis()
                    val p = PublicacionPropia("p${ahora.toString(36)}", ahora, texto.trim().take(2000), obra.orEmpty(), visibilidad = visibilidad, spoiler = spoiler, cambio = ahora, imagenes = imagenes, video = nombreVideo, poster = poster, duracionVideo = preparado?.segundos ?: 0)
                    cambiar { e -> e.copy(publicaciones = e.publicaciones + p) }
                    subirAhora()
                }
                if (ok) { decir(R.string.red_publicada); cargarFeed(); listo() }
            } finally { preparado?.archivo?.delete(); _progresoVideo.value = null; _publicando.value = false }
        }.also { publicacionEnCurso = it }
    }

    /**
     * Borrar una publicación propia desde su pantalla: se pide al servidor por su id, así se borra aunque la haya
     * subido otro teléfono o el cajón de la cuenta esté sin abrir; después se quita de lo cargado y del cajón local.
     */
    fun borrarPublicacion(id: Long, clave: String) = lanzar {
        cliente().borrarPublicacion(id)
        if (clave.startsWith("o:") || clave.startsWith("r:") || clave.startsWith("n:")) {
            cambiar { e -> e.copy(resenas = e.resenas.map { if (it.id == clave) it.copy(borrada = true, cambio = System.currentTimeMillis()) else it }) }
            borrarResena(clave, eliminarEnRed = false)
        } else borrarPropia(clave.removePrefix("f:"))
    }

    fun borrarPropia(clave: String) {
        val memoria = MemoriaPublicaciones(_feed.value, _explorar.value, _guardados.value, _vistas.value, _perfiles.value)
            .sinPropia(_usuario.value.orEmpty(), clave)
        _feed.value = memoria.feed; _explorar.value = memoria.explorar; _guardados.value = memoria.guardadas
        _vistas.value = memoria.vistas; _perfiles.value = memoria.perfiles
        cambiar { e -> e.copy(publicaciones = e.publicaciones.map { if (it.clave == clave) it.copy(borrada = true, cambio = System.currentTimeMillis()) else it }) }
        sincronizarYa()
    }

    /* ----- Lo que se sube ----- */

    /** Una foto (de un archivo o del selector) achicada a [lado] px y en JPEG de menos de [max] bytes. */
    private suspend fun jpeg(uri: String, lado: Int, max: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = app.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() } ?: return@runCatching null
            val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= lado) muestra *= 2
            val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return@runCatching null
            val escala = minOf(1f, lado.toFloat() / maxOf(original.width, original.height))
            val chica = android.graphics.Bitmap.createScaledBitmap(original, (original.width * escala).toInt().coerceAtLeast(1), (original.height * escala).toInt().coerceAtLeast(1), true)
            var calidad = 88
            var salida: ByteArray
            do {
                salida = java.io.ByteArrayOutputStream().also { chica.compress(android.graphics.Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
                calidad -= 12
            } while (salida.size > max && calidad > 30)
            salida.takeIf { it.size <= max }
        }.getOrNull()
    }

    companion object {
        internal data class MemoriaPublicaciones(
            val feed: List<PublicacionSocial>?, val explorar: List<PublicacionSocial>?,
            val guardadas: List<PublicacionSocial>?, val vistas: Map<Long, PublicacionSocial>,
            val perfiles: Map<String, PerfilSocial>,
        ) {
            fun sinPropia(usuario: String, clave: String): MemoriaPublicaciones {
                fun esPropia(p: PublicacionSocial) = p.autor.usuario == usuario &&
                    (texto(p, "clave") == "f:$clave" || texto(p, "propia") == clave)
                fun quitar(lista: List<PublicacionSocial>?) = lista?.filterNot(::esPropia)
                return copy(feed = quitar(feed), explorar = quitar(explorar), guardadas = quitar(guardadas),
                    vistas = vistas.filterValues { !esPropia(it) },
                    perfiles = perfiles.mapValues { (_, p) -> p.copy(publicaciones = quitar(p.publicaciones)!!) })
            }
        }
        internal data class EntradaPerfil(val estado: Estado, val subir: Boolean)

        internal fun planEntrada(e: Estado, usuario: String, nueva: Boolean,
            remoto: PerfilSocial? = null, foto: String = "", nombre: String = ""): EntradaPerfil = if (nueva) {
            // El apodo elegido al crear la cuenta es el del perfil: si no, se subiría el nombre local (a veces vacío).
            EntradaPerfil(e.copy(ajustes = if (nombre.isBlank()) e.ajustes else e.ajustes.copy(nombre = nombre.take(40), cambio = System.currentTimeMillis()),
                perfil = e.perfil.copy(usuario = usuario,
                visibilidad = PartesPerfil.AL_CREAR_CUENTA + e.perfil.visibilidad,
                cambio = System.currentTimeMillis())), subir = true)
        } else {
            EntradaPerfil(aplicarPerfilPropio(e, requireNotNull(remoto), foto), subir = false)
        }

        internal fun puedeSubirPerfil(pendiente: Boolean, recuperado: Boolean, bloqueado: Boolean) =
            !pendiente && recuperado && !bloqueado

        internal fun accionFoto(foto: String, ultima: String?, retirada: Boolean): String = when {
            foto.isBlank() && retirada -> "borrar"
            foto.isNotBlank() && foto != ultima -> "subir"
            else -> "ninguna"
        }

        private fun aplicarPerfilPropio(e: Estado, p: PerfilSocial, foto: String): Estado {
            val ahora = System.currentTimeMillis()
            return e.copy(ajustes = e.ajustes.copy(nombre = p.persona.nombre.ifBlank { e.ajustes.nombre }, cambio = ahora),
                perfil = e.perfil.copy(usuario = p.persona.usuario, bio = p.bio, portada = p.portada,
                    enlaces = p.enlaces, foto = foto, encuadreFoto = io.github.fogonapps.cajon.datos.Encuadre(),
                    encuadrePortada = io.github.fogonapps.cajon.datos.Encuadre(), cambio = ahora))
        }

        /** La versión de la política que conoce esta versión de la app (el servidor dice la vigente). */
        const val POLITICA_CONOCIDA = io.github.fogonapps.cajon.datos.POLITICA_VIGENTE

        private fun https(s: String) = s.takeIf { it.startsWith("https://") }.orEmpty()

        private fun obraBreve(o: Obra) = buildJsonObject {
            put("titulo", o.titulo); put("tipoObra", o.tipo); put("portada", https(o.portada))
            o.anio?.let { put("anio", it) }
            if (o.creador.isNotBlank()) put("creador", o.creador)
            if (o.fuente.isNotBlank()) put("fuente", o.fuente)
        }

        /** Por qué sale una publicación en Explorar (lo que se muestra en la tarjeta). */
        sealed interface Motivo {
            data class TienesLaObra(val titulo: String) : Motivo
            data class TeGustaElCreador(val creador: String) : Motivo
            data class TusEtiquetas(val etiqueta: String) : Motivo
            data object Nueva : Motivo
        }

        private fun texto(p: PublicacionSocial, k: String) = (p.datos[k] as? JsonPrimitive)?.content.orEmpty()
        private fun etiquetas(s: String) = Regex("#([\\p{L}\\p{N}_]{2,40})").findAll(s).map { it.groupValues[1].lowercase() }.toSet()

        /** Lo que el teléfono sabe de la persona para ordenar (se calcula una vez por lista; nada de esto sale del teléfono). */
        private class Gusto(e: Estado) {
            val obras = e.vivas.filter { it.fuente.isNotBlank() }.associateBy { it.fuente }
            val creadores = (e.vivas.filter { it.meGusta || it.nota >= 7 || it.estado == io.github.fogonapps.cajon.datos.Estados.TERMINADO }.map { it.creador } +
                e.artistasVivos.filter { it.favorito || it.nota >= 7 }.map { it.nombre }).map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
            val dichos = e.gustos.filter { !it.borrada }
            val generos = dichos.filter { it.clave.startsWith("genero:") && it.senal == io.github.fogonapps.cajon.datos.Senales.GUSTA }
                .map { it.clave.removePrefix("genero:").lowercase().filter { c -> c.isLetterOrDigit() } }.toSet()
            val fuera = dichos.filter { it.senal in setOf(io.github.fogonapps.cajon.datos.Senales.EXCLUIR, io.github.fogonapps.cajon.datos.Senales.NO_INTERESA,
                io.github.fogonapps.cajon.datos.Senales.NO_RECOMENDAR, io.github.fogonapps.cajon.datos.Senales.NO_GUSTA) }.map { it.clave.lowercase() }.toSet()
            val susEtiquetas = e.publicaciones.flatMap { etiquetas(it.texto) }.toSet() +
                e.vivas.flatMap { o -> o.registros.flatMap { etiquetas(it.texto) } }.toSet()
        }

        /** La afinidad (0 a 1) y su motivo principal. */
        private fun afinidad(p: PublicacionSocial, g: Gusto): Pair<Double, Motivo?> {
            var bruta = 0.0; var motivo: Motivo? = null
            g.obras[texto(p, "fuente")]?.let { o ->
                bruta += when { o.nota in 1..4 || o.estado == io.github.fogonapps.cajon.datos.Estados.ABANDONADO -> -3.0; o.meGusta || o.nota >= 8 -> 3.0; else -> 1.0 }
                if (bruta > 0) motivo = Motivo.TienesLaObra(o.titulo)
            }
            val creador = texto(p, "creador").lowercase().trim()
            if (creador.isNotBlank() && creador in g.creadores) { bruta += 2; if (motivo == null) motivo = Motivo.TeGustaElCreador(texto(p, "creador")) }
            val suyas = etiquetas(texto(p, "texto"))
            val comunes = suyas.filter { it in g.susEtiquetas || it in g.generos }
            if (comunes.isNotEmpty()) { bruta += minOf(2.0, comunes.size.toDouble()); if (motivo == null) motivo = Motivo.TusEtiquetas(comunes.first()) }
            return (bruta / 6).coerceIn(0.0, 1.0) to motivo
        }

        private fun excluida(p: PublicacionSocial, g: Gusto) =
            "obra:${texto(p, "fuente")}".lowercase() in g.fuera || "artista:${texto(p, "creador")}".lowercase() in g.fuera ||
                "red:${p.autor.usuario}".lowercase() in g.fuera

        /** Lo que no se muestra en ninguna lista: silenciado, escondido, o con una palabra oculta. */
        fun seMuestra(p: PublicacionSocial, e: Estado, ocultas: Set<Long>): Boolean {
            if (p.id in ocultas) return false
            if (e.gustos.any { !it.borrada && it.clave == "red:${p.autor.usuario}" && it.senal == io.github.fogonapps.cajon.datos.Senales.EXCLUIR }) return false
            return !io.github.fogonapps.cajon.ui.lleva(e.ajustes.palabrasOcultas, texto(p, "texto") + " " + texto(p, "titulo"))
        }

        /**
         * Explorar, ordenado en el teléfono: puntaje = afinidad con lo
         * tuyo + 0,6 × calidad (la del servidor, igual para todos) + 0,4 × frescura (la mitad cada 3 días). Cada cinco,
         * una nueva con pocas vistas, para que tenga su oportunidad; nunca dos seguidas de la misma persona ni de la
         * misma obra, y máximo dos por persona cada veinte. Lo que marcaste que no te interesa no sale.
         */
        fun ordenarPorGustos(lista: List<PublicacionSocial>, e: Estado, ahora: Long = System.currentTimeMillis()): List<Pair<PublicacionSocial, Motivo?>> {
            val g = Gusto(e)
            val puntuadas = lista.filter { !excluida(it, g) && !io.github.fogonapps.cajon.ui.lleva(e.ajustes.palabrasOcultas, texto(it, "texto") + " " + texto(it, "titulo")) }.map { p ->
                val (a, motivo) = afinidad(p, g)
                val horas = ((ahora - p.cuando) / 3_600_000.0).coerceAtLeast(0.0)
                val s = a + 0.6 * (p.calidad.coerceIn(0.0, 2.0) / 2) + 0.4 * Math.pow(2.0, -horas / 72)
                Triple(p, s, motivo)
            }
            val esNueva = { p: PublicacionSocial -> p.vistas < 30 && ahora - p.cuando < 72 * 3_600_000L }
            val nuevas = puntuadas.filter { esNueva(it.first) }.sortedByDescending { it.second }.toMutableList()
            val resto = puntuadas.filterNot { esNueva(it.first) }.sortedByDescending { it.second }.toMutableList()
            val salida = mutableListOf<Pair<PublicacionSocial, Motivo?>>()
            val porAutor = HashMap<String, Int>()
            fun puede(p: PublicacionSocial): Boolean {
                val ultima = salida.lastOrNull()?.first
                if (ultima != null && (ultima.autor.usuario == p.autor.usuario || texto(ultima, "fuente").let { it.isNotBlank() && it == texto(p, "fuente") })) return false
                return (porAutor[p.autor.usuario] ?: 0) < 2 + salida.size / 20 * 2
            }
            while (nuevas.isNotEmpty() || resto.isNotEmpty()) {
                val tocaNueva = (salida.size + 1) % 5 == 0 && nuevas.isNotEmpty()
                val fuente = if (tocaNueva || resto.isEmpty()) nuevas else resto
                val i = fuente.indexOfFirst { puede(it.first) }.takeIf { it >= 0 } ?: 0   // si nada cumple, la mejor
                val (p, _, motivo) = fuente.removeAt(i)
                salida += p to (if (tocaNueva && motivo == null) Motivo.Nueva else motivo)
                porAutor[p.autor.usuario] = (porAutor[p.autor.usuario] ?: 0) + 1
            }
            return salida
        }

        /** La canción del perfil (de cualquiera de los dos formatos de [Perfil.cancion]). */
        data class CancionPerfil(val titulo: String, val artista: String, val album: String, val portada: String, val audio: String = "", val enlace: String = "")

        fun cancionDe(e: Estado): CancionPerfil? {
            val c = e.perfil.cancion
            // v3 lleva además el avance de 30 segundos y el enlace a Apple Music.
            if (c.startsWith("v3|")) return c.split("|").let { p -> if (p.size >= 7 && p[1].isNotBlank()) CancionPerfil(p[1], p[2], p[3], p[4], p[5], p[6]) else null }
            if (c.startsWith("v2|")) return c.split("|").let { p -> if (p.size >= 5 && p[1].isNotBlank()) CancionPerfil(p[1], p[2], p[3], p[4]) else null }
            val (id, titulo) = c.split("|", limit = 2).takeIf { it.size == 2 } ?: return null
            return e.vivas.firstOrNull { it.id == id }?.let { CancionPerfil(titulo, it.creador, it.titulo, it.portada) }
        }

        /** El perfil que se sube: la identidad y las partes que la persona marcó como públicas o para sus seguidores. */
        /** El valor de una cifra del perfil ([PartesPerfil].C_…), igual para tu perfil y para lo que ve la red. */
        fun valorCifra(c: String, e: Estado, hoy: LocalDate): String {
            val delAnio = io.github.fogonapps.cajon.datos.Logica.cifras(e, hoy.year)
            val siempre = io.github.fogonapps.cajon.datos.Logica.cifras(e, null)
            return when (c) {
                PartesPerfil.C_TERMINADAS -> "${delAnio.porTipo.values.sum()}"
                PartesPerfil.C_HORAS_CINE -> "${siempre.minutosCine / 60}"
                PartesPerfil.C_HORAS_SERIES -> "${siempre.minutosSeries / 60}"
                PartesPerfil.C_PAGINAS -> "${siempre.paginas}"
                PartesPerfil.C_CANCIONES -> "${siempre.canciones}"
                PartesPerfil.C_DIARIO -> "${siempre.registros}"
                PartesPerfil.C_RACHA -> "${io.github.fogonapps.cajon.datos.Logica.racha(e, hoy)}"
                else -> siempre.notaMedia?.let { "%.1f".format(it) } ?: "–"
            }
        }

        fun perfilDe(e: Estado): PerfilSubido {
            val p = e.perfil
            val porId = e.vivas.associateBy { it.id }
            fun parte(nombre: String, datos: kotlinx.serialization.json.JsonElement): Pair<String, ParteSubida>? =
                Visibilidad.de(p.visibilidad[nombre]).takeIf { it != Visibilidad.PRIVADA }?.let { nombre to ParteSubida(it, datos) }
            // Los artistas que más le gustan (corazón, nota y escuchas) y los géneros que dijo.
            val artistas = e.musicosVivos.sortedWith(compareByDescending<io.github.fogonapps.cajon.datos.Artista> { it.favorito }.thenByDescending { it.nota }).take(12)
            val generos = e.gustos.filter { !it.borrada && it.clave.startsWith("genero:") && it.senal == io.github.fogonapps.cajon.datos.Senales.GUSTA }.map { it.clave.removePrefix("genero:") }.take(20)
            val terminadas = e.vivas.filter { it.estado == io.github.fogonapps.cajon.datos.Estados.TERMINADO }.groupingBy { it.tipo }.eachCount()
            val partes = listOfNotNull(
                parte(PartesPerfil.MUSICA, JsonArray(artistas.map { a -> buildJsonObject { put("nombre", a.nombre); put("foto", https(a.foto)) } })).takeIf { artistas.isNotEmpty() },
                parte(PartesPerfil.GUSTOS, JsonArray(generos.map { JsonPrimitive(it) })).takeIf { generos.isNotEmpty() },
                parte(PartesPerfil.CIFRAS, JsonObject(terminadas.mapValues { JsonPrimitive(it.value) } + mapOf(
                    "anio" to JsonPrimitive(java.time.LocalDate.now().year),
                    "valores" to JsonObject((p.cifras ?: PartesPerfil.CIFRAS_DE_SIEMPRE).associateWith { JsonPrimitive(valorCifra(it, e, java.time.LocalDate.now())) }),
                ))),
                parte(PartesPerfil.GUARDADAS, JsonPrimitive("")),
                parte(PartesPerfil.FAVORITAS, JsonArray(e.ajustes.favoritas.mapNotNull { porId[it]?.let(::obraBreve) })),
                parte(PartesPerfil.DESTACADOS, JsonObject(p.destacados.mapNotNull { (hueco, id) ->
                    porId[id]?.let { o -> hueco to JsonObject(obraBreve(o) + (p.porQue[hueco]?.let { mapOf("porQue" to JsonPrimitive(it)) } ?: emptyMap())) }
                }.toMap())),
            ).toMap() + listOfNotNull(cancionDe(e)?.let { c ->
                "cancion" to ParteSubida(Visibilidad.PUBLICA, buildJsonObject {
                    put("cancion", c.titulo); put("creador", c.artista); put("titulo", c.album); put("portada", https(c.portada))
                    if (c.audio.startsWith("https://audio-ssl.itunes.apple.com/")) put("audio", c.audio)
                    if (c.enlace.startsWith("https://music.apple.com/")) put("enlace", c.enlace)
                })
            })
            return PerfilSubido(e.ajustes.nombre.take(40), p.bio.take(200), https(p.portada), p.enlaces.filter { it.startsWith("http") }.take(5), partes)
        }

        /** Lo que se publica: el diario y las reseñas compartidos, las listas compartidas y lo publicado a mano. */
        fun publicacionesDe(e: Estado): List<PublicacionSubida> {
            val diario = Visibilidad.de(e.perfil.visibilidad[PartesPerfil.DIARIO])
            val porId = e.vivas.associateBy { it.id }
            val delDiario = e.vivas.flatMap { o ->
                o.registros.mapNotNull { r ->
                    val vis = if (r.visibilidad.isBlank()) diario else Visibilidad.de(r.visibilidad)
                    if (vis == Visibilidad.PRIVADA || e.resenas.any { it.obra == o.id && it.registro == r.id }) return@mapNotNull null
                    val cuando = runCatching { LocalDate.parse(r.fecha).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrDefault(o.cambio)
                    PublicacionSubida("r:${o.id}:${r.id}", vis, cuando, "diario",
                        JsonObject(obraBreve(o) + buildJsonObject {
                            if (r.nota > 0) put("estrellas", r.nota / 2.0)
                            if (r.otraVez) put("otraVez", true)
                            put("fecha", r.fecha)
                        }))
                }
            }
            val resenas = e.resenas.filter { !it.borrada && Visibilidad.de(it.visibilidad) != Visibilidad.PRIVADA }.mapNotNull { r ->
                val o = io.github.fogonapps.cajon.datos.Resenas.obra(e, r.obra) ?: return@mapNotNull null
                val cuando = runCatching { LocalDate.parse(r.fecha).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrDefault(r.cambio)
                PublicacionSubida(r.id, Visibilidad.de(r.visibilidad), cuando, "resena", JsonObject(obraBreve(o) + buildJsonObject {
                    put("fuente", o.fuente); put("parte", r.parte); put("texto", r.texto.take(5000))
                    r.nota?.let { put("nota", it); put("estrellas", it / 2.0) }
                    put("spoiler", r.spoiler)
                    put("fotos", JsonArray(r.fotos.take(4).map { JsonPrimitive(it) }))
                    put("fecha", r.fecha)
                }))
            }
            val listas = e.listas.filter { !it.borrada }.mapNotNull { l ->
                val vis = Visibilidad.de(l.visibilidad).takeIf { it != Visibilidad.PRIVADA } ?: return@mapNotNull null
                val obras = l.obras.mapNotNull { porId[it] }
                PublicacionSubida("l:${l.id}", vis, l.cambio, "lista", buildJsonObject {
                    put("titulo", l.nombre); if (l.descripcion.isNotBlank()) put("texto", l.descripcion.take(2000))
                    put("cuantas", obras.size)
                    put("portadas", JsonArray(obras.map { https(it.portada) }.filter { it.isNotEmpty() }.take(4).map { JsonPrimitive(it) }))
                })
            }
            val propias = e.publicaciones.filter { !it.borrada && it.visibilidad != Visibilidad.PRIVADA }.map { p ->
                PublicacionSubida("f:${p.clave}", Visibilidad.de(p.visibilidad), p.cuando, "foto", JsonObject(
                    (porId[p.obra]?.let(::obraBreve) ?: buildJsonObject { }) + buildJsonObject {
                        if (p.texto.isNotBlank()) put("texto", p.texto)
                        if (p.video.isNotBlank()) {
                            put("video", p.video); put("poster", p.poster); put("duracion", p.duracionVideo)
                        }
                        val imagenes = if (p.video.isNotBlank()) emptyList() else p.imagenes.ifEmpty { listOfNotNull(p.imagen.takeIf { it.isNotBlank() }) }
                        put("imagenes", JsonArray(imagenes.map { JsonPrimitive(it) }))
                        imagenes.firstOrNull()?.let { put("imagen", it) }
                        if (p.spoiler) put("spoiler", true)
                        put("propia", p.clave)
                    }))
            }
            // Con su clave adentro, para poder quitarla de la red desde la publicación.
            return (delDiario + resenas + listas + propias).sortedByDescending { it.cuando }.take(2000)
                .map { it.copy(datos = JsonObject(it.datos + ("clave" to JsonPrimitive(it.clave)))) }
        }
    }
}
